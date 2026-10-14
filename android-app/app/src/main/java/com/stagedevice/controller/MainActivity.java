package com.stagedevice.controller;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.tabs.TabLayout;

import org.json.JSONException;

/**
 * Ana ekran: üstte başlık ve durum satırı, altında sekmeler (Bağlantı, Işık, Ses).
 * Bağlantı durumunu dinler, bağlanınca get_state ile cihaz durumunu yükler ve
 * görünen sekmeye haber verir.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SDC-MainActivity";
    private static final String KEY_TAB = "tab";

    private static final int TAB_CONNECTION = 0;
    private static final int TAB_LIGHT = 1;
    private static final int TAB_AUDIO = 2;

    // Durum göstergesinin renkleri
    private static final int COLOR_DISCONNECTED = 0xFFEF5350; // kırmızı
    private static final int COLOR_BUSY = 0xFFFFA726;         // turuncu
    private static final int COLOR_CONNECTED = 0xFF66BB6A;    // yeşil

    private TextView textStatus;
    private TabLayout tabLayout;

    // Uygulama sürecinde yaşarlar (StageApp), bu yüzden ekran dönünce kaybolmaz
    private SocketClient client;
    private DeviceState deviceState;

    private int shownTab = -1;

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

        StageApp app = (StageApp) getApplication();
        client = app.getClient();
        deviceState = app.getDeviceState();

        textStatus = findViewById(R.id.textStatus);
        tabLayout = findViewById(R.id.tabLayout);

        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_connection));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_light));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_audio));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showTab(tab.getPosition());
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // gerek yok
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // gerek yok
            }
        });

        // Ekran döndüyse önceki sekmeyi geri getir
        int saved = savedInstanceState != null
                ? savedInstanceState.getInt(KEY_TAB, TAB_CONNECTION) : TAB_CONNECTION;
        TabLayout.Tab tab = tabLayout.getTabAt(saved);
        if (tab != null) {
            tab.select();
        }
        showTab(saved);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_TAB, tabLayout.getSelectedTabPosition());
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
        // Ekran dönerken isFinishing() false'tur, bağlantı korunur.
        // Kullanıcı uygulamadan çıkarsa bağlantıyı kapat.
        if (isFinishing()) {
            client.disconnect();
        }
        super.onDestroy();
    }

    /** Seçilen sekmenin fragment'ini gösterir. */
    private void showTab(int position) {
        if (position == shownTab) {
            return;
        }
        shownTab = position;
        Fragment fragment;
        switch (position) {
            case TAB_LIGHT:
                fragment = ChannelsFragment.newInstance(CommandBuilder.TYPE_LIGHT);
                break;
            case TAB_AUDIO:
                fragment = ChannelsFragment.newInstance(CommandBuilder.TYPE_AUDIO);
                break;
            default:
                fragment = new ConnectionFragment();
                break;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    /** Durum satırını günceller: renkli bir nokta ve metin. */
    private void setStatus(String text, int color) {
        textStatus.setText("● " + text);
        textStatus.setTextColor(color);
    }

    private ConnectionAware currentTab() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        return fragment instanceof ConnectionAware ? (ConnectionAware) fragment : null;
    }

    /** Bağlantı durumuna göre durum satırını ayarlar, sekmeye haber verir. */
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

        ConnectionAware tab = currentTab();
        if (tab != null) {
            tab.onConnectionState(state, detail);
        }

        boolean idle = state == ConnectionState.DISCONNECTED || state == ConnectionState.LOST;
        if (idle && detail != null) {
            Toast.makeText(this, getString(R.string.toast_connection_problem, detail),
                    Toast.LENGTH_LONG).show();
        }

        if (state == ConnectionState.CONNECTED) {
            requestDeviceState();
        }
    }

    /** get_state ile cihazın gerçek durumunu çeker ve kaydırıcılara yansıtır. */
    private void requestDeviceState() {
        client.request(CommandBuilder.getState(), new SocketClient.ResponseCallback() {
            @Override
            public void onResponse(String line, long elapsedMs) {
                if (isDestroyed()) {
                    return;
                }
                try {
                    ResponseParser.Response response = ResponseParser.parse(line);
                    if (response.isOk() && "get_state".equals(response.getCmd())) {
                        deviceState.loadFrom(response.getJson());
                        ConnectionAware tab = currentTab();
                        if (tab != null) {
                            tab.onDeviceStateLoaded();
                        }
                    } else {
                        Log.w(TAG, "get_state beklenmeyen yanıt: " + line);
                    }
                } catch (ResponseParser.ParseException | JSONException e) {
                    Log.w(TAG, "get_state yanıtı çözülemedi: " + line, e);
                }
            }

            @Override
            public void onError(String message) {
                Log.w(TAG, "get_state başarısız: " + message);
            }
        });
    }
}