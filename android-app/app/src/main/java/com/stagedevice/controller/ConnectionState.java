package com.stagedevice.controller;

/** Sunucu bağlantısının olası durumları. */
public enum ConnectionState {
    DISCONNECTED,   // bağlı değil
    CONNECTING,     // ilk bağlantı deneniyor
    CONNECTED,      // bağlı, komut gönderilebilir
    LOST,           // bağlantı beklenmedik biçimde koptu
    RECONNECTING    // otomatik yeniden bağlanma deneniyor (Aşama 6)
}