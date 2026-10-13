package com.stagedevice.controller;

import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 8 kanallık kaydırıcı listesi. Aynı sınıf hem ışık hem ses sekmesi için kullanılır:
 * hangisi olduğu "type" argümanıyla belirlenir. Ses sekmesinde her satırda mute kutusu da olur.
 */
public class ChannelsFragment extends Fragment implements ConnectionAware {

    private static final String TAG = "SDC-ChannelsFragment";
    private static final String ARG_TYPE = "type";
    private static final long THROTTLE_MS = 100; // sürüklerken en fazla 100 ms'de bir gönder

    public static ChannelsFragment newInstance(String type) {
        ChannelsFragment fragment = new ChannelsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, type);
        fragment.setArguments(args);
        return fragment;
    }

    private String type;
    private boolean audio;
    private SocketClient client;
    private DeviceState deviceState;

    private final SeekBar[] seekBars = new SeekBar[DeviceState.CHANNELS];
    private final TextView[] valueTexts = new TextView[DeviceState.CHANNELS];
    private final CheckBox[] muteBoxes = new CheckBox[DeviceState.CHANNELS];
    private final long[] lastSentMs = new long[DeviceState.CHANNELS];

    // Arayüzü kodla güncellerken (sunucudan gelen değerler) komut göndermemek için
    private boolean updatingUi;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = requireArguments().getString(ARG_TYPE, CommandBuilder.TYPE_LIGHT);
        audio = CommandBuilder.TYPE_AUDIO.equals(type);
        StageApp app = (StageApp) requireActivity().getApplication();
        client = app.getClient();
        deviceState = app.getDeviceState();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_channels, container, false);
        ViewGroup rows = view.findViewById(R.id.rows);

        for (int i = 0; i < DeviceState.CHANNELS; i++) {
            final int index = i;
            View row = inflater.inflate(R.layout.item_channel, rows, false);

            TextView label = row.findViewById(R.id.textLabel);
            label.setText(getString(R.string.channel_label, i + 1));

            seekBars[i] = row.findViewById(R.id.seekBar);
            valueTexts[i] = row.findViewById(R.id.textValue);
            muteBoxes[i] = row.findViewById(R.id.checkMute);

            seekBars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    valueTexts[index].setText(String.valueOf(progress));
                    if (updatingUi || !fromUser) {
                        return;
                    }
                    // Sürüklerken en fazla her 100 ms'de bir gönder
                    long now = SystemClock.uptimeMillis();
                    if (now - lastSentMs[index] >= THROTTLE_MS) {
                        lastSentMs[index] = now;
                        sendLevel(index, progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                    // gerek yok
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    // Parmak kalkınca son değeri mutlaka gönder
                    sendLevel(index, bar.getProgress());
                }
            });

            if (audio) {
                muteBoxes[i].setOnCheckedChangeListener((box, isChecked) -> {
                    if (!updatingUi) {
                        sendMute(index, isChecked);
                    }
                });
            } else {
                muteBoxes[i].setVisibility(View.GONE); // mute yalnızca ses kanallarında var
            }

            rows.addView(row);
        }
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        refresh();
        setControlsEnabled(client.getState() == ConnectionState.CONNECTED);
    }

    @Override
    public void onConnectionState(ConnectionState state, String detail) {
        if (getView() == null) {
            return;
        }
        setControlsEnabled(state == ConnectionState.CONNECTED);
    }

    @Override
    public void onDeviceStateLoaded() {
        if (getView() == null) {
            return;
        }
        refresh();
    }

    /** Kaydırıcıları ve mute kutularını DeviceState'teki değerlere getirir. */
    private void refresh() {
        updatingUi = true;
        for (int i = 0; i < DeviceState.CHANNELS; i++) {
            int level = audio ? deviceState.getAudioLevel(i) : deviceState.getLight(i);
            seekBars[i].setProgress(level);
            valueTexts[i].setText(String.valueOf(level));
            if (audio) {
                muteBoxes[i].setChecked(deviceState.isAudioMute(i));
            }
        }
        updatingUi = false;
    }

    private void setControlsEnabled(boolean enabled) {
        for (int i = 0; i < DeviceState.CHANNELS; i++) {
            seekBars[i].setEnabled(enabled);
            muteBoxes[i].setEnabled(enabled);
        }
    }

    private void sendLevel(final int index, final int value) {
        send(CommandBuilder.setLevel(type, index + 1, value), () -> {
            if (audio) {
                deviceState.setAudioLevel(index, value);
            } else {
                deviceState.setLight(index, value);
            }
        });
    }

    private void sendMute(final int index, final boolean mute) {
        send(CommandBuilder.setMute(index + 1, mute),
                () -> deviceState.setAudioMute(index, mute));
    }

    /** Komutu gönderir. Sunucu "ok" derse onOk çalışır, hata derse kısa bir mesaj gösterilir. */
    private void send(String command, final Runnable onOk) {
        client.request(command, new SocketClient.ResponseCallback() {
            @Override
            public void onResponse(String line, long elapsedMs) {
                try {
                    ResponseParser.Response response = ResponseParser.parse(line);
                    if (response.isOk()) {
                        onOk.run();
                    } else {
                        showServerError(response.getErrorCode());
                    }
                } catch (ResponseParser.ParseException e) {
                    Log.w(TAG, "Geçersiz yanıt: " + line, e);
                    showServerError("INVALID_RESPONSE");
                }
            }

            @Override
            public void onError(String message) {
                // Bağlantı sorunu zaten durum satırında ve uyarıda gösteriliyor,
                // kuyrukta bekleyen her komut için ayrıca mesaj çıkarmıyoruz.
                Log.w(TAG, "Komut gönderilemedi: " + message);
            }
        });
    }

    private void showServerError(String code) {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), getString(R.string.toast_server_error, code),
                Toast.LENGTH_SHORT).show();
    }
}