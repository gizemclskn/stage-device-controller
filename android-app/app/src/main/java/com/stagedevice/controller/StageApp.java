package com.stagedevice.controller;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Uygulama süreciyle birlikte yaşar. SocketClient, cihaz durumu, log listesi ve test durumunu
 * burada tutarız ki ekran döndüğünde (Activity yeniden oluşurken) bağlantı ve değerler kaybolmasın.
 */
public class StageApp extends Application {

    // Sıra önemli: SocketClient, LogStore'u ister, o yüzden LogStore önce oluşturulur
    private final LogStore logStore = new LogStore();
    private final SocketClient client = new SocketClient(logStore);
    private final DeviceState deviceState = new DeviceState();
    private final TestHolder testHolder = new TestHolder();

    @Override
    public void onCreate() {
        super.onCreate();
        // Sahne konsolu teması koyu tasarlandı: telefon açık modda olsa da koyu kal
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    public SocketClient getClient() {
        return client;
    }

    public DeviceState getDeviceState() {
        return deviceState;
    }

    public LogStore getLogStore() {
        return logStore;
    }

    public TestHolder getTestHolder() {
        return testHolder;
    }
}