package com.stagedevice.controller;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

/**
 * Test sekmesi: yalnızca gösterir. Testin kendisi, sonuçlar ve rapor TestHolder'da
 * (uygulama düzeyinde) durur, bu yüzden ekran dönünce ya da sekme değişince kaybolmaz.
 *
 * Testler normal bağlantıdan bağımsız, kendi TCP bağlantısıyla çalışır (TestRunner).
 * Sunucu adresi, Bağlantı sekmesinde en son Bağlan'a basılan adrestir.
 */
public class TestFragment extends Fragment {

    private static final String TAG = "SDC-TestFragment";

    private TestHolder holder;
    private TestResultAdapter adapter;
    private RecyclerView recycler;

    private Button buttonStart;
    private Button buttonShare;
    private TextView textTarget;
    private TextView textProgress;
    private TextView textSummary;
    private TextView textReport;

    // Her fragment'in kendi dinleyicisi var: eski ekran yenisinin kaydını silemesin
    private final TestHolder.Listener changeListener = this::render;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        holder = ((StageApp) requireActivity().getApplication()).getTestHolder();

        buttonStart = view.findViewById(R.id.buttonStartTests);
        buttonShare = view.findViewById(R.id.buttonShareReport);
        textTarget = view.findViewById(R.id.textTestTarget);
        textProgress = view.findViewById(R.id.textTestProgress);
        textSummary = view.findViewById(R.id.textTestSummary);
        textReport = view.findViewById(R.id.textTestReport);
        recycler = view.findViewById(R.id.recyclerTests);

        adapter = new TestResultAdapter(holder.getResults(),
                ContextCompat.getColor(requireContext(), R.color.test_pass),
                ContextCompat.getColor(requireContext(), R.color.stage_error));
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);

        buttonStart.setOnClickListener(v -> onStartClicked());
        buttonShare.setOnClickListener(v -> shareReport());

        holder.setListener(changeListener);
        render(); // kaldığı yerden çiz: sonuçlar, özet, rapor
    }

    @Override
    public void onDestroyView() {
        // Test devam eder, yalnızca bu ekranın dinlemesi biter
        if (holder != null) {
            holder.removeListener(changeListener);
        }
        super.onDestroyView();
    }

    /** Görünüm hâlâ canlı mı? */
    private boolean alive() {
        return isAdded() && getView() != null;
    }

    private void onStartClicked() {
        if (holder.isRunning()) {
            holder.cancel();
        } else {
            SharedPreferences prefs = requireContext()
                    .getSharedPreferences(ConnectionFragment.PREFS_NAME, Context.MODE_PRIVATE);
            String host = prefs.getString(ConnectionFragment.KEY_IP, ConnectionFragment.DEFAULT_IP);
            int port = prefs.getInt(ConnectionFragment.KEY_PORT, ConnectionFragment.DEFAULT_PORT);
            holder.start(requireContext(), host, port);
        }
    }

    /** Ekranı TestHolder'daki duruma göre baştan çizer. */
    private void render() {
        if (!alive()) {
            return;
        }
        boolean running = holder.isRunning();

        // Adres: sonuçlar varsa o çalıştırmanın adresi, yoksa kayıtlı adres
        String host = holder.getHost();
        int port = holder.getPort();
        if (host == null || (!running && holder.getResults().isEmpty())) {
            SharedPreferences prefs = requireContext()
                    .getSharedPreferences(ConnectionFragment.PREFS_NAME, Context.MODE_PRIVATE);
            host = prefs.getString(ConnectionFragment.KEY_IP, ConnectionFragment.DEFAULT_IP);
            port = prefs.getInt(ConnectionFragment.KEY_PORT, ConnectionFragment.DEFAULT_PORT);
        }
        textTarget.setText(getString(R.string.test_target, host, port));

        // Başlat / İptal düğmesi
        if (holder.isCancelling()) {
            buttonStart.setEnabled(false);
            buttonStart.setText(R.string.button_test_cancelling);
        } else {
            buttonStart.setEnabled(true);
            buttonStart.setText(running ? R.string.button_test_cancel
                    : R.string.button_test_start);
        }

        // İlerleme satırı
        TestScenario current = holder.getCurrentScenario();
        if (running) {
            if (current != null) {
                textProgress.setText(getString(R.string.test_progress,
                        holder.getCurrentIndex() + 1, holder.getTotal(),
                        current.getId(), current.getName()));
            } else {
                textProgress.setText(R.string.test_starting);
            }
        } else if (holder.isFinished()) {
            textProgress.setText(R.string.test_finished);
        } else {
            textProgress.setText(R.string.test_ready);
        }

        // Özet satırı
        if (!running && holder.isFinished()) {
            showSummary(holder.getResults());
        } else if (!running && holder.wasCancelled()) {
            textSummary.setText(getString(R.string.test_cancelled, holder.getResults().size()));
            textSummary.setTextColor(ContextCompat.getColor(requireContext(),
                    R.color.stage_text_dim));
        } else {
            textSummary.setText("");
        }

        // Rapor satırı ve Paylaş düğmesi
        File report = holder.getReportFile();
        if (report != null) {
            textReport.setText(getString(R.string.test_report_saved, report.getName()));
        } else if (holder.isReportFailed()) {
            textReport.setText(R.string.test_report_failed);
        } else {
            textReport.setText("");
        }
        buttonShare.setEnabled(!running && report != null);

        // Sonuç listesi
        adapter.notifyDataSetChanged();
        if (adapter.getItemCount() > 0) {
            recycler.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void showSummary(List<TestResult> results) {
        int passed = 0;
        for (TestResult r : results) {
            if (r.isPassed()) {
                passed++;
            }
        }
        int failed = results.size() - passed;
        textSummary.setText(getString(R.string.test_summary, results.size(), passed, failed));
        textSummary.setTextColor(ContextCompat.getColor(requireContext(),
                failed == 0 ? R.color.test_pass : R.color.stage_error));
    }

    /** Kaydedilen rapor dosyasını paylaşma menüsüyle gönderir. */
    private void shareReport() {
        File report = holder.getReportFile();
        if (report == null) {
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", report);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("application/json");
            send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.test_share_subject));
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.setClipData(ClipData.newRawUri(report.getName(), uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.test_share_title)));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Rapor paylaşılamadı", e);
            Toast.makeText(requireContext(), R.string.test_share_error, Toast.LENGTH_SHORT).show();
        }
    }
}