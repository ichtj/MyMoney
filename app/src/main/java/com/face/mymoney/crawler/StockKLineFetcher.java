package com.face.mymoney.crawler;

import com.face.mymoney.model.KLineItem;
import com.face.mymoney.model.Stock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class StockKLineFetcher {
    private static final String TAG = "MyMoneyKLine";
    private static final int TIMEOUT_MILLIS = 12000;
    private static final int MAX_READ_BYTES = 512 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 MyMoneyBot/1.0";

    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    public KLineResult fetchDailyKLine(Stock stock, int limit) {
        if (stock == null || stock.code == null || stock.code.trim().length() == 0) {
            return KLineResult.fail("empty stock code");
        }
        int safeLimit = Math.max(30, Math.min(limit, 300));
        String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid="
                + secId(stock.code)
                + "&fields1=f1,f2,f3,f4,f5,f6"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                + "&klt=101&fqt=1&beg=0&end=20500101&lmt="
                + safeLimit;
        try {
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT);
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "kline http failed code=" + stock.code
                        + ", status=" + response.statusCode
                        + ", error=" + response.errorMessage
                        + ", body=" + preview(response.body));
                return KLineResult.fail(response.transportSuccess ? "HTTP " + response.statusCode : response.errorMessage);
            }

            JSONObject data = new JSONObject(response.body).optJSONObject("data");
            if (data == null) {
                android.util.Log.w(TAG, "kline no data code=" + stock.code + ", body=" + preview(response.body));
                return KLineResult.fail("no kline data");
            }

            JSONArray klines = data.optJSONArray("klines");
            if (klines == null || klines.length() == 0) {
                return KLineResult.fail("empty kline data");
            }

            ArrayList<KLineItem> items = new ArrayList<KLineItem>();
            for (int i = 0; i < klines.length(); i++) {
                KLineItem item = parseLine(klines.optString(i, ""));
                if (item != null) {
                    items.add(item);
                }
            }
            if (items.size() == 0) {
                return KLineResult.fail("invalid kline fields");
            }

            android.util.Log.d(TAG, "kline success code=" + stock.code
                    + ", count=" + items.size()
                    + ", elapsedMs=" + response.elapsedMillis);
            return KLineResult.success(items);
        } catch (Exception e) {
            android.util.Log.w(TAG, "kline failed code=" + stock.code
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return KLineResult.fail(e.getClass().getSimpleName() + ": " + safeMessage(e));
        }
    }

    private KLineItem parseLine(String line) {
        if (line == null || line.length() == 0) {
            return null;
        }
        String[] fields = line.split(",");
        if (fields.length < 11) {
            return null;
        }
        Double open = parseDouble(fields[1]);
        Double close = parseDouble(fields[2]);
        Double high = parseDouble(fields[3]);
        Double low = parseDouble(fields[4]);
        Long volume = parseLong(fields[5]);
        Double amount = parseDouble(fields[6]);
        Double changePercent = parseDouble(fields[8]);
        Double turnover = parseDouble(fields[10]);
        if (open == null || close == null || high == null || low == null) {
            return null;
        }
        return new KLineItem(fields[0], open, close, high, low,
                volume == null ? 0L : volume.longValue(),
                amount == null ? 0d : amount.doubleValue(),
                changePercent == null ? 0d : changePercent.doubleValue(),
                turnover == null ? 0d : turnover.doubleValue());
    }

    private String secId(String code) {
        String safeCode = code == null ? "" : code.trim();
        String market = safeCode.startsWith("6") || safeCode.startsWith("9") ? "1" : "0";
        return market + "." + safeCode;
    }

    private Double parseDouble(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() == 0 || "-".equals(text)) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long parseLong(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.length() == 0 || "-".equals(text)) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            try {
                return Long.valueOf(String.format(Locale.US, "%.0f", Double.parseDouble(text)));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "no detail" : message;
    }

    public static class KLineResult {
        public final boolean success;
        public final String message;
        public final ArrayList<KLineItem> items;

        private KLineResult(boolean success, String message, ArrayList<KLineItem> items) {
            this.success = success;
            this.message = message;
            this.items = items;
        }

        static KLineResult success(ArrayList<KLineItem> items) {
            return new KLineResult(true, "Eastmoney", items);
        }

        static KLineResult fail(String message) {
            return new KLineResult(false, message, new ArrayList<KLineItem>());
        }
    }
}
