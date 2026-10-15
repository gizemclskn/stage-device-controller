package com.stagedevice.controller;

/** Tek bir test senaryosunun sonucu. Değişmezdir (oluşturulduktan sonra değiştirilemez). */
public final class TestResult {

    public enum Status {
        PASS, FAIL
    }

    private final String id;
    private final String name;
    private final Status status;
    private final long durationMs;
    private final String detail;

    public TestResult(String id, String name, Status status, long durationMs, String detail) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.durationMs = durationMs;
        this.detail = detail;
    }

    /** Kısa kimlik, örneğin "T3". */
    public String getId() {
        return id;
    }

    /** Ekranda ve raporda görünen ad, örneğin "Işık kanalları 1-8". */
    public String getName() {
        return name;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isPassed() {
        return status == Status.PASS;
    }

    /** Senaryonun toplam süresi (milisaniye). */
    public long getDurationMs() {
        return durationMs;
    }

    /** İnsan okuması için açıklama: geçtiyse ölçülen değer, kaldıysa nedeni. */
    public String getDetail() {
        return detail;
    }
}
