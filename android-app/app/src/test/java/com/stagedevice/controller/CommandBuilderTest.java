package com.stagedevice.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.json.JSONObject;
import org.junit.Test;

public class CommandBuilderTest {

    @Test
    public void ping_buildsPingCommand() throws Exception {
        JSONObject json = new JSONObject(CommandBuilder.ping());
        assertEquals("ping", json.getString("cmd"));
        assertEquals(1, json.length());
    }

    @Test
    public void ping_hasNoLineBreak() {
        assertFalse(CommandBuilder.ping().contains("\n"));
    }
}