package com.face.mymoney.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.face.mymoney.R;
import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.db.AppDatabase;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocalStockRepository {
    private static final String TAG = "MyMoneyStorage";
    private static final String PREF_NAME = "mymoney_mvp";
    private static final String KEY_GROUPS = "groups";
    private static final String KEY_SAMPLE_CLEANED = "sample_cleaned_v1";

    private final Context context;
    private final SharedPreferences preferences;
    private final AppDatabase db;

    /**
     * 构造方法：创建 LocalStockRepository 实例。
     */
    public LocalStockRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        db = AppDatabase.getInstance(this.context);
    }

    /**
     * 获取all分组。
     */
    public String getAllGroup() {
        return context.getString(R.string.group_all);
    }

    /**
     * 加载股票列表。
     */
    public ArrayList<Stock> loadStocks() {
        List<Stock> list = db.stockDao().getAllStocks();
        return new ArrayList<>(list);
    }

    /**
     * 加载决策笔记列表。
     */
    public ArrayList<DecisionNote> loadNotes() {
        List<DecisionNote> list = db.decisionNoteDao().getAllNotes();
        return new ArrayList<>(list);
    }

    /**
     * 加载saved分组列表。
     */
    public ArrayList<String> loadSavedGroups() {
        return parseStringArray(preferences.getString(KEY_GROUPS, "[]"));
    }

    /**
     * 加载详情缓存。
     */
    public DetailCache loadDetailCache(String stockCode) {
        DetailCacheEntity entity = db.detailCacheDao().getDetailCache(stockCode);
        if (entity == null) {
            DetailCache cache = new DetailCache();
            cache.stockCode = stockCode;
            return cache;
        }
        DetailCache cache = new DetailCache();
        cache.stockCode = entity.stockCode;
        cache.newsFetchedAt = entity.newsFetchedAt;
        cache.opinionFetchedAt = entity.opinionFetchedAt;
        cache.analysisFetchedAt = entity.analysisFetchedAt;
        try {
            if (entity.newsJson != null && entity.newsJson.length() > 0) {
                cache.news = DetailCache.parseNews(new JSONArray(entity.newsJson));
            }
            if (entity.opinionsJson != null && entity.opinionsJson.length() > 0) {
                cache.opinions = DetailCache.parseOpinions(new JSONArray(entity.opinionsJson));
            }
            if (entity.analysisJson != null && entity.analysisJson.length() > 0) {
                cache.analysis = DeepSeekAnalysisResult.fromJson(new JSONObject(entity.analysisJson));
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "parseDetailCache failed stockCode=" + stockCode + ": " + e.getMessage());
        }
        return cache;
    }

    /**
     * 保存详情缓存。
     */
    public void saveDetailCache(DetailCache cache) {
        if (cache == null || cache.stockCode == null || cache.stockCode.length() == 0) {
            return;
        }
        DetailCacheEntity entity = new DetailCacheEntity();
        entity.stockCode = cache.stockCode;
        entity.newsFetchedAt = cache.newsFetchedAt;
        entity.opinionFetchedAt = cache.opinionFetchedAt;
        entity.analysisFetchedAt = cache.analysisFetchedAt;
        entity.newsJson = cache.news != null ? DetailCache.newsToJson(cache.news).toString() : "[]";
        entity.opinionsJson = cache.opinions != null ? DetailCache.opinionsToJson(cache.opinions).toString() : "[]";
        entity.analysisJson = cache.analysis != null ? cache.analysis.toJson().toString() : "";
        db.detailCacheDao().insertDetailCache(entity);
    }

    /**
     * 清除详情analysis。
     */
    public void clearDetailAnalysis(String stockCode) {
        DetailCache cache = loadDetailCache(stockCode);
        cache.stockCode = stockCode;
        cache.analysis = null;
        cache.analysisFetchedAt = 0L;
        saveDetailCache(cache);
    }

    /**
     * 保存股票列表。
     */
    public void saveStocks(final ArrayList<Stock> stocks) {
        db.runInTransaction(new Runnable() {
            @Override
            public void run() {
                db.stockDao().deleteAllStocks();
                if (stocks != null && stocks.size() > 0) {
                    db.stockDao().insertStocks(stocks);
                }
            }
        });
    }

    /**
     * 保存决策笔记列表。
     */
    public void saveNotes(ArrayList<DecisionNote> notes) {
        db.decisionNoteDao().deleteAllNotes();
        if (notes != null && notes.size() > 0) {
            db.decisionNoteDao().insertNotes(notes);
        }
    }

    /**
     * 数据填充股票列表ifempty。
     */
    public void seedStocksIfEmpty() {
        // Keep new installs empty. Users create their own watchlist and groups.
    }

    /**
     * 移除sample股票列表once。
     */
    public void removeSampleStocksOnce() {
        if (preferences.getBoolean(KEY_SAMPLE_CLEANED, false)) {
            return;
        }
        ArrayList<Stock> stocks = loadStocks();
        int sampleCount = 0;
        for (int i = 0; i < stocks.size(); i++) {
            if (isSampleStock(stocks.get(i))) {
                sampleCount++;
            }
        }
        if (sampleCount == 0 || sampleCount < stocks.size()) {
            preferences.edit().putBoolean(KEY_SAMPLE_CLEANED, true).apply();
            return;
        }
        for (int i = stocks.size() - 1; i >= 0; i--) {
            Stock stock = stocks.get(i);
            if (isSampleStock(stock)) {
                db.stockDao().deleteStock(stock);
            }
        }
        preferences.edit().putBoolean(KEY_SAMPLE_CLEANED, true).apply();
    }

    /**
     * 保存分组ifneeded。
     */
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

    /**
     * createdefault股票。
     */
    public Stock createDefaultStock(String code, String name, String group, String remark) {
        String safeGroup = group.length() == 0 ? getString(R.string.group_candidate) : group;
        String market = code.startsWith("6") ? getString(R.string.market_shanghai) : getString(R.string.market_shenzhen);
        return sampleStock(code, name, market, safeGroup, remark, "--", "0.00%", "--", getString(R.string.pending_industry),
                getString(R.string.pending_company_info_source), getString(R.string.pending_sync), getString(R.string.pending_sync), getString(R.string.pending_sync), getString(R.string.pending_risk_tag));
    }

    /**
     * 构建新闻资讯。
     */
    public ArrayList<News> buildNews(Stock stock) {
        ArrayList<News> list = new ArrayList<News>();
        list.add(new News(getString(R.string.mock_news_title_format, stock.name), getString(R.string.mock_news_source_notice), getString(R.string.mock_news_today), stock.name, getString(R.string.mock_news_notice_content)));
        list.add(new News(getString(R.string.mock_news_industry_title_format, stock.industry), getString(R.string.mock_news_source_finance), getString(R.string.mock_news_yesterday), stock.industry, getString(R.string.mock_news_industry_content)));
        list.add(new News(getString(R.string.mock_news_review_title), getString(R.string.mock_news_source_mine), getString(R.string.mock_news_this_week), stock.groupName, getString(R.string.mock_news_review_content)));
        return list;
    }

    /**
     * 过滤股票列表。
     */
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

    /**
     * 获取分组列表。
     */
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

    /**
     * 统计高风险股票列表。
     */
    public int countRiskStocks(ArrayList<Stock> stocks) {
        int count = 0;
        for (int i = 0; i < stocks.size(); i++) {
            if (stocks.get(i).riskTag.length() > 0 && !getString(R.string.pending_risk_tag).equals(stocks.get(i).riskTag)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取决策笔记列表。
     */
    public ArrayList<DecisionNote> getNotes(ArrayList<DecisionNote> notes, String stockCode) {
        ArrayList<DecisionNote> result = new ArrayList<DecisionNote>();
        for (int i = 0; i < notes.size(); i++) {
            if (stockCode.equals(notes.get(i).stockCode)) {
                result.add(notes.get(i));
            }
        }
        return result;
    }

    /**
     * sample股票。
     */
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

    /**
     * 获取字符串。
     */
    private String getString(int resId) {
        return context.getString(resId);
    }

    /**
     * 获取字符串。
     */
    private String getString(int resId, Object... args) {
        return context.getString(resId, args);
    }

    /**
     * 判断是否sample股票。
     */
    private boolean isSampleStock(Stock stock) {
        return ("300308".equals(stock.code) && getString(R.string.sample_stock_zz_name).equals(stock.name))
                || ("600519".equals(stock.code) && getString(R.string.sample_stock_mt_name).equals(stock.name))
                || ("600776".equals(stock.code) && getString(R.string.sample_stock_df_name).equals(stock.name));
    }

    /**
     * 保存分组列表。
     */
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

    /**
     * 解析字符串数组。
     */
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
