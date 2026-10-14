package com.stagedevice.controller;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

/**
 * Uygulama süreciyle birlikte yaşar. SocketClient ve cihaz durumunu burada tutarız ki
 * ekran döndüğünde (Activity yeniden oluşurken) bağlantı ve değerler kaybolmasın.
 */
public class StageApp extends Application {

    private final SocketClient client = new SocketClient();
    private final DeviceState deviceState = new DeviceState();

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
}