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

    private final SharedPreferences preferences;

    public HotStockCandidateRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

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

    public void saveCandidates(ArrayList<HotStockCandidate> candidates) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < candidates.size(); i++) {
            array.put(candidates.get(i).toJson());
        }
        preferences.edit().putString(KEY_CANDIDATES, array.toString()).apply();
    }
}
