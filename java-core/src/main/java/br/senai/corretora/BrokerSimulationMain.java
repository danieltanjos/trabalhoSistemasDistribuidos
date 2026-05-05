package br.senai.corretora;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;

public class BrokerSimulationMain {
    public static void main(String[] args) throws Exception {
        runDemo();
    }

    public static void runDemo() throws Exception {
        Thread casa1 = new Thread(() -> sendOrders("casa-1", List.of(
                "1,joao,PETR4,BUY,30.00,10",
                "2,carlos,VALE3,BUY,60.00,8",
                "3,joao,BTC,BUY,100.00,1"
        )));
        Thread casa2 = new Thread(() -> sendOrders("casa-2", List.of(
                "4,maria,PETR4,SELL,29.00,5",
                "5,ana,VALE3,SELL,60.00,8",
                "6,maria,BTC,SELL,100.00,1"
        )));

        casa1.start();
        casa2.start();
        casa1.join();
        casa2.join();
    }

    private static void sendOrders(String brokerId, List<String> orders) {
        try (Socket socket = new Socket("127.0.0.1", 5000);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {
            for (String order : orders) {
                writer.println(order);
                System.out.println("[simulador] " + brokerId + " enviou " + order);
                Thread.sleep(300);
            }
        } catch (Exception ignored) {
        }
    }
}
