package com.face.mymoney.crawler;

import com.face.mymoney.model.Stock;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

public class StockQuoteFetcher {
    private static final String TAG = "MyMoneyQuote";
    private static final int TIMEOUT_MILLIS = 12000;
    private static final int MAX_READ_BYTES = 128 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 MyMoneyBot/1.0";

    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    public int refreshQuotes(ArrayList<Stock> stocks) {
        return refreshQuotesDetailed(stocks).successCount;
    }

    public QuoteRefreshResult refreshQuotesDetailed(ArrayList<Stock> stocks) {
        QuoteRefreshResult result = new QuoteRefreshResult(stocks.size());
        int successCount = 0;
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            QuoteResult quoteResult = refreshQuoteDetailed(stock);
            if (quoteResult.success) {
                successCount++;
            } else {
                result.failedItems.add(new QuoteFailure(stock.code, stock.name, quoteResult.message));
            }
        }
        result.successCount = successCount;
        android.util.Log.d(TAG, "refreshQuotes finish success=" + successCount + ", total=" + stocks.size());
        return result;
    }

    public boolean refreshQuote(Stock stock) {
        return refreshQuoteDetailed(stock).success;
    }

    public QuoteResult refreshQuoteDetailed(Stock stock) {
        QuoteResult eastmoney = refreshQuoteFromEastmoney(stock);
        if (eastmoney.success) {
            return eastmoney;
        }
        QuoteResult sina = refreshQuoteFromSina(stock);
        if (sina.success) {
            android.util.Log.d(TAG, "quote fallback success code=" + stock.code + ", source=Sina");
            return sina;
        }
        return QuoteResult.fail("Eastmoney failed: " + eastmoney.message + "; Sina failed: " + sina.message);
    }

    private QuoteResult refreshQuoteFromEastmoney(Stock stock) {
        String url = "https://push2.eastmoney.com/api/qt/stock/get?secid="
                + secId(stock.code)
                + "&fields=f43,f57,f58,f60,f168,f169,f170";
        try {
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT);
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "quote http failed code=" + stock.code
                        + ", status=" + response.statusCode
                        + ", error=" + response.errorMessage
                        + ", body=" + preview(response.body));
                return QuoteResult.fail(response.transportSuccess ? "HTTP " + response.statusCode : response.errorMessage);
            }

            JSONObject data = new JSONObject(response.body).optJSONObject("data");
            if (data == null) {
                android.util.Log.w(TAG, "quote no data code=" + stock.code + ", body=" + preview(response.body));
                return QuoteResult.fail("no quote data");
            }

            String price = formatScaled(data.opt("f43"), 2);
            String changePercent = formatPercent(data.opt("f170"));
            String turnover = formatPercent(data.opt("f168"));
            if (price.length() == 0 || changePercent.length() == 0) {
                android.util.Log.w(TAG, "quote empty fields code=" + stock.code + ", body=" + preview(response.body));
                return QuoteResult.fail("empty required quote fields");
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
                    + ", elapsedMs=" + response.elapsedMillis);
            return QuoteResult.success("Eastmoney");
        } catch (Exception e) {
            android.util.Log.w(TAG, "quote failed code=" + stock.code
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return QuoteResult.fail(e.getClass().getSimpleName() + ": " + safeMessage(e));
        }
    }

    private QuoteResult refreshQuoteFromSina(Stock stock) {
        String url = "https://hq.sinajs.cn/list=" + sinaSymbol(stock.code);
        try {
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "text/plain,*/*", USER_AGENT, "https://finance.sina.com.cn/");
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "sina quote http failed code=" + stock.code
                        + ", status=" + response.statusCode
                        + ", error=" + response.errorMessage
                        + ", body=" + preview(response.body));
                return QuoteResult.fail(response.transportSuccess ? "HTTP " + response.statusCode : response.errorMessage);
            }
            int firstQuote = response.body.indexOf('"');
            int lastQuote = response.body.lastIndexOf('"');
            if (firstQuote < 0 || lastQuote <= firstQuote) {
                return QuoteResult.fail("invalid response format");
            }
            String[] fields = response.body.substring(firstQuote + 1, lastQuote).split(",");
            if (fields.length < 4) {
                return QuoteResult.fail("missing quote fields");
            }
            Double previousClose = parseNumber(fields[2]);
            Double currentPrice = parseNumber(fields[3]);
            if (previousClose == null || previousClose <= 0d || currentPrice == null || currentPrice <= 0d) {
                return QuoteResult.fail("invalid price fields");
            }
            stock.price = String.format(Locale.CHINA, "%.2f", currentPrice);
            double changePercent = (currentPrice - previousClose) * 100d / previousClose;
            stock.changePercent = (changePercent > 0 ? "+" : "") + String.format(Locale.CHINA, "%.2f%%", changePercent);
            android.util.Log.d(TAG, "sina quote success code=" + stock.code
                    + ", price=" + stock.price
                    + ", change=" + stock.changePercent
                    + ", elapsedMs=" + response.elapsedMillis);
            return QuoteResult.success("Sina");
        } catch (Exception e) {
            android.util.Log.w(TAG, "sina quote failed code=" + stock.code
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return QuoteResult.fail(e.getClass().getSimpleName() + ": " + safeMessage(e));
        }
    }

    private String secId(String code) {
        String safeCode = code == null ? "" : code.trim();
        String market = safeCode.startsWith("6") || safeCode.startsWith("9") ? "1" : "0";
        return market + "." + safeCode;
    }

    private String sinaSymbol(String code) {
        String safeCode = code == null ? "" : code.trim();
        String market = safeCode.startsWith("6") || safeCode.startsWith("9") ? "sh" : "sz";
        return market + safeCode;
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

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "no detail" : message;
    }

    public static class QuoteRefreshResult {
        public final int totalCount;
        public int successCount;
        public final ArrayList<QuoteFailure> failedItems = new ArrayList<QuoteFailure>();

        QuoteRefreshResult(int totalCount) {
            this.totalCount = totalCount;
        }
    }

    public static class QuoteFailure {
        public final String code;
        public final String name;
        public final String reason;

        QuoteFailure(String code, String name, String reason) {
            this.code = code;
            this.name = name;
            this.reason = reason;
        }
    }

    public static class QuoteResult {
        public final boolean success;
        public final String message;

        private QuoteResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static QuoteResult success(String source) {
            return new QuoteResult(true, source);
        }

        static QuoteResult fail(String message) {
            return new QuoteResult(false, message);
        }
    }
}
