package com.stagedevice.controller;

/**
 * Log ekranındaki tek satır: ne zaman, hangi yönde (giden/gelen) ve ne yazıyordu.
 * Değiştirilemez bir modeldir. Çok uzun metinler kesilir ki liste ve paylaşım şişmesin.
 */
public final class LogEntry {

    public enum Direction {
        SENT,     // uygulamadan sunucuya
        RECEIVED  // sunucudan uygulamaya
    }

    public static final int MAX_TEXT_LENGTH = 2000;

    private final long timestampMs;
    private final Direction direction;
    private final String text;

    public LogEntry(long timestampMs, Direction direction, String text) {
        this.timestampMs = timestampMs;
        this.direction = direction;
        this.text = limit(text);
    }

    public long getTimestampMs() {
        return timestampMs;
    }

    public Direction getDirection() {
        return direction;
    }

    public String getText() {
        return text;
    }

    private static String limit(String text) {
        if (text == null) {
            return "";
        }
        if (text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + "\u2026 (kesildi)";
    }
}