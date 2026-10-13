package com.stagedevice.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class DeviceStateTest {

    /** Işık i*10, ses seviyesi i*5, çift indeksli ses kanalları mute. */
    private static JSONObject stateJson(int channelCount, int lightOverride) throws Exception {
        JSONArray light = new JSONArray();
        JSONArray audio = new JSONArray();
        for (int i = 0; i < channelCount; i++) {
            light.put(i == 0 && lightOverride >= 0 ? lightOverride : i * 10);
            audio.put(new JSONObject().put("level", i * 5).put("mute", i % 2 == 0));
        }
        return new JSONObject()
                .put("status", "ok").put("cmd", "get_state")
                .put("light", light).put("audio", audio);
    }

    @Test
    public void startsAtZero() {
        DeviceState state = new DeviceState();
        assertEquals(0, state.getLight(0));
        assertEquals(0, state.getAudioLevel(7));
        assertFalse(state.isAudioMute(3));
    }

    @Test
    public void loadsLightAndAudio() throws Exception {
        DeviceState state = new DeviceState();
        state.loadFrom(stateJson(8, -1));
        assertEquals(30, state.getLight(3));
        assertEquals(15, state.getAudioLevel(3));
        assertTrue(state.isAudioMute(2));
        assertFalse(state.isAudioMute(3));
    }

    @Test(expected = JSONException.class)
    public void rejectsWrongChannelCount() throws Exception {
        new DeviceState().loadFrom(stateJson(7, -1));
    }

    @Test(expected = JSONException.class)
    public void rejectsOutOfRangeValue() throws Exception {
        new DeviceState().loadFrom(stateJson(8, 101));
    }

    @Test
    public void failedLoadKeepsPreviousValues() throws Exception {
        DeviceState state = new DeviceState();
        state.loadFrom(stateJson(8, -1));
        try {
            state.loadFrom(stateJson(8, 101));
        } catch (JSONException expected) {
            // beklenen
        }
        assertEquals(30, state.getLight(3));
        assertEquals(0, state.getLight(0));
    }
}