package com.face.mymoney.crawler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

public class StockLookupFetcher {
    private static final String TAG = "MyMoneyStockLookup";
    private static final int TIMEOUT_MILLIS = 12000;
    private static final int MAX_READ_BYTES = 128 * 1024;
    private static final String USER_AGENT = "Mozilla/5.0 MyMoneyBot/1.0";
    private static final String EASTMONEY_TOKEN = "D43BF722C8E33BDC906FB84D85E326E8";

    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    /**
     * 根据代码或名称解析A股身份。
     */
    public StockIdentity resolve(String codeInput, String nameInput) {
        String code = normalizeCode(codeInput);
        String name = cleanText(nameInput);
        if (!isAllowedCode(code)) {
            String codeFromNameInput = normalizeCode(nameInput);
            if (isAllowedCode(codeFromNameInput)) {
                code = codeFromNameInput;
                name = "";
            }
        }
        if (isAllowedCode(code)) {
            StockIdentity quoted = fetchByCode(code);
            if (quoted != null) {
                if (quoted.name.length() == 0 && name.length() > 0) {
                    quoted.name = name;
                }
                return quoted;
            }
            return new StockIdentity(code, name.length() == 0 ? code : name, marketName(code));
        }
        if (name.length() == 0) {
            return null;
        }
        StockIdentity searched = searchEastmoney(name);
        if (searched != null) {
            return searched;
        }
        return searchTencent(name);
    }

    /**
     * 根据代码直接取当前行情里的名称。
     */
    private StockIdentity fetchByCode(String code) {
        String url = "https://push2.eastmoney.com/api/qt/stock/get?secid="
                + secId(code)
                + "&fields=f57,f58,f100";
        try {
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT);
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "quote lookup http failed code=" + code
                        + ", status=" + response.statusCode
                        + ", error=" + response.errorMessage);
                return null;
            }
            JSONObject data = new JSONObject(response.body).optJSONObject("data");
            if (data == null) {
                return null;
            }
            String fetchedCode = normalizeCode(data.optString("f57", code));
            if (!isAllowedCode(fetchedCode)) {
                fetchedCode = code;
            }
            String name = cleanText(data.optString("f58", ""));
            if (name.length() == 0) {
                return null;
            }
            return new StockIdentity(fetchedCode, name, marketName(fetchedCode));
        } catch (Exception e) {
            android.util.Log.w(TAG, "quote lookup failed code=" + code
                    + ", error=" + e.getClass().getSimpleName() + ": " + safeMessage(e));
            return null;
        }
    }

    /**
     * 东方财富搜索名称。
     */
    private StockIdentity searchEastmoney(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, "UTF-8");
            String url = "https://searchapi.eastmoney.com/api/suggest/get?input="
                    + encoded
                    + "&type=14&token=" + EASTMONEY_TOKEN
                    + "&count=10";
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT, "https://www.eastmoney.com/");
            if (!response.isHttpSuccess()) {
                android.util.Log.w(TAG, "eastmoney search http failed keyword=" + keyword
                        + ", status=" + response.statusCode
                        + ", error=" + response.errorMessage);
                return null;
            }
            JSONObject object = new JSONObject(unwrapJsonp(response.body));
            ArrayList<StockIdentity> candidates = new ArrayList<StockIdentity>();
            collectCandidates(object, candidates);
            return chooseBest(keyword, candidates);
        } catch (Exception e) {
            android.util.Log.w(TAG, "eastmoney search failed keyword=" + keyword
                    + ", error=" + e.getClass().getSimpleName() + ": " + safeMessage(e));
            return null;
        }
    }

    /**
     * 腾讯智能提示搜索名称，作为备用。
     */
    private StockIdentity searchTencent(String keyword) {
        try {
            String encoded = URLEncoder.encode(keyword, "UTF-8");
            String url = "https://smartbox.gtimg.cn/s3/?q=" + encoded + "&t=all";
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "text/plain,*/*", USER_AGENT, "https://stockapp.finance.qq.com/");
            if (!response.isHttpSuccess()) {
                return null;
            }
            ArrayList<StockIdentity> candidates = parseTencentSmartBox(response.body);
            return chooseBest(keyword, candidates);
        } catch (Exception e) {
            android.util.Log.w(TAG, "tencent search failed keyword=" + keyword
                    + ", error=" + e.getClass().getSimpleName() + ": " + safeMessage(e));
            return null;
        }
    }

    /**
     * 递归收集搜索结果，适配不同搜索返回结构。
     */
    private void collectCandidates(Object value, ArrayList<StockIdentity> candidates) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                collectCandidates(array.opt(i), candidates);
            }
            return;
        }
        if (!(value instanceof JSONObject)) {
            return;
        }
        JSONObject object = (JSONObject) value;
        StockIdentity identity = identityFromJson(object);
        if (identity != null && !containsCode(candidates, identity.code)) {
            candidates.add(identity);
        }
        Iterator<String> keys = object.keys();
        while (keys.hasNext()) {
            collectCandidates(object.opt(keys.next()), candidates);
        }
    }

    /**
     * 从单个JSON对象解析股票身份。
     */
    private StockIdentity identityFromJson(JSONObject object) {
        String code = normalizeCode(firstNonEmpty(object,
                "Code", "code", "SECURITY_CODE", "SecurityCode", "securityCode",
                "symbol", "QuoteID", "quoteId"));
        String name = cleanText(firstNonEmpty(object,
                "Name", "name", "SECURITY_NAME_ABBR", "SECURITY_NAME",
                "SecurityName", "securityName", "CodeName", "codeName"));
        if (!isAllowedCode(code) || name.length() == 0) {
            return null;
        }
        return new StockIdentity(code, name, marketName(code));
    }

    /**
     * 解析腾讯smartbox文本。
     */
    private ArrayList<StockIdentity> parseTencentSmartBox(String body) {
        ArrayList<StockIdentity> result = new ArrayList<StockIdentity>();
        if (body == null || body.length() == 0) {
            return result;
        }
        String normalized = body.replace("v_hint=", "")
                .replace("\"", "")
                .replace("'", "")
                .replace("\\n", ";");
        String[] rows = normalized.split("[;\\n]");
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i].trim();
            if (row.length() == 0) {
                continue;
            }
            String[] parts = row.split("[~,]");
            String code = "";
            String name = "";
            for (int j = 0; j < parts.length; j++) {
                String part = cleanText(parts[j]);
                String partCode = normalizeCode(part);
                if (code.length() == 0 && isAllowedCode(partCode)) {
                    code = partCode;
                } else if (name.length() == 0 && part.length() > 0 && !part.equals(partCode)
                        && !part.toLowerCase(Locale.US).startsWith("sh")
                        && !part.toLowerCase(Locale.US).startsWith("sz")) {
                    name = part;
                }
            }
            if (isAllowedCode(code) && name.length() > 0 && !containsCode(result, code)) {
                result.add(new StockIdentity(code, name, marketName(code)));
            }
        }
        return result;
    }

    /**
     * 选择最匹配的搜索结果。
     */
    private StockIdentity chooseBest(String keyword, ArrayList<StockIdentity> candidates) {
        if (candidates.size() == 0) {
            return null;
        }
        String cleanKeyword = cleanText(keyword);
        for (int i = 0; i < candidates.size(); i++) {
            StockIdentity item = candidates.get(i);
            if (cleanKeyword.equals(item.name) || cleanKeyword.equals(item.code)) {
                return item;
            }
        }
        for (int i = 0; i < candidates.size(); i++) {
            StockIdentity item = candidates.get(i);
            if (item.name.contains(cleanKeyword) || cleanKeyword.contains(item.name)) {
                return item;
            }
        }
        return candidates.get(0);
    }

    private boolean containsCode(ArrayList<StockIdentity> candidates, String code) {
        for (int i = 0; i < candidates.size(); i++) {
            if (code.equals(candidates.get(i).code)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonEmpty(JSONObject object, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            String value = cleanText(object.optString(keys[i], ""));
            if (value.length() > 0 && !"--".equals(value)) {
                return value;
            }
        }
        return "";
    }

    private String unwrapJsonp(String text) {
        if (text == null) {
            return "";
        }
        String body = text.trim();
        int start = body.indexOf('{');
        int end = body.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return body.substring(start, end + 1);
        }
        return body;
    }

    private String normalizeCode(String value) {
        String text = value == null ? "" : value.trim();
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '0' && ch <= '9') {
                digits.append(ch);
            } else if (digits.length() > 0 && digits.length() < 6) {
                digits.setLength(0);
            }
            if (digits.length() == 6) {
                String code = digits.toString();
                if (isAllowedCode(code)) {
                    return code;
                }
                digits.setLength(0);
            }
        }
        return "";
    }

    private boolean isAllowedCode(String code) {
        return code != null && code.matches("[03689][0-9]{5}");
    }

    private String secId(String code) {
        return (code.startsWith("6") || code.startsWith("9") ? "1." : "0.") + code;
    }

    private String marketName(String code) {
        return code.startsWith("6") || code.startsWith("9") ? "沪市" : "深市";
    }

    private String cleanText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ').trim();
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "no detail" : message;
    }

    public static class StockIdentity {
        public final String code;
        public String name;
        public final String market;

        public StockIdentity(String code, String name, String market) {
            this.code = code == null ? "" : code;
            this.name = name == null ? "" : name;
            this.market = market == null ? "" : market;
        }
    }
}
