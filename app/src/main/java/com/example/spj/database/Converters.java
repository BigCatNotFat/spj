package com.example.spj.database;
import androidx.room.TypeConverter;

import java.util.Date;

/**
 * 类型转换器，用于Room数据库
 */
public class Converters {

    /**
     * 将Date转换为Long类型存储
     */
    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    /**
     * 将Long类型转换回Date
     */
    @TypeConverter
    public static Date timestampToDate(Long value) {
        return value == null ? null : new Date(value);
    }
}