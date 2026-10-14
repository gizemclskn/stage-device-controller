package com.stagedevice.controller;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Senaryoları arka plan thread'inde sırayla çalıştırır.
 *
 * Kendi bağlantısını (TestSession) kullanır. Bir senaryo zaman aşımına ya da ağ hatasına
 * düşerse FAIL olur, bağlantı kapatılır ve bir sonraki senaryo için yeniden bağlanılır.
 * Sonuçlar Handler ile ana thread'e bildirilir. Her çalıştırma için yeni bir thread açılır
 * ve iş bitince kapanır (thread sızıntısı olmaz).
 */
public class TestRunner {

    /** Tüm bildirimler ana thread'de gelir. */
    public interface Listener {
        void onScenarioStarted(int index, int total, TestScenario scenario);

        void onScenarioFinished(int index, int total, TestResult result);

        /** cancelled true ise çalıştırma yarıda kesilmiştir, results o ana kadarkileri içerir. */
        void onFinished(List<TestResult> results, boolean cancelled);
    }

    private static final String TAG = "SDC-TestRunner";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<TestScenario> scenarios;

    private boolean running;                       // "this" ile korunur
    private volatile boolean cancelled;
    private volatile TestSession currentSession;

    public TestRunner(List<TestScenario> scenarios) {
        this.scenarios = Collections.unmodifiableList(new ArrayList<>(scenarios));
    }

    public synchronized boolean isRunning() {
        return running;
    }

    /** Testleri başlatır. Zaten çalışıyorsa false döner. */
    public synchronized boolean start(final String host, final int port,
                                      final Listener listener) {
        if (running) {
            return false;
        }
        running = true;
        cancelled = false;
        ExecutorService executor = Executors.newSingleThreadExecutor(
                r -> new Thread(r, "sdc-test-runner"));
        executor.execute(() -> runAll(host, port, listener));
        executor.shutdown(); // kuyruktaki iş biter, sonra thread kapanır
        return true;
    }

    /** Çalışan testi yarıda keser (bloklu okuma da uyandırılır). */
    public void cancel() {
        cancelled = true;
        TestSession session = currentSession;
        if (session != null) {
            session.closeAsync();
        }
    }

    // ---- Runner thread'inde çalışan işler ----

    private void runAll(String host, int port, Listener listener) {
        TestSession session = new TestSession(host, port);
        currentSession = session;
        List<TestResult> results = new ArrayList<>();
        int total = scenarios.size();
        try {
            for (int i = 0; i < total && !cancelled; i++) {
                TestScenario scenario = scenarios.get(i);
                postStarted(listener, i, total, scenario);
                TestResult result = runOne(session, scenario);
                if (cancelled) {
                    break; // iptal yüzünden bozulan senaryo sonuç sayılmaz
                }
                results.add(result);
                postFinished(listener, i, total, result);
            }
        } finally {
            session.close();
            currentSession = null;
            synchronized (this) {
                running = false;
            }
        }
        postDone(listener, results, cancelled);
    }

    private TestResult runOne(TestSession session, TestScenario scenario) {
        long start = System.nanoTime();
        boolean passed;
        String detail;
        try {
            if (!scenario.ownsConnection() && !session.isOpen()) {
                session.connect();
            }
            detail = scenario.getBody().run(session);
            passed = true;
        } catch (TestScenario.Failure e) {
            // Beklenen koşul sağlanmadı. Yanıt alındığı için bağlantı eşzamanlı kalır.
            detail = e.getMessage();
            passed = false;
        } catch (SocketTimeoutException e) {
            detail = "zaman aşımı: sunucu 3 sn içinde yanıt vermedi";
            passed = false;
            session.close(); // geç gelen yanıt sonraki senaryoyu bozmasın
        } catch (IOException e) {
            detail = "ağ hatası: " + e.getMessage();
            passed = false;
            session.close();
        } catch (Exception e) {
            Log.e(TAG, "Beklenmeyen hata: " + scenario.getId(), e);
            detail = "beklenmeyen hata: " + e;
            passed = false;
            session.close();
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        Log.d(TAG, scenario.getId() + " " + (passed ? "PASS" : "FAIL") + " ("
                + durationMs + " ms): " + detail);
        return new TestResult(scenario.getId(), scenario.getName(),
                passed ? TestResult.Status.PASS : TestResult.Status.FAIL, durationMs, detail);
    }

    // ---- Ana thread'e bildirim ----

    private void postStarted(final Listener l, final int index, final int total,
                             final TestScenario scenario) {
        if (l != null) {
            mainHandler.post(() -> l.onScenarioStarted(index, total, scenario));
        }
    }

    private void postFinished(final Listener l, final int index, final int total,
                              final TestResult result) {
        if (l != null) {
            mainHandler.post(() -> l.onScenarioFinished(index, total, result));
        }
    }

    private void postDone(final Listener l, final List<TestResult> results,
                          final boolean wasCancelled) {
        if (l != null) {
            final List<TestResult> copy = Collections.unmodifiableList(results);
            mainHandler.post(() -> l.onFinished(copy, wasCancelled));
        }
    }
}
