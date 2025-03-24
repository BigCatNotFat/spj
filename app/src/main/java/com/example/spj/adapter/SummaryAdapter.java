package com.example.spj.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spj.R;
import com.example.spj.model.Summary;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 总结列表适配器
 */
public class SummaryAdapter extends RecyclerView.Adapter<SummaryAdapter.SummaryViewHolder> {

    private List<Summary> summaries = new ArrayList<>();
    private OnSummaryClickListener listener;

    // 点击监听接口
    public interface OnSummaryClickListener {
        void onSummaryClick(Summary summary);
    }

    public SummaryAdapter() {
    }

    public void setSummaries(List<Summary> summaries) {
        this.summaries = summaries != null ? summaries : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnSummaryClickListener(OnSummaryClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SummaryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_summary, parent, false);
        return new SummaryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull SummaryViewHolder holder, int position) {
        Summary summary = summaries.get(position);
        holder.bind(summary);
    }

    @Override
    public int getItemCount() {
        return summaries.size();
    }

    class SummaryViewHolder extends RecyclerView.ViewHolder {
        private TextView textViewTitle;
        private TextView textViewDate;
        private TextView textViewPreview;

        SummaryViewHolder(View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.textViewSummaryTitle);
            textViewDate = itemView.findViewById(R.id.textViewSummaryDate);
            textViewPreview = itemView.findViewById(R.id.textViewSummaryPreview);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSummaryClick(summaries.get(position));
                }
            });
        }

        void bind(Summary summary) {
            // 设置标题，如果没有则使用默认标题
            String title = summary.getTitle();
            if (title == null || title.isEmpty()) {
                title = "总结 #" + summary.getId();
            }
            textViewTitle.setText(title);

            // 格式化日期
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            String dateStr = summary.getCreatedAt() != null ?
                    dateFormat.format(summary.getCreatedAt()) : "未知日期";
            textViewDate.setText(dateStr);

            // 设置内容预览
            String content = summary.getContent();
            if (content != null && !content.isEmpty()) {
                // 只显示前50个字符作为预览
                if (content.length() > 50) {
                    content = content.substring(0, 50) + "...";
                }
                textViewPreview.setText(content);
            } else {
                textViewPreview.setText("无内容");
            }
        }
    }
}