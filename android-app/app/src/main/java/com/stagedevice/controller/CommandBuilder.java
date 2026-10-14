package com.stagedevice.controller;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Sunucuya gönderilecek komut satırlarını üretir (satır sonu karakteri olmadan).
 * Değerleri bilerek doğrulamaz: Test Modu (Aşama 7) sunucunun hata yanıtlarını
 * denemek için geçersiz kanal ve değer de gönderebilmelidir.
 */
public final class CommandBuilder {

    public static final String TYPE_LIGHT = "light";
    public static final String TYPE_AUDIO = "audio";

    private CommandBuilder() {
    }

    /** {"cmd":"ping"} */
    public static String ping() {
        return build("ping");
    }

    /** {"cmd":"get_state"} */
    public static String getState() {
        return build("get_state");
    }

    /** {"cmd":"set_level","type":"light","channel":3,"value":75} */
    public static String setLevel(String type, int channel, int value) {
        try {
            return new JSONObject()
                    .put("cmd", "set_level")
                    .put("type", type)
                    .put("channel", channel)
                    .put("value", value)
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {"cmd":"set_mute","channel":2,"mute":true} */
    public static String setMute(int channel, boolean mute) {
        try {
            return new JSONObject()
                    .put("cmd", "set_mute")
                    .put("channel", channel)
                    .put("mute", mute)
                    .toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String build(String cmd) {
        try {
            return new JSONObject().put("cmd", cmd).toString();
        } catch (JSONException e) {
            // cmd sabit bir metin olduğu için buraya düşülmemeli
            throw new IllegalStateException(e);
        }
    }
}