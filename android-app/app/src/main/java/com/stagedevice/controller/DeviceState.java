package com.stagedevice.controller;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Cihazın son bilinen durumu: 8 ışık, 8 ses (seviye + mute).
 * Kanal indeksleri 0 tabanlıdır (indeks 0 = Kanal 1). Yalnızca ana thread'de kullanılır.
 */
public class DeviceState {

    public static final int CHANNELS = 8;

    private final int[] light = new int[CHANNELS];
    private final int[] audioLevel = new int[CHANNELS];
    private final boolean[] audioMute = new boolean[CHANNELS];

    public int getLight(int index) {
        return light[index];
    }

    public int getAudioLevel(int index) {
        return audioLevel[index];
    }

    public boolean isAudioMute(int index) {
        return audioMute[index];
    }

    public void setLight(int index, int value) {
        light[index] = value;
    }

    public void setAudioLevel(int index, int value) {
        audioLevel[index] = value;
    }

    public void setAudioMute(int index, boolean mute) {
        audioMute[index] = mute;
    }

    /**
     * get_state yanıtından durumu yükler. Yanıt bozuksa JSONException fırlatır ve
     * eski değerler değişmez (önce geçici dizilere okunur, sonra kopyalanır).
     */
    public void loadFrom(JSONObject json) throws JSONException {
        JSONArray lightArray = json.getJSONArray("light");
        JSONArray audioArray = json.getJSONArray("audio");
        if (lightArray.length() != CHANNELS || audioArray.length() != CHANNELS) {
            throw new JSONException("8 kanal bekleniyordu");
        }

        int[] newLight = new int[CHANNELS];
        int[] newLevel = new int[CHANNELS];
        boolean[] newMute = new boolean[CHANNELS];
        for (int i = 0; i < CHANNELS; i++) {
            newLight[i] = checkLevel(lightArray.getInt(i));
            JSONObject audio = audioArray.getJSONObject(i);
            newLevel[i] = checkLevel(audio.getInt("level"));
            newMute[i] = audio.getBoolean("mute");
        }

        System.arraycopy(newLight, 0, light, 0, CHANNELS);
        System.arraycopy(newLevel, 0, audioLevel, 0, CHANNELS);
        System.arraycopy(newMute, 0, audioMute, 0, CHANNELS);
    }

    private static int checkLevel(int value) throws JSONException {
        if (value < 0 || value > 100) {
            throw new JSONException("değer 0-100 dışında: " + value);
        }
        return value;
    }
}