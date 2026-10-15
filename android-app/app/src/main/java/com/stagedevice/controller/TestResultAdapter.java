package com.stagedevice.controller;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Test sonuçlarını listeler: durum etiketi (yeşil PASS, kırmızı FAIL), ad, süre ve açıklama. */
public class TestResultAdapter extends RecyclerView.Adapter<TestResultAdapter.Holder> {

    private final List<TestResult> results;
    private final int passColor;
    private final int failColor;

    public TestResultAdapter(List<TestResult> results, int passColor, int failColor) {
        this.results = results;
        this.passColor = passColor;
        this.failColor = failColor;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_test_result, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        TestResult result = results.get(position);
        holder.badge.setText(result.getStatus().name());
        holder.badge.setTextColor(result.isPassed() ? passColor : failColor);
        holder.title.setText(result.getId() + "  " + result.getName());
        holder.duration.setText(holder.itemView.getContext()
                .getString(R.string.test_duration_ms, result.getDurationMs()));
        holder.detail.setText(result.getDetail());
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView badge;
        final TextView title;
        final TextView duration;
        final TextView detail;

        Holder(@NonNull View itemView) {
            super(itemView);
            badge = itemView.findViewById(R.id.textBadge);
            title = itemView.findViewById(R.id.textTestTitle);
            duration = itemView.findViewById(R.id.textTestDuration);
            detail = itemView.findViewById(R.id.textTestDetail);
        }
    }
}
