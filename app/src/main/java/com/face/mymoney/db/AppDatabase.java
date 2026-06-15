package com.face.mymoney.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.face.mymoney.data.DetailCacheEntity;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.HotStockCandidate;
import com.face.mymoney.model.Stock;

@Database(entities = {Stock.class, DecisionNote.class, HotStockCandidate.class, DetailCacheEntity.class}, version = 1, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "mymoney.db";
    private static volatile AppDatabase instance;

    public abstract StockDao stockDao();
    public abstract DecisionNoteDao decisionNoteDao();
    public abstract DetailCacheDao detailCacheDao();
    public abstract HotStockCandidateDao hotStockCandidateDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, DATABASE_NAME)
                            .allowMainThreadQueries()
                            .build();
                }
            }
        }
        return instance;
    }
}
