package com.stagedevice.controller;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Sunucudan gelen tek satırlık yanıtı çözer.
 *
 * Savunmacı yazıldı: bozuk, boş ya da beklenmeyen biçimdeki bir yanıt
 * uygulamayı çökertmez, kontrollü bir ParseException üretir.
 */
public final class ResponseParser {

    /** Yanıt çözülemediğinde fırlatılır. */
    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    /** Çözülmüş bir sunucu yanıtı. */
    public static final class Response {
        private final boolean ok;
        private final String cmd;
        private final String errorCode;
        private final String message;
        private final JSONObject json;

        private Response(boolean ok, String cmd, String errorCode, String message,
                         JSONObject json) {
            this.ok = ok;
            this.cmd = cmd;
            this.errorCode = errorCode;
            this.message = message;
            this.json = json;
        }

        public boolean isOk() {
            return ok;
        }

        /** Başarılı yanıtlarda "cmd" alanı, yoksa null. */
        public String getCmd() {
            return cmd;
        }

        /** Hata yanıtlarında "code" alanı, yoksa "UNKNOWN". Başarılı yanıtlarda null. */
        public String getErrorCode() {
            return errorCode;
        }

        /** Hata yanıtlarında "message" alanı, yoksa boş metin. Başarılı yanıtlarda null. */
        public String getMessage() {
            return message;
        }

        /** Yanıtın tüm alanları (örneğin get_state'in light ve audio dizileri). */
        public JSONObject getJson() {
            return json;
        }
    }

    private ResponseParser() {
    }

    public static Response parse(String line) throws ParseException {
        if (line == null || line.trim().isEmpty()) {
            throw new ParseException("boş yanıt");
        }
        JSONObject json;
        try {
            json = new JSONObject(line);
        } catch (JSONException e) {
            throw new ParseException("geçerli bir JSON nesnesi değil");
        }

        Object status = json.opt("status");
        if (!(status instanceof String)) {
            throw new ParseException("status alanı yok ya da metin değil");
        }

        if ("ok".equals(status)) {
            Object cmd = json.opt("cmd");
            return new Response(true, cmd instanceof String ? (String) cmd : null,
                    null, null, json);
        }
        if ("error".equals(status)) {
            Object code = json.opt("code");
            Object message = json.opt("message");
            return new Response(false, null,
                    code instanceof String ? (String) code : "UNKNOWN",
                    message instanceof String ? (String) message : "",
                    json);
        }
        throw new ParseException("bilinmeyen status: " + status);
    }
}