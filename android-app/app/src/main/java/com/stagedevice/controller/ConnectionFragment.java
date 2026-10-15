package com.stagedevice.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/** Bağlantı sekmesi: IP, port, Bağlan/Kes ve ping. */
public class ConnectionFragment extends Fragment implements ConnectionAware {

    private static final String TAG = "SDC-ConnectionFragment";

    // Son kullanılan adresin saklandığı yer (SharedPreferences). TestFragment da okur.
    static final String PREFS_NAME = "connection";
    static final String KEY_IP = "last_ip";
    static final String KEY_PORT = "last_port";
    static final String DEFAULT_IP = "10.0.2.2"; // emülatörden bilgisayara giden adres
    static final int DEFAULT_PORT = 5000;

    private EditText editIp;
    private EditText editPort;
    private Button buttonConnect;
    private Button buttonDisconnect;
    private Button buttonPing;
    private TextView textPing;

    private SocketClient client;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_connection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        client = ((StageApp) requireActivity().getApplication()).getClient();

        editIp = view.findViewById(R.id.editIp);
        editPort = view.findViewById(R.id.editPort);
        buttonConnect = view.findViewById(R.id.buttonConnect);
        buttonDisconnect = view.findViewById(R.id.buttonDisconnect);
        buttonPing = view.findViewById(R.id.buttonPing);
        textPing = view.findViewById(R.id.textPing);

        loadLastAddress();

        buttonConnect.setOnClickListener(v -> onConnectClicked());
        buttonDisconnect.setOnClickListener(v -> client.disconnect());
        buttonPing.setOnClickListener(v -> onPingClicked());

        // Sekme açıldığında mevcut bağlantı durumuna göre düğmeleri ayarla
        onConnectionState(client.getState(), null);
    }

    @Override
    public void onConnectionState(ConnectionState state, String detail) {
        if (getView() == null) {
            return; // görünüm henüz yok ya da yok edilmiş
        }
        boolean idle = state == ConnectionState.DISCONNECTED || state == ConnectionState.LOST;
        buttonConnect.setEnabled(idle);
        buttonDisconnect.setEnabled(!idle);
        editIp.setEnabled(idle);
        editPort.setEnabled(idle);
        updatePingButton();
        if (state != ConnectionState.CONNECTED) {
            textPing.setText(R.string.ping_idle);
        }
    }

    @Override
    public void onDeviceStateLoaded() {
        // Bu sekmede gösterilecek cihaz durumu yok
    }

    /** Daha önce kaydedilmiş IP ve portu alanlara yazar. Kayıt yoksa varsayılanları kullanır. */
    private void loadLastAddress() {
        SharedPreferences prefs =
                requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        editIp.setText(prefs.getString(KEY_IP, DEFAULT_IP));
        editPort.setText(String.valueOf(prefs.getInt(KEY_PORT, DEFAULT_PORT)));
    }

    /** Geçerli bir IP ve portu bir sonraki açılış için saklar. */
    private void saveLastAddress(String ip, int port) {
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_IP, ip)
                .putInt(KEY_PORT, port)
                .apply();
    }

    private void onConnectClicked() {
        if (!validateInputs()) {
            return; // hata mesajları alanlarda gösterildi, bağlantı denenmez
        }
        String ip = editIp.getText().toString().trim();
        int port = InputValidator.parsePort(editPort.getText().toString());
        saveLastAddress(ip, port);
        Log.d(TAG, "Bağlan: " + ip + ":" + port);
        client.connect(ip, port);
    }

    private void onPingClicked() {
        buttonPing.setEnabled(false);
        textPing.setText(R.string.ping_waiting);
        client.request(CommandBuilder.ping(), new SocketClient.ResponseCallback() {
            @Override
            public void onResponse(String line, long elapsedMs) {
                if (!isAdded() || getView() == null) {
                    return; // sekme bu arada kapanmış ya da yeniden oluşmuş
                }
                showPingResult(line, elapsedMs);
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || getView() == null) {
                    return;
                }
                Log.w(TAG, "Ping başarısız: " + message);
                textPing.setText(getString(R.string.ping_error, message));
                updatePingButton();
            }
        });
    }

    /** Ping yanıtını çözer ve sonucu (süre ya da hata) ekrana yazar. */
    private void showPingResult(String line, long elapsedMs) {
        try {
            ResponseParser.Response response = ResponseParser.parse(line);
            if (response.isOk() && "pong".equals(response.getCmd())) {
                textPing.setText(getString(R.string.ping_result, elapsedMs));
            } else if (!response.isOk()) {
                textPing.setText(getString(R.string.ping_server_error, response.getErrorCode()));
            } else {
                textPing.setText(getString(R.string.ping_unexpected, line));
            }
        } catch (ResponseParser.ParseException e) {
            Log.w(TAG, "Geçersiz ping yanıtı: " + line, e);
            textPing.setText(getString(R.string.ping_invalid_response, e.getMessage()));
        }
        updatePingButton();
    }

    private void updatePingButton() {
        buttonPing.setEnabled(client.getState() == ConnectionState.CONNECTED);
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