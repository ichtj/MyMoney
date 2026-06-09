package com.face.mymoney.crawler;

import com.face.mymoney.model.Stock;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;

public class StockBoardFetcher {
    private static final String TAG = "MyMoneyBoardFetch";
    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_READ_BYTES = 2 * 1024 * 1024;
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PAGES = 12;
    private static final String USER_AGENT = "Mozilla/5.0 MyMoneyBot/1.0";

    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    /**
     * 刷新boards。
     */
    public int refreshBoards(ArrayList<Stock> stocks) {
        if (stocks == null || stocks.size() == 0) {
            return 0;
        }
        int updated = refreshFromEastmoneyList(stocks);
        updated += refreshRemainingFromEastmoneyQuote(stocks);
        android.util.Log.d(TAG, "refreshBoards finish updated=" + updated
                + ", targetCount=" + stocks.size());
        return updated;
    }

    /**
     * 刷新从东方财富列表。
     */
    private int refreshFromEastmoneyList(ArrayList<Stock> stocks) {
        HashSet<String> pendingCodes = pendingCodes(stocks);
        if (pendingCodes.size() == 0) {
            return 0;
        }
        int updated = 0;
        String fs = "m:0+t:6,m:0+t:80,m:0+t:81,m:1+t:2,m:1+t:23";
        for (int page = 1; page <= MAX_PAGES && pendingCodes.size() > 0; page++) {
            String url = "https://push2.eastmoney.com/api/qt/clist/get"
                    + "?pn=" + page
                    + "&pz=" + PAGE_SIZE
                    + "&po=1&np=1&ut=bd1d9ddb04089700cf9c27f6f7426281"
                    + "&fltt=2&invt=2&fid=f3"
                    + "&fs=" + fs
                    + "&fields=f12,f14,f100";
            try {
                SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                        "application/json,text/plain,*/*", USER_AGENT);
                if (!response.isHttpSuccess()) {
                    android.util.Log.w(TAG, "eastmoneyList http failed page=" + page
                            + ", status=" + response.statusCode
                            + ", error=" + response.errorMessage);
                    continue;
                }
                JSONObject data = new JSONObject(response.body).optJSONObject("data");
                JSONArray array = data == null ? null : data.optJSONArray("diff");
                if (array == null || array.length() == 0) {
                    android.util.Log.w(TAG, "eastmoneyList empty page=" + page
                            + ", bodyLength=" + response.body.length());
                    continue;
                }
                android.util.Log.d(TAG, "eastmoneyList page=" + page
                        + ", rawCount=" + array.length()
                        + ", pending=" + pendingCodes.size());
                for (int i = 0; i < array.length() && pendingCodes.size() > 0; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String code = item.optString("f12", "").trim();
                    if (!pendingCodes.contains(code)) {
                        continue;
                    }
                    String industry = cleanText(item.optString("f100", ""));
                    android.util.Log.d(TAG, "eastmoneyList hit code=" + code
                            + ", name=" + item.optString("f14", "")
                            + ", rawF100=" + item.optString("f100", "")
                            + ", parsedIndustry=" + industry);
                    if (usefulIndustry(industry) && updateStockIndustry(stocks, code, industry, "eastmoneyList")) {
                        updated++;
                        pendingCodes.remove(code);
                    }
                }
            } catch (Exception e) {
                android.util.Log.w(TAG, "eastmoneyList failed page=" + page
                        + ", error=" + e.getClass().getSimpleName() + ": " + safeMessage(e));
            }
        }
        android.util.Log.d(TAG, "eastmoneyList finish updated=" + updated
                + ", remaining=" + pendingCodes.size());
        return updated;
    }

    /**
     * 刷新remaining从东方财富quote。
     */
    private int refreshRemainingFromEastmoneyQuote(ArrayList<Stock> stocks) {
        int updated = 0;
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            if (hasUsefulIndustry(stock.industry)) {
                continue;
            }
            String url = "https://push2.eastmoney.com/api/qt/stock/get?secid="
                    + secId(stock.code)
                    + "&fields=f57,f58,f100";
            try {
                SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                        "application/json,text/plain,*/*", USER_AGENT);
                if (!response.isHttpSuccess()) {
                    android.util.Log.w(TAG, "eastmoneyQuote http failed code=" + stock.code
                            + ", status=" + response.statusCode
                            + ", error=" + response.errorMessage);
                    continue;
                }
                JSONObject data = new JSONObject(response.body).optJSONObject("data");
                String industry = data == null ? "" : cleanText(data.optString("f100", ""));
                android.util.Log.d(TAG, "eastmoneyQuote parsed code=" + stock.code
                        + ", rawF100=" + (data == null ? "" : data.optString("f100", ""))
                        + ", parsedIndustry=" + industry
                        + ", oldIndustry=" + stock.industry);
                if (usefulIndustry(industry) && updateStockIndustry(stocks, stock.code, industry, "eastmoneyQuote")) {
                    updated++;
                }
            } catch (Exception e) {
                android.util.Log.w(TAG, "eastmoneyQuote failed code=" + stock.code
                        + ", error=" + e.getClass().getSimpleName() + ": " + safeMessage(e));
            }
        }
        android.util.Log.d(TAG, "eastmoneyQuote finish updated=" + updated);
        return updated;
    }

    /**
     * pendingcodes。
     */
    private HashSet<String> pendingCodes(ArrayList<Stock> stocks) {
        HashSet<String> codes = new HashSet<String>();
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            if (stock != null && stock.code != null && stock.code.length() > 0
                    && !hasUsefulIndustry(stock.industry)) {
                codes.add(stock.code);
            }
        }
        return codes;
    }

    /**
     * 更新股票行业。
     */
    private boolean updateStockIndustry(ArrayList<Stock> stocks, String code, String industry, String source) {
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            if (code.equals(stock.code)) {
                String before = stock.industry;
                stock.industry = industry;
                android.util.Log.d(TAG, "board updated source=" + source
                        + ", code=" + stock.code
                        + ", name=" + stock.name
                        + ", before=" + before
                        + ", after=" + stock.industry);
                return true;
            }
        }
        return false;
    }

    /**
     * secid。
     */
    private String secId(String code) {
        String safeCode = code == null ? "" : code.trim();
        String market = safeCode.startsWith("6") || safeCode.startsWith("9") ? "1" : "0";
        return market + "." + safeCode;
    }

    /**
     * 有效/有用的行业。
     */
    private boolean usefulIndustry(String value) {
        return hasUsefulIndustry(value);
    }

    /**
     * 判断是否有有效/有用的行业。
     */
    private boolean hasUsefulIndustry(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        return text.length() > 0
                && !"--".equals(text)
                && !"-".equals(text)
                && !text.contains("待同步")
                && !text.contains("寰呭悓姝");
    }

    /**
     * clean创建文本控件。
     */
    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        String text = value.replaceAll("\\s+", " ").trim();
        return "-".equals(text) ? "" : text;
    }

    /**
     * 安全message。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "no detail" : message;
    }
}
