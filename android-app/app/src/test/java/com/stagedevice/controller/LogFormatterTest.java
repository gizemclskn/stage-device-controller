package com.stagedevice.controller;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

public class LogFormatterTest {

    private final LogFormatter formatter = new LogFormatter(TimeZone.getTimeZone("UTC"));

    private static LogEntry entry(String text) {
        return new LogEntry(0L, LogEntry.Direction.SENT, text);
    }

    @Test
    public void shareLine_hasTimeDirectionAndText() {
        LogEntry sent = new LogEntry(3_723_004L, LogEntry.Direction.SENT, "{\"cmd\":\"ping\"}");
        LogEntry received = new LogEntry(0L, LogEntry.Direction.RECEIVED, "ok");
        assertEquals("01:02:03.004  SENT     {\"cmd\":\"ping\"}", formatter.shareLine(sent));
        assertEquals("00:00:00.000  RECEIVED ok", formatter.shareLine(received));
    }

    @Test
    public void listLine_usesArrows() {
        LogEntry sent = new LogEntry(0L, LogEntry.Direction.SENT, "hi");
        LogEntry received = new LogEntry(0L, LogEntry.Direction.RECEIVED, "hi");
        assertEquals("00:00:00.000 \u2192 hi", formatter.listLine(sent));
        assertEquals("00:00:00.000 \u2190 hi", formatter.listLine(received));
    }

    @Test
    public void formatAll_keepsAllLinesUnderLimit() {
        List<LogEntry> list = Arrays.asList(entry("a"), entry("b"));
        String expected = formatter.shareLine(list.get(0)) + "\n"
                + formatter.shareLine(list.get(1)) + "\n";
        assertEquals(expected, formatter.formatAll(list, 10_000));
    }

    @Test
    public void formatAll_dropsOldestLinesOverLimit() {
        List<LogEntry> list = Arrays.asList(entry("a"), entry("b"), entry("c"));
        int lineSize = formatter.shareLine(list.get(0)).length() + 1;
        String expected = formatter.shareLine(list.get(1)) + "\n"
                + formatter.shareLine(list.get(2)) + "\n";
        assertEquals(expected, formatter.formatAll(list, 2 * lineSize));
    }
}