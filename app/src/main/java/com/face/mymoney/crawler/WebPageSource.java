package com.face.mymoney.crawler;

import android.net.Uri;

import com.face.mymoney.model.Stock;

import org.json.JSONException;
import org.json.JSONObject;

public class WebPageSource {
    public String id;
    public String name;
    public String category;
    public String url;
    public String method;
    public String parserType;
    public boolean enabled;
    public String charset;
    public String userAgent;
    public int delayMillis;
    public int timeoutMillis;
    public String titleSelector;
    public String contentSelector;
    public String timeSelector;

    public static WebPageSource fromJson(JSONObject object) throws JSONException {
        WebPageSource source = new WebPageSource();
        source.id = object.optString("id", "");
        source.name = object.optString("name", "");
        source.category = object.optString("category", "");
        source.url = object.optString("url", "");
        source.method = object.optString("method", "GET");
        source.parserType = object.optString("parserType", "html");
        source.enabled = object.optBoolean("enabled", true);
        source.charset = object.optString("charset", "UTF-8");
        source.userAgent = object.optString("userAgent", "MyMoneyBot/1.0");
        source.delayMillis = object.optInt("delayMillis", 3000);
        source.timeoutMillis = object.optInt("timeoutMillis", 15000);

        JSONObject selectors = object.optJSONObject("selectors");
        if (selectors != null) {
            source.titleSelector = selectors.optString("title", "");
            source.contentSelector = selectors.optString("content", "");
            source.timeSelector = selectors.optString("time", "");
        } else {
            source.titleSelector = "";
            source.contentSelector = "";
            source.timeSelector = "";
        }
        return source;
    }

    public boolean isValid() {
        return id.length() > 0 && url.length() > 0;
    }

    public WebPageSource resolveForStock(Stock stock) {
        WebPageSource copy = copy();
        copy.url = url.replace("{stockCode}", encode(stock.code))
                .replace("{stockName}", encode(stock.name))
                .replace("{industry}", encode(stock.industry))
                .replace("{groupName}", encode(stock.groupName))
                .replace("{eastmoneySecCode}", encode(eastmoneySecCode(stock)))
                .replace("{eastmoneySearchParam}", encode(eastmoneySearchParam(stock)))
                .replace("{eastmoneyCodeSearchParam}", encode(eastmoneySearchParam(stock.code)))
                .replace("{eastmoneyIndustrySearchParam}", encode(eastmoneySearchParam(stock.name + " " + stock.industry)));
        return copy;
    }

    private String eastmoneySecCode(Stock stock) {
        String code = stock.code == null ? "" : stock.code.trim();
        if (code.startsWith("6")) {
            return "SH" + code;
        }
        if (code.startsWith("0") || code.startsWith("3")) {
            return "SZ" + code;
        }
        if (code.startsWith("4") || code.startsWith("8") || code.startsWith("9")) {
            return "BJ" + code;
        }
        return code;
    }

    private String eastmoneySearchParam(Stock stock) {
        String keyword = stock.name;
        if (stock.code != null && stock.code.length() > 0) {
            keyword = keyword + " " + stock.code;
        }
        return eastmoneySearchParam(keyword);
    }

    private String eastmoneySearchParam(String keyword) {
        return "{\"uid\":\"\",\"keyword\":\"" + escapeJson(keyword) + "\",\"type\":[\"cmsArticleWebOld\"],"
                + "\"client\":\"web\",\"clientType\":\"web\",\"clientVersion\":\"curr\","
                + "\"param\":{\"cmsArticleWebOld\":{\"searchScope\":\"default\",\"sort\":\"default\","
                + "\"pageIndex\":1,\"pageSize\":20,\"preTag\":\"\",\"postTag\":\"\"}}}";
    }

    private String escapeJson(String value) {
        return (value == null ? "" : value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private WebPageSource copy() {
        WebPageSource source = new WebPageSource();
        source.id = id;
        source.name = name;
        source.category = category;
        source.url = url;
        source.method = method;
        source.parserType = parserType;
        source.enabled = enabled;
        source.charset = charset;
        source.userAgent = userAgent;
        source.delayMillis = delayMillis;
        source.timeoutMillis = timeoutMillis;
        source.titleSelector = titleSelector;
        source.contentSelector = contentSelector;
        source.timeSelector = timeSelector;
        return source;
    }

    private String encode(String value) {
        return Uri.encode(value == null ? "" : value);
    }
}
