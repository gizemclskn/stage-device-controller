package com.stagedevice.controller;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Test Modu'nun kendi TCP bağlantısı. Uygulamanın normal SocketClient'ından bağımsızdır:
 * testler sırasında normal bağlantının durumu (LOST, yeniden bağlanma) değişmez.
 *
 * Senkron çalışır, bu yüzden yalnızca TestRunner'ın arka plan thread'inden kullanılır.
 * Tek istisna closeAsync(): iptal için herhangi bir thread'den çağrılabilir.
 * Ham satır gönderebilir (bozuk JSON senaryosu için).
 */
public final class TestSession {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final int MAX_LINE_BYTES = 64 * 1024;

    private final String host;
    private final int port;

    private volatile Socket socket;
    private InputStream in;
    private OutputStream out;

    public TestSession(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Bağlantıyı (yeniden) açar. Açık bir bağlantı varsa önce kapatılır. */
    public void connect() throws IOException {
        close();
        Socket s = new Socket();
        socket = s;
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setSoTimeout(READ_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            in = new BufferedInputStream(s.getInputStream());
            out = s.getOutputStream();
        } catch (IOException | RuntimeException e) {
            closeQuietly(s);
            socket = null;
            throw e;
        }
    }

    public boolean isOpen() {
        Socket s = socket;
        return s != null && s.isConnected() && !s.isClosed();
    }

    /**
     * Bir satır gönderir ve tek satırlık yanıtı döndürür. Süre dolarsa
     * SocketTimeoutException fırlatır. Bu durumda bağlantı kapatılmalıdır:
     * geç gelen yanıt sonraki komutun yanıtıyla karışmasın.
     */
    public String request(String line) throws IOException {
        if (out == null) {
            throw new IOException("bağlı değil");
        }
        out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
        return readLine();
    }

    /** Bağlantıyı kapatır (yalnızca runner thread'inde). */
    public void close() {
        Socket s = socket;
        socket = null;
        in = null;
        out = null;
        closeQuietly(s);
    }

    /** İptal için: soketi ayrı bir thread'de kapatır, bloklu okumayı uyandırır. */
    public void closeAsync() {
        final Socket s = socket;
        if (s != null) {
            new Thread(() -> closeQuietly(s), "sdc-test-closer").start();
        }
    }

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
