package com.stagedevice.controller;

import android.app.Application;

/**
 * Uygulama süreciyle birlikte yaşar. SocketClient'ı burada tutarız ki
 * ekran döndüğünde (Activity yeniden oluşurken) bağlantı kopmasın.
 */
public class StageApp extends Application {

    private final SocketClient client = new SocketClient();

    public SocketClient getClient() {
        return client;
    }
}