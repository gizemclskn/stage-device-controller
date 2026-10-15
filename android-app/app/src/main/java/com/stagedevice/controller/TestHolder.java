package com.stagedevice.controller;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Test Modu'nun durumunu uygulama süresince tutar (StageApp'te yaşar).
 * Sonuçlar, çalışma durumu ve rapor dosyası burada durur: ekran dönünce ya da sekme
 * değişince TestFragment yok olur ama test devam eder, fragment geri gelince durumu buradan çizer.
 *
 * Tüm metotlar ana thread'de çağrılır (TestRunner sonuçları Handler ile ana thread'e getirir).
 */
public class TestHolder {

    /** Durum değişince çağrılır. Dinleyici en fazla bir tane olur (görünen test ekranı). */
    public interface Listener {
        void onChanged();
    }

    private static final String TAG = "SDC-TestHolder";

    private final TestRunner runner = new TestRunner(TestScenarios.all());
    private final List<TestResult> results = new ArrayList<>();
    private Listener listener;

    private boolean running;
    private boolean cancelling;
    private boolean finished;   // tüm senaryolar bitti
    private boolean cancelled;  // kullanıcı yarıda kesti

    private int currentIndex;
    private int total;
    private TestScenario currentScenario;

    private String host;
    private int port;
    private Context appContext;
    private File reportFile;
    private boolean reportFailed;

    private final TestRunner.Listener runnerListener = new TestRunner.Listener() {
        @Override
        public void onScenarioStarted(int index, int totalCount, TestScenario scenario) {
            currentIndex = index;
            total = totalCount;
            currentScenario = scenario;
            notifyChanged();
        }

        @Override
        public void onScenarioFinished(int index, int totalCount, TestResult result) {
            results.add(result);
            notifyChanged();
        }

        @Override
        public void onFinished(List<TestResult> ignored, boolean wasCancelled) {
            running = false;
            cancelling = false;
            currentScenario = null;
            if (wasCancelled) {
                cancelled = true;
            } else {
                finished = true;
                saveReport();
            }
            notifyChanged();
        }
    };

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Yalnızca verilen dinleyici hâlâ kayıtlıysa kaldırır (eski ekran yenisini silmesin). */
    public void removeListener(Listener toRemove) {
        if (listener == toRemove) {
            listener = null;
        }
    }

    /** Testi başlatır. Zaten çalışıyorsa false döner, önceki sonuçlar silinir. */
    public boolean start(Context context, String host, int port) {
        if (running) {
            return false;
        }
        if (!runner.start(host, port, runnerListener)) {
            return false; // önceki çalıştırma hâlâ kapanıyor
        }
        this.appContext = context.getApplicationContext();
        this.host = host;
        this.port = port;
        results.clear();
        running = true;
        cancelling = false;
        finished = false;
        cancelled = false;
        currentScenario = null;
        reportFile = null;
        reportFailed = false;
        notifyChanged();
        return true;
    }

    /** Çalışan testi keser. Bitiş bildirimi gelene kadar durum "iptal ediliyor" olur. */
    public void cancel() {
        if (!running || cancelling) {
            return;
        }
        cancelling = true;
        runner.cancel();
        notifyChanged();
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isCancelling() {
        return cancelling;
    }

    public boolean isFinished() {
        return finished;
    }

    public boolean wasCancelled() {
        return cancelled;
    }

    /** Ekranın gösterdiği liste. Her zaman aynı nesnedir, içi değişir. */
    public List<TestResult> getResults() {
        return results;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getTotal() {
        return total;
    }

    public TestScenario getCurrentScenario() {
        return currentScenario;
    }

    /** Son çalıştırmanın sunucusu. Hiç çalıştırılmadıysa null. */
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public File getReportFile() {
        return reportFile;
    }

    public boolean isReportFailed() {
        return reportFailed;
    }

    private void notifyChanged() {
        Listener l = listener;
        if (l != null) {
            l.onChanged();
        }
    }

    /** Tamamlanan çalıştırmanın raporunu JSON olarak kaydeder. */
    private void saveReport() {
        long now = System.currentTimeMillis();
        try {
            String json = TestReport.build(results, host, port, appVersion(), now).toString(2);
            reportFile = TestReport.save(appContext, json, now);
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Rapor kaydedilemedi", e);
            reportFailed = true;
        }
    }

    private String appVersion() {
        try {
            String version = appContext.getPackageManager()
                    .getPackageInfo(appContext.getPackageName(), 0).versionName;
            return version != null ? version : "?";
        } catch (PackageManager.NameNotFoundException e) {
            return "?";
        }
    }
}