package com.face.mymoney.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.face.mymoney.data.DetailCacheEntity;

@Dao
public interface DetailCacheDao {
    @Query("SELECT * FROM detail_caches WHERE stockCode = :stockCode LIMIT 1")
    DetailCacheEntity getDetailCache(String stockCode);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertDetailCache(DetailCacheEntity entity);

    @Query("DELETE FROM detail_caches WHERE stockCode = :stockCode")
    void deleteDetailCache(String stockCode);
}
