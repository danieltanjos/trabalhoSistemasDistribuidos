package br.senai.corretora;

import java.rmi.Naming;
import java.util.List;

public class MonitoringPanelMain {
    public static void main(String[] args) throws Exception {
        while (true) {
            printSnapshot();
            Thread.sleep(5000);
        }
    }

    public static void printSnapshot() throws Exception {
        MonitoringService service = (MonitoringService) Naming.lookup("rmi://localhost/MonitoringService");
        List<String> ativos = List.of("PETR4", "VALE3", "BTC", "ETH");

        System.out.println("==== Painel ====");
        System.out.println(service.consultarResumoMercado());
        for (String ativo : ativos) {
            System.out.printf("%s -> ultima=%.2f volume=%d%n",
                    ativo,
                    service.consultarUltimaCotacao(ativo),
                    service.consultarVolumeNegociado(ativo));
        }
    }
}
