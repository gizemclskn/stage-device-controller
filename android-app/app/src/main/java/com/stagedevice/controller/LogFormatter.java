package com.stagedevice.controller;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Log satırlarını metne çevirir: ekrandaki kısa biçim ve paylaşılan düz metin biçimi.
 * SimpleDateFormat thread-safe olmadığı için yalnızca ana thread'de kullanılır.
 */
public class LogFormatter {

    private static final String ARROW_SENT = "\u2192";     // →
    private static final String ARROW_RECEIVED = "\u2190"; // ←

    private final SimpleDateFormat timeFormat;

    public LogFormatter() {
        this(TimeZone.getDefault());
    }

    /** Saat dilimi dışarıdan verilebilir ki testler her bilgisayarda aynı sonucu versin. */
    public LogFormatter(TimeZone zone) {
        timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        timeFormat.setTimeZone(zone);
    }

    public String time(LogEntry entry) {
        return timeFormat.format(new Date(entry.getTimestampMs()));
    }

    /** Ekrandaki satır: "14:02:11.532 → {...}" */
    public String listLine(LogEntry entry) {
        String arrow = entry.getDirection() == LogEntry.Direction.SENT
                ? ARROW_SENT : ARROW_RECEIVED;
        return time(entry) + " " + arrow + " " + entry.getText();
    }

    /** Paylaşılan satır: "14:02:11.532  SENT     {...}" */
    public String shareLine(LogEntry entry) {
        String direction = entry.getDirection() == LogEntry.Direction.SENT
                ? "SENT" : "RECEIVED";
        return String.format(Locale.US, "%s  %-8s %s", time(entry), direction, entry.getText());
    }

    /**
     * Tüm kayıtları satır satır tek metne çevirir. Toplam uzunluk sınırı aşılırsa en eski
     * satırlar bırakılır (en yeni satır her zaman dahil edilir).
     */
    public String formatAll(List<LogEntry> entries, int maxChars) {
        List<String> lines = new ArrayList<>();
        int total = 0;
        for (int i = entries.size() - 1; i >= 0; i--) {
            String line = shareLine(entries.get(i));
            if (total + line.length() + 1 > maxChars && !lines.isEmpty()) {
                break;
            }
            lines.add(line);
            total += line.length() + 1;
        }
        StringBuilder text = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) {
            text.append(lines.get(i)).append('\n');
        }
        return text.toString();
    }
}