package com.example.spj.adapter;
import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.spj.R;
import com.example.spj.database.AppDatabase;
import com.example.spj.model.Entry;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 条目列表适配器
 */
public class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.EntryViewHolder> {

    private final List<Entry> entries = new ArrayList<>();
    private final Context context;
    private OnEntryClickListener listener;
    private OnEntryPinnedListener pinnedListener;
    private OnEntryDeletedListener deleteListener;
    private boolean dragAndDropEnabled = true;

    public EntryAdapter(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_entry, parent, false);
        return new EntryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        Entry current = entries.get(position);
        holder.textViewTitle.setText(current.getName());
        holder.textViewCount.setText(context.getString(R.string.video_count, current.getVideoCount()));
        holder.textViewInitial.setText(current.getInitial());

        // 显示置顶状态
        if (current.isPinned()) {
            holder.imagePinned.setVisibility(View.VISIBLE);
        } else {
            holder.imagePinned.setVisibility(View.GONE);
        }

        // 如果有缩略图，则加载缩略图
        if (current.getThumbnailPath() != null && !current.getThumbnailPath().isEmpty()) {
            File thumbnailFile = new File(current.getThumbnailPath());
            if (thumbnailFile.exists()) {
                holder.imageViewThumbnail.setVisibility(View.VISIBLE);
                holder.textViewInitial.setVisibility(View.GONE);

                Glide.with(context)
                        .load(thumbnailFile)
                        .centerCrop()
                        .into(holder.imageViewThumbnail);
            } else {
                holder.imageViewThumbnail.setVisibility(View.GONE);
                holder.textViewInitial.setVisibility(View.VISIBLE);
            }
        } else {
            holder.imageViewThumbnail.setVisibility(View.GONE);
            holder.textViewInitial.setVisibility(View.VISIBLE);
        }

        // 设置点击监听器
        holder.itemView.setOnClickListener(v -> {
            if (listener != null && position != RecyclerView.NO_POSITION) {
                listener.onEntryClick(entries.get(position));
            }
        });

        // 设置长按监听器（显示选项菜单）
        holder.itemView.setOnLongClickListener(v -> {
            showEntryOptionsMenu(v, current, position);
            return true;
        });
    }

    /**
     * 显示条目选项菜单
     */
    private void showEntryOptionsMenu(View view, Entry entry, int position) {
        PopupMenu popup = new PopupMenu(context, view);
        popup.inflate(R.menu.menu_entry_options);

        // 根据置顶状态更新菜单项文本
        if (entry.isPinned()) {
            popup.getMenu().findItem(R.id.action_pin_entry).setTitle(R.string.unpin_entry);
        } else {
            popup.getMenu().findItem(R.id.action_pin_entry).setTitle(R.string.pin_entry);
        }

        popup.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_pin_entry) {
                toggleEntryPin(entry, position);
                return true;
            } else if (itemId == R.id.action_edit_entry) {
                showEditEntryDialog(entry, position);
                return true;
            } else if (itemId == R.id.action_delete_entry) {
                showDeleteEntryConfirmation(entry, position);
                return true;
            }
            return false;
        });

        popup.show();
    }

    /**
     * 显示编辑条目名称对话框
     */
    private void showEditEntryDialog(Entry entry, int position) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_entry, null);
        EditText editTextEntryName = dialogView.findViewById(R.id.editTextEntryName);

        // 预填充当前名称
        editTextEntryName.setText(entry.getName());

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.edit_entry_name)
                .setView(dialogView)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    String newName = editTextEntryName.getText().toString().trim();
                    if (!newName.isEmpty()) {
                        updateEntryName(entry, newName, position);
                    } else {
                        Toast.makeText(context, R.string.entry_name_empty, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /**
     * 更新条目名称
     */
    private void updateEntryName(Entry entry, String newName, int position) {
        // 更新本地数据
        String oldName = entry.getName();
        entry.setName(newName);

        // 更新UI
        notifyItemChanged(position);

        // 更新数据库
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase database = AppDatabase.getDatabase(context);
            int result = database.entryDao().updateEntryName(entry.getId(), newName);

            // 主线程中通知结果
            if (result > 0) {
                ((Activity) context).runOnUiThread(() ->
                        Toast.makeText(context, R.string.entry_name_updated, Toast.LENGTH_SHORT).show());
            } else {
                // 更新失败，恢复原名称
                entry.setName(oldName);
                ((Activity) context).runOnUiThread(() -> {
                    notifyItemChanged(position);
                    Toast.makeText(context, R.string.update_entry_name_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 显示删除条目确认对话框
     */
    private void showDeleteEntryConfirmation(Entry entry, int position) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.delete_entry)
                .setMessage(context.getString(R.string.delete_entry_confirm, entry.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deleteEntry(entry, position);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * 删除条目
     */
    private void deleteEntry(Entry entry, int position) {
        // 从本地列表移除
        entries.remove(position);
        notifyItemRemoved(position);

        // 从数据库删除
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase database = AppDatabase.getDatabase(context);
            try {
                // 先删除关联的所有视频
                database.videoDao().deleteAllVideosForEntry(entry.getId());

                // 删除条目
                database.entryDao().delete(entry);

                // 通知删除成功
                ((Activity) context).runOnUiThread(() -> {
                    Toast.makeText(context, R.string.delete_entry_success, Toast.LENGTH_SHORT).show();

                    // 通知监听器
                    if (deleteListener != null) {
                        deleteListener.onEntryDeleted(entry);
                    }
                });
            } catch (Exception e) {
                // 删除失败，恢复列表
                ((Activity) context).runOnUiThread(() -> {
                    entries.add(position, entry);
                    notifyItemInserted(position);
                    Toast.makeText(context, R.string.delete_entry_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    /**
     * 切换条目置顶状态
     */
    private void toggleEntryPin(Entry entry, int position) {
        boolean newPinnedState = !entry.isPinned();
        entry.setPinned(newPinnedState);

        // 更新数据库
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase database = AppDatabase.getDatabase(context);
            database.entryDao().updatePinned(entry.getId(), newPinnedState);

            // 如果是置顶，给一个较小的displayOrder值确保排在前面
            if (newPinnedState) {
                int minOrder = 0;
                for (Entry e : entries) {
                    if (e.isPinned() && e.getId() != entry.getId() && e.getDisplayOrder() <= minOrder) {
                        minOrder = e.getDisplayOrder() - 1;
                    }
                }
                entry.setDisplayOrder(minOrder);
                database.entryDao().updateDisplayOrder(entry.getId(), minOrder);
            }
        });

        // 通知视图更新
        notifyItemChanged(position);

        // 通知监听器
        if (pinnedListener != null) {
            pinnedListener.onEntryPinned(entry, newPinnedState);
        }
    }

    /**
     * 处理条目移动（拖放）
     */
    public boolean onItemMove(int fromPosition, int toPosition) {
        if (!dragAndDropEnabled) {
            return false;
        }

        // 检查是否在同一区域内移动（置顶区或非置顶区）
        Entry fromEntry = entries.get(fromPosition);
        Entry toEntry = entries.get(toPosition);

        // 不允许置顶和非置顶项之间拖动
        if (fromEntry.isPinned() != toEntry.isPinned()) {
            return false;
        }

        // 交换位置并通知适配器
        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(entries, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(entries, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    /**
     * 拖放完成后更新数据库
     */
    public void onDragFinished() {
        // 更新所有条目的显示顺序
        AppDatabase.databaseWriteExecutor.execute(() -> {
            AppDatabase database = AppDatabase.getDatabase(context);
            for (int i = 0; i < entries.size(); i++) {
                Entry entry = entries.get(i);
                entry.setDisplayOrder(i);
                database.entryDao().updateDisplayOrder(entry.getId(), i);
            }
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    /**
     * 更新数据
     */
    public void setEntries(List<Entry> entries) {
        this.entries.clear();
        if (entries != null) {
            this.entries.addAll(entries);
        }
        notifyDataSetChanged();
    }

    /**
     * 获取指定位置的条目
     */
    public Entry getEntryAt(int position) {
        if (position >= 0 && position < entries.size()) {
            return entries.get(position);
        }
        return null;
    }

    /**
     * 设置条目点击监听器
     */
    public void setOnEntryClickListener(OnEntryClickListener listener) {
        this.listener = listener;
    }

    /**
     * 设置条目置顶状态变化监听器
     */
    public void setOnEntryPinnedListener(OnEntryPinnedListener listener) {
        this.pinnedListener = listener;
    }

    /**
     * 设置条目删除监听器
     */
    public void setOnEntryDeletedListener(OnEntryDeletedListener listener) {
        this.deleteListener = listener;
    }

    /**
     * 设置是否允许拖动
     */
    public void setDragAndDropEnabled(boolean enabled) {
        this.dragAndDropEnabled = enabled;
    }

    /**
     * 条目点击监听器接口
     */
    public interface OnEntryClickListener {
        void onEntryClick(Entry entry);
    }

    /**
     * 条目置顶状态变化监听器接口
     */
    public interface OnEntryPinnedListener {
        void onEntryPinned(Entry entry, boolean pinned);
    }

    /**
     * 条目删除监听器接口
     */
    public interface OnEntryDeletedListener {
        void onEntryDeleted(Entry entry);
    }

    /**
     * 条目ViewHolder
     */
    static class EntryViewHolder extends RecyclerView.ViewHolder {
        private final TextView textViewTitle;
        private final TextView textViewCount;
        private final TextView textViewInitial;
        private final ImageView imageViewThumbnail;
        private final ImageView imagePinned; // 置顶图标

        public EntryViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.textViewTitle);
            textViewCount = itemView.findViewById(R.id.textViewCount);
            textViewInitial = itemView.findViewById(R.id.textViewInitial);
            imageViewThumbnail = itemView.findViewById(R.id.imageViewThumbnail);
            imagePinned = itemView.findViewById(R.id.imagePinned);
        }
    }
}