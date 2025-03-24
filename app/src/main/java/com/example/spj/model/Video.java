package com.example.spj.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * 视频实体类，代表一个视频或图片记录
 */
@Entity(tableName = "videos",
        foreignKeys = @ForeignKey(
                entity = Entry.class,
                parentColumns = "id",
                childColumns = "entryId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("entryId")})
public class Video {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private int entryId;

    private String filePath;

    private String thumbnailPath;

    private Date recordedAt;

    private long duration; // 视频时长（毫秒）

    private long fileSize; // 文件大小（字节）

    private String notes; // 添加注释字段

    // 媒体类型枚举
    public static final int TYPE_VIDEO = 0;
    public static final int TYPE_IMAGE = 1;

    private int mediaType = TYPE_VIDEO; // 默认为视频类型

    public Video(int entryId, String filePath) {
        this.entryId = entryId;
        this.filePath = filePath;
        this.recordedAt = new Date();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getEntryId() {
        return entryId;
    }

    public void setEntryId(int entryId) {
        this.entryId = entryId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getThumbnailPath() {
        return thumbnailPath;
    }

    public void setThumbnailPath(String thumbnailPath) {
        this.thumbnailPath = thumbnailPath;
    }

    public Date getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Date recordedAt) {
        this.recordedAt = recordedAt;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // 媒体类型相关的getter和setter
    public int getMediaType() {
        return mediaType;
    }

    public void setMediaType(int mediaType) {
        this.mediaType = mediaType;
    }

    // 辅助方法：判断是否为图片
    public boolean isImage() {
        return mediaType == TYPE_IMAGE;
    }

    // 辅助方法：判断是否为视频
    public boolean isVideo() {
        return mediaType == TYPE_VIDEO;
    }
}