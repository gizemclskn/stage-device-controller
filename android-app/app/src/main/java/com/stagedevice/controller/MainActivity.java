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

        // Geçici: gerçek bağlantı kodu Aşama 3'te yazılacak
        buttonConnect.setOnClickListener(view -> showNotImplemented());
        buttonDisconnect.setOnClickListener(view -> showNotImplemented());
    }

    /** Durum satırını günceller: renkli bir nokta ve metin. */
    private void setStatus(String text, int color) {
        textStatus.setText("● " + text);
        textStatus.setTextColor(color);
    }

    private void showNotImplemented() {
        Toast.makeText(this, R.string.toast_not_implemented, Toast.LENGTH_SHORT).show();
    }
}