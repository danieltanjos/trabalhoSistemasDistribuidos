package br.senai.corretora;

import br.senai.custodia.grpc.AssinaturaBrokerRequest;
import br.senai.custodia.grpc.CustodiaServiceGrpc;
import br.senai.custodia.grpc.LiquidacaoRequest;
import br.senai.custodia.grpc.LiquidacaoResponse;
import br.senai.custodia.grpc.SaldoUpdate;
import br.senai.custodia.grpc.ValidarOrdemRequest;
import br.senai.custodia.grpc.ValidarOrdemResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustodyServerMain {
    public static void main(String[] args) throws Exception {
        Server server = startServer();
        server.awaitTermination();
    }

    public static Server startServer() throws IOException {
        Server server = ServerBuilder.forPort(50051)
                .addService(new CustodyService())
                .build()
                .start();
        System.out.println("[custodia] gRPC ouvindo na porta 50051");
        return server;
    }

    private static class CustodyService extends CustodiaServiceGrpc.CustodiaServiceImplBase {
        private final Map<String, Double> cash = new HashMap<>();
        private final Map<String, Map<String, Integer>> positions = new HashMap<>();
        private final List<StreamObserver<SaldoUpdate>> subscribers = new ArrayList<>();

        private CustodyService() {
            cash.put("joao", 10000.0);
            cash.put("carlos", 10000.0);
            cash.put("maria", 10000.0);
            cash.put("ana", 10000.0);

            setPosition("maria", "PETR4", 20);
            setPosition("ana", "VALE3", 20);
            setPosition("maria", "BTC", 2);
            setPosition("ana", "ETH", 3);
        }

        @Override
        public synchronized void validarOrdem(ValidarOrdemRequest request, StreamObserver<ValidarOrdemResponse> observer) {
            boolean approved;
            String reason = "ok";
            String investor = request.getInvestidor().toLowerCase();
            String symbol = request.getAtivo().toUpperCase();

            if ("BUY".equalsIgnoreCase(request.getLado())) {
                double needed = request.getPreco() * request.getQuantidade();
                approved = cash.getOrDefault(investor, 0.0) >= needed;
                if (!approved) {
                    reason = "saldo insuficiente";
                }
            } else {
                approved = getPosition(investor, symbol) >= request.getQuantidade();
                if (!approved) {
                    reason = "ativo insuficiente";
                }
            }

            observer.onNext(ValidarOrdemResponse.newBuilder()
                    .setAprovado(approved)
                    .setMotivo(reason)
                    .build());
            observer.onCompleted();
        }

        @Override
        public synchronized void liquidarOperacao(LiquidacaoRequest request, StreamObserver<LiquidacaoResponse> observer) {
            String buyer = request.getComprador().toLowerCase();
            String seller = request.getVendedor().toLowerCase();
            String symbol = request.getAtivo().toUpperCase();
            int quantity = request.getQuantidade();
            double total = request.getPreco() * quantity;

            if (cash.getOrDefault(buyer, 0.0) < total || getPosition(seller, symbol) < quantity) {
                observer.onNext(LiquidacaoResponse.newBuilder()
                        .setSucesso(false)
                        .setMensagem("falha na liquidacao")
                        .build());
                observer.onCompleted();
                return;
            }

            cash.put(buyer, cash.getOrDefault(buyer, 0.0) - total);
            cash.put(seller, cash.getOrDefault(seller, 0.0) + total);
            setPosition(buyer, symbol, getPosition(buyer, symbol) + quantity);
            setPosition(seller, symbol, getPosition(seller, symbol) - quantity);

            notifySubscribers(buyer, symbol, "compra liquidada");
            notifySubscribers(seller, symbol, "venda liquidada");

            observer.onNext(LiquidacaoResponse.newBuilder()
                    .setSucesso(true)
                    .setMensagem("ok")
                    .build());
            observer.onCompleted();
        }

        @Override
        public synchronized void acompanharSaldos(AssinaturaBrokerRequest request, StreamObserver<SaldoUpdate> observer) {
            subscribers.add(observer);
            System.out.println("[custodia] broker inscrito: " + request.getBrokerId());
        }

        private void notifySubscribers(String investor, String symbol, String description) {
            SaldoUpdate update = SaldoUpdate.newBuilder()
                    .setInvestidor(investor)
                    .setAtivo(symbol)
                    .setDescricao(description)
                    .build();

            subscribers.removeIf(observer -> {
                try {
                    observer.onNext(update);
                    return false;
                } catch (Exception exc) {
                    return true;
                }
            });
        }

        private void setPosition(String investor, String symbol, int quantity) {
            positions.computeIfAbsent(investor, key -> new HashMap<>()).put(symbol, quantity);
        }

        private int getPosition(String investor, String symbol) {
            return positions.getOrDefault(investor, Map.of()).getOrDefault(symbol, 0);
        }
    }
}
