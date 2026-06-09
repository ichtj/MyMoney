package com.face.mymoney.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.face.mymoney.model.HotStockCandidate;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class HotStockCandidateRepository {
    private static final String TAG = "MyMoneyHotStorage";
    private static final String PREF_NAME = "mymoney_hot_candidates";
    private static final String KEY_CANDIDATES = "hot_candidates";
    private static final String KEY_STATUS = "hot_status";
    private static final String KEY_REFRESHED_AT = "hot_refreshed_at";
    private static final String KEY_SOURCE_SUMMARY = "hot_source_summary";

    private final SharedPreferences preferences;

    /**
     * 构造方法：创建 HotStockCandidateRepository 实例。
     */
    public HotStockCandidateRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 加载候选股票列表。
     */
    public ArrayList<HotStockCandidate> loadCandidates() {
        ArrayList<HotStockCandidate> list = new ArrayList<HotStockCandidate>();
        try {
            JSONArray array = new JSONArray(preferences.getString(KEY_CANDIDATES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                list.add(HotStockCandidate.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            android.util.Log.w(TAG, "loadCandidates failed: " + e.getMessage());
            list.clear();
        }
        return list;
    }

    /**
     * 保存候选股票列表。
     */
    public void saveCandidates(ArrayList<HotStockCandidate> candidates) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < candidates.size(); i++) {
            array.put(candidates.get(i).toJson());
        }
        preferences.edit().putString(KEY_CANDIDATES, array.toString()).apply();
    }

    /**
     * 加载meta。
     */
    public HotStockCandidateMeta loadMeta() {
        HotStockCandidateMeta meta = new HotStockCandidateMeta();
        meta.status = preferences.getString(KEY_STATUS, "");
        meta.refreshedAtMillis = preferences.getLong(KEY_REFRESHED_AT, 0L);
        meta.sourceSummary = preferences.getString(KEY_SOURCE_SUMMARY, "");
        return meta;
    }

    /**
     * 保存meta。
     */
    public void saveMeta(String status, long refreshedAtMillis, String sourceSummary) {
        preferences.edit()
                .putString(KEY_STATUS, status == null ? "" : status)
                .putLong(KEY_REFRESHED_AT, refreshedAtMillis)
                .putString(KEY_SOURCE_SUMMARY, sourceSummary == null ? "" : sourceSummary)
                .apply();
    }

    public static class HotStockCandidateMeta {
        public String status;
        public long refreshedAtMillis;
        public String sourceSummary;
    }
}
