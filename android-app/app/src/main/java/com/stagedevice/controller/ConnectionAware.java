package com.stagedevice.controller;

/** Activity'nin, ekrandaki sekmeye bağlantı ve durum değişikliğini haber vermesi için. */
public interface ConnectionAware {

    /** Bağlantı durumu değişti (ana thread). */
    void onConnectionState(ConnectionState state, String detail);

    /** get_state yanıtı DeviceState'e yüklendi (ana thread). */
    void onDeviceStateLoaded();
}