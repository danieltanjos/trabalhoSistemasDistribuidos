package br.senai.corretora;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface MonitoringService extends Remote {
    double consultarUltimaCotacao(String ativo) throws RemoteException;

    long consultarVolumeNegociado(String ativo) throws RemoteException;

    String consultarResumoMercado() throws RemoteException;
}
