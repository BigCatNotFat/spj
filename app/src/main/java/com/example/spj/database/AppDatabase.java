package com.example.spj.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.spj.model.Entry;
import com.example.spj.model.Summary;
import com.example.spj.model.Video;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Room数据库类
 */
@Database(entities = {Entry.class, Video.class, Summary.class}, version = 5, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    public abstract EntryDao entryDao();
    public abstract VideoDao videoDao();
    public abstract SummaryDao summaryDao();

    private static volatile AppDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;

    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    // 添加数据库迁移策略，从版本1迁移到版本2
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE videos ADD COLUMN notes TEXT");
        }
    };

    // 从版本2迁移到版本3（添加置顶和排序字段）
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 添加置顶字段
            database.execSQL("ALTER TABLE entries ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0");
            // 添加排序字段
            database.execSQL("ALTER TABLE entries ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0");

            // 初始化排序为ID顺序
            database.execSQL("UPDATE entries SET displayOrder = id");
        }
    };

    // 从版本3迁移到版本4（添加媒体类型字段）
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE videos ADD COLUMN mediaType INTEGER NOT NULL DEFAULT 0");
        }
    };

    // 从版本4迁移到版本5（添加Summary表）
    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // 创建summaries表
            database.execSQL("CREATE TABLE IF NOT EXISTS `summaries` " +
                    "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`entryId` INTEGER NOT NULL, " +
                    "`title` TEXT, " +
                    "`content` TEXT, " +
                    "`rawText` TEXT, " +
                    "`createdAt` INTEGER, " +
                    "FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )");

            // 创建索引
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_summaries_entryId` ON `summaries` (`entryId`)");
        }
    };

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "video_journal_database")
                            .addCallback(sRoomDatabaseCallback)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    /**
     * 数据库首次创建时的回调
     */
    private static final RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            // 如果需要在数据库创建时添加一些初始数据，可以在这里实现
            databaseWriteExecutor.execute(() -> {
                // 示例：预先添加一些条目
                // EntryDao dao = INSTANCE.entryDao();
                // dao.insert(new Entry("早上跑步打卡"));
                // dao.insert(new Entry("做饭教程"));
            });
        }
    };
}