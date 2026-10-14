package com.stagedevice.controller;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Log listesini RecyclerView'a bağlar. Giden ve gelen satırlar farklı renkte çizilir. */
public class LogAdapter extends RecyclerView.Adapter<LogAdapter.Holder> {

    private final List<LogEntry> entries;
    private final LogFormatter formatter;
    private final int sentColor;
    private final int receivedColor;

    /** entries, LogStore'un canlı görünümüdür: liste değiştikçe adapter de aynı veriyi görür. */
    public LogAdapter(List<LogEntry> entries, LogFormatter formatter,
                      int sentColor, int receivedColor) {
        this.entries = entries;
        this.formatter = formatter;
        this.sentColor = sentColor;
        this.receivedColor = receivedColor;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LogEntry entry = entries.get(position);
        holder.text.setText(formatter.listLine(entry));
        holder.text.setTextColor(entry.getDirection() == LogEntry.Direction.SENT
                ? sentColor : receivedColor);
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    public static class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        public Holder(@NonNull View itemView) {
            super(itemView);
            text = (TextView) itemView;
        }
    }
}