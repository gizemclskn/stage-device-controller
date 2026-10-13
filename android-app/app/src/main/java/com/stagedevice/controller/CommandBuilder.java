package com.stagedevice.controller;

import org.json.JSONException;
import org.json.JSONObject;

/** Sunucuya gönderilecek komut satırlarını üretir (satır sonu karakteri olmadan). */
public final class CommandBuilder {

    private CommandBuilder() {
    }

    /** {"cmd":"ping"} */
    public static String ping() {
        return build("ping");
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