package com.face.mymoney.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "detail_caches")
public class DetailCacheEntity {
    @PrimaryKey
    @NonNull
    public String stockCode = "";

    public long newsFetchedAt;
    public long opinionFetchedAt;
    public long analysisFetchedAt;

    public String newsJson;
    public String opinionsJson;
    public String analysisJson;
}
