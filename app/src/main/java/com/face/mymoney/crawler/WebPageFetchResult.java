package com.face.mymoney.crawler;

public class WebPageFetchResult {
    public WebPageSource source;
    public int statusCode;
    public boolean success;
    public String title;
    public String snippet;
    public String rawContent;
    public String errorMessage;
    public long elapsedMillis;

    public static WebPageFetchResult error(WebPageSource source, String errorMessage, long elapsedMillis) {
        WebPageFetchResult result = new WebPageFetchResult();
        result.source = source;
        result.success = false;
        result.statusCode = -1;
        result.title = "";
        result.snippet = "";
        result.rawContent = "";
        result.errorMessage = errorMessage;
        result.elapsedMillis = elapsedMillis;
        return result;
    }
}
