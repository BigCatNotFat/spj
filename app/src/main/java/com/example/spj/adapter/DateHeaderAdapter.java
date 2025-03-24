package com.example.spj.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spj.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 日期头部适配器，用于分组显示视频
 */
public class DateHeaderAdapter extends RecyclerView.Adapter<DateHeaderAdapter.DateHeaderViewHolder> {

    private List<String> headerDates = new ArrayList<>();

    @NonNull
    @Override
    public DateHeaderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_date_header, parent, false);
        return new DateHeaderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DateHeaderViewHolder holder, int position) {
        String date = headerDates.get(position);
        holder.textViewDateHeader.setText(date);
    }

    @Override
    public int getItemCount() {
        return headerDates.size();
    }

    /**
     * 设置日期头部列表
     */
    public void setHeaderDates(List<String> dates) {
        this.headerDates = new ArrayList<>(dates);
        notifyDataSetChanged();
    }

    /**
     * 获取指定位置的日期
     */
    public String getDateAt(int position) {
        if (position >= 0 && position < headerDates.size()) {
            return headerDates.get(position);
        }
        return null;
    }

    /**
     * 视图持有者类
     */
    static class DateHeaderViewHolder extends RecyclerView.ViewHolder {
        final TextView textViewDateHeader;

        DateHeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewDateHeader = itemView.findViewById(R.id.textViewDateHeader);
        }
    }
}