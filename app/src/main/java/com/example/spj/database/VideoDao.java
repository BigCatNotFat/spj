package com.example.spj.database;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.spj.model.Video;

import java.util.List;

/**
 * 视频数据访问对象接口
 */
@Dao
public interface VideoDao {

    /**
     * 插入一个视频
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Video video);

    /**
     * 更新视频
     */
    @Update
    void update(Video video);

    /**
     * 删除视频
     */
    @Delete
    void delete(Video video);

    /**
     * 获取特定条目的所有视频
     */
    @Query("SELECT * FROM videos WHERE entryId = :entryId ORDER BY recordedAt DESC")
    LiveData<List<Video>> getVideosForEntry(int entryId);

    /**
     * 获取特定条目的所有视频（非LiveData）- 用于删除操作
     */
    @Query("SELECT * FROM videos WHERE entryId = :entryId")
    List<Video> getAllVideosForEntrySync(int entryId);

    /**
     * 根据ID获取视频
     */
    @Query("SELECT * FROM videos WHERE id = :id")
    LiveData<Video> getVideoById(int id);

    /**
     * 根据ID获取视频（非LiveData）
     */
    @Query("SELECT * FROM videos WHERE id = :id")
    Video getVideoByIdSync(int id);

    /**
     * 获取特定条目的视频数量
     */
    @Query("SELECT COUNT(*) FROM videos WHERE entryId = :entryId")
    int getVideoCountForEntry(int entryId);

    /**
     * 获取特定条目的最新视频
     */
    @Query("SELECT * FROM videos WHERE entryId = :entryId ORDER BY recordedAt DESC LIMIT 1")
    Video getLatestVideoForEntry(int entryId);

    /**
     * 删除特定条目的所有视频
     */
    @Query("DELETE FROM videos WHERE entryId = :entryId")
    void deleteAllVideosForEntry(int entryId);
    @Query("SELECT * FROM videos WHERE entryId = :entryId AND notes IS NOT NULL AND notes != '' ORDER BY recordedAt DESC")
    List<Video> getAllVideosWithNotesForEntrySync(int entryId);
    /**
     * 按媒体类型获取特定条目的视频
     */
    @Query("SELECT * FROM videos WHERE entryId = :entryId AND mediaType = :mediaType ORDER BY recordedAt DESC")
    LiveData<List<Video>> getMediaForEntryByType(int entryId, int mediaType);

    /**
     * 获取视频的总大小（字节）
     */
    @Query("SELECT SUM(fileSize) FROM videos WHERE entryId = :entryId")
    long getTotalFileSizeForEntry(int entryId);

    /**
     * 获取符合条件的视频数
     */
    @Query("SELECT COUNT(*) FROM videos WHERE entryId = :entryId AND mediaType = :mediaType")
    int getMediaCountForEntryByType(int entryId, int mediaType);

    /**
     * 更新视频的注释
     */
    @Query("UPDATE videos SET notes = :notes WHERE id = :videoId")
    void updateNotes(int videoId, String notes);

    /**
     * 搜索视频注释
     */
    @Query("SELECT * FROM videos WHERE notes LIKE '%' || :keyword || '%' ORDER BY recordedAt DESC")
    LiveData<List<Video>> searchVideosByNotes(String keyword);

    /**
     * 根据日期范围获取视频
     */
    @Query("SELECT * FROM videos WHERE recordedAt BETWEEN :startTime AND :endTime ORDER BY recordedAt DESC")
    LiveData<List<Video>> getVideosByDateRange(long startTime, long endTime);

    /**
     * 获取所有视频列表，按日期排序
     */
    @Query("SELECT * FROM videos ORDER BY recordedAt DESC")
    LiveData<List<Video>> getAllVideos();

    /**
     * 将视频移动到另一个合集
     */
    @Query("UPDATE videos SET entryId = :newEntryId WHERE id = :videoId")
    void moveVideoToEntry(int videoId, int newEntryId);

    /**
     * 批量将视频移动到另一个合集
     */
    @Query("UPDATE videos SET entryId = :newEntryId WHERE id IN (:videoIds)")
    void moveVideosToEntry(List<Integer> videoIds, int newEntryId);
}