package com.stagedevice.controller;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/** Log sekmesi: giden ve gelen tüm mesajlar, Temizle ve Paylaş düğmeleri. */
public class LogFragment extends Fragment {

    private static final int MAX_SHARE_CHARS = 200_000; // paylaşılan metin bu kadarla sınırlı

    private LogStore logStore;
    private LogFormatter formatter;
    private RecyclerView recycler;
    private LogAdapter adapter;
    private TextView textEmpty;
    private Button buttonClear;
    private Button buttonShare;

    private final LogStore.Listener storeListener = new LogStore.Listener() {
        @Override
        public void onEntryAdded(int position, boolean oldestRemoved) {
            // Kullanıcı en alttaysa yeni mesajı görsün. Yukarı kaydırıp okuyorsa yerini bozma.
            boolean atBottom = !recycler.canScrollVertically(1);
            if (oldestRemoved) {
                adapter.notifyItemRemoved(0);
            }
            adapter.notifyItemInserted(position);
            if (atBottom) {
                recycler.scrollToPosition(position);
            }
            updateEmptyState();
        }

        @Override
        public void onCleared() {
            adapter.notifyDataSetChanged();
            updateEmptyState();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_log, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        logStore = ((StageApp) requireActivity().getApplication()).getLogStore();
        formatter = new LogFormatter();

        recycler = view.findViewById(R.id.recyclerLog);
        textEmpty = view.findViewById(R.id.textLogEmpty);
        buttonClear = view.findViewById(R.id.buttonClearLog);
        buttonShare = view.findViewById(R.id.buttonShareLog);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LogAdapter(logStore.getEntries(), formatter,
                ContextCompat.getColor(requireContext(), R.color.log_sent),
                ContextCompat.getColor(requireContext(), R.color.log_received));
        recycler.setAdapter(adapter);

        buttonClear.setOnClickListener(v -> logStore.clear());
        buttonShare.setOnClickListener(v -> shareLog());

        // Sekme açılırken en yeni mesajlar görünsün
        if (adapter.getItemCount() > 0) {
            recycler.scrollToPosition(adapter.getItemCount() - 1);
        }
        updateEmptyState();

        logStore.setListener(storeListener);
    }

    @Override
    public void onDestroyView() {
        // LogStore uygulama süresince yaşar: görünüm yok olunca dinlemeyi bırak
        logStore.setListener(null);
        super.onDestroyView();
    }

    /** Liste boşsa ipucu metnini göster, Temizle ve Paylaş'ı kapat. */
    private void updateEmptyState() {
        boolean empty = logStore.size() == 0;
        textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        buttonClear.setEnabled(!empty);
        buttonShare.setEnabled(!empty);
    }

    /** Logu düz metin olarak paylaşır (destek senaryosu: hatayı birine iletmek). */
    private void shareLog() {
        String text = formatter.formatAll(logStore.getEntries(), MAX_SHARE_CHARS);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_share_subject));
        send.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(send, getString(R.string.log_share_title)));
    }
}