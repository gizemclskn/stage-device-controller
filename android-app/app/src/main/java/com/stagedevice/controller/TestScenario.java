package com.stagedevice.controller;

/**
 * Bir test senaryosunun tanımı: kimlik, ad ve çalıştırılacak gövde.
 * Gövde geçerse açıklama metni döndürür, kalırsa Failure fırlatır.
 * IOException (zaman aşımı dahil) gövdeden dışarı çıkabilir, TestRunner bunu FAIL sayar.
 */
public final class TestScenario {

    /** Senaryonun çalışan kısmı. Başarılıysa kısa bir açıklama döndürür. */
    public interface Body {
        String run(TestSession session) throws Exception;
    }

    /** Beklenen koşul sağlanmadığında fırlatılır, mesajı sonuç açıklamasına yazılır. */
    public static final class Failure extends Exception {
        public Failure(String message) {
            super(message);
        }
    }

    private final String id;
    private final String name;
    private final boolean ownsConnection;
    private final Body body;

    /**
     * @param ownsConnection true ise runner senaryodan önce bağlantı açmaz, senaryo
     *                       bağlantıyı kendisi yönetir (T1 ve T11 gibi)
     */
    public TestScenario(String id, String name, boolean ownsConnection, Body body) {
        this.id = id;
        this.name = name;
        this.ownsConnection = ownsConnection;
        this.body = body;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean ownsConnection() {
        return ownsConnection;
    }

    public Body getBody() {
        return body;
    }
}
