package com.face.mymoney.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.face.mymoney.R;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class LocalStockRepository {
    private static final String TAG = "MyMoneyStorage";
    private static final String PREF_NAME = "mymoney_mvp";
    private static final String KEY_STOCKS = "stocks";
    private static final String KEY_NOTES = "notes";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_SAMPLE_CLEANED = "sample_cleaned_v1";

    private final Context context;
    private final SharedPreferences preferences;

    public LocalStockRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public String getAllGroup() {
        return context.getString(R.string.group_all);
    }

    public ArrayList<Stock> loadStocks() {
        return parseStocks(preferences.getString(KEY_STOCKS, "[]"));
    }

    public ArrayList<DecisionNote> loadNotes() {
        return parseNotes(preferences.getString(KEY_NOTES, "[]"));
    }

    public ArrayList<String> loadSavedGroups() {
        return parseStringArray(preferences.getString(KEY_GROUPS, "[]"));
    }

    public void saveStocks(ArrayList<Stock> stocks) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < stocks.size(); i++) {
            array.put(stocks.get(i).toJson());
        }
        preferences.edit().putString(KEY_STOCKS, array.toString()).apply();
    }

    public void saveNotes(ArrayList<DecisionNote> notes) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < notes.size(); i++) {
            array.put(notes.get(i).toJson());
        }
        preferences.edit().putString(KEY_NOTES, array.toString()).apply();
    }

    public void seedStocksIfEmpty() {
        // Keep new installs empty. Users create their own watchlist and groups.
    }

    public void removeSampleStocksOnce() {
        if (preferences.getBoolean(KEY_SAMPLE_CLEANED, false)) {
            return;
        }
        ArrayList<Stock> stocks = loadStocks();
        boolean changed = false;
        for (int i = stocks.size() - 1; i >= 0; i--) {
            Stock stock = stocks.get(i);
            if (isSampleStock(stock)) {
                stocks.remove(i);
                changed = true;
            }
        }
        SharedPreferences.Editor editor = preferences.edit().putBoolean(KEY_SAMPLE_CLEANED, true);
        if (changed) {
            JSONArray array = new JSONArray();
            for (int i = 0; i < stocks.size(); i++) {
                array.put(stocks.get(i).toJson());
            }
            editor.putString(KEY_STOCKS, array.toString());
        }
        editor.apply();
    }

    public void saveGroupIfNeeded(String group) {
        String safeGroup = group == null ? "" : group.trim();
        if (safeGroup.length() == 0 || getAllGroup().equals(safeGroup)) {
            return;
        }
        ArrayList<String> groups = loadSavedGroups();
        if (!groups.contains(safeGroup)) {
            groups.add(safeGroup);
            saveGroups(groups);
        }
    }

    public Stock createDefaultStock(String code, String name, String group, String remark) {
        String safeGroup = group.length() == 0 ? getString(R.string.group_candidate) : group;
        String market = code.startsWith("6") ? getString(R.string.market_shanghai) : getString(R.string.market_shenzhen);
        return sampleStock(code, name, market, safeGroup, remark, "--", "0.00%", "--", getString(R.string.pending_industry),
                getString(R.string.pending_company_info_source), getString(R.string.pending_sync), getString(R.string.pending_sync), getString(R.string.pending_sync), getString(R.string.pending_risk_tag));
    }

    public ArrayList<News> buildNews(Stock stock) {
        ArrayList<News> list = new ArrayList<News>();
        list.add(new News(getString(R.string.mock_news_title_format, stock.name), getString(R.string.mock_news_source_notice), getString(R.string.mock_news_today), stock.name, getString(R.string.mock_news_notice_content)));
        list.add(new News(getString(R.string.mock_news_industry_title_format, stock.industry), getString(R.string.mock_news_source_finance), getString(R.string.mock_news_yesterday), stock.industry, getString(R.string.mock_news_industry_content)));
        list.add(new News(getString(R.string.mock_news_review_title), getString(R.string.mock_news_source_mine), getString(R.string.mock_news_this_week), stock.groupName, getString(R.string.mock_news_review_content)));
        return list;
    }

    public ArrayList<Stock> filterStocks(ArrayList<Stock> stocks, String selectedGroup) {
        ArrayList<Stock> result = new ArrayList<Stock>();
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            if (getAllGroup().equals(selectedGroup) || selectedGroup.equals(stock.groupName)) {
                result.add(stock);
            }
        }
        return result;
    }

    public ArrayList<String> getGroups(ArrayList<Stock> stocks) {
        ArrayList<String> groups = new ArrayList<String>();
        Set<String> exists = new HashSet<String>();
        groups.add(getAllGroup());
        exists.add(getAllGroup());
        ArrayList<String> savedGroups = loadSavedGroups();
        for (int i = 0; i < savedGroups.size(); i++) {
            String group = savedGroups.get(i);
            if (group.length() > 0 && !exists.contains(group)) {
                groups.add(group);
                exists.add(group);
            }
        }
        for (int i = 0; i < stocks.size(); i++) {
            String group = stocks.get(i).groupName;
            if (group.length() > 0 && !exists.contains(group)) {
                groups.add(group);
                exists.add(group);
            }
        }
        return groups;
    }

    public int countRiskStocks(ArrayList<Stock> stocks) {
        int count = 0;
        for (int i = 0; i < stocks.size(); i++) {
            if (stocks.get(i).riskTag.length() > 0 && !getString(R.string.pending_risk_tag).equals(stocks.get(i).riskTag)) {
                count++;
            }
        }
        return count;
    }

    public ArrayList<DecisionNote> getNotes(ArrayList<DecisionNote> notes, String stockCode) {
        ArrayList<DecisionNote> result = new ArrayList<DecisionNote>();
        for (int i = 0; i < notes.size(); i++) {
            if (stockCode.equals(notes.get(i).stockCode)) {
                result.add(notes.get(i));
            }
        }
        return result;
    }

    private Stock sampleStock(String code, String name, String market, String groupName, String remark,
                              String price, String change, String turnover, String industry, String business,
                              String marketValue, String pe, String revenue, String risk) {
        Stock stock = new Stock();
        stock.code = code;
        stock.name = name;
        stock.market = market;
        stock.groupName = groupName;
        stock.remark = remark;
        stock.price = price;
        stock.changePercent = change;
        stock.turnover = turnover;
        stock.industry = industry;
        stock.mainBusiness = business;
        stock.marketValue = marketValue;
        stock.pe = pe;
        stock.revenue = revenue;
        stock.profit = getString(R.string.pending_finance_api);
        stock.riskTag = risk;
        return stock;
    }

    private String getString(int resId) {
        return context.getString(resId);
    }

    private String getString(int resId, Object... args) {
        return context.getString(resId, args);
    }

    private boolean isSampleStock(Stock stock) {
        return ("300308".equals(stock.code) && getString(R.string.sample_stock_zz_name).equals(stock.name))
                || ("600519".equals(stock.code) && getString(R.string.sample_stock_mt_name).equals(stock.name))
                || ("600776".equals(stock.code) && getString(R.string.sample_stock_df_name).equals(stock.name));
    }

    private void saveGroups(ArrayList<String> groups) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);
            if (group != null && group.trim().length() > 0) {
                array.put(group.trim());
            }
        }
        preferences.edit().putString(KEY_GROUPS, array.toString()).apply();
    }

    private ArrayList<Stock> parseStocks(String json) {
        ArrayList<Stock> list = new ArrayList<Stock>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(Stock.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            android.util.Log.w(TAG, "parseStocks failed: " + e.getMessage());
            list.clear();
        }
        return list;
    }

    private ArrayList<DecisionNote> parseNotes(String json) {
        ArrayList<DecisionNote> list = new ArrayList<DecisionNote>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                list.add(DecisionNote.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException e) {
            android.util.Log.w(TAG, "parseNotes failed: " + e.getMessage());
            list.clear();
        }
        return list;
    }

    private ArrayList<String> parseStringArray(String json) {
        ArrayList<String> list = new ArrayList<String>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, "").trim();
                if (value.length() > 0 && !list.contains(value)) {
                    list.add(value);
                }
            }
        } catch (JSONException e) {
            android.util.Log.w(TAG, "parseStringArray failed: " + e.getMessage());
            list.clear();
        }
        return list;
    }
}
