package com.stagedevice.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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

    @Test
    public void getState_buildsGetStateCommand() throws Exception {
        JSONObject json = new JSONObject(CommandBuilder.getState());
        assertEquals("get_state", json.getString("cmd"));
        assertEquals(1, json.length());
    }

    @Test
    public void setLevel_buildsLightCommand() throws Exception {
        JSONObject json = new JSONObject(
                CommandBuilder.setLevel(CommandBuilder.TYPE_LIGHT, 3, 75));
        assertEquals("set_level", json.getString("cmd"));
        assertEquals("light", json.getString("type"));
        assertEquals(3, json.getInt("channel"));
        assertEquals(75, json.getInt("value"));
        assertEquals(4, json.length());
    }

    @Test
    public void setLevel_buildsAudioCommand() throws Exception {
        JSONObject json = new JSONObject(
                CommandBuilder.setLevel(CommandBuilder.TYPE_AUDIO, 8, 0));
        assertEquals("audio", json.getString("type"));
        assertEquals(8, json.getInt("channel"));
        assertEquals(0, json.getInt("value"));
    }

    @Test
    public void setMute_buildsMuteCommand() throws Exception {
        JSONObject json = new JSONObject(CommandBuilder.setMute(2, true));
        assertEquals("set_mute", json.getString("cmd"));
        assertEquals(2, json.getInt("channel"));
        assertTrue(json.getBoolean("mute"));
        assertEquals(3, json.length());
    }

    @Test
    public void allCommands_haveNoLineBreak() {
        assertFalse(CommandBuilder.getState().contains("\n"));
        assertFalse(CommandBuilder.setLevel("light", 1, 50).contains("\n"));
        assertFalse(CommandBuilder.setMute(1, false).contains("\n"));
    }
}