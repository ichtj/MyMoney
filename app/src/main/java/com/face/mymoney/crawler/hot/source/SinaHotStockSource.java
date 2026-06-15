package com.face.mymoney.crawler.hot.source;

import com.face.mymoney.crawler.SimpleHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class SinaHotStockSource implements HotStockSource {
    private static final String TAG = "MyMoneySinaSrc";
    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_READ_BYTES = 512 * 1024;
    private static final int MAX_PAGE = 4;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String[] NODES = new String[]{"sh_a", "sz_a", "cyb"};

    private final SimpleHttpClient httpClient = new SimpleHttpClient();
    private final HotStockSourceConfig config;

    /**
     * 构造方法：创建 SinaHotStockSource 实例。
     */
    public SinaHotStockSource(HotStockSourceConfig config) {
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
            if (channel.enabled) {
                result.addAll(fetchRankChannel(channel));
            }
        }
        return result;
    }

    /**
     * 获取rankchannel。
     */
    private ArrayList<HotStockSourceItem> fetchRankChannel(HotStockSourceChannelConfig channel) {
        ArrayList<HotStockSourceItem> result = new ArrayList<HotStockSourceItem>();
        int targetCount = Math.max(80, config.rankPageSize);
        int targetCountPerNode = Math.max(30, targetCount / NODES.length);
        for (int n = 0; n < NODES.length; n++) {
            String node = NODES[n];
            int nodeCount = 0;
            for (int page = 1; page <= MAX_PAGE && nodeCount < targetCountPerNode; page++) {
                String url = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/Market_Center.getHQNodeData"
                        + "?page=" + page
                        + "&num=100"
                        + "&sort=" + channel.sortField
                        + "&asc=0&node=" + node
                        + "&symbol=&_s_r_a=page";
                try {
                    SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                            "application/json,text/plain,*/*", USER_AGENT, "https://finance.sina.com.cn/");
                    if (!response.isHttpSuccess()) {
                        android.util.Log.w(TAG, "http failed channel=" + channel.id
                                + ", node=" + node
                                + ", page=" + page
                                + ", status=" + response.statusCode
                                + ", error=" + response.errorMessage);
                        continue;
                    }
                    JSONArray array = new JSONArray(response.body);
                    android.util.Log.d(TAG, "response channel=" + channel.id
                            + ", node=" + node
                            + ", page=" + page
                            + ", rawCount=" + array.length());
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        HotStockSourceItem sourceItem = fromJson(item, channel);
                        if (isValid(sourceItem)) {
                            result.add(sourceItem);
                            nodeCount++;
                            if (nodeCount >= targetCountPerNode) {
                                break;
                            }
                        }
                    }
                    if (array.length() == 0) {
                        break;
                    }
                } catch (Exception e) {
                    android.util.Log.w(TAG, "fetch failed channel=" + channel.id
                            + ", node=" + node
                            + ", page=" + page
                            + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
        android.util.Log.d(TAG, "parsed channel=" + channel.id + ", validCount=" + result.size());
        return result;
    }

    /**
     * 从JSON。
     */
    private HotStockSourceItem fromJson(JSONObject item, HotStockSourceChannelConfig channel) {
        HotStockSourceItem sourceItem = new HotStockSourceItem();
        sourceItem.code = item.optString("code", "");
        sourceItem.name = item.optString("name", "");
        sourceItem.market = HotStockFormat.marketName(sourceItem.code);
        sourceItem.industry = "--";
        sourceItem.concept = "--";
        sourceItem.price = HotStockFormat.formatPrice(firstPositiveValue(item,
                new String[]{"trade", "price", "lasttrade", "now", "current"},
                new String[]{"settlement", "prevclose", "preclose", "close", "open"}));
        sourceItem.changePercent = HotStockFormat.formatPercent(firstValue(item,
                "changepercent", "changePercent", "pct_chg", "pctChg"));
        sourceItem.turnoverRate = HotStockFormat.formatPercent(firstValue(item,
                "turnoverratio", "turnoverRate", "turnover_rate"));
        sourceItem.amount = HotStockFormat.formatAmount(firstValue(item,
                "amount", "turnover", "turnoverAmount"));
        sourceItem.volumeRatio = HotStockFormat.formatNumber(firstValue(item,
                "volumeratio", "volumeRatio", "volume_ratio"), 2);
        sourceItem.mainNetInflow = "--";
        sourceItem.fromLimitUpPool = false;
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
                && hasTodayRankSignal(item)
                && isAllowedCode(item.code);
    }

    /**
     * 判断是否有todayranksignal。
     */
    private boolean hasTodayRankSignal(HotStockSourceItem item) {
        Double amount = HotStockFormat.parseNumber(item.amount);
        Double turnoverRate = HotStockFormat.parseNumber(item.turnoverRate);
        Double changePercent = HotStockFormat.parseNumber(item.changePercent);
        return (amount != null && amount > 0d)
                || (turnoverRate != null && turnoverRate > 0d)
                || (changePercent != null && changePercent != 0d);
    }

    /**
     * 判断是否allowedcode。
     */
    private boolean isAllowedCode(String code) {
        return (code.startsWith("00") || code.startsWith("30") || code.startsWith("60"))
                && !code.startsWith("688");
    }

    /**
     * 首个/第一个positivevalue。
     */
    private Object firstPositiveValue(JSONObject object, String[] primaryKeys, String[] fallbackKeys) {
        Object primary = firstValue(object, primaryKeys);
        Double primaryNumber = HotStockFormat.parseNumber(primary);
        if (primaryNumber != null && primaryNumber > 0d) {
            return primary;
        }
        Object fallback = firstValue(object, fallbackKeys);
        Double fallbackNumber = HotStockFormat.parseNumber(fallback);
        if (fallbackNumber != null && fallbackNumber > 0d) {
            return fallback;
        }
        return primary;
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
}
