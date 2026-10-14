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
import java.net.SocketTimeoutException;
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
 *
 * Bağlantı beklenmedik biçimde koparsa (okuma/yazma hatası, sunucunun kapatması,
 * okuma zaman aşımı) otomatik yeniden bağlanır: en fazla 5 deneme, 1-2-4-8-16 sn arayla.
 * Deneme sayacı yalnızca bir istek gerçekten yanıtlanınca sıfırlanır; yalnızca TCP
 * bağlantısının kurulması yetmez (yanıt vermeyen sunucuda sonsuz döngüyü önler).
 * Kullanıcı Kes'e basarsa yeniden bağlanma durur.
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

    // Yeniden bağlanma denemeleri arasındaki bekleme (saniye). Dizi uzunluğu = deneme sayısı.
    private static final int[] BACKOFF_SECONDS = {1, 2, 4, 8, 16};

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Her connect/disconnect/kopma yeni bir numara üretir. Eski numaralı bir işin
    // sonucu artık geçersizdir (örneğin bağlanırken Kes'e basılırsa).
    private final AtomicInteger generation = new AtomicInteger();

    // Durum, numara ve deneme sayacı değişiklikleri bu kilit altında yapılır, böylece
    // Kes ile kopma aynı anda gelse bile durum tutarlı kalır.
    private final Object lock = new Object();

    // Log ekranının veri kaynağı. Yalnızca ana thread'de değiştirilir (bkz. logMessage).
    private final LogStore logStore;

    // Executor, bağlantıyla birlikte yaşar: Kes'te ya da vazgeçilince kapatılır,
    // bir sonraki bağlanmada yeniden oluşturulur (boşta thread kalmasın). "this" ile korunur.
    private ExecutorService executor;

    private volatile Listener listener;
    private volatile ConnectionState state = ConnectionState.DISCONNECTED;
    private volatile Socket socket;

    // Yeniden bağlanma için son kullanılan adres
    private volatile String lastHost;
    private volatile int lastPort;

    // Son başarılı yanıttan beri planlanan yeniden bağlanma denemesi sayısı (lock ile korunur)
    private int reconnectAttempts;

    // Bekleyen yeniden bağlanma görevi (Kes'te iptal edilir)
    private volatile Runnable pendingReconnect;

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
        final int id;
        synchronized (lock) {
            if (state != ConnectionState.DISCONNECTED && state != ConnectionState.LOST) {
                return;
            }
            lastHost = host;
            lastPort = port;
            reconnectAttempts = 0; // kullanıcı elle bağlanıyor: temiz başlangıç
            id = generation.incrementAndGet();
            setState(ConnectionState.CONNECTING, null);
        }
        getExecutor().execute(() -> doConnect(id, host, port));
    }

    /**
     * Bağlantıyı kapatır. Bağlanma, okuma ya da yeniden bağlanma sürüyorsa hepsini
     * sonlandırır ve arka plan thread'ini serbest bırakır.
     */
    public void disconnect() {
        synchronized (lock) {
            generation.incrementAndGet(); // süren işlerin sonucu artık geçersiz
            reconnectAttempts = 0;
            cancelPendingReconnect();
            closeInBackground();
            setState(ConnectionState.DISCONNECTED, null);
        }
        releaseExecutor();
    }

    /**
     * Bir satır gönderir ve tek satırlık yanıtı bekler. Sonuç ana thread'de bildirilir.
     * Okuma zaman aşımı dahil her ağ hatası bağlantıyı "koptu" durumuna düşürür:
     * geç gelen bir yanıt, sonraki komutun yanıtıyla karışmasın.
     */
    public void request(final String line, final ResponseCallback callback) {
        if (state != ConnectionState.CONNECTED) {
            postError(callback, "Bağlı değil");
            return;
        }
        final int id = generation.get();
        getExecutor().execute(() -> doRequest(id, line, callback));
    }

    /** Uygulama kapanırken çağrılır: bağlantıyı ve arka plan thread'ini sonlandırır. */
    public void shutdown() {
        synchronized (lock) {
            generation.incrementAndGet();
            cancelPendingReconnect();
            listener = null;
            closeInBackground();
        }
        synchronized (this) {
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
        }
    }

    // ---- Executor yönetimi ----

    private synchronized ExecutorService getExecutor() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor(r -> new Thread(r, "sdc-network"));
        }
        return executor;
    }

    /** Kuyruktaki işler (numaraları eski olduğu için hemen biter) tamamlanır, thread kapanır. */
    private synchronized void releaseExecutor() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    // ---- Executor thread'inde çalışan işler ----

    private void doConnect(int id, String host, int port) {
        if (id != generation.get()) {
            return; // sıradayken Kes'e basılmış
        }
        try {
            if (openConnection(id, host, port)) {
                Log.d(TAG, "Bağlandı: " + host + ":" + port);
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Bağlanılamadı: " + host + ":" + port, e);
            boolean current;
            synchronized (lock) {
                current = id == generation.get();
                if (current) {
                    // İlk bağlanma başarısızsa otomatik denenmez: kullanıcı yanlış adres girmiş olabilir
                    setState(ConnectionState.DISCONNECTED, "Bağlanılamadı: " + e.getMessage());
                }
            }
            if (current) {
                releaseExecutor();
            }
        }
    }

    /** Yeniden bağlanma denemesi. Başarısız olursa bir sonrakini planlar. */
    private void doReconnect(int id, int attempt) {
        if (id != generation.get()) {
            return; // beklerken Kes'e basılmış
        }
        String host = lastHost;
        int port = lastPort;
        try {
            if (openConnection(id, host, port)) {
                Log.d(TAG, "Yeniden bağlandı (deneme " + attempt + "): " + host + ":" + port);
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Yeniden bağlanma başarısız (deneme " + attempt + "): " + e.getMessage());
            synchronized (lock) {
                if (id == generation.get()) {
                    scheduleReconnect();
                }
            }
        }
    }

    /**
     * Soketi açar ve CONNECTED durumuna geçer. Bu sırada Kes'e basılmışsa (numara eskimişse)
     * soketi kapatıp false döner. Bağlantı hatasında soketi kapatıp istisnayı iletir.
     */
    private boolean openConnection(int id, String host, int port) throws IOException {
        Socket s = new Socket();
        synchronized (lock) {
            if (id != generation.get()) {
                return false;
            }
            socket = s;
        }
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(READ_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            InputStream newIn = new BufferedInputStream(s.getInputStream());
            OutputStream newOut = s.getOutputStream();
            synchronized (lock) {
                if (id != generation.get()) {
                    closeQuietly(s); // bağlanırken Kes'e basılmış
                    return false;
                }
                in = newIn;
                out = newOut;
                setState(ConnectionState.CONNECTED, null);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            closeQuietly(s);
            throw e;
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
            // Sunucu gerçekten yanıt verdi: bağlantı sağlıklı, deneme sayacı sıfırlanır
            synchronized (lock) {
                if (id == generation.get()) {
                    reconnectAttempts = 0;
                }
            }
            postResponse(callback, reply, elapsedMs);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "İstek başarısız", e);
            // Okuma zaman aşımı da kopma sayılır: geç gelen yanıt sonraki komutla karışmasın
            String reason = (e instanceof SocketTimeoutException)
                    ? "yanıt zaman aşımına uğradı"
                    : String.valueOf(e.getMessage());
            handleConnectionLoss(id, reason);
            postError(callback, "İstek başarısız: " + reason);
        }
    }

    /**
     * Bağlantının koptuğunu işler ve yeniden bağlanmayı başlatır.
     * Numara eskiyse Kes'e basılmış ya da kopma zaten işlenmiştir, bir şey yapılmaz.
     */
    private void handleConnectionLoss(int id, String reason) {
        synchronized (lock) {
            if (id != generation.get()) {
                return;
            }
            generation.incrementAndGet(); // sırada bekleyen istekler de geçersiz olsun
            closeInBackground();
            setState(ConnectionState.LOST, reason);
            if (lastHost != null) {
                scheduleReconnect();
            }
        }
    }

    /**
     * Sıradaki yeniden bağlanma denemesini bekleme süresinden sonra başlatır (lock altında
     * çağrılır). Deneme hakkı bittiyse LOST durumunda bırakır ve thread'i serbest bırakır;
     * kullanıcı elle Bağlan diyebilir.
     */
    private void scheduleReconnect() {
        if (reconnectAttempts >= BACKOFF_SECONDS.length) {
            generation.incrementAndGet();
            reconnectAttempts = 0;
            setState(ConnectionState.LOST,
                    "Yeniden bağlanılamadı (" + BACKOFF_SECONDS.length + " deneme)");
            releaseExecutor();
            return;
        }
        final int attempt = ++reconnectAttempts;
        final int delaySeconds = BACKOFF_SECONDS[attempt - 1];
        final int id = generation.incrementAndGet();
        setState(ConnectionState.RECONNECTING,
                "Deneme " + attempt + "/" + BACKOFF_SECONDS.length
                        + " (" + delaySeconds + " sn sonra)");
        Runnable task = () -> {
            if (id != generation.get()) {
                return; // beklerken Kes'e basılmış
            }
            getExecutor().execute(() -> doReconnect(id, attempt));
        };
        pendingReconnect = task;
        mainHandler.postDelayed(task, delaySeconds * 1000L);
    }

    private void cancelPendingReconnect() {
        Runnable task = pendingReconnect;
        pendingReconnect = null;
        if (task != null) {
            mainHandler.removeCallbacks(task);
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