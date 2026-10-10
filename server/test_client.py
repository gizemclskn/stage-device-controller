"""stage-device-controller: sunucuyu elle denemek için komut satırı istemcisi."""

import argparse
import json
import socket
import sys

HELP = """Komutlar:
  ping                          sunucuyu yoklar
  level light <kanal> <değer>   ışık seviyesi (örn: level light 3 75)
  level audio <kanal> <değer>   ses seviyesi (örn: level audio 8 100)
  mute <kanal> on|off           ses kanalını sustur / aç (örn: mute 2 on)
  state                         tüm durumu getirir
  raw <metin>                   metni olduğu gibi gönderir (bozuk JSON denemek için)
  help                          bu yardım
  quit                          çıkış"""


class Connection:
    """Sunucuya TCP bağlantısı ve satır tabanlı okuma."""

    def __init__(self, host, port, timeout):
        self.timeout = timeout
        self.sock = socket.create_connection((host, port), timeout=timeout)
        self.sock.settimeout(timeout)
        self.buf = bytearray()

    def send_line(self, text):
        self.sock.sendall(text.encode("utf-8") + b"\n")

    def read_line(self):
        """Bir satır okur. Süre dolarsa None döner, bağlantı kapanırsa ConnectionError fırlatır."""
        while b"\n" not in self.buf:
            try:
                data = self.sock.recv(4096)
            except socket.timeout:
                return None
            if not data:
                raise ConnectionError("sunucu bağlantıyı kapattı")
            self.buf.extend(data)
        idx = self.buf.index(b"\n")
        line = bytes(self.buf[:idx])
        del self.buf[:idx + 1]
        return line.decode("utf-8", "replace").rstrip("\r")

    def drain(self):
        """Önceki bir komuta ait geç gelmiş yanıtları temizler ve döndürür."""
        late = []
        self.sock.settimeout(0.05)
        try:
            while True:
                line = self.read_line()
                if line is None:
                    break
                late.append(line)
        finally:
            self.sock.settimeout(self.timeout)
        return late

    def close(self):
        self.sock.close()


def to_number(text):
    """'3' -> 3, '3.5' -> 3.5, 'abc' -> 'abc'. Hatalı değerleri de denemek için metin olduğu gibi kalır."""
    for kind in (int, float):
        try:
            return kind(text)
        except ValueError:
            pass
    return text


def build_request(line):
    """Kullanıcının yazdığı kısa komutu sunucuya gidecek satıra çevirir."""
    parts = line.split()
    cmd = parts[0].lower()
    if cmd == "raw":
        return line[3:].strip()
    if cmd == "ping":
        return json.dumps({"cmd": "ping"})
    if cmd == "state":
        return json.dumps({"cmd": "get_state"})
    if cmd == "level" and len(parts) == 4:
        return json.dumps({"cmd": "set_level", "type": parts[1],
                           "channel": to_number(parts[2]), "value": to_number(parts[3])})
    if cmd == "mute" and len(parts) == 3 and parts[2].lower() in ("on", "off"):
        return json.dumps({"cmd": "set_mute", "channel": to_number(parts[1]),
                           "mute": parts[2].lower() == "on"})
    raise ValueError("anlaşılamadı, komutlar için 'help' yaz")


def show_reply(line):
    """Yanıtı gösterir. get_state yanıtını okunaklı bir tablo olarak yazar."""
    try:
        reply = json.loads(line)
        if reply.get("cmd") == "get_state" and reply.get("status") == "ok":
            rows = list(zip(reply["light"], reply["audio"]))
            print("<- durum:")
            print("   kanal  ışık  ses  mute")
            for number, (light, audio) in enumerate(rows, start=1):
                mute = "evet" if audio["mute"] else "-"
                print(f"   {number:>5}  {light:>4}  {audio['level']:>3}  {mute}")
            return
    except (ValueError, KeyError, TypeError, AttributeError):
        pass  # tablo çizilemezse yanıtı olduğu gibi göster
    print(f"<- {line}")


def main(argv=None):
    parser = argparse.ArgumentParser(description="stage-device-controller: elle test istemcisi")
    parser.add_argument("--host", default="127.0.0.1", help="sunucu adresi (varsayılan: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=5000, help="sunucu portu (varsayılan: 5000)")
    parser.add_argument("--timeout", type=float, default=3.0,
                        help="yanıt bekleme süresi, saniye (varsayılan: 3)")
    args = parser.parse_args(argv)

    try:
        conn = Connection(args.host, args.port, args.timeout)
    except OSError as err:
        print(f"Bağlanılamadı ({args.host}:{args.port}): {err}")
        return 1
    print(f"Bağlandı: {args.host}:{args.port}. Komutlar için 'help', çıkmak için 'quit'.")

    try:
        while True:
            try:
                line = input("> ").strip()
            except (EOFError, KeyboardInterrupt):
                print()
                break
            if not line:
                continue
            word = line.split()[0].lower()
            if word in ("quit", "exit", "q"):
                break
            if word == "help":
                print(HELP)
                continue
            try:
                request = build_request(line)
            except ValueError as err:
                print(f"! {err}")
                continue
            for late in conn.drain():
                print(f"<- (önceki komuta ait geç yanıt) {late}")
            conn.send_line(request)
            print(f"-> {request}")
            reply = conn.read_line()
            if reply is None:
                print(f"<- (yanıt gelmedi, {args.timeout:g} sn beklendi)")
            else:
                show_reply(reply)
    except OSError as err:  # ConnectionError de OSError'ın alt sınıfıdır
        print(f"Bağlantı koptu: {err}")
        return 1
    finally:
        conn.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())