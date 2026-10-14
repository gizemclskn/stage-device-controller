package com.stagedevice.controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sunucuyla TCP bağlantısını yönetir.
 *
 * Tüm ağ işi tek bir arka plan thread'inde (ExecutorService) yapılır, çünkü Android
 * ağ işine ana thread'de izin vermez. Sonuçlar Handler ile ana thread'e döner,
 * böylece listener ve callback'lerden ekran güvenle güncellenebilir.
 * Giden ve gelen her satır ayrıca LogStore'a yazılır (Log ekranı için).
 */
public class SocketClient {

    /** Bağlantı durumu değiştiğinde ana thread'de çağrılır. */
    public interface Listener {
        void onStateChanged(ConnectionState state, String detail);
    }

    /** Bir isteğin sonucu, ana thread'de bildirilir. */
    public interface ResponseCallback {
        void onResponse(String line, long elapsedMs);

        void onError(String message);
    }

    // Tüm logcat etiketleri "SDC-" ile başlar: Logcat'te "tag:SDC-" filtresiyle hepsi görünür.
    private static final String TAG = "SDC-SocketClient";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int MAX_LINE_BYTES = 64 * 1024;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Her connect/disconnect/kopma yeni bir numara üretir. Eski numaralı bir işin
    // sonucu artık geçersizdir (örneğin bağlanırken Kes'e basılırsa).
    private final AtomicInteger generation = new AtomicInteger();

    // Log ekranının veri kaynağı. Yalnızca ana thread'de değiştirilir (bkz. logMessage).
    private final LogStore logStore;

    private volatile Listener listener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile Socket socket;

    // Yalnızca executor thread'inde kullanılır
    private InputStream in;
    private OutputStream out;

    public SocketClient(LogStore logStore) {
        this.logStore = logStore;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public ConnectionState getState() {
        return state;
    }

    /** Sunucuya bağlanmayı dener. Zaten bağlıysa ya da bağlanıyorsa bir şey yapmaz. */
    public void connect(final String host, final int port) {
        if (state != ConnectionState.DISCONNECTED && state != ConnectionState.LOST) {
            return;
        }
        final int id = generation.incrementAndGet();
        setState(ConnectionState.CONNECTING, null);
        executor.execute(() -> doConnect(id, host, port));
    }

    /** Bağlantıyı kapatır. Bağlanma ya da okuma sürüyorsa onu da hemen sonlandırır. */
    public void disconnect() {
        generation.incrementAndGet(); // süren işlerin sonucu artık geçersiz
        closeInBackground();
        setState(ConnectionState.DISCONNECTED, null);
    }

    /**
     * Bir satır gönderir ve tek satırlık yanıtı bekler. Sonuç ana thread'de bildirilir.
     * Okuma zaman aşımı dahil her ağ hatası bağlantıyı "koptu" durumuna düşürür:
     * geç gelen bir yanıt, sonraki komutun yanıtıyla karışmasın.
     */
    public void request(final String line, final ResponseCallback callback) {
        final int id = generation.get();
        executor.execute(() -> doRequest(id, line, callback));
    }

    /** Uygulama kapanırken çağrılır: bağlantıyı ve arka plan thread'ini sonlandırır. */
    public void shutdown() {
        generation.incrementAndGet();
        listener = null;
        closeInBackground();
        executor.shutdownNow();
    }

    // ---- Executor thread'inde çalışan işler ----

    private void doConnect(int id, String host, int port) {
        if (id != generation.get()) {
            return; // sıradayken Kes'e basılmış
        }
        Socket s = new Socket();
        socket = s;
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(READ_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            if (id != generation.get()) {
                closeQuietly(s); // bağlanırken Kes'e basılmış
                return;
            }
            in = new BufferedInputStream(s.getInputStream());
            out = s.getOutputStream();
            Log.d(TAG, "Bağlandı: " + host + ":" + port);
            setState(ConnectionState.CONNECTED, null);
        } catch (IOException e) {
            closeQuietly(s);
            if (id == generation.get()) {
                Log.e(TAG, "Bağlanılamadı: " + host + ":" + port, e);
                setState(ConnectionState.DISCONNECTED, "Bağlanılamadı: " + e.getMessage());
            }
        }
    }

    private void doRequest(int id, String line, ResponseCallback callback) {
        if (id != generation.get() || state != ConnectionState.CONNECTED) {
            postError(callback, "Bağlı değil");
            return;
        }
        try {
            long start = System.nanoTime();
            Log.d(TAG, "-> " + line);
            out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            logMessage(LogEntry.Direction.SENT, line);
            String reply = readLine();
            logMessage(LogEntry.Direction.RECEIVED, reply);
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            Log.d(TAG, "<- " + reply + " (" + elapsedMs + " ms)");
            postResponse(callback, reply, elapsedMs);
        } catch (IOException e) {
            Log.e(TAG, "İstek başarısız", e);
            if (id == generation.get()) {
                generation.incrementAndGet(); // sırada bekleyen istekler de geçersiz olsun
                closeInBackground();
                setState(ConnectionState.LOST, e.getMessage());
            }
            postError(callback, "İstek başarısız: " + e.getMessage());
        }
    }

    /** Bir satır okur. Süre dolarsa SocketTimeoutException fırlatır. */
    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("sunucu bağlantıyı kapattı");
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                line.write(b);
            }
            if (line.size() > MAX_LINE_BYTES) {
                throw new IOException("yanıt çok uzun");
            }
        }
        return new String(line.toByteArray(), StandardCharsets.UTF_8);
    }

    // ---- Yardımcılar ----

    /**
     * Mesajı log listesine ekler. Zaman damgası gerçek gönderme/alma anında (burada) alınır,
     * listeye ekleme ana thread'de yapılır. Handler kuyruğu sırayı koruduğu için
     * giden mesaj her zaman yanıtından önce görünür.
     */
    private void logMessage(LogEntry.Direction direction, String text) {
        final LogEntry entry = new LogEntry(System.currentTimeMillis(), direction, text);
        mainHandler.post(() -> logStore.add(entry));
    }

    private void setState(final ConnectionState newState, final String detail) {
        state = newState;
        Log.d(TAG, "Durum: " + newState + (detail != null ? " (" + detail + ")" : ""));
        mainHandler.post(() -> {
            Listener l = listener;
            if (l != null) {
                l.onStateChanged(newState, detail);
            }
        });
    }

    private void postResponse(final ResponseCallback callback, final String line, final long elapsedMs) {
        if (callback != null) {
            mainHandler.post(() -> callback.onResponse(line, elapsedMs));
        }
    }

    private void postError(final ResponseCallback callback, final String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onError(message));
        }
    }

    /** Soketi ayrı bir thread'de kapatır (bloklu bir okuma ya da bağlanmayı da uyandırır). */
    private void closeInBackground() {
        final Socket s = socket;
        socket = null;
        if (s != null) {
            new Thread(() -> closeQuietly(s), "socket-closer").start();
        }
    }

    private static void closeQuietly(Socket s) {
        if (s == null) {
            return;
        }
        try {
            s.close();
        } catch (IOException ignored) {
            // kapatırken oluşan hata önemsiz
        }
    }
}