package com.stagedevice.controller;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Test sonuçlarından JSON rapor üretir ve uygulamanın kendi klasörüne kaydeder.
 * Klasör: getExternalFilesDir/reports (izin gerektirmez). Dış depolama yoksa dahili klasör.
 */
public final class TestReport {

    private static final String DIR_NAME = "reports";

    private TestReport() {
    }

    /** Raporu JSON nesnesi olarak üretir: cihaz bilgisi, özet ve senaryo sonuçları. */
    public static JSONObject build(List<TestResult> results, String host, int port,
                                   String appVersion, long timeMs) throws JSONException {
        int passed = 0;
        JSONArray items = new JSONArray();
        for (TestResult r : results) {
            if (r.isPassed()) {
                passed++;
            }
            JSONObject item = new JSONObject();
            item.put("id", r.getId());
            item.put("name", r.getName());
            item.put("status", r.getStatus().name());
            item.put("durationMs", r.getDurationMs());
            item.put("detail", r.getDetail());
            items.put(item);
        }
        int failed = results.size() - passed;

        JSONObject summary = new JSONObject();
        summary.put("total", results.size());
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("overall", failed == 0 ? "PASS" : "FAIL");

        JSONObject root = new JSONObject();
        root.put("app", "Stage Device Controller");
        root.put("appVersion", appVersion);
        root.put("date", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
                .format(new Date(timeMs)));
        root.put("server", host + ":" + port);
        root.put("summary", summary);
        root.put("results", items);
        return root;
    }

    /** Rapor metnini zaman damgalı bir dosyaya yazar ve dosyayı döndürür. */
    public static File save(Context context, String content, long timeMs) throws IOException {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir(); // dış depolama yoksa dahili klasör
        }
        File dir = new File(base, DIR_NAME);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("klasör oluşturulamadı: " + dir);
        }
        String name = "test-report-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date(timeMs))
                + ".json";
        File file = new File(dir, name);
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file),
                StandardCharsets.UTF_8)) {
            writer.write(content);
        }
        return file;
    }
}
