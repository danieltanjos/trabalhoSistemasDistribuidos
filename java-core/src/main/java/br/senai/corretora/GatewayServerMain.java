package br.senai.corretora;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GatewayServerMain {
    public static void main(String[] args) throws Exception {
        startServer();
    }

    public static void startServer() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(5000)) {
            System.out.println("[gateway] TCP ouvindo na porta 5000");
            while (true) {
                Socket brokerSocket = serverSocket.accept();
                pool.submit(() -> handleBroker(brokerSocket));
            }
        }
    }

    private static void handleBroker(Socket brokerSocket) {
        try (brokerSocket;
             Socket coreSocket = connectToCore();
             BufferedReader reader = new BufferedReader(new InputStreamReader(brokerSocket.getInputStream()));
             PrintWriter writer = new PrintWriter(coreSocket.getOutputStream(), true)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    writer.println(line.trim());
                    System.out.println("[gateway] encaminhada: " + line.trim());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static Socket connectToCore() throws Exception {
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                return new Socket("localhost", 5100);
            } catch (Exception exc) {
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("core indisponivel");
    }
}
