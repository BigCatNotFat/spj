package com.example.spj.database;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.spj.model.Entry;

import java.util.List;

/**
 * 条目数据访问对象接口
 */
@Dao
public interface EntryDao {

    /**
     * 插入一个条目
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Entry entry);

    /**
     * 更新条目
     */
    @Update
    void update(Entry entry);

    /**
     * 删除条目
     */
    @Delete
    void delete(Entry entry);

    /**
     * 获取所有条目，按置顶状态和显示顺序排序
     */
    @Query("SELECT * FROM entries ORDER BY pinned DESC, displayOrder ASC")
    LiveData<List<Entry>> getAllEntries();

    /**
     * 根据ID获取条目
     */
    @Query("SELECT * FROM entries WHERE id = :id")
    LiveData<Entry> getEntryById(int id);

    /**
     * 根据ID获取条目（非LiveData）
     */
    @Query("SELECT * FROM entries WHERE id = :id")
    Entry getEntryByIdSync(int id);

    /**
     * 更新条目的视频数量
     */
    @Query("UPDATE entries SET videoCount = :count WHERE id = :entryId")
    void updateVideoCount(int entryId, int count);

    /**
     * 更新条目的缩略图
     */
    @Query("UPDATE entries SET thumbnailPath = :thumbnailPath WHERE id = :entryId")
    void updateThumbnail(int entryId, String thumbnailPath);

    /**
     * 更新条目的置顶状态
     */
    @Query("UPDATE entries SET pinned = :pinned WHERE id = :entryId")
    void updatePinned(int entryId, boolean pinned);

    /**
     * 更新条目的显示顺序
     */
    @Query("UPDATE entries SET displayOrder = :displayOrder WHERE id = :entryId")
    void updateDisplayOrder(int entryId, int displayOrder);

    /**
     * 更新条目的名称
     */
    @Query("UPDATE entries SET name = :name WHERE id = :entryId")
    int updateEntryName(int entryId, String name);

    /**
     * 获取最大的显示顺序值
     */
    @Query("SELECT MAX(displayOrder) FROM entries")
    int getMaxDisplayOrder();
}