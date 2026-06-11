package com.face.mymoney.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.face.mymoney.model.DecisionNote;

import java.util.List;

@Dao
public interface DecisionNoteDao {
    @Query("SELECT * FROM notes")
    List<DecisionNote> getAllNotes();

    @Query("SELECT * FROM notes WHERE stockCode = :stockCode")
    List<DecisionNote> getNotesForStock(String stockCode);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertNotes(List<DecisionNote> notes);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertNote(DecisionNote note);

    @Delete
    void deleteNote(DecisionNote note);

    @Query("DELETE FROM notes")
    void deleteAllNotes();
}
