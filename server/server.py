"""stage-device-controller: sahne cihazı simülatörü (TCP sunucusu)."""

import argparse
import json
import socket
import sys
import threading
import traceback
from datetime import datetime

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 5000
DEFAULT_IDLE_TIMEOUT = 300.0  # saniye; bu sürede hiçbir şey göndermeyen istemci kapatılır
MAX_LINE = 1024               # satır başına en fazla 1 KB
CHANNELS = 8                  # ışık ve ses için kanal sayısı (kanallar 1-8)
ACCEPT_POLL = 1.0             # accept() bu aralıkla uyanır (Windows'ta Ctrl+C için gerekli)

CLIENTS = set()               # bağlı istemci soketleri (kapanışta hepsini kapatmak için)
CLIENTS_LOCK = threading.Lock()


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


class ProtocolError(Exception):
    """İstemciye hata yanıtı olarak dönecek protokol hatası."""

    def __init__(self, code, message):
        super().__init__(message)
        self.code = code
        self.message = message


class DeviceState:
    """Cihazın durumu: 8 ışık ve 8 ses kanalı (seviye + mute).

    Birden fazla istemci thread'i aynı anda erişebilir, bu yüzden
    her okuma ve yazma Lock ile korunur.
    """

    def __init__(self):
        self._lock = threading.Lock()
        self._light = [0] * CHANNELS
        self._audio = [{"level": 0, "mute": False} for _ in range(CHANNELS)]

    def set_level(self, kind, channel, value):
        with self._lock:
            if kind == "light":
                self._light[channel - 1] = value
            else:
                self._audio[channel - 1]["level"] = value

    def set_mute(self, channel, mute):
        with self._lock:
            self._audio[channel - 1]["mute"] = mute

    def snapshot(self):
        """Durumun kopyasını döndürür (kilit dışında güvenle kullanılabilir)."""
        with self._lock:
            return {
                "light": list(self._light),
                "audio": [dict(a) for a in self._audio],
            }


STATE = DeviceState()  # tüm istemcilerin paylaştığı tek cihaz durumu


def is_int(value):
    # bool, Python'da int'in alt sınıfıdır. true/false'u sayı saymamak için ayrıca eleriz.
    return isinstance(value, int) and not isinstance(value, bool)


def get_channel(msg):
    channel = msg.get("channel")
    if not is_int(channel) or not 1 <= channel <= CHANNELS:
        raise ProtocolError("INVALID_CHANNEL", "kanal 1-8 arasında bir tam sayı olmalı")
    return channel


def handle_ping(msg):
    return {"status": "ok", "cmd": "pong"}


def handle_set_level(msg):
    kind = msg.get("type")
    if kind not in ("light", "audio"):
        raise ProtocolError("INVALID_VALUE", "type 'light' veya 'audio' olmalı")
    channel = get_channel(msg)
    value = msg.get("value")
    if not is_int(value) or not 0 <= value <= 100:
        raise ProtocolError("INVALID_VALUE", "value 0-100 arasında bir tam sayı olmalı")
    STATE.set_level(kind, channel, value)
    return {"status": "ok", "cmd": "set_level", "type": kind,
            "channel": channel, "value": value}


def handle_set_mute(msg):
    channel = get_channel(msg)
    mute = msg.get("mute")
    if not isinstance(mute, bool):
        raise ProtocolError("INVALID_VALUE", "mute true veya false olmalı")
    STATE.set_mute(channel, mute)
    return {"status": "ok", "cmd": "set_mute", "channel": channel, "mute": mute}


def handle_get_state(msg):
    reply = {"status": "ok", "cmd": "get_state"}
    reply.update(STATE.snapshot())
    return reply


# Komut adı -> işleyici fonksiyon
HANDLERS = {
    "ping": handle_ping,
    "set_level": handle_set_level,
    "set_mute": handle_set_mute,
    "get_state": handle_get_state,
}


def error_reply(code, message):
    return {"status": "error", "code": code, "message": message}


def process_line(line):
    """Bir satırı işler ve istemciye gönderilecek yanıt sözlüğünü döndürür."""
    if line is None:
        return error_reply("INVALID_JSON", "satır 1 KB sınırını aşıyor")
    try:
        msg = json.loads(line.decode("utf-8"))
    except (ValueError, RecursionError):
        # UnicodeDecodeError ve JSONDecodeError, ValueError'ın alt sınıflarıdır.
        # Boş satır da burada yakalanır.
        return error_reply("INVALID_JSON", "geçerli bir JSON değil")
    if not isinstance(msg, dict):
        return error_reply("INVALID_JSON", "JSON bir nesne olmalı")
    cmd = msg.get("cmd")
    handler = HANDLERS.get(cmd) if isinstance(cmd, str) else None
    if handler is None:
        return error_reply("UNKNOWN_CMD", "bilinmeyen komut")
    try:
        return handler(msg)
    except ProtocolError as err:
        return error_reply(err.code, err.message)


def log(msg):
    print(f"[{datetime.now():%H:%M:%S}] {msg}", flush=True)


def handle_client(conn, addr, idle_timeout):
    """Tek bir istemciyi kendi thread'inde sunar."""
    log(f"Bağlandı: {addr[0]}:{addr[1]}")
    conn.settimeout(idle_timeout)  # bu sürede veri gelmezse recv() zaman aşımına düşer
    buf = LineBuffer(MAX_LINE)
    with conn:
        while True:
            try:
                data = conn.recv(4096)
            except socket.timeout:
                log(f"Boşta kalma zaman aşımı ({idle_timeout:g} sn): {addr[0]}:{addr[1]}")
                break
            except OSError:
                break
            if not data:  # istemci bağlantıyı kapattı
                break
            for line in buf.feed(data):
                shown = "(1 KB'ı aşan satır)" if line is None else line.decode("utf-8", "replace")
                log(f"{addr[1]} <- {shown}")
                out = json.dumps(process_line(line), ensure_ascii=False)
                log(f"{addr[1]} -> {out}")
                try:
                    conn.sendall((out + "\n").encode("utf-8"))
                except OSError:
                    return
    log(f"Ayrıldı: {addr[0]}:{addr[1]}")


def serve_client(conn, addr, idle_timeout):
    """handle_client'i sarar: beklenmeyen bir hata yalnızca bu istemciyi düşürür."""
    with CLIENTS_LOCK:
        CLIENTS.add(conn)
    try:
        handle_client(conn, addr, idle_timeout)
    except Exception:
        log(f"HATA: {addr[1]} istemcisinde beklenmeyen hata:\n{traceback.format_exc()}")
    finally:
        with CLIENTS_LOCK:
            CLIENTS.discard(conn)


def close_all_clients():
    """Sunucu kapanırken bağlı tüm istemcilerin bağlantısını keser."""
    with CLIENTS_LOCK:
        conns = list(CLIENTS)
    for conn in conns:
        try:
            conn.shutdown(socket.SHUT_RDWR)  # istemci thread'inin recv()'i sona erer
        except OSError:
            pass


def port_number(text):
    try:
        value = int(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"geçersiz port: {text!r}")
    if not 1 <= value <= 65535:
        raise argparse.ArgumentTypeError("port 1-65535 arasında olmalı")
    return value


def timeout_seconds(text):
    try:
        value = float(text)
    except ValueError:
        raise argparse.ArgumentTypeError(f"geçersiz süre: {text!r}")
    if not 0 < value <= 86400:
        raise argparse.ArgumentTypeError("süre 0 ile 86400 saniye arasında olmalı")
    return value


def parse_args(argv=None):
    parser = argparse.ArgumentParser(
        description="stage-device-controller: sahne cihazı simülatörü (TCP sunucusu)")
    parser.add_argument("--host", default=DEFAULT_HOST,
                        help=f"dinlenecek adres (varsayılan: {DEFAULT_HOST})")
    parser.add_argument("--port", type=port_number, default=DEFAULT_PORT,
                        help=f"dinlenecek port, 1-65535 (varsayılan: {DEFAULT_PORT})")
    parser.add_argument("--idle-timeout", type=timeout_seconds, default=DEFAULT_IDLE_TIMEOUT,
                        metavar="SANIYE",
                        help=f"bu kadar süre sessiz kalan istemci kapatılır (varsayılan: {DEFAULT_IDLE_TIMEOUT:g})")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(argv)
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
        # Windows: SO_REUSEADDR, aynı porta ikinci bir sunucunun bağlanmasına izin verir.
        # Bunu istemiyoruz, o yüzden port özel olarak kilitlenir.
        server.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
    else:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        server.bind((args.host, args.port))
    except OSError as err:
        log(f"HATA: {args.host}:{args.port} adresine bağlanılamadı: {err}")
        server.close()
        return 1
    server.listen()
    server.settimeout(ACCEPT_POLL)
    log(f"Dinleniyor: {args.host}:{args.port} (boşta kalma zaman aşımı: {args.idle_timeout:g} sn)")
    try:
        while True:
            try:
                conn, addr = server.accept()
            except socket.timeout:
                continue  # her saniye uyanır, böylece Ctrl+C işlenebilir
            threading.Thread(target=serve_client,
                             args=(conn, addr, args.idle_timeout),
                             daemon=True).start()
    except KeyboardInterrupt:
        log("Kapatılıyor...")
    finally:
        server.close()
        close_all_clients()
    log("Sunucu kapandı.")
    return 0


if __name__ == "__main__":
    sys.exit(main())