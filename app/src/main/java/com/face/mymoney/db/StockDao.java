package com.face.mymoney.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.face.mymoney.model.Stock;

import java.util.List;

@Dao
public interface StockDao {
    @Query("SELECT * FROM stocks")
    List<Stock> getAllStocks();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStocks(List<Stock> stocks);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertStock(Stock stock);

    @Delete
    void deleteStock(Stock stock);

    @Query("DELETE FROM stocks")
    void deleteAllStocks();
}
