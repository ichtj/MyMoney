package com.face.mymoney.crawler;

import com.face.mymoney.model.Stock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

public class StockQuoteFetcher {
    private static final String TAG = "MyMoneyQuote";
    private static final int TIMEOUT_MILLIS = 12000;
    private static final int MAX_READ_BYTES = 128 * 1024;

    public int refreshQuotes(ArrayList<Stock> stocks) {
        int successCount = 0;
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            if (refreshQuote(stock)) {
                successCount++;
            }
        }
        android.util.Log.d(TAG, "refreshQuotes finish success=" + successCount + ", total=" + stocks.size());
        return successCount;
    }

    public boolean refreshQuote(Stock stock) {
        String url = "https://push2.eastmoney.com/api/qt/stock/get?secid="
                + secId(stock.code)
                + "&fields=f43,f57,f58,f60,f168,f169,f170";
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 MyMoneyBot/1.0");
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");

            int statusCode = connection.getResponseCode();
            InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readText(inputStream);
            if (statusCode < 200 || statusCode >= 400) {
                android.util.Log.w(TAG, "quote http failed code=" + stock.code + ", status=" + statusCode + ", body=" + preview(body));
                return false;
            }

            JSONObject object = new JSONObject(body);
            JSONObject data = object.optJSONObject("data");
            if (data == null) {
                android.util.Log.w(TAG, "quote no data code=" + stock.code + ", body=" + preview(body));
                return false;
            }

            String price = formatScaled(data.opt("f43"), 2);
            String changePercent = formatPercent(data.opt("f170"));
            String turnover = formatPercent(data.opt("f168"));
            if (price.length() == 0 || changePercent.length() == 0) {
                android.util.Log.w(TAG, "quote empty fields code=" + stock.code + ", body=" + preview(body));
                return false;
            }

            stock.price = price;
            stock.changePercent = changePercent;
            if (turnover.length() > 0) {
                stock.turnover = turnover;
            }
            android.util.Log.d(TAG, "quote success code=" + stock.code
                    + ", price=" + stock.price
                    + ", change=" + stock.changePercent
                    + ", turnover=" + stock.turnover
                    + ", elapsedMs=" + (System.currentTimeMillis() - start));
            return true;
        } catch (Exception e) {
            android.util.Log.w(TAG, "quote failed code=" + stock.code + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String secId(String code) {
        String safeCode = code == null ? "" : code.trim();
        String market = safeCode.startsWith("6") || safeCode.startsWith("9") ? "1" : "0";
        return market + "." + safeCode;
    }

    private String readText(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            int allow = Math.min(length, MAX_READ_BYTES - total);
            if (allow > 0) {
                outputStream.write(buffer, 0, allow);
                total += allow;
            }
            if (total >= MAX_READ_BYTES) {
                break;
            }
        }
        inputStream.close();
        return outputStream.toString("UTF-8");
    }

    private String formatScaled(Object value, int scale) {
        Double number = parseNumber(value);
        if (number == null) {
            return "";
        }
        double divider = Math.pow(10, scale);
        return String.format(Locale.CHINA, "%." + scale + "f", number / divider);
    }

    private String formatPercent(Object value) {
        Double number = parseNumber(value);
        if (number == null) {
            return "";
        }
        double percent = number / 100.0;
        String prefix = percent > 0 ? "+" : "";
        return prefix + String.format(Locale.CHINA, "%.2f%%", percent);
    }

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

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }
}
