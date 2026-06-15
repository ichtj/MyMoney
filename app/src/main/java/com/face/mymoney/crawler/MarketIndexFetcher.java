package com.face.mymoney.crawler;

import com.face.mymoney.model.MarketIndexQuote;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public class MarketIndexFetcher {
    private static final String TAG = "MyMoneyIndex";
    private static final int TIMEOUT_MILLIS = 12000;
    private static final int MAX_READ_BYTES = 128 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    /**
     * 获取defaultindices。
     */
    public ArrayList<MarketIndexQuote> fetchDefaultIndices() {
        ArrayList<MarketIndexQuote> result = new ArrayList<MarketIndexQuote>();
        result.add(orEmpty(fetchChinaIndex("上证指数", "1.000001", "s_sh000001"), "上证指数"));
        result.add(orEmpty(fetchChinaIndex("深证成指", "0.399001", "s_sz399001"), "深证成指"));
        result.add(orEmpty(fetchChinaIndex("创业板指", "0.399006", "s_sz399006"), "创业板指"));
        result.add(orEmpty(fetchYahooIndex("纳斯达克", "^IXIC"), "纳斯达克"));
        result.add(orEmpty(fetchYahooIndex("道琼斯", "^DJI"), "道琼斯"));
        android.util.Log.d(TAG, "fetchDefaultIndices finish count=" + result.size());
        return result;
    }

    /**
     * 获取china大盘指数。
     */
    private MarketIndexQuote fetchChinaIndex(String name, String eastmoneySecId, String sinaSymbol) {
        MarketIndexQuote eastmoney = fetchEastmoneyIndex(name, eastmoneySecId);
        if (eastmoney != null) {
            return eastmoney;
        }
        MarketIndexQuote sina = fetchSinaChinaIndex(name, sinaSymbol);
        if (sina != null) {
            android.util.Log.d(TAG, "china index fallback success name=" + name + ", source=Sina");
        }
        return sina;
    }

    /**
     * 获取东方财富大盘指数。
     */
    private MarketIndexQuote fetchEastmoneyIndex(String name, String secId) {
        String url = "https://push2.eastmoney.com/api/qt/stock/get?secid="
                + secId + "&fields=f43,f58,f170";
        try {
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT);
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "eastmoney index http failed name=" + name
                        + ", status=" + response.statusCode
                        + ", error=" + response.errorMessage);
                return null;
            }
            JSONObject data = new JSONObject(response.body).optJSONObject("data");
            if (data == null) {
                return null;
            }
            String price = formatScaled(data.opt("f43"), 2);
            String changePercent = formatPercent(data.opt("f170"));
            if (price.length() == 0 || changePercent.length() == 0) {
                return null;
            }
            return new MarketIndexQuote(name, price, changePercent, "东方财富");
        } catch (Exception e) {
            android.util.Log.w(TAG, "eastmoney index failed name=" + name
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取sinachina大盘指数。
     */
    private MarketIndexQuote fetchSinaChinaIndex(String name, String symbol) {
        String url = "https://hq.sinajs.cn/list=" + symbol;
        try {
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "text/plain,*/*", USER_AGENT, "https://finance.sina.com.cn/");
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "sina index http failed name=" + name
                        + ", status=" + response.statusCode
                        + ", error=" + response.errorMessage);
                return null;
            }
            int firstQuote = response.body.indexOf('"');
            int lastQuote = response.body.lastIndexOf('"');
            if (firstQuote < 0 || lastQuote <= firstQuote) {
                return null;
            }
            String[] fields = response.body.substring(firstQuote + 1, lastQuote).split(",");
            if (fields.length < 4) {
                return null;
            }
            Double price = parseNumber(fields[1]);
            Double changePercent = parseNumber(fields[3]);
            if (price == null || changePercent == null) {
                return null;
            }
            return new MarketIndexQuote(name,
                    String.format(Locale.CHINA, "%.2f", price),
                    formatPercentValue(changePercent),
                    "新浪行情");
        } catch (Exception e) {
            android.util.Log.w(TAG, "sina index failed name=" + name
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取yahoo大盘指数。
     */
    private MarketIndexQuote fetchYahooIndex(String name, String symbol) {
        try {
            String encodedSymbol = URLEncoder.encode(symbol, "UTF-8");
            String url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encodedSymbol + "?range=1d&interval=1m";
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT);
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "yahoo index http failed name=" + name
                        + ", status=" + response.statusCode
                        + ", error=" + response.errorMessage);
                return null;
            }
            JSONObject chart = new JSONObject(response.body).optJSONObject("chart");
            JSONArray results = chart == null ? null : chart.optJSONArray("result");
            if (results == null || results.length() == 0) {
                return null;
            }
            JSONObject meta = results.optJSONObject(0).optJSONObject("meta");
            if (meta == null) {
                return null;
            }
            Double price = parseNumber(meta.opt("regularMarketPrice"));
            Double previousClose = parseNumber(meta.opt("chartPreviousClose"));
            if (price == null || previousClose == null || previousClose <= 0d) {
                return null;
            }
            double changePercent = (price - previousClose) * 100d / previousClose;
            return new MarketIndexQuote(name,
                    String.format(Locale.CHINA, "%.2f", price),
                    formatPercentValue(changePercent),
                    "Yahoo");
        } catch (Exception e) {
            android.util.Log.w(TAG, "yahoo index failed name=" + name
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * orempty。
     */
    private MarketIndexQuote orEmpty(MarketIndexQuote quote, String name) {
        return quote == null ? MarketIndexQuote.empty(name) : quote;
    }

    /**
     * 格式化scaled。
     */
    private String formatScaled(Object value, int scale) {
        Double number = parseNumber(value);
        if (number == null) {
            return "";
        }
        double divider = Math.pow(10, scale);
        return String.format(Locale.CHINA, "%." + scale + "f", number / divider);
    }

    /**
     * 格式化percent。
     */
    private String formatPercent(Object value) {
        Double number = parseNumber(value);
        if (number == null) {
            return "";
        }
        return formatPercentValue(number / 100.0);
    }

    /**
     * 格式化percentvalue。
     */
    private String formatPercentValue(double percent) {
        String prefix = percent > 0 ? "+" : "";
        return prefix + String.format(Locale.CHINA, "%.2f%%", percent);
    }

    /**
     * 解析number。
     */
    private Double parseNumber(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.length() == 0 || "-".equals(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
