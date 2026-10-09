"""stage-device-controller: sahne cihazı simülatörü (TCP sunucusu)."""

import json
import socket
import threading
from datetime import datetime

HOST = "0.0.0.0"
PORT = 5000
MAX_LINE = 1024  # satır başına en fazla 1 KB


class LineBuffer:
    """Gelen baytları satırlara ayırır.

    TCP bir akıştır: bir satır birkaç parçada gelebilir, bir paketin
    içinde birden fazla satır olabilir. Bu sınıf ikisini de doğru yönetir.
    feed() her tam satır için bytes, 1 KB'ı aşan satır için None döndürür.
    """

    def __init__(self, max_len):
        self.max_len = max_len
        self._buf = bytearray()
        self._discarding = False  # aşırı uzun satırın kalanını atıyor muyuz

    def feed(self, data):
        out = []
        self._buf.extend(data)
        while True:
            idx = self._buf.find(b"\n")
            if idx == -1:
                # Satır henüz bitmedi. Limit aşıldıysa bir kez bildir, kalanı at.
                if len(self._buf) > self.max_len and not self._discarding:
                    out.append(None)
                    self._discarding = True
                if self._discarding:
                    self._buf.clear()
                break
            line = bytes(self._buf[:idx])
            del self._buf[:idx + 1]
            if self._discarding:
                # Aşırı uzun satırın kuyruğu, zaten bildirildi.
                self._discarding = False
                continue
            if len(line) > self.max_len:
                out.append(None)
            else:
                out.append(line.rstrip(b"\r"))
        return out


def log(msg):
    print(f"[{datetime.now():%H:%M:%S}] {msg}", flush=True)


def handle_client(conn, addr):
    """Tek bir istemciyi kendi thread'inde sunar."""
    log(f"Bağlandı: {addr[0]}:{addr[1]}")
    buf = LineBuffer(MAX_LINE)
    with conn:
        while True:
            try:
                data = conn.recv(4096)
            except OSError:
                break
            if not data:  # istemci bağlantıyı kapattı
                break
            for line in buf.feed(data):
                # GEÇİCİ: komut işleme 1.4'te gelecek. Şimdilik sadece yankılıyoruz.
                if line is None:
                    reply = {"status": "error", "code": "INVALID_JSON",
                             "message": "satır çok uzun"}
                    log(f"{addr[1]} <- (1 KB'ı aşan satır)")
                else:
                    log(f"{addr[1]} <- {line.decode('utf-8', 'replace')}")
                    reply = {"status": "ok", "echo": line.decode("utf-8", "replace")}
                try:
                    conn.sendall((json.dumps(reply) + "\n").encode("utf-8"))
                except OSError:
                    return
    log(f"Ayrıldı: {addr[0]}:{addr[1]}")


def main():
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, PORT))
    server.listen()
    log(f"Dinleniyor: {HOST}:{PORT}")
    while True:
        conn, addr = server.accept()
        threading.Thread(target=handle_client, args=(conn, addr), daemon=True).start()


if __name__ == "__main__":
    main()