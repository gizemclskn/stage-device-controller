package com.stagedevice.controller;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    // Durum göstergesinin renkleri
    private static final int COLOR_DISCONNECTED = 0xFFC62828; // kırmızı
    private static final int COLOR_BUSY = 0xFFEF6C00;         // turuncu
    private static final int COLOR_CONNECTED = 0xFF2E7D32;    // yeşil

    // Son kullanılan adresin saklandığı yer (SharedPreferences)
    private static final String PREFS_NAME = "connection";
    private static final String KEY_IP = "last_ip";
    private static final String KEY_PORT = "last_port";
    private static final String DEFAULT_IP = "10.0.2.2"; // emülatörden bilgisayara giden adres
    private static final int DEFAULT_PORT = 5000;

    private EditText editIp;
    private EditText editPort;
    private Button buttonConnect;
    private Button buttonDisconnect;
    private TextView textStatus;

    // Şimdilik ekranla birlikte yaşıyor. Ekran dönünce yok olur, 3.7'de düzelteceğiz.
    private SocketClient client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Ekrandaki öğeleri koda bağla
        editIp = findViewById(R.id.editIp);
        editPort = findViewById(R.id.editPort);
        buttonConnect = findViewById(R.id.buttonConnect);
        buttonDisconnect = findViewById(R.id.buttonDisconnect);
        textStatus = findViewById(R.id.textStatus);

        loadLastAddress();

        client = new SocketClient();
        buttonConnect.setOnClickListener(view -> onConnectClicked());
        buttonDisconnect.setOnClickListener(view -> client.disconnect());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Ekran görünürken durum değişikliklerini dinle ve mevcut durumu hemen çiz
        client.setListener(this::onConnectionStateChanged);
        onConnectionStateChanged(client.getState(), null);
    }

    @Override
    protected void onStop() {
        client.setListener(null);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        client.shutdown();
        super.onDestroy();
    }

    /** Daha önce kaydedilmiş IP ve portu alanlara yazar. Kayıt yoksa varsayılanları kullanır. */
    private void loadLastAddress() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        editIp.setText(prefs.getString(KEY_IP, DEFAULT_IP));
        editPort.setText(String.valueOf(prefs.getInt(KEY_PORT, DEFAULT_PORT)));
    }

    /** Geçerli bir IP ve portu bir sonraki açılış için saklar. */
    private void saveLastAddress(String ip, int port) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_IP, ip)
                .putInt(KEY_PORT, port)
                .apply();
    }

    /** Durum satırını günceller: renkli bir nokta ve metin. */
    private void setStatus(String text, int color) {
        textStatus.setText("● " + text);
        textStatus.setTextColor(color);
    }

    private void onConnectClicked() {
        if (!validateInputs()) {
            return; // hata mesajları alanlarda gösterildi, bağlantı denenmez
        }
        String ip = editIp.getText().toString().trim();
        int port = InputValidator.parsePort(editPort.getText().toString());
        saveLastAddress(ip, port);
        client.connect(ip, port);
    }

    /** Bağlantı durumuna göre durum satırını, düğmeleri ve alanları ayarlar. */
    private void onConnectionStateChanged(ConnectionState state, String detail) {
        switch (state) {
            case CONNECTING:
                setStatus(getString(R.string.status_connecting), COLOR_BUSY);
                break;
            case CONNECTED:
                setStatus(getString(R.string.status_connected), COLOR_CONNECTED);
                break;
            case RECONNECTING:
                setStatus(getString(R.string.status_reconnecting), COLOR_BUSY);
                break;
            case LOST:
                setStatus(getString(R.string.status_lost), COLOR_DISCONNECTED);
                break;
            default: // DISCONNECTED
                setStatus(getString(R.string.status_disconnected), COLOR_DISCONNECTED);
                break;
        }

        boolean idle = state == ConnectionState.DISCONNECTED || state == ConnectionState.LOST;
        buttonConnect.setEnabled(idle);
        buttonDisconnect.setEnabled(!idle);
        editIp.setEnabled(idle);
        editPort.setEnabled(idle);

        if (idle && detail != null) {
            Toast.makeText(this, getString(R.string.toast_connection_problem, detail),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** IP ve port alanlarını doğrular. Hata varsa ilgili alanda kırmızı uyarı gösterir. */
    private boolean validateInputs() {
        String ipError = ipErrorMessage(InputValidator.validateIp(editIp.getText().toString()));
        String portError = portErrorMessage(
                InputValidator.validatePort(editPort.getText().toString()));

        editIp.setError(ipError);     // null verilirse eski uyarı temizlenir
        editPort.setError(portError);

        if (ipError != null) {
            editIp.requestFocus();
        } else if (portError != null) {
            editPort.requestFocus();
        }
        return ipError == null && portError == null;
    }

    private String ipErrorMessage(InputValidator.IpResult result) {
        switch (result) {
            case EMPTY:
                return getString(R.string.error_ip_empty);
            case INVALID:
                return getString(R.string.error_ip_invalid);
            default:
                return null;
        }
    }

    private String portErrorMessage(InputValidator.PortResult result) {
        switch (result) {
            case EMPTY:
                return getString(R.string.error_port_empty);
            case INVALID:
                return getString(R.string.error_port_invalid);
            case OUT_OF_RANGE:
                return getString(R.string.error_port_range);
            default:
                return null;
        }
    }
}