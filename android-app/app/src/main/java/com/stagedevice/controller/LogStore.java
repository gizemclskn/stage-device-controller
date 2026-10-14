package com.stagedevice.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bellek içi log listesi. En fazla {@link #MAX_ENTRIES} kayıt tutar, dolunca en eskiyi siler.
 * Yalnızca ana thread'de kullanılır (kilit yok). SocketClient kayıtları Handler ile
 * ana thread'e taşıyıp buraya ekler.
 */
public class LogStore {

    public static final int MAX_ENTRIES = 500;

    /** Liste değişince ana thread'de çağrılır. */
    public interface Listener {
        /**
         * @param position      yeni kaydın listedeki sırası
         * @param oldestRemoved liste doluydu, en eski kayıt (sıra 0) silindi
         */
        void onEntryAdded(int position, boolean oldestRemoved);

        void onCleared();
    }

    private final List<LogEntry> entries = new ArrayList<>();
    private final int maxEntries;
    private Listener listener;

    public LogStore() {
        this(MAX_ENTRIES);
    }

    /** Sınırı değiştirebilmek yalnızca testlerde küçük bir sınırla denemek içindir. */
    public LogStore(int maxEntries) {
        if (maxEntries < 1) {
            throw new IllegalArgumentException("maxEntries en az 1 olmalı");
        }
        this.maxEntries = maxEntries;
    }

    /** Değiştirilemez, canlı görünüm: liste değiştikçe bu görünüm de güncellenir. */
    public List<LogEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void add(LogEntry entry) {
        boolean oldestRemoved = false;
        if (entries.size() >= maxEntries) {
            entries.remove(0);
            oldestRemoved = true;
        }
        entries.add(entry);
        if (listener != null) {
            listener.onEntryAdded(entries.size() - 1, oldestRemoved);
        }
    }

    public void clear() {
        if (entries.isEmpty()) {
            return; // boş listeyi temizlemek kimseyi uyandırmasın
        }
        entries.clear();
        if (listener != null) {
            listener.onCleared();
        }
    }
}