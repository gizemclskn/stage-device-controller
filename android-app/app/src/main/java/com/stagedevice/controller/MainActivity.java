package com.stagedevice.controller;

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

    // Durum göstergesinin rengi (kırmızı). Diğer durumların renkleri Aşama 3'te eklenecek.
    private static final int COLOR_DISCONNECTED = 0xFFC62828;

    private EditText editIp;
    private EditText editPort;
    private Button buttonConnect;
    private Button buttonDisconnect;
    private TextView textStatus;

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

        setStatus(getString(R.string.status_disconnected), COLOR_DISCONNECTED);

        buttonConnect.setOnClickListener(view -> onConnectClicked());
        // Geçici: gerçek bağlantı kodu Aşama 3'te yazılacak
        buttonDisconnect.setOnClickListener(view -> showNotImplemented());
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
        // Geçici: gerçek bağlantı kodu Aşama 3'te yazılacak
        Toast.makeText(this, getString(R.string.toast_inputs_valid, ip, port),
                Toast.LENGTH_SHORT).show();
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

    private void showNotImplemented() {
        Toast.makeText(this, R.string.toast_not_implemented, Toast.LENGTH_SHORT).show();
    }
}