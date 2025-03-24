package com.example.spj.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

/**
 * 总结数据模型
 */
@Entity(tableName = "summaries",
        foreignKeys = @ForeignKey(
                entity = Entry.class,
                parentColumns = "id",
                childColumns = "entryId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("entryId")}
)
public class Summary {
    @PrimaryKey(autoGenerate = true)
    private int id;

    private int entryId;
    private String title;
    private String content;
    private String rawText;
    private Date createdAt;

    public Summary(int entryId, String title, String content, String rawText) {
        this.entryId = entryId;
        this.title = title;
        this.content = content;
        this.rawText = rawText;
        this.createdAt = new Date();
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }
}