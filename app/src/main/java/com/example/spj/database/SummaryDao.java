package com.example.spj.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.spj.model.Summary;

import java.util.List;

/**
 * 总结数据访问对象
 */
@Dao
public interface SummaryDao {
    @Insert
    long insert(Summary summary);

    @Query("SELECT * FROM summaries WHERE entryId = :entryId ORDER BY createdAt DESC")
    LiveData<List<Summary>> getSummariesForEntry(int entryId);

    @Query("SELECT * FROM summaries WHERE entryId = :entryId ORDER BY createdAt DESC LIMIT 1")
    Summary getLatestSummaryForEntrySync(int entryId);

    @Query("SELECT * FROM summaries WHERE id = :id")
    Summary getSummaryByIdSync(int id);

    @Query("DELETE FROM summaries WHERE entryId = :entryId")
    void deleteAllForEntry(int entryId);
}