package br.senai.corretora;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class BrokerSimulationMain {
    public static void main(String[] args) throws Exception {
        runDemo();
    }

    public static void runDemo() throws Exception {
        List<Thread> brokers = new ArrayList<>();
        brokers.add(new Thread(() -> sendOrders("casa-1", List.of(
                "1,joao,PETR4,BUY,30.00,10",
                "2,carlos,VALE3,BUY,60.00,8",
                "3,joao,BTC,BUY,100.00,1",
                "4,bruno,ETH,BUY,210.00,1"
        ))));
        brokers.add(new Thread(() -> sendOrders("casa-2", List.of(
                "5,maria,PETR4,SELL,29.00,5",
                "6,ana,VALE3,SELL,60.00,8",
                "7,maria,BTC,SELL,100.00,1",
                "8,paula,PETR4,SELL,30.00,3"
        ))));
        brokers.add(new Thread(() -> sendOrders("casa-3", List.of(
                "9,lara,PETR4,BUY,31.00,4",
                "10,renato,VALE3,SELL,59.00,4",
                "11,sofia,ETH,BUY,205.00,1",
                "12,ana,ETH,SELL,205.00,1"
        ))));
        brokers.add(new Thread(() -> sendOrders("casa-4", List.of(
                "13,diego,VALE3,BUY,61.00,5",
                "14,marcos,BTC,SELL,99.00,1",
                "15,joao,PETR4,BUY,30.00,2",
                "16,maria,PETR4,SELL,30.00,2"
        ))));
        brokers.add(new Thread(() -> sendOrders("casa-5", List.of(
                "17,carla,PETR4,BUY,30.00,3",
                "18,bianca,VALE3,SELL,61.00,5",
                "19,gustavo,BTC,BUY,99.00,1",
                "20,heitor,ETH,SELL,210.00,1"
        ))));

        for (Thread broker : brokers) {
            broker.start();
        }
        for (Thread broker : brokers) {
            broker.join();
        }
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
