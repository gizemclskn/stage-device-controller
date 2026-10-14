package com.stagedevice.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LogEntryTest {

    @Test
    public void keepsShortText() {
        LogEntry entry = new LogEntry(1L, LogEntry.Direction.SENT, "hello");
        assertEquals("hello", entry.getText());
        assertEquals(1L, entry.getTimestampMs());
        assertEquals(LogEntry.Direction.SENT, entry.getDirection());
    }

    @Test
    public void truncatesLongText() {
        String longText = new String(new char[3000]).replace('\0', 'x');
        String text = new LogEntry(0L, LogEntry.Direction.RECEIVED, longText).getText();
        assertTrue(text.startsWith(longText.substring(0, LogEntry.MAX_TEXT_LENGTH)));
        assertTrue(text.endsWith("(kesildi)"));
        assertTrue(text.length() < 3000);
    }

    @Test
    public void nullTextBecomesEmpty() {
        assertEquals("", new LogEntry(0L, LogEntry.Direction.SENT, null).getText());
    }
}