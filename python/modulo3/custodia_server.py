import queue
import threading
from concurrent import futures

import grpc

import custodia_pb2
import custodia_pb2_grpc


HOST = "0.0.0.0:50051"
INITIAL_CASH = 1_000_000.0
INITIAL_ASSET = 100
ATIVOS = ["PETR4", "VALE3", "BTC", "ETH"]


class CustodiaService(custodia_pb2_grpc.CustodiaServiceServicer):
    def __init__(self):
        self._lock = threading.Lock()
        self._cash_balances = {}
        self._asset_balances = {}
        self._subscribers = []

    def _ensure_investidor(self, investidor):
        if investidor not in self._cash_balances:
            self._cash_balances[investidor] = INITIAL_CASH
            self._asset_balances[investidor] = {ativo: INITIAL_ASSET for ativo in ATIVOS}

    def ValidarOrdem(self, request, context):
        with self._lock:
            self._ensure_investidor(request.investidor)
            if request.lado == "BUY":
                total = request.preco * request.quantidade
                aprovado = self._cash_balances[request.investidor] >= total
                motivo = "saldo financeiro insuficiente" if not aprovado else "ok"
            else:
                saldo_ativo = self._asset_balances[request.investidor].get(request.ativo, 0)
                aprovado = saldo_ativo >= request.quantidade
                motivo = "saldo de ativo insuficiente" if not aprovado else "ok"

        return custodia_pb2.ValidarOrdemResponse(aprovado=aprovado, motivo=motivo)

    def LiquidarOperacao(self, request, context):
        total = request.preco * request.quantidade
        with self._lock:
            self._ensure_investidor(request.comprador)
            self._ensure_investidor(request.vendedor)

            self._cash_balances[request.comprador] -= total
            self._cash_balances[request.vendedor] += total
            self._asset_balances[request.comprador][request.ativo] += request.quantidade
            self._asset_balances[request.vendedor][request.ativo] -= request.quantidade

            comprador_update = custodia_pb2.SaldoUpdate(
                investidor=request.comprador,
                ativo=request.ativo,
                saldo_financeiro=self._cash_balances[request.comprador],
                saldo_ativo=self._asset_balances[request.comprador][request.ativo],
                descricao=f"Compra liquidada de {request.quantidade} {request.ativo}",
            )
            vendedor_update = custodia_pb2.SaldoUpdate(
                investidor=request.vendedor,
                ativo=request.ativo,
                saldo_financeiro=self._cash_balances[request.vendedor],
                saldo_ativo=self._asset_balances[request.vendedor][request.ativo],
                descricao=f"Venda liquidada de {request.quantidade} {request.ativo}",
            )
            self._notify(comprador_update)
            self._notify(vendedor_update)

        return custodia_pb2.LiquidacaoResponse(sucesso=True, mensagem="liquidacao concluida")

    def AcompanharSaldos(self, request, context):
        updates = queue.Queue()
        with self._lock:
            self._subscribers.append(updates)
        print(f"[custodia] broker inscrito no streaming: {request.broker_id}")

        try:
            while context.is_active():
                yield updates.get()
        finally:
            with self._lock:
                if updates in self._subscribers:
                    self._subscribers.remove(updates)

    def _notify(self, update):
        for subscriber in list(self._subscribers):
            subscriber.put(update)


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    custodia_pb2_grpc.add_CustodiaServiceServicer_to_server(CustodiaService(), server)
    server.add_insecure_port(HOST)
    server.start()
    print(f"[custodia] gRPC ouvindo em {HOST}")
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
