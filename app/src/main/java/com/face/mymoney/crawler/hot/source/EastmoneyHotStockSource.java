package com.face.mymoney.crawler.hot.source;

import com.face.mymoney.crawler.SimpleHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class EastmoneyHotStockSource implements HotStockSource {
    private static final String TAG = "MyMoneyEastmoneySrc";
    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_READ_BYTES = 512 * 1024;
    private static final int MAX_RETRY = 2;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String REFERER = "https://quote.eastmoney.com/";

    private final SimpleHttpClient httpClient = new SimpleHttpClient();
    private final HotStockSourceConfig config;

    /**
     * 构造方法：创建 EastmoneyHotStockSource 实例。
     */
    public EastmoneyHotStockSource(HotStockSourceConfig config) {
        this.config = config;
    }

    /**
     * id。
     */
    @Override
    public String id() {
        return config.id;
    }

    /**
     * 权重。
     */
    @Override
    public int weight() {
        return config.weight;
    }

    /**
     * 获取。
     */
    @Override
    public ArrayList<HotStockSourceItem> fetch() {
        ArrayList<HotStockSourceItem> result = new ArrayList<HotStockSourceItem>();
        for (int i = 0; i < config.channels.size(); i++) {
            HotStockSourceChannelConfig channel = config.channels.get(i);
            if (!channel.enabled) {
                continue;
            }
            int before = result.size();
            if ("limit_up_pool".equals(channel.id)) {
                result.addAll(fetchLimitUpPool(channel));
            } else {
                result.addAll(fetchRankChannel(channel));
            }
            android.util.Log.d(TAG, "channel finish id=" + channel.id
                    + ", added=" + (result.size() - before)
                    + ", total=" + result.size());
        }
        if (result.size() == 0) {
            android.util.Log.w(TAG, "source empty id=" + id()
                    + ", enabledChannels=" + config.channels.size());
        }
        return result;
    }

    /**
     * 获取rankchannel。
     */
    private ArrayList<HotStockSourceItem> fetchRankChannel(HotStockSourceChannelConfig channel) {
        ArrayList<HotStockSourceItem> result = new ArrayList<HotStockSourceItem>();
        String fields = "f2,f3,f6,f8,f10,f12,f14,f20,f62,f100";
        String fs = "m:0+t:6,m:0+t:80,m:1+t:2";
        String url = "https://push2.eastmoney.com/api/qt/clist/get"
                + "?pn=1&pz=" + config.rankPageSize
                + "&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281"
                + "&fltt=2&invt=2&fid=" + channel.sortField
                + "&fs=" + fs
                + "&fields=" + fields;
        try {
            android.util.Log.d(TAG, "rank request channel=" + channel.id
                    + ", sort=" + channel.sortField
                    + ", url=" + url);
            SimpleHttpClient.HttpText response = getWithRetry(url, "rank", channel.id);
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "rank http failed channel=" + channel.id
                        + ", status=" + response.statusCode
                        + ", elapsed=" + response.elapsedMillis + "ms"
                        + ", error=" + response.errorMessage
                        + ", body=" + preview(response.body));
                return result;
            }
            JSONObject data = new JSONObject(response.body).optJSONObject("data");
            JSONArray array = data == null ? null : data.optJSONArray("diff");
            if (array == null) {
                android.util.Log.w(TAG, "rank empty array channel=" + channel.id
                        + ", status=" + response.statusCode
                        + ", elapsed=" + response.elapsedMillis + "ms"
                        + ", bodyLength=" + (response.body == null ? 0 : response.body.length())
                        + ", body=" + preview(response.body));
                return result;
            }
            android.util.Log.d(TAG, "rank response channel=" + channel.id
                    + ", rawCount=" + array.length()
                    + ", pageSize=" + config.rankPageSize
                    + ", elapsed=" + response.elapsedMillis + "ms"
                    + ", bodyLength=" + (response.body == null ? 0 : response.body.length()));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    HotStockSourceItem sourceItem = fromRankJson(item, channel);
                    if (isValid(sourceItem)) {
                        result.add(sourceItem);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "rank fetch failed channel=" + channel.id + ", error="
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        android.util.Log.d(TAG, "rank parsed channel=" + channel.id + ", validCount=" + result.size());
        return result;
    }

    /**
     * 获取limitup线程池。
     */
    private ArrayList<HotStockSourceItem> fetchLimitUpPool(HotStockSourceChannelConfig channel) {
        ArrayList<HotStockSourceItem> result = new ArrayList<HotStockSourceItem>();
        String date = new SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(new Date());
        String url = "https://push2ex.eastmoney.com/getTopicZTPool"
                + "?ut=7eea3edcaed734bea9cbfc24409ed989"
                + "&d=" + date
                + "&Pageindex=0&pagesize=" + config.limitUpPageSize
                + "&sort=fbt:asc";
        try {
            android.util.Log.d(TAG, "limit pool request url=" + url);
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT, REFERER);
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "limit pool http failed status=" + response.statusCode
                        + ", elapsed=" + response.elapsedMillis + "ms"
                        + ", error=" + response.errorMessage
                        + ", body=" + preview(response.body));
                return result;
            }
            JSONObject data = new JSONObject(response.body).optJSONObject("data");
            JSONArray array = data == null ? null : data.optJSONArray("pool");
            if (array == null) {
                android.util.Log.w(TAG, "limit pool empty array status=" + response.statusCode
                        + ", elapsed=" + response.elapsedMillis + "ms"
                        + ", bodyLength=" + (response.body == null ? 0 : response.body.length())
                        + ", body=" + preview(response.body));
                return result;
            }
            android.util.Log.d(TAG, "limit pool response rawCount=" + array.length()
                    + ", elapsed=" + response.elapsedMillis + "ms");
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) {
                    HotStockSourceItem sourceItem = fromLimitUpJson(item, channel);
                    if (isValid(sourceItem)) {
                        result.add(sourceItem);
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "limit pool fetch failed error="
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        android.util.Log.d(TAG, "limit pool parsed validCount=" + result.size());
        return result;
    }

    /**
     * 从rankJSON。
     */
    private HotStockSourceItem fromRankJson(JSONObject item, HotStockSourceChannelConfig channel) {
        HotStockSourceItem sourceItem = new HotStockSourceItem();
        sourceItem.code = item.optString("f12", "");
        sourceItem.name = item.optString("f14", "");
        sourceItem.market = HotStockFormat.marketName(sourceItem.code);
        sourceItem.industry = HotStockFormat.clean(item.optString("f100", "--"));
        sourceItem.concept = sourceItem.industry;
        sourceItem.price = HotStockFormat.formatPrice(item.opt("f2"));
        sourceItem.changePercent = HotStockFormat.formatPercent(item.opt("f3"));
        sourceItem.turnoverRate = HotStockFormat.formatPercent(item.opt("f8"));
        sourceItem.amount = HotStockFormat.formatAmount(item.opt("f6"));
        sourceItem.volumeRatio = HotStockFormat.formatNumber(item.opt("f10"), 2);
        sourceItem.mainNetInflow = HotStockFormat.formatAmount(item.opt("f62"));
        sourceItem.fromLimitUpPool = false;
        sourceItem.sourceWeight = config.weight;
        sourceItem.channelWeight = channel.weight;
        sourceItem.sourceId = id();
        sourceItem.sourceName = config.name;
        sourceItem.channelId = channel.id;
        return sourceItem;
    }

    /**
     * 从limitupJSON。
     */
    private HotStockSourceItem fromLimitUpJson(JSONObject item, HotStockSourceChannelConfig channel) {
        HotStockSourceItem sourceItem = new HotStockSourceItem();
        sourceItem.code = firstNonEmpty(item, "c", "code");
        sourceItem.name = firstNonEmpty(item, "n", "name");
        sourceItem.market = HotStockFormat.marketName(sourceItem.code);
        sourceItem.industry = HotStockFormat.clean(firstNonEmpty(item, "hybk", "industry"));
        sourceItem.concept = sourceItem.industry;
        sourceItem.price = HotStockFormat.formatPrice(firstValue(item, "p", "price"));
        sourceItem.changePercent = HotStockFormat.formatPercent(firstValue(item, "zdp", "changePercent"));
        sourceItem.turnoverRate = HotStockFormat.formatPercent(firstValue(item, "hs", "turnoverRate"));
        sourceItem.amount = HotStockFormat.formatAmount(firstValue(item, "amount", "amt"));
        sourceItem.volumeRatio = HotStockFormat.formatNumber(firstValue(item, "lb", "volumeRatio"), 2);
        sourceItem.mainNetInflow = HotStockFormat.formatAmount(firstValue(item, "fund", "zljlr", "mainNetInflow"));
        sourceItem.fromLimitUpPool = true;
        sourceItem.sourceWeight = config.weight;
        sourceItem.channelWeight = channel.weight;
        sourceItem.sourceId = id();
        sourceItem.sourceName = config.name;
        sourceItem.channelId = channel.id;
        return sourceItem;
    }

    /**
     * 判断是否有效的。
     */
    private boolean isValid(HotStockSourceItem item) {
        return item.code != null && item.code.length() == 6
                && item.name != null && item.name.length() > 0
                && HotStockFormat.parseNumber(item.price) != null
                && HotStockFormat.parseNumber(item.price) > 0d
                && hasRankSignal(item)
                && isAllowedCode(item.code);
    }

    /**
     * 判断是否有ranksignal。
     */
    private boolean hasRankSignal(HotStockSourceItem item) {
        Double amount = HotStockFormat.parseNumber(item.amount);
        Double turnoverRate = HotStockFormat.parseNumber(item.turnoverRate);
        Double changePercent = HotStockFormat.parseNumber(item.changePercent);
        return item.fromLimitUpPool
                || (amount != null && amount > 0d)
                || (turnoverRate != null && turnoverRate > 0d)
                || (changePercent != null && changePercent != 0d);
    }

    /**
     * 获取使用retry。
     */
    private SimpleHttpClient.HttpText getWithRetry(String url, String kind, String channelId) {
        SimpleHttpClient.HttpText response = null;
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT, REFERER);
            if (response.isHttpSuccess()) {
                if (attempt > 1) {
                    android.util.Log.d(TAG, kind + " retry success channel=" + channelId
                            + ", attempt=" + attempt);
                }
                return response;
            }
            android.util.Log.w(TAG, kind + " retry failed channel=" + channelId
                    + ", attempt=" + attempt
                    + ", status=" + response.statusCode
                    + ", elapsed=" + response.elapsedMillis + "ms"
                    + ", error=" + response.errorMessage
                    + ", body=" + preview(response.body));
        }
        return response;
    }

    /**
     * preview。
     */
    private String preview(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    /**
     * 判断是否allowedcode。
     */
    private boolean isAllowedCode(String code) {
        return (code.startsWith("00") || code.startsWith("30") || code.startsWith("60"))
                && !code.startsWith("688");
    }

    /**
     * 首个/第一个value。
     */
    private Object firstValue(JSONObject object, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            if (object.has(keys[i])) {
                return object.opt(keys[i]);
            }
        }
        return null;
    }

    /**
     * 首个/第一个nonempty。
     */
    private String firstNonEmpty(JSONObject object, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            String value = object.optString(keys[i], "").trim();
            if (value.length() > 0 && !"-".equals(value)) {
                return value;
            }
        }
        return "";
    }
}
