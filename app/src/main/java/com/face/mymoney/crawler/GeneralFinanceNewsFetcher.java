package com.face.mymoney.crawler;

import android.net.Uri;

import com.face.mymoney.model.News;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeneralFinanceNewsFetcher {
    private static final String TAG = "MyMoneyFinanceNews";
    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_READ_BYTES = 256 * 1024;
    private static final int MAX_TOTAL = 40;
    private static final int MAX_PER_HTML_SOURCE = 8;
    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    public ArrayList<News> fetchImportantNews() {
        ArrayList<News> news = new ArrayList<News>();
        android.util.Log.d(TAG, "fetchImportantNews start");
        fetchEastmoneyHtml(news, "https://finance.eastmoney.com/", "东方财富财经");
        fetchEastmoneyHtml(news, "https://kuaixun.eastmoney.com/", "东方财富快讯");
        fetchEastmoneyHtml(news, "https://www.cls.cn/telegraph", "财联社电报");
        fetchEastmoneyHtml(news, "https://finance.sina.com.cn/roll/", "新浪财经");
        fetchBingRss(news, "财经 重要 宏观 A股 美股 利率 汇率 政策");
        fetchBingRss(news, "中国 股市 证监会 央行 财政部 重要");
        fetchBingRss(news, "Nasdaq Dow Jones Federal Reserve inflation market news");
        android.util.Log.d(TAG, "fetchImportantNews finish count=" + news.size());
        return news;
    }

    private void fetchEastmoneyHtml(ArrayList<News> target, String url, String sourceName) {
        android.util.Log.d(TAG, "fetchHtml start source=" + sourceName + ", url=" + url);
        SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                "text/html,application/xhtml+xml,*/*", "Mozilla/5.0 MyMoneyBot/1.0");
        android.util.Log.d(TAG, "fetchHtml response source=" + sourceName
                + ", status=" + response.statusCode
                + ", length=" + response.body.length()
                + ", preview=" + preview(response.body));
        if (!response.isHttpSuccess()) {
            android.util.Log.w(TAG, "eastmoney html failed status=" + response.statusCode
                    + ", error=" + response.errorMessage
                    + ", url=" + url);
            return;
        }
        parseHtmlLinks(target, response.body, sourceName, url);
    }

    private void parseHtmlLinks(ArrayList<News> target, String html, String sourceName, String baseUrl) {
        Matcher matcher = Pattern.compile("(?is)<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>").matcher(html);
        int seen = 0;
        int accepted = 0;
        int rejected = 0;
        while (matcher.find() && target.size() < MAX_TOTAL && accepted < MAX_PER_HTML_SOURCE) {
            seen++;
            String link = normalizeUrl(baseUrl, cleanText(matcher.group(1)));
            String title = cleanText(stripTags(matcher.group(2)));
            if (!isNewsArticleLink(link) || isMarketQuoteTitle(title) || !isImportant(title, "") || containsTitle(target, title)) {
                rejected++;
                if (rejected <= 8 && title.length() > 0) {
                    android.util.Log.d(TAG, "html reject source=" + sourceName
                            + ", title=" + trimLog(title)
                            + ", link=" + link);
                }
                continue;
            }
            String detail = fetchArticleDetail(link);
            String content = "摘要：" + (detail.length() == 0 ? title : detail)
                    + "\n\n重要性：该消息来自 " + sourceName + "，命中宏观、市场、政策或海外市场相关关键词。"
                    + "\n\n来源链接：" + link;
            target.add(new News(title, sourceName, "Realtime", "重要财经", content));
            accepted++;
            android.util.Log.d(TAG, "html accept source=" + sourceName
                    + ", title=" + trimLog(title)
                    + ", detailLength=" + detail.length()
                    + ", link=" + link);
        }
        android.util.Log.d(TAG, "parseHtmlLinks finish source=" + sourceName
                + ", seen=" + seen
                + ", accepted=" + accepted
                + ", rejected=" + rejected
                + ", total=" + target.size());
    }

    private String fetchArticleDetail(String link) {
        if (link.length() == 0 || link.contains("quote.eastmoney.com")) {
            return "";
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(link).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 MyMoneyBot/1.0");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
            int statusCode = connection.getResponseCode();
            String body = readText(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
            android.util.Log.d(TAG, "fetchArticleDetail response status=" + statusCode
                    + ", length=" + body.length()
                    + ", link=" + link
                    + ", preview=" + preview(body));
            if (statusCode < 200 || statusCode >= 400) {
                return "";
            }
            String detail = firstMetaContent(body);
            if (detail.length() == 0) {
                detail = firstParagraphs(body);
            }
            return trimDetail(detail);
        } catch (Exception e) {
            android.util.Log.w(TAG, "fetchArticleDetail failed link=" + link
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private String firstMetaContent(String html) {
        Matcher matcher = Pattern.compile("(?is)<meta[^>]+(?:name|property)=[\"'](?:description|og:description)[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>").matcher(html);
        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }
        matcher = Pattern.compile("(?is)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:name|property)=[\"'](?:description|og:description)[\"'][^>]*>").matcher(html);
        if (matcher.find()) {
            return cleanText(matcher.group(1));
        }
        return "";
    }

    private String firstParagraphs(String html) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = Pattern.compile("(?is)<p[^>]*>(.*?)</p>").matcher(html);
        while (matcher.find() && builder.length() < 260) {
            String text = cleanText(stripTags(matcher.group(1)));
            if (text.length() < 12 || text.contains("责任编辑") || text.contains("文章来源")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("\n");
            }
            builder.append(text);
        }
        return builder.toString();
    }

    private String trimDetail(String value) {
        String cleaned = cleanText(value);
        return cleaned.length() > 300 ? cleaned.substring(0, 300) + "..." : cleaned;
    }

    private void fetchBingRss(ArrayList<News> target, String query) {
        if (target.size() >= MAX_TOTAL) {
            return;
        }
        String url = "https://www.bing.com/news/search?q=" + Uri.encode(query) + "&format=rss";
        HttpURLConnection connection = null;
        try {
            android.util.Log.d(TAG, "fetchRss start query=" + query + ", url=" + url);
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 MyMoneyBot/1.0");
            connection.setRequestProperty("Accept", "application/rss+xml,application/xml,text/xml,*/*");
            int statusCode = connection.getResponseCode();
            String body = readText(statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream());
            android.util.Log.d(TAG, "fetchRss response query=" + query
                    + ", status=" + statusCode
                    + ", length=" + body.length()
                    + ", preview=" + preview(body));
            if (statusCode < 200 || statusCode >= 400) {
                android.util.Log.w(TAG, "rss failed status=" + statusCode + ", query=" + query);
                return;
            }
            parseRss(target, body, query);
        } catch (Exception e) {
            android.util.Log.w(TAG, "rss failed query=" + query + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void parseRss(ArrayList<News> target, String xml, String query) {
        Matcher matcher = Pattern.compile("(?is)<item\\b.*?</item>").matcher(xml);
        int seen = 0;
        int accepted = 0;
        int rejected = 0;
        while (matcher.find() && target.size() < MAX_TOTAL) {
            seen++;
            String item = matcher.group();
            String title = cleanText(tagValue(item, "title"));
            String description = cleanText(stripTags(tagValue(item, "description")));
            if ((!isImportant(title, description) && target.size() > 0) || containsTitle(target, title)) {
                rejected++;
                if (rejected <= 8 && title.length() > 0) {
                    android.util.Log.d(TAG, "rss reject query=" + query + ", title=" + trimLog(title));
                }
                continue;
            }
            String source = cleanText(tagValue(item, "source"));
            String time = cleanText(tagValue(item, "pubDate"));
            String link = cleanText(tagValue(item, "link"));
            String content = description.length() == 0 ? title : description;
            if (link.length() > 0) {
                content = content + "\n\nSource: " + link;
            }
            target.add(new News(title, source.length() == 0 ? "重要财经" : source,
                    time.length() == 0 ? "Realtime" : time, query, content));
            accepted++;
            android.util.Log.d(TAG, "rss accept source=" + (source.length() == 0 ? "重要财经" : source)
                    + ", title=" + trimLog(title));
        }
        android.util.Log.d(TAG, "parseRss finish query=" + query
                + ", seen=" + seen
                + ", accepted=" + accepted
                + ", rejected=" + rejected
                + ", total=" + target.size());
    }

    private boolean isImportant(String title, String description) {
        String value = (title + " " + description).toLowerCase();
        if (title.length() < 6) {
            return false;
        }
        return containsAny(value,
                "证监会", "央行", "财政部", "国务院", "发改委", "美联储", "fed",
                "利率", "降息", "加息", "通胀", "cpi", "pmi", "gdp",
                "a股", "港股", "美股", "纳斯达克", "道琼斯", "人民币", "汇率",
                "政策", "监管", "关税", "制裁", "债券", "房地产", "能源", "芯片");
    }

    private boolean isNewsArticleLink(String link) {
        if (link == null || link.length() == 0) {
            return false;
        }
        String value = link.toLowerCase();
        if (value.contains("quote.eastmoney.com")
                || value.contains("/unify/r/")
                || value.contains("/quote/")
                || value.contains("data.eastmoney.com")) {
            return false;
        }
        return value.contains("/a/")
                || value.contains("finance.eastmoney.com")
                || value.contains("kuaixun.eastmoney.com")
                || value.contains("cls.cn")
                || value.contains("finance.sina.com.cn");
    }

    private boolean isMarketQuoteTitle(String title) {
        if (title == null) {
            return true;
        }
        String value = title.trim();
        return "上证指数".equals(value)
                || "深证成指".equals(value)
                || "创业板指".equals(value)
                || "纳斯达克综合指数".equals(value)
                || "道琼斯指数".equals(value)
                || "恒生指数".equals(value)
                || value.endsWith("指数")
                || value.endsWith("期货")
                || value.contains("行情")
                || value.contains("报价");
    }

    private String normalizeUrl(String baseUrl, String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (value.startsWith("/")) {
            if (baseUrl.startsWith("https://finance.eastmoney.com")) {
                return "https://finance.eastmoney.com" + value;
            }
            if (baseUrl.startsWith("https://kuaixun.eastmoney.com")) {
                return "https://kuaixun.eastmoney.com" + value;
            }
            if (baseUrl.startsWith("https://www.cls.cn")) {
                return "https://www.cls.cn" + value;
            }
            if (baseUrl.startsWith("https://finance.sina.com.cn")) {
                return "https://finance.sina.com.cn" + value;
            }
        }
        return baseUrl + value;
    }

    private boolean containsAny(String value, String... keywords) {
        for (int i = 0; i < keywords.length; i++) {
            if (value.contains(keywords[i].toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTitle(ArrayList<News> news, String title) {
        for (int i = 0; i < news.size(); i++) {
            if (title.equals(news.get(i).title)) {
                return true;
            }
        }
        return false;
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

    private String tagValue(String xml, String tag) {
        Matcher matcher = Pattern.compile("(?is)<" + tag + "\\b[^>]*>(.*?)</" + tag + ">").matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String stripTags(String value) {
        return value.replaceAll("(?is)<[^>]+>", " ");
    }

    private String cleanText(String value) {
        return decodeEntities(value == null ? "" : value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String decodeEntities(String value) {
        return value.replace("<![CDATA[", "")
                .replace("]]>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private String preview(String value) {
        String cleaned = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private String trimLog(String value) {
        String cleaned = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
