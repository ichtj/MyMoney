package com.face.mymoney.crawler.hot.source;

import com.face.mymoney.crawler.SimpleHttpClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

public class TencentHotStockSource implements HotStockSource {
    private static final String TAG = "MyMoneyTencentSrc";
    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_READ_BYTES = 512 * 1024;
    private static final int MAX_PAGE = 3;
    private static final int QUOTE_BATCH_SIZE = 80;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String[] NODES = new String[]{"sh_a", "sz_a", "cyb"};

    private final SimpleHttpClient httpClient = new SimpleHttpClient();
    private final HotStockSourceConfig config;

    /**
     * 构造方法：创建 TencentHotStockSource 实例。
     */
    public TencentHotStockSource(HotStockSourceConfig config) {
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
            android.util.Log.d(TAG, "channel start id=" + channel.id
                    + ", sort=" + channel.sortField
                    + ", target=" + config.rankPageSize);
            ArrayList<SeedStock> seeds = fetchSeedCodes(channel);
            ArrayList<HotStockSourceItem> quoted = fetchTencentQuotes(seeds, channel);
            Collections.sort(quoted, channelComparator(channel));
            int limit = Math.min(config.rankPageSize, quoted.size());
            for (int j = 0; j < limit; j++) {
                result.add(quoted.get(j));
            }
            android.util.Log.d(TAG, "parsed channel=" + channel.id
                    + ", seedCount=" + seeds.size()
                    + ", validCount=" + quoted.size()
                    + ", added=" + limit);
        }
        android.util.Log.d(TAG, "source finish id=" + id()
                + ", total=" + result.size());
        return result;
    }

    /**
     * 获取数据填充codes。
     */
    private ArrayList<SeedStock> fetchSeedCodes(HotStockSourceChannelConfig channel) {
        ArrayList<SeedStock> result = new ArrayList<SeedStock>();
        HashSet<String> seen = new HashSet<String>();
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
                        android.util.Log.w(TAG, "seed http failed channel=" + channel.id
                                + ", node=" + node
                                + ", page=" + page
                                + ", status=" + response.statusCode
                                + ", error=" + response.errorMessage);
                        continue;
                    }
                    JSONArray array = new JSONArray(response.body);
                    for (int i = 0; i < array.length(); i++) {
                        JSONObject item = array.optJSONObject(i);
                        if (item == null) {
                            continue;
                        }
                        String code = item.optString("code", "");
                        if (!isAllowedCode(code) || seen.contains(code)) {
                            continue;
                        }
                        seen.add(code);
                        result.add(new SeedStock(code, item.optString("name", "")));
                        nodeCount++;
                        if (nodeCount >= targetCountPerNode) {
                            break;
                        }
                    }
                    if (array.length() == 0) {
                        break;
                    }
                } catch (Exception e) {
                    android.util.Log.w(TAG, "seed fetch failed channel=" + channel.id
                            + ", node=" + node
                            + ", page=" + page
                            + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
            android.util.Log.d(TAG, "seed node finish channel=" + channel.id
                    + ", node=" + node
                    + ", count=" + nodeCount);
        }
        android.util.Log.d(TAG, "seed finish channel=" + channel.id
                + ", total=" + result.size());
        return result;
    }

    /**
     * 获取tencentquotes。
     */
    private ArrayList<HotStockSourceItem> fetchTencentQuotes(ArrayList<SeedStock> seeds,
                                                             HotStockSourceChannelConfig channel) {
        ArrayList<HotStockSourceItem> result = new ArrayList<HotStockSourceItem>();
        HashMap<String, String> seedNames = new HashMap<String, String>();
        for (int i = 0; i < seeds.size(); i++) {
            seedNames.put(seeds.get(i).code, seeds.get(i).name);
        }
        for (int start = 0; start < seeds.size(); start += QUOTE_BATCH_SIZE) {
            StringBuilder symbols = new StringBuilder();
            int end = Math.min(start + QUOTE_BATCH_SIZE, seeds.size());
            for (int i = start; i < end; i++) {
                if (symbols.length() > 0) {
                    symbols.append(",");
                }
                symbols.append(tencentSymbol(seeds.get(i).code));
            }
            String url = "https://qt.gtimg.cn/q=" + symbols;
            try {
                SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                        "text/plain,*/*", USER_AGENT, "https://stockapp.finance.qq.com/");
                if (!response.isHttpSuccess()) {
                    android.util.Log.w(TAG, "quote http failed channel=" + channel.id
                            + ", start=" + start
                            + ", status=" + response.statusCode
                            + ", error=" + response.errorMessage);
                    continue;
                }
                int before = result.size();
                parseQuoteResponse(response.body, seedNames, channel, result);
                android.util.Log.d(TAG, "quote batch channel=" + channel.id
                        + ", start=" + start
                        + ", size=" + (end - start)
                        + ", added=" + (result.size() - before)
                        + ", elapsed=" + response.elapsedMillis + "ms");
            } catch (Exception e) {
                android.util.Log.w(TAG, "quote fetch failed channel=" + channel.id
                        + ", start=" + start
                        + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return result;
    }

    /**
     * 解析quoteresponse。
     */
    private void parseQuoteResponse(String body,
                                    HashMap<String, String> seedNames,
                                    HotStockSourceChannelConfig channel,
                                    ArrayList<HotStockSourceItem> result) {
        if (body == null || body.length() == 0) {
            return;
        }
        String[] lines = body.split(";");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int left = line.indexOf('"');
            int right = line.lastIndexOf('"');
            if (left < 0 || right <= left) {
                continue;
            }
            String[] fields = line.substring(left + 1, right).split("~");
            if (fields.length < 50) {
                continue;
            }
            HotStockSourceItem item = new HotStockSourceItem();
            item.code = safeField(fields, 2);
            String seedName = seedNames.get(item.code);
            item.name = seedName != null && seedName.length() > 0 ? seedName : safeField(fields, 1);
            item.market = HotStockFormat.marketName(item.code);
            item.industry = "--";
            item.concept = "--";
            item.price = HotStockFormat.formatPrice(safeField(fields, 3));
            item.changePercent = HotStockFormat.formatPercent(safeField(fields, 32));
            item.turnoverRate = HotStockFormat.formatPercent(safeField(fields, 38));
            item.amount = HotStockFormat.formatAmount(parseWanAmount(safeField(fields, 37)));
            item.volumeRatio = HotStockFormat.formatNumber(safeField(fields, 49), 2);
            item.mainNetInflow = "--";
            item.fromLimitUpPool = false;
            item.sourceWeight = config.weight;
            item.channelWeight = channel.weight;
            item.sourceId = id();
            item.sourceName = config.name;
            item.channelId = channel.id;
            if (isValid(item)) {
                result.add(item);
            }
        }
    }

    /**
     * channelcomparator。
     */
    private Comparator<HotStockSourceItem> channelComparator(final HotStockSourceChannelConfig channel) {
        return new Comparator<HotStockSourceItem>() {
            @Override
            public int compare(HotStockSourceItem left, HotStockSourceItem right) {
                double leftValue = channelMetric(left, channel.id);
                double rightValue = channelMetric(right, channel.id);
                if (rightValue > leftValue) {
                    return 1;
                }
                if (rightValue < leftValue) {
                    return -1;
                }
                return 0;
            }
        };
    }

    /**
     * channelmetric。
     */
    private double channelMetric(HotStockSourceItem item, String channelId) {
        if ("amount".equals(channelId)) {
            return amountYi(item.amount);
        }
        if ("turnover".equals(channelId)) {
            return number(item.turnoverRate);
        }
        return number(item.changePercent);
    }

    /**
     * 判断是否有效的。
     */
    private boolean isValid(HotStockSourceItem item) {
        return item.code != null && item.code.length() == 6
                && item.name != null && item.name.length() > 0
                && HotStockFormat.parseNumber(item.price) != null
                && HotStockFormat.parseNumber(item.price) > 0d
                && hasTodaySignal(item)
                && isAllowedCode(item.code);
    }

    /**
     * 判断是否有todaysignal。
     */
    private boolean hasTodaySignal(HotStockSourceItem item) {
        return amountYi(item.amount) > 0d
                || number(item.turnoverRate) > 0d
                || number(item.changePercent) != 0d;
    }

    /**
     * 判断是否allowedcode。
     */
    private boolean isAllowedCode(String code) {
        return (code.startsWith("00") || code.startsWith("30") || code.startsWith("60"))
                && !code.startsWith("688");
    }

    /**
     * tencentsymbol。
     */
    private String tencentSymbol(String code) {
        return (code.startsWith("6") || code.startsWith("9") ? "sh" : "sz") + code;
    }

    /**
     * 安全field。
     */
    private String safeField(String[] fields, int index) {
        return index >= 0 && index < fields.length ? fields[index].trim() : "";
    }

    /**
     * 解析wanamount。
     */
    private Double parseWanAmount(String value) {
        Double number = HotStockFormat.parseNumber(value);
        return number == null ? null : number * 10000d;
    }

    /**
     * amountyi。
     */
    private double amountYi(String value) {
        Double number = HotStockFormat.parseNumber(value);
        if (number == null) {
            return 0d;
        }
        if (value != null && value.contains("\u4ebf")) {
            return number;
        }
        if (value != null && value.contains("\u4e07")) {
            return number / 10000d;
        }
        return number / 100000000d;
    }

    /**
     * number。
     */
    private double number(String value) {
        Double number = HotStockFormat.parseNumber(value);
        return number == null ? 0d : number;
    }

    private static class SeedStock {
        final String code;
        final String name;

        SeedStock(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
