package com.face.mymoney.crawler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SimpleWebPageFetcher {
    private static final String TAG = "MyMoneyCrawler";
    private static final int MAX_READ_BYTES = 512 * 1024;

    public WebPageFetchResult fetch(WebPageSource source) {
        long start = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            URL url = new URL(source.url);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(source.method.length() == 0 ? "GET" : source.method);
            connection.setConnectTimeout(source.timeoutMillis);
            connection.setReadTimeout(source.timeoutMillis);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", source.userAgent);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Charset", source.charset);

            int statusCode = connection.getResponseCode();
            InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String html = readText(inputStream, source.charset);

            WebPageFetchResult result = new WebPageFetchResult();
            result.source = source;
            result.statusCode = statusCode;
            result.success = statusCode >= 200 && statusCode < 400;
            result.title = extractTitle(html);
            result.snippet = buildSnippet(html);
            result.rawContent = html;
            result.errorMessage = result.success ? "" : "HTTP " + statusCode;
            result.elapsedMillis = System.currentTimeMillis() - start;
            android.util.Log.d(TAG, "fetch result source=" + source.id
                    + ", status=" + statusCode
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
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String readText(InputStream inputStream, String charset) throws IOException {
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
        return outputStream.toString(charset.length() == 0 ? "UTF-8" : charset);
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
