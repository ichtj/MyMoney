package com.face.mymoney.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.face.mymoney.model.HotStockCandidate;

import java.util.List;

@Dao
public interface HotStockCandidateDao {
    @Query("SELECT * FROM hot_candidates")
    List<HotStockCandidate> getAllCandidates();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCandidates(List<HotStockCandidate> candidates);

    @Query("DELETE FROM hot_candidates")
    void deleteAllCandidates();
}
