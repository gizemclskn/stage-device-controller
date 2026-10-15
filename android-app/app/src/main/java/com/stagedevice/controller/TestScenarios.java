package com.stagedevice.controller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * Test Modu senaryoları T1-T11. Her senaryo CommandBuilder ile komut üretir, yanıtı
 * ResponseParser ile çözer ve beklenenle karşılaştırır. Geçersiz kanal/değer gibi
 * senaryolar sunucunun hata yanıtlarını bilerek dener.
 */
public final class TestScenarios {

    private static final int PING_COUNT = 5;
    private static final long PING_AVERAGE_LIMIT_MS = 500;
    private static final long CLOSED_PORT_LIMIT_MS = 4000; // bağlanma timeout'u 3 sn + pay

    private TestScenarios() {
    }

    /** T1-T11, çalışma sırasıyla. */
    public static List<TestScenario> all() {
        List<TestScenario> list = new ArrayList<>();
        list.add(new TestScenario("T1", "Bağlantı", true, TestScenarios::connection));
        list.add(new TestScenario("T2", "Ping süresi", false, TestScenarios::pingTime));
        list.add(new TestScenario("T3", "Işık kanalları 1-8", false,
                s -> levelChannels(s, CommandBuilder.TYPE_LIGHT)));
        list.add(new TestScenario("T4", "Ses kanalları 1-8", false,
                s -> levelChannels(s, CommandBuilder.TYPE_AUDIO)));
        list.add(new TestScenario("T5", "Mute aç/kapat", false, TestScenarios::muteToggle));
        list.add(new TestScenario("T6", "get_state tutarlılığı", false,
                TestScenarios::stateConsistency));
        list.add(new TestScenario("T7", "Geçersiz kanal", false, TestScenarios::invalidChannel));
        list.add(new TestScenario("T8", "Geçersiz değer", false, TestScenarios::invalidValue));
        list.add(new TestScenario("T9", "Bilinmeyen komut", false,
                TestScenarios::unknownCommand));
        list.add(new TestScenario("T10", "Bozuk JSON", false, TestScenarios::malformedJson));
        list.add(new TestScenario("T11", "Zaman aşımı (kapalı port)", true,
                TestScenarios::closedPortTimeout));
        return list;
    }

    // ---- Senaryolar ----

    /** T1: sunucuya süre içinde bağlanılır. Bağlantıyı bu senaryo kendisi açar. */
    private static String connection(TestSession session) throws Exception {
        long start = System.nanoTime();
        session.connect(); // bağlanamazsa IOException, runner FAIL sayar
        return "bağlandı (" + elapsedMs(start) + " ms)";
    }

    /** T2: 5 ping, ortalama süre 500 ms'nin altında olmalı. */
    private static String pingTime(TestSession session) throws Exception {
        long total = 0;
        long worst = 0;
        for (int i = 0; i < PING_COUNT; i++) {
            long start = System.nanoTime();
            ResponseParser.Response r = send(session, CommandBuilder.ping());
            long ms = elapsedMs(start);
            expectOk(r, "pong");
            total += ms;
            worst = Math.max(worst, ms);
        }
        long average = total / PING_COUNT;
        if (average >= PING_AVERAGE_LIMIT_MS) {
            throw new TestScenario.Failure("ortalama " + average + " ms (sınır "
                    + PING_AVERAGE_LIMIT_MS + " ms)");
        }
        return PING_COUNT + " ping, ortalama " + average + " ms, en yavaş " + worst + " ms";
    }

    /** T3 ve T4: kanal 1-8'e farklı değerler yazılır, her yanıt gönderilenle eşleşmeli. */
    private static String levelChannels(TestSession session, String type) throws Exception {
        for (int channel = 1; channel <= 8; channel++) {
            int value = levelFor(type, channel);
            ResponseParser.Response r = send(session,
                    CommandBuilder.setLevel(type, channel, value));
            expectOk(r, "set_level");
            JSONObject json = r.getJson();
            if (!type.equals(json.opt("type"))
                    || !isInt(json.opt("channel"), channel)
                    || !isInt(json.opt("value"), value)) {
                throw new TestScenario.Failure("kanal " + channel
                        + ": yanıt gönderilenle eşleşmiyor: " + json);
            }
        }
        return "8 kanalın hepsi doğru yanıtladı";
    }

    /** T5: kanal 2 mute açılır, sonra kapatılır. */
    private static String muteToggle(TestSession session) throws Exception {
        for (boolean mute : new boolean[]{true, false}) {
            ResponseParser.Response r = send(session, CommandBuilder.setMute(2, mute));
            expectOk(r, "set_mute");
            JSONObject json = r.getJson();
            if (!isInt(json.opt("channel"), 2) || !Boolean.valueOf(mute).equals(json.opt("mute"))) {
                throw new TestScenario.Failure("mute=" + mute + " için yanıt yanlış: " + json);
            }
        }
        return "mute açıldı ve kapandı";
    }

    /** T6: bilinen değerler yazılır, get_state ile okunan durum bunlarla uyuşmalı. */
    private static String stateConsistency(TestSession session) throws Exception {
        expectOk(send(session, CommandBuilder.setLevel(CommandBuilder.TYPE_LIGHT, 4, 33)),
                "set_level");
        expectOk(send(session, CommandBuilder.setLevel(CommandBuilder.TYPE_AUDIO, 6, 66)),
                "set_level");
        expectOk(send(session, CommandBuilder.setMute(6, true)), "set_mute");

        ResponseParser.Response r = send(session, CommandBuilder.getState());
        expectOk(r, "get_state");
        JSONObject json = r.getJson();
        JSONArray light = json.optJSONArray("light");
        JSONArray audio = json.optJSONArray("audio");
        if (light == null || light.length() != 8) {
            throw new TestScenario.Failure("light dizisi yok ya da 8 elemanlı değil");
        }
        if (audio == null || audio.length() != 8) {
            throw new TestScenario.Failure("audio dizisi yok ya da 8 elemanlı değil");
        }
        if (!isInt(light.opt(3), 33)) { // indeks 0 = Kanal 1
            throw new TestScenario.Failure("ışık kanalı 4 beklenen 33, gelen " + light.opt(3));
        }
        JSONObject channel6 = audio.optJSONObject(5);
        if (channel6 == null || !isInt(channel6.opt("level"), 66)
                || !Boolean.TRUE.equals(channel6.opt("mute"))) {
            throw new TestScenario.Failure("ses kanalı 6 beklenen 66 ve mute açık, gelen "
                    + channel6);
        }
        // Cihazı sessiz bırakma: mute geri alınır
        expectOk(send(session, CommandBuilder.setMute(6, false)), "set_mute");
        return "ışık 4 = 33, ses 6 = 66 ve mute doğru okundu";
    }

    /** T7: kanal 0 ve 9 için INVALID_CHANNEL dönmeli. */
    private static String invalidChannel(TestSession session) throws Exception {
        for (int channel : new int[]{0, 9}) {
            expectError(session,
                    CommandBuilder.setLevel(CommandBuilder.TYPE_LIGHT, channel, 50),
                    "INVALID_CHANNEL");
        }
        return "kanal 0 ve 9 reddedildi";
    }

    /** T8: değer -1 ve 101 için INVALID_VALUE dönmeli. */
    private static String invalidValue(TestSession session) throws Exception {
        for (int value : new int[]{-1, 101}) {
            expectError(session,
                    CommandBuilder.setLevel(CommandBuilder.TYPE_LIGHT, 1, value),
                    "INVALID_VALUE");
        }
        return "değer -1 ve 101 reddedildi";
    }

    /** T9: tanınmayan komut için UNKNOWN_CMD dönmeli. */
    private static String unknownCommand(TestSession session) throws Exception {
        expectError(session, "{\"cmd\":\"reboot\"}", "UNKNOWN_CMD");
        return "bilinmeyen komut reddedildi";
    }

    /** T10: ham, bozuk bir satır için INVALID_JSON dönmeli. */
    private static String malformedJson(TestSession session) throws Exception {
        expectError(session, "{bad json", "INVALID_JSON");
        return "bozuk JSON reddedildi";
    }

    /**
     * T11: kapalı porta bağlanma süre içinde hata vermeli. Kapalı port, cihazın kendi
     * localhost'unda boş bir port seçilip bırakılarak elde edilir, sunucudan bağımsızdır.
     * Test arka thread'de koştuğu için arayüz donmaz.
     */
    private static String closedPortTimeout(TestSession session) throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        long start = System.nanoTime();
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", closedPort), 3000);
        } catch (IOException e) {
            long ms = elapsedMs(start);
            if (ms > CLOSED_PORT_LIMIT_MS) {
                throw new TestScenario.Failure("hata geç geldi: " + ms + " ms");
            }
            return "bağlanma " + ms + " ms içinde hata verdi ("
                    + e.getClass().getSimpleName() + ")";
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // kapatırken oluşan hata önemsiz
            }
        }
        throw new TestScenario.Failure("kapalı porta bağlanıldı, hata bekleniyordu");
    }

    // ---- Yardımcılar ----

    /** Satırı gönderir, yanıtı çözer. Çözülemezse senaryo FAIL olur. */
    private static ResponseParser.Response send(TestSession session, String line)
            throws IOException, TestScenario.Failure {
        String reply = session.request(line);
        try {
            return ResponseParser.parse(reply);
        } catch (ResponseParser.ParseException e) {
            throw new TestScenario.Failure("geçersiz yanıt (" + e.getMessage() + "): " + reply);
        }
    }

    private static void expectOk(ResponseParser.Response r, String cmd)
            throws TestScenario.Failure {
        if (!r.isOk()) {
            throw new TestScenario.Failure("sunucu hata döndürdü: " + r.getErrorCode()
                    + " (" + r.getMessage() + ")");
        }
        if (!cmd.equals(r.getCmd())) {
            throw new TestScenario.Failure("beklenen cmd '" + cmd + "', gelen '"
                    + r.getCmd() + "'");
        }
    }

    private static void expectError(TestSession session, String line, String code)
            throws IOException, TestScenario.Failure {
        ResponseParser.Response r = send(session, line);
        if (r.isOk()) {
            throw new TestScenario.Failure("hata bekleniyordu ama sunucu ok döndürdü: " + line);
        }
        if (!code.equals(r.getErrorCode())) {
            throw new TestScenario.Failure("beklenen " + code + ", gelen " + r.getErrorCode()
                    + " (" + line + ")");
        }
    }

    /** JSON değeri tam sayı olarak beklenene eşit mi? (metin ve ondalık sayılmaz) */
    private static boolean isInt(Object value, int expected) {
        return value instanceof Integer && ((Integer) value) == expected;
    }

    private static int levelFor(String type, int channel) {
        return CommandBuilder.TYPE_LIGHT.equals(type) ? channel * 12 : 100 - channel * 10;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
