import socket
import threading


GATEWAY_HOST = "0.0.0.0"
GATEWAY_PORT = 5000
CORE_HOST = "127.0.0.1"
CORE_PORT = 5100


def encaminhar_ordens(client_socket, address):
    print(f"[gateway] corretora conectada: {address}")
    with client_socket:
        try:
            with socket.create_connection((CORE_HOST, CORE_PORT)) as core_socket:
                client_reader = client_socket.makefile("r", encoding="utf-8")
                core_writer = core_socket.makefile("w", encoding="utf-8")
                for line in client_reader:
                    payload = line.strip()
                    if not payload:
                        continue
                    core_writer.write(payload + "\n")
                    core_writer.flush()
                    print(f"[gateway] encaminhada: {payload}")
        except OSError as exc:
            print(f"[gateway] falha ao encaminhar {address}: {exc}")


def iniciar_gateway():
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((GATEWAY_HOST, GATEWAY_PORT))
        server.listen()
        print(f"[gateway] ouvindo em {GATEWAY_HOST}:{GATEWAY_PORT}")

        while True:
            client_socket, address = server.accept()
            worker = threading.Thread(
                target=encaminhar_ordens,
                args=(client_socket, address),
                daemon=True,
            )
            worker.start()


if __name__ == "__main__":
    iniciar_gateway()
