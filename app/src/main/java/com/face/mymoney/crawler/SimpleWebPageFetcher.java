package com.face.mymoney.crawler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleWebPageFetcher {
    private static final String TAG = "MyMoneyCrawler";
    private static final int MAX_READ_BYTES = 512 * 1024;
    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    public WebPageFetchResult fetch(WebPageSource source) {
        long start = System.currentTimeMillis();
        try {
            SimpleHttpClient.HttpText response = httpClient.get(source.url, source.timeoutMillis, MAX_READ_BYTES,
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8", source.userAgent);
            String html = response.body;

            WebPageFetchResult result = new WebPageFetchResult();
            result.source = source;
            result.statusCode = response.statusCode;
            result.success = response.isHttpSuccess();
            result.title = extractTitle(html);
            result.snippet = buildSnippet(html);
            result.rawContent = html;
            result.errorMessage = result.success ? "" : (response.transportSuccess ? "HTTP " + response.statusCode : response.errorMessage);
            result.elapsedMillis = response.elapsedMillis;
            android.util.Log.d(TAG, "fetch result source=" + source.id
                    + ", status=" + response.statusCode
                    + ", success=" + result.success
                    + ", elapsedMs=" + result.elapsedMillis
                    + ", titleLength=" + result.title.length()
                    + ", snippetLength=" + result.snippet.length());
            return result;
        } catch (Exception e) {
            android.util.Log.w(TAG, "fetch failed source=" + source.id
                    + ", url=" + source.url
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return WebPageFetchResult.error(source, e.getClass().getSimpleName() + ": " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private String extractTitle(String html) {
        Matcher matcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }
        return "";
    }

    private String buildSnippet(String html) {
        String cleaned = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        cleaned = cleanText(cleaned);
        if (cleaned.length() > 280) {
            return cleaned.substring(0, 280);
        }
        return cleaned;
    }

    private String cleanText(String value) {
        return value.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
