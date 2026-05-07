package br.senai.corretora;

import br.senai.custodia.grpc.AssinaturaBrokerRequest;
import br.senai.custodia.grpc.CustodiaServiceGrpc;
import br.senai.custodia.grpc.LiquidacaoRequest;
import br.senai.custodia.grpc.LiquidacaoResponse;
import br.senai.custodia.grpc.SaldoUpdate;
import br.senai.custodia.grpc.ValidarOrdemRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class BrokerServerMain {
    private static final int TCP_PORT = 5100;
    private static final BrokerState STATE = new BrokerState();

    public static void main(String[] args) throws Exception {
        startServer();
    }

    public static void startServer() throws Exception {
        STATE.startCustodyStream();

        try {
            LocateRegistry.createRegistry(1099);
        } catch (RemoteException ignored) {
        }
        Naming.rebind("rmi://localhost/MonitoringService", new MonitoringRemote());
        System.out.println("[core] RMI pronto");

        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
            System.out.println("[core] TCP ouvindo na porta " + TCP_PORT);
            while (true) {
                Socket socket = serverSocket.accept();
                pool.submit(() -> handle(socket));
            }
        }
    }

    private static void handle(Socket socket) {
        try (socket; BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    STATE.process(line.trim());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static class MonitoringRemote extends UnicastRemoteObject implements MonitoringService {
        protected MonitoringRemote() throws RemoteException {
        }

        @Override
        public double consultarUltimaCotacao(String ativo) {
            return STATE.getLastPrice(ativo);
        }

        @Override
        public long consultarVolumeNegociado(String ativo) {
            return STATE.getVolume(ativo);
        }

        @Override
        public String consultarResumoMercado() {
            return STATE.getMarketSummary();
        }
    }

    private static class BrokerState {
        private final ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", 50051).usePlaintext().build();
        private final CustodiaServiceGrpc.CustodiaServiceBlockingStub custody = CustodiaServiceGrpc.newBlockingStub(channel);
        private final CustodiaServiceGrpc.CustodiaServiceStub custodyAsync = CustodiaServiceGrpc.newStub(channel);
        private final Map<String, List<Order>> buys = new HashMap<>();
        private final Map<String, List<Order>> sells = new HashMap<>();
        private final Map<String, Double> lastPrice = new HashMap<>();
        private final Map<String, Long> volume = new HashMap<>();
        private final AtomicLong sequence = new AtomicLong();
        private boolean suspended = false;
        private long trades = 0;

        synchronized void process(String line) {
            System.out.println("[core] ordem recebida: " + line);
            if (suspended) {
                System.out.println("[core] negociacoes suspensas, ordem descartada");
                return;
            }
            Order order;
            try {
                order = Order.parse(line);
            } catch (Exception exc) {
                return;
            }
            if (order == null) {
                return;
            }
            order.sequence = sequence.incrementAndGet();

            try {
                var validation = custody.validarOrdem(ValidarOrdemRequest.newBuilder()
                        .setInvestidor(order.investor)
                        .setAtivo(order.symbol)
                        .setLado(order.side)
                        .setPreco(order.price)
                        .setQuantidade(order.quantity)
                        .build());
                if (!validation.getAprovado()) {
                    System.out.println("[custodia] ordem rejeitada para " + order.investor + ": " + validation.getMotivo());
                    return;
                }
                suspended = false;
            } catch (StatusRuntimeException exc) {
                suspended = true;
                System.out.println("[core] custodia indisponivel, negociacoes suspensas");
                return;
            }

            List<Order> ownBook = order.side.equals("BUY")
                    ? buys.computeIfAbsent(order.symbol, key -> new LinkedList<>())
                    : sells.computeIfAbsent(order.symbol, key -> new LinkedList<>());
            ownBook.add(order);
            match(order.symbol);
        }

        private void match(String symbol) {
            List<Order> buyBook = buys.computeIfAbsent(symbol, key -> new LinkedList<>());
            List<Order> sellBook = sells.computeIfAbsent(symbol, key -> new LinkedList<>());

            while (true) {
                Order buy = bestBuy(buyBook);
                Order sell = bestSell(sellBook);
                if (buy == null || sell == null || buy.price < sell.price) {
                    return;
                }

                int qty = Math.min(buy.quantity, sell.quantity);
                double price = sell.price;

                try {
                    LiquidacaoResponse response = custody.liquidarOperacao(LiquidacaoRequest.newBuilder()
                            .setComprador(buy.investor)
                            .setVendedor(sell.investor)
                            .setAtivo(symbol)
                            .setPreco(price)
                            .setQuantidade(qty)
                            .build());
                    if (!response.getSucesso()) {
                        suspended = true;
                        System.out.println("[core] liquidacao rejeitada, negociacoes suspensas");
                        return;
                    }
                } catch (StatusRuntimeException exc) {
                    suspended = true;
                    System.out.println("[core] falha na liquidacao, negociacoes suspensas");
                    return;
                }

                buy.quantity -= qty;
                sell.quantity -= qty;
                if (buy.quantity == 0) {
                    buyBook.remove(buy);
                }
                if (sell.quantity == 0) {
                    sellBook.remove(sell);
                }

                lastPrice.put(symbol, price);
                volume.put(symbol, volume.getOrDefault(symbol, 0L) + qty);
                trades++;
                System.out.printf("[trade] %s qty=%d price=%.2f buyer=%s seller=%s%n",
                        symbol, qty, price, buy.investor, sell.investor);
            }
        }

        private Order bestBuy(List<Order> orders) {
            Order best = null;
            for (Order order : orders) {
                if (best == null
                        || order.price > best.price
                        || (order.price == best.price && order.sequence < best.sequence)) {
                    best = order;
                }
            }
            return best;
        }

        private Order bestSell(List<Order> orders) {
            Order best = null;
            for (Order order : orders) {
                if (best == null
                        || order.price < best.price
                        || (order.price == best.price && order.sequence < best.sequence)) {
                    best = order;
                }
            }
            return best;
        }

        synchronized double getLastPrice(String ativo) {
            return lastPrice.getOrDefault(ativo, 0.0);
        }

        synchronized long getVolume(String ativo) {
            return volume.getOrDefault(ativo, 0L);
        }

        synchronized String getMarketSummary() {
            return "suspenso=" + suspended +
                    ", ativos=" + new HashMap<>(lastPrice) +
                    ", volume=" + new HashMap<>(volume) +
                    ", negocios=" + trades;
        }

        private void startCustodyStream() {
            custodyAsync.acompanharSaldos(
                    AssinaturaBrokerRequest.newBuilder().setBrokerId("BROKER_CORE").build(),
                    new StreamObserver<>() {
                        @Override
                        public void onNext(SaldoUpdate saldoUpdate) {
                            System.out.printf("[custodia-stream] %s | %s | %s%n",
                                    saldoUpdate.getInvestidor(),
                                    saldoUpdate.getAtivo(),
                                    saldoUpdate.getDescricao());
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            suspended = true;
                        }

                        @Override
                        public void onCompleted() {
                            suspended = true;
                        }
                    }
            );
        }
    }

    private static class Order {
        private final String investor;
        private final String symbol;
        private final String side;
        private final double price;
        private long sequence;
        private int quantity;

        private Order(String investor, String symbol, String side, double price, int quantity) {
            this.investor = investor;
            this.symbol = symbol;
            this.side = side;
            this.price = price;
            this.quantity = quantity;
        }

        private static Order parse(String line) {
            String[] parts = line.split(",");
            if (parts.length < 6) {
                return null;
            }
            return new Order(
                    parts[1].trim(),
                    parts[2].trim(),
                    parts[3].trim().toUpperCase(),
                    Double.parseDouble(parts[4].trim()),
                    Integer.parseInt(parts[5].trim())
            );
        }
    }
}
