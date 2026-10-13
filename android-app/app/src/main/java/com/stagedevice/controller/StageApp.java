package com.stagedevice.controller;

import android.app.Application;

/**
 * Uygulama süreciyle birlikte yaşar. SocketClient ve cihaz durumunu burada tutarız ki
 * ekran döndüğünde (Activity yeniden oluşurken) bağlantı ve değerler kaybolmasın.
 */
public class StageApp extends Application {

    private final SocketClient client = new SocketClient();
    private final DeviceState deviceState = new DeviceState();

    public SocketClient getClient() {
        return client;
    }

    public DeviceState getDeviceState() {
        return deviceState;
    }
}