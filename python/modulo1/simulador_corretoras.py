import random
import socket
import threading
import time


GATEWAY_HOST = "127.0.0.1"
GATEWAY_PORT = 5000
ATIVOS = ["PETR4", "VALE3", "BTC", "ETH"]
INVESTIDORES = [f"INVESTIDOR_{i}" for i in range(1, 9)]


def gerar_ordem(broker_id, sequence):
    ativo = random.choice(ATIVOS)
    lado = random.choice(["BUY", "SELL"])
    preco_base = {
        "PETR4": 37.0,
        "VALE3": 63.0,
        "BTC": 320000.0,
        "ETH": 18000.0,
    }[ativo]
    preco = round(preco_base + random.uniform(-2.5, 2.5), 2)
    quantidade = random.randint(1, 5)
    investidor = random.choice(INVESTIDORES)
    return f"{broker_id},{investidor},{ativo},{lado},{preco},{quantidade},{sequence}"


def simular_corretora(broker_id, total_ordens=15, atraso=0.2):
    with socket.create_connection((GATEWAY_HOST, GATEWAY_PORT)) as sock:
        writer = sock.makefile("w", encoding="utf-8")
        for sequence in range(1, total_ordens + 1):
            ordem = gerar_ordem(broker_id, sequence)
            writer.write(ordem + "\n")
            writer.flush()
            print(f"[simulador] enviada: {ordem}")
            time.sleep(atraso)


def iniciar_simulacao(total_corretoras=4, ordens_por_corretora=15):
    workers = []
    for idx in range(1, total_corretoras + 1):
        worker = threading.Thread(
            target=simular_corretora,
            args=(f"CORRETORA_{idx}", ordens_por_corretora, 0.1),
            daemon=False,
        )
        workers.append(worker)
        worker.start()

    for worker in workers:
        worker.join()


if __name__ == "__main__":
    iniciar_simulacao()
