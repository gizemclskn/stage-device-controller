package com.stagedevice.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ResponseParserTest {

    @Test
    public void parsesPong() throws Exception {
        ResponseParser.Response r =
                ResponseParser.parse("{\"status\":\"ok\",\"cmd\":\"pong\"}");
        assertTrue(r.isOk());
        assertEquals("pong", r.getCmd());
    }

    @Test
    public void parsesErrorResponse() throws Exception {
        ResponseParser.Response r = ResponseParser.parse(
                "{\"status\":\"error\",\"code\":\"INVALID_VALUE\",\"message\":\"value out of range\"}");
        assertFalse(r.isOk());
        assertEquals("INVALID_VALUE", r.getErrorCode());
        assertEquals("value out of range", r.getMessage());
    }

    @Test
    public void errorWithoutCodeGetsDefaults() throws Exception {
        ResponseParser.Response r = ResponseParser.parse("{\"status\":\"error\"}");
        assertFalse(r.isOk());
        assertEquals("UNKNOWN", r.getErrorCode());
        assertEquals("", r.getMessage());
    }

    @Test
    public void keepsExtraFields() throws Exception {
        ResponseParser.Response r = ResponseParser.parse(
                "{\"status\":\"ok\",\"cmd\":\"get_state\",\"light\":[10,20]}");
        assertTrue(r.isOk());
        assertEquals(2, r.getJson().getJSONArray("light").length());
    }

    @Test(expected = ResponseParser.ParseException.class)
    public void rejectsNull() throws Exception {
        ResponseParser.parse(null);
    }

    @Test(expected = ResponseParser.ParseException.class)
    public void rejectsEmptyLine() throws Exception {
        ResponseParser.parse("   ");
    }

    @Test(expected = ResponseParser.ParseException.class)
    public void rejectsPlainText() throws Exception {
        ResponseParser.parse("pong");
    }

    @Test(expected = ResponseParser.ParseException.class)
    public void rejectsJsonArray() throws Exception {
        ResponseParser.parse("[1,2]");
    }

    @Test(expected = ResponseParser.ParseException.class)
    public void rejectsMissingStatus() throws Exception {
        ResponseParser.parse("{\"cmd\":\"pong\"}");
    }

    @Test(expected = ResponseParser.ParseException.class)
    public void rejectsNonTextStatus() throws Exception {
        ResponseParser.parse("{\"status\":1}");
    }

    @Test(expected = ResponseParser.ParseException.class)
    public void rejectsUnknownStatus() throws Exception {
        ResponseParser.parse("{\"status\":\"maybe\"}");
    }
}