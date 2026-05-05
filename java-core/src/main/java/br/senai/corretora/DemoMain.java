package br.senai.corretora;

import io.grpc.Server;

import java.net.Socket;
import java.rmi.Naming;

public class DemoMain {
    public static void main(String[] args) throws Exception {
        Server custodyServer = CustodyServerMain.startServer();

        Thread coreThread = new Thread(() -> runQuietly(() -> BrokerServerMain.startServer()), "core");
        coreThread.setDaemon(true);
        coreThread.start();

        Thread gatewayThread = new Thread(() -> runQuietly(() -> GatewayServerMain.startServer()), "gateway");
        gatewayThread.setDaemon(true);
        gatewayThread.start();

        waitForPort(50051);
        waitForPort(5000);
        waitForMonitoring();

        BrokerSimulationMain.runDemo();
        Thread.sleep(1500);
        MonitoringPanelMain.printSnapshot();

        custodyServer.shutdownNow();
        System.exit(0);
    }

    private static void runQuietly(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception exc) {
            throw new RuntimeException(exc);
        }
    }

    private static void waitForPort(int port) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            try (Socket ignored = new Socket("localhost", port)) {
                return;
            } catch (Exception exc) {
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("porta nao ficou pronta: " + port);
    }

    private static void waitForMonitoring() throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            try {
                Naming.lookup("rmi://localhost/MonitoringService");
                return;
            } catch (Exception exc) {
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("servico RMI nao ficou pronto");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
