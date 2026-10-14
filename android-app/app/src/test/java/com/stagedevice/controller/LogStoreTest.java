package com.stagedevice.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LogStoreTest {

    private static LogEntry entry(String text) {
        return new LogEntry(0L, LogEntry.Direction.SENT, text);
    }

    @Test
    public void startsEmpty() {
        assertEquals(0, new LogStore().size());
    }

    @Test
    public void keepsInsertionOrder() {
        LogStore store = new LogStore();
        store.add(entry("a"));
        store.add(entry("b"));
        assertEquals("a", store.getEntries().get(0).getText());
        assertEquals("b", store.getEntries().get(1).getText());
    }

    @Test
    public void dropsOldestWhenFull() {
        LogStore store = new LogStore(3);
        for (String text : new String[]{"1", "2", "3", "4"}) {
            store.add(entry(text));
        }
        assertEquals(3, store.size());
        assertEquals("2", store.getEntries().get(0).getText());
        assertEquals("4", store.getEntries().get(2).getText());
    }

    @Test
    public void defaultLimitIs500() {
        LogStore store = new LogStore();
        for (int i = 0; i < 600; i++) {
            store.add(entry("m" + i));
        }
        assertEquals(500, store.size());
        assertEquals("m100", store.getEntries().get(0).getText());
    }

    @Test
    public void listenerReportsPositionRemovalAndClear() {
        LogStore store = new LogStore(2);
        final List<String> events = new ArrayList<>();
        store.setListener(new LogStore.Listener() {
            @Override
            public void onEntryAdded(int position, boolean oldestRemoved) {
                events.add(position + ":" + oldestRemoved);
            }

            @Override
            public void onCleared() {
                events.add("cleared");
            }
        });

        store.add(entry("a"));
        store.add(entry("b"));
        store.add(entry("c"));
        store.clear();
        store.clear(); // boş liste, olay üretmemeli

        assertEquals(Arrays.asList("0:false", "1:false", "1:true", "cleared"), events);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void entriesViewIsReadOnly() {
        LogStore store = new LogStore();
        store.add(entry("a"));
        store.getEntries().clear();
    }
}