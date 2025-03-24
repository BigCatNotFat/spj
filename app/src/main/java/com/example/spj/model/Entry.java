package com.example.spj.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

import java.util.Date;

/**
 * 条目实体类，代表一个视频分类
 */
@Entity(tableName = "entries")
public class Entry {

    @PrimaryKey(autoGenerate = true)
    private int id;

    @NonNull
    private String name;

    private Date createdAt;

    private String thumbnailPath;

    private int videoCount;

    // New fields for ordering and pinning
    private boolean pinned = false;
    private int displayOrder = 0;

    public Entry(@NonNull String name) {
        this.name = name;
        this.createdAt = new Date();
        this.videoCount = 0;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public int getVideoCount() {
        return videoCount;
    }

    public void setVideoCount(int videoCount) {
        this.videoCount = videoCount;
    }

    /**
     * 获取条目第一个字符作为初始缩略图
     */
    public String getInitial() {
        if (name != null && !name.isEmpty()) {
            return String.valueOf(name.charAt(0));
        }
        return "";
    }

    // Getters and setters for new fields
    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}