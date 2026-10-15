"""stage-device-controller: sunucu birim testleri.

Çalıştırma (server/ klasöründe):  python -m unittest -v
Yalnızca standart kütüphane kullanır.
"""

import json
import socket
import threading
import unittest
from unittest import mock

import server


def call(obj):
    """Bir sözlüğü JSON satırına çevirip process_line'a verir, yanıt sözlüğünü döndürür."""
    return server.process_line(json.dumps(obj).encode("utf-8"))


class ServerTestCase(unittest.TestCase):
    def setUp(self):
        # Her testin temiz bir cihaz durumuyla başlaması için global STATE yenilenir.
        server.STATE = server.DeviceState()
        # Konsol logu test çıktısını kirletmesin.
        patcher = mock.patch.object(server, "log")
        patcher.start()
        self.addCleanup(patcher.stop)

    def assert_error(self, reply, code):
        self.assertEqual(reply["status"], "error", reply)
        self.assertEqual(reply["code"], code, reply)
        self.assertIsInstance(reply["message"], str)


class PingTests(ServerTestCase):
    def test_ping(self):
        self.assertEqual(call({"cmd": "ping"}), {"status": "ok", "cmd": "pong"})


class SetLevelTests(ServerTestCase):
    def test_valid_light(self):
        reply = call({"cmd": "set_level", "type": "light", "channel": 3, "value": 75})
        self.assertEqual(reply, {"status": "ok", "cmd": "set_level", "type": "light",
                                 "channel": 3, "value": 75})

    def test_valid_audio(self):
        reply = call({"cmd": "set_level", "type": "audio", "channel": 8, "value": 40})
        self.assertEqual(reply["status"], "ok")
        self.assertEqual(reply["type"], "audio")

    def test_boundary_values(self):
        for value in (0, 100):
            with self.subTest(value=value):
                reply = call({"cmd": "set_level", "type": "light", "channel": 1, "value": value})
                self.assertEqual(reply["status"], "ok")
                self.assertEqual(reply["value"], value)

    def test_value_out_of_range(self):
        for value in (-1, 101):
            with self.subTest(value=value):
                self.assert_error(
                    call({"cmd": "set_level", "type": "light", "channel": 1, "value": value}),
                    "INVALID_VALUE")

    def test_value_wrong_type(self):
        # ondalık, metin, true/false ve null sayı sayılmaz
        for value in (50.5, "50", True, False, None, [50]):
            with self.subTest(value=value):
                self.assert_error(
                    call({"cmd": "set_level", "type": "light", "channel": 1, "value": value}),
                    "INVALID_VALUE")

    def test_missing_value(self):
        self.assert_error(call({"cmd": "set_level", "type": "light", "channel": 1}),
                          "INVALID_VALUE")

    def test_invalid_channel(self):
        for channel in (0, 9, -1, "3", 2.5, True, None):
            with self.subTest(channel=channel):
                self.assert_error(
                    call({"cmd": "set_level", "type": "light", "channel": channel, "value": 10}),
                    "INVALID_CHANNEL")

    def test_missing_channel(self):
        self.assert_error(call({"cmd": "set_level", "type": "light", "value": 10}),
                          "INVALID_CHANNEL")

    def test_invalid_type(self):
        for kind in ("video", "", 5, None):
            with self.subTest(kind=kind):
                self.assert_error(
                    call({"cmd": "set_level", "type": kind, "channel": 1, "value": 10}),
                    "INVALID_VALUE")

    def test_missing_type(self):
        self.assert_error(call({"cmd": "set_level", "channel": 1, "value": 10}),
                          "INVALID_VALUE")

    def test_invalid_command_does_not_change_state(self):
        call({"cmd": "set_level", "type": "light", "channel": 1, "value": 101})
        self.assertEqual(server.STATE.snapshot()["light"][0], 0)


class SetMuteTests(ServerTestCase):
    def test_mute_on_off(self):
        on = call({"cmd": "set_mute", "channel": 2, "mute": True})
        self.assertEqual(on, {"status": "ok", "cmd": "set_mute", "channel": 2, "mute": True})
        off = call({"cmd": "set_mute", "channel": 2, "mute": False})
        self.assertEqual(off["mute"], False)

    def test_mute_not_bool(self):
        for mute in ("true", 1, 0, None):
            with self.subTest(mute=mute):
                self.assert_error(call({"cmd": "set_mute", "channel": 2, "mute": mute}),
                                  "INVALID_VALUE")

    def test_mute_missing(self):
        self.assert_error(call({"cmd": "set_mute", "channel": 2}), "INVALID_VALUE")

    def test_mute_invalid_channel(self):
        for channel in (0, 9):
            with self.subTest(channel=channel):
                self.assert_error(call({"cmd": "set_mute", "channel": channel, "mute": True}),
                                  "INVALID_CHANNEL")


class GetStateTests(ServerTestCase):
    def test_initial_state(self):
        reply = call({"cmd": "get_state"})
        self.assertEqual(reply["status"], "ok")
        self.assertEqual(reply["cmd"], "get_state")
        self.assertEqual(reply["light"], [0] * 8)
        self.assertEqual(reply["audio"], [{"level": 0, "mute": False}] * 8)

    def test_state_matches_last_writes(self):
        call({"cmd": "set_level", "type": "light", "channel": 3, "value": 75})
        call({"cmd": "set_level", "type": "audio", "channel": 5, "value": 40})
        call({"cmd": "set_mute", "channel": 5, "mute": True})
        reply = call({"cmd": "get_state"})
        self.assertEqual(reply["light"][2], 75)  # indeks 0 = Kanal 1
        self.assertEqual(reply["audio"][4], {"level": 40, "mute": True})


class MalformedInputTests(ServerTestCase):
    def test_unknown_command(self):
        self.assert_error(call({"cmd": "reboot"}), "UNKNOWN_CMD")

    def test_missing_or_non_string_command(self):
        for msg in ({}, {"cmd": 5}, {"cmd": None}):
            with self.subTest(msg=msg):
                self.assert_error(call(msg), "UNKNOWN_CMD")

    def test_invalid_json(self):
        for raw in (b"{bad json", b"{\"cmd\":", b"hello"):
            with self.subTest(raw=raw):
                self.assert_error(server.process_line(raw), "INVALID_JSON")

    def test_json_but_not_object(self):
        for raw in (b"5", b"[1]", b"\"ping\"", b"null", b"true"):
            with self.subTest(raw=raw):
                self.assert_error(server.process_line(raw), "INVALID_JSON")

    def test_empty_line(self):
        self.assert_error(server.process_line(b""), "INVALID_JSON")

    def test_invalid_utf8(self):
        self.assert_error(server.process_line(b"\xff\xfe"), "INVALID_JSON")

    def test_oversized_line_marker(self):
        # LineBuffer 1 KB'ı aşan satır için None üretir
        self.assert_error(server.process_line(None), "INVALID_JSON")

    def test_deeply_nested_json_does_not_crash(self):
        self.assert_error(server.process_line(b"[" * 100000), "INVALID_JSON")


class LineBufferTests(unittest.TestCase):
    def test_single_line(self):
        self.assertEqual(server.LineBuffer(1024).feed(b"abc\n"), [b"abc"])

    def test_partial_line(self):
        buf = server.LineBuffer(1024)
        self.assertEqual(buf.feed(b'{"cmd":'), [])
        self.assertEqual(buf.feed(b'"ping"}\n'), [b'{"cmd":"ping"}'])

    def test_combined_lines(self):
        self.assertEqual(server.LineBuffer(1024).feed(b"a\nb\nc\n"), [b"a", b"b", b"c"])

    def test_combined_and_partial(self):
        buf = server.LineBuffer(1024)
        self.assertEqual(buf.feed(b"a\nb"), [b"a"])
        self.assertEqual(buf.feed(b"c\n"), [b"bc"])

    def test_crlf_is_stripped(self):
        self.assertEqual(server.LineBuffer(1024).feed(b"abc\r\n"), [b"abc"])

    def test_empty_line(self):
        self.assertEqual(server.LineBuffer(1024).feed(b"\n"), [b""])

    def test_exactly_max_length_is_accepted(self):
        line = b"a" * 1024
        self.assertEqual(server.LineBuffer(1024).feed(line + b"\n"), [line])

    def test_oversized_line_in_one_chunk(self):
        self.assertEqual(server.LineBuffer(1024).feed(b"a" * 2000 + b"\n"), [None])

    def test_oversized_line_in_pieces_then_recovers(self):
        buf = server.LineBuffer(1024)
        self.assertEqual(buf.feed(b"a" * 1025), [None])   # bir kez bildirilir
        self.assertEqual(buf.feed(b"b" * 500), [])         # kalan atılır
        self.assertEqual(buf.feed(b"tail\n"), [])          # satırın sonu atılır
        self.assertEqual(buf.feed(b"ok\n"), [b"ok"])       # sonraki satır normal


class FaultConfigTests(unittest.TestCase):
    def test_disabled_by_default(self):
        faults = server.FaultConfig()
        self.assertFalse(faults.enabled)
        self.assertFalse(faults.should_drop())

    def test_enabled_flags(self):
        self.assertTrue(server.FaultConfig(latency_ms=100).enabled)
        self.assertTrue(server.FaultConfig(drop_rate=0.5).enabled)

    def test_drop_rate_extremes(self):
        self.assertTrue(all(server.FaultConfig(drop_rate=1.0).should_drop() for _ in range(50)))
        self.assertFalse(any(server.FaultConfig(drop_rate=0.0).should_drop() for _ in range(50)))


class FakeClient:
    """Sunucunun serve_client fonksiyonuna gerçek bir soket çiftiyle bağlanan test istemcisi."""

    def __init__(self, test, number, idle_timeout=5.0):
        server_side, self.sock = socket.socketpair()
        self.sock.settimeout(3)
        self.reader = self.sock.makefile("rb")
        self.thread = threading.Thread(
            target=server.serve_client,
            args=(server_side, ("test", number), idle_timeout, server.FaultConfig()),
            daemon=True)
        self.thread.start()
        test.addCleanup(self.close)

    def send_raw(self, data):
        self.sock.sendall(data)

    def send(self, obj):
        self.send_raw(json.dumps(obj).encode("utf-8") + b"\n")

    def read(self):
        return json.loads(self.reader.readline())

  def close(self):
        self.reader.close()
        self.sock.close()
        self.thread.join(timeout=3)


class ConnectionTests(ServerTestCase):
    def test_two_clients_share_state(self):
        a = FakeClient(self, 1)
        b = FakeClient(self, 2)
        a.send({"cmd": "set_level", "type": "light", "channel": 3, "value": 75})
        self.assertEqual(a.read()["status"], "ok")
        b.send({"cmd": "get_state"})
        self.assertEqual(b.read()["light"][2], 75)

    def test_oversized_line_keeps_connection_open(self):
        client = FakeClient(self, 1)
        client.send_raw(b"x" * 2000 + b"\n")
        self.assert_error(client.read(), "INVALID_JSON")
        client.send({"cmd": "ping"})
        self.assertEqual(client.read(), {"status": "ok", "cmd": "pong"})

    def test_bad_json_keeps_connection_open(self):
        client = FakeClient(self, 1)
        client.send_raw(b"{not json\n")
        self.assert_error(client.read(), "INVALID_JSON")
        client.send({"cmd": "ping"})
        self.assertEqual(client.read()["cmd"], "pong")

    def test_two_commands_in_one_packet(self):
        client = FakeClient(self, 1)
        client.send_raw(b'{"cmd":"ping"}\n{"cmd":"get_state"}\n')
        self.assertEqual(client.read()["cmd"], "pong")
        self.assertEqual(client.read()["cmd"], "get_state")


if __name__ == "__main__":
    unittest.main()
