package com.example.spj.util;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.spj.adapter.EntryAdapter;

/**
 * ItemTouchHelper回调类，用于处理RecyclerView中的拖拽操作
 */
public class ItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private final EntryAdapter mAdapter;

    public ItemTouchHelperCallback(EntryAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public boolean isLongPressDragEnabled() {
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false; // 不启用滑动删除
    }

    @Override
    public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        // 设置上下拖动
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeFlags = 0; // 不支持滑动
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source,
                          @NonNull RecyclerView.ViewHolder target) {
        // 调用适配器的方法处理拖动
        return mAdapter.onItemMove(source.getAdapterPosition(), target.getAdapterPosition());
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // 不需要实现，因为我们禁用了滑动
    }

    @Override
    public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        // 当拖放结束时，通知适配器保存更改
        mAdapter.onDragFinished();
    }
}