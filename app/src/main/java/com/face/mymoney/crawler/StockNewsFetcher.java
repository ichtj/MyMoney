package com.face.mymoney.crawler;

import android.content.Context;

import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StockNewsFetcher {
    private static final String TAG = "MyMoneyNews";
    private static final String NEWS_CATEGORY = "news";
    private static final String TIME_LABEL = "Realtime";
    private static final int MAX_TOTAL_NEWS = 30;
    private static final int MAX_PER_SOURCE = 10;

    private final Context context;

    public StockNewsFetcher(Context context) {
        this.context = context.getApplicationContext();
    }

    public ArrayList<News> fetchForStock(Stock stock) {
        ArrayList<News> news = new ArrayList<News>();
        DefaultWebPageSourceLoader loader = new DefaultWebPageSourceLoader(context);
        ArrayList<WebPageSource> sources = loader.loadSourcesByCategory(NEWS_CATEGORY);
        SimpleWebPageFetcher fetcher = new SimpleWebPageFetcher();
        android.util.Log.d(TAG, "fetchForStock start code=" + stock.code
                + ", name=" + stock.name
                + ", industry=" + stock.industry
                + ", sourceCount=" + sources.size());

        for (int i = 0; i < sources.size() && news.size() < MAX_TOTAL_NEWS; i++) {
            WebPageSource source = sources.get(i).resolveForStock(stock);
            android.util.Log.d(TAG, "request source=" + source.id
                    + ", name=" + source.name
                    + ", url=" + source.url);
            WebPageFetchResult result = fetcher.fetch(source);
            boolean blocked = isBlockedPage(result);
            boolean shellPage = isSearchShellPage(result);
            android.util.Log.d(TAG, "response source=" + source.id
                    + ", success=" + result.success
                    + ", status=" + result.statusCode
                    + ", blocked=" + blocked
                    + ", shellPage=" + shellPage
                    + ", title=" + result.title
                    + ", error=" + result.errorMessage);
            if (result.success && !blocked && !shellPage) {
                ArrayList<News> parsedNews = toNewsList(stock, result);
                addNews(news, parsedNews);
                android.util.Log.d(TAG, "parsed source=" + source.id
                        + ", count=" + parsedNews.size()
                        + ", total=" + news.size());
            } else {
                android.util.Log.w(TAG, "skip source=" + source.id
                        + ", success=" + result.success
                        + ", blocked=" + blocked
                        + ", shellPage=" + shellPage
                        + ", snippet=" + preview(result.snippet));
            }
            if (i < sources.size() - 1) {
                sleepQuietly(source.delayMillis);
            }
        }
        android.util.Log.d(TAG, "fetchForStock finish code=" + stock.code + ", newsCount=" + news.size());
        return news;
    }

    private boolean isBlockedPage(WebPageFetchResult result) {
        String value = (result.title + " " + result.snippet + " " + result.errorMessage).toLowerCase();
        return value.contains("安全验证")
                || value.contains("验证码")
                || value.contains("captcha")
                || value.contains("verify")
                || value.contains("访问异常")
                || value.contains("网络不给力");
    }

    private boolean isSearchShellPage(WebPageFetchResult result) {
        String title = result.title == null ? "" : result.title.trim();
        return "搜索结果 - 东方财富网".equals(title)
                || "搜索 - Microsoft 必应".equals(title)
                || title.endsWith("| 搜索");
    }

    private ArrayList<News> toNewsList(Stock stock, WebPageFetchResult result) {
        if ("eastmoney_search_json".equalsIgnoreCase(result.source.parserType)) {
            return parseEastmoneySearchJson(stock, result);
        }
        if ("eastmoney_notice_json".equalsIgnoreCase(result.source.parserType)) {
            return parseEastmoneyNoticeJson(stock, result);
        }
        if ("eastmoney_column_json".equalsIgnoreCase(result.source.parserType)) {
            return parseEastmoneyColumnJson(stock, result);
        }
        if ("eastmoney_hsf10".equalsIgnoreCase(result.source.parserType)) {
            return parseEastmoneyHsf10News(stock, result);
        }
        if ("rss".equalsIgnoreCase(result.source.parserType)) {
            return parseRssNews(stock, result);
        }

        ArrayList<News> list = new ArrayList<News>();
        News item = toNews(stock, result);
        if (isUsefulStockNews(stock, item.title, item.content)) {
            list.add(item);
        }
        return list;
    }

    private ArrayList<News> parseEastmoneySearchJson(Stock stock, WebPageFetchResult result) {
        ArrayList<News> list = new ArrayList<News>();
        try {
            JSONObject object = new JSONObject(unwrapJsonp(result.rawContent));
            JSONObject resultObject = object.optJSONObject("result");
            JSONArray array = resultObject == null ? null : resultObject.optJSONArray("cmsArticleWebOld");
            if (array == null) {
                android.util.Log.w(TAG, "parseEastmoneySearchJson no array rawPreview=" + preview(result.rawContent));
                return list;
            }
            for (int i = 0; i < array.length() && list.size() < MAX_PER_SOURCE; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String title = cleanText(stripTags(item.optString("title", "")));
                String content = cleanText(stripTags(item.optString("content", "")));
                if (!isUsefulStockNews(stock, title, content) || containsTitle(list, title)) {
                    continue;
                }
                String time = cleanText(item.optString("date", ""));
                String source = cleanText(item.optString("mediaName", ""));
                String link = cleanText(item.optString("url", ""));
                list.add(buildNews(stock, title, source.length() == 0 ? result.source.name : source,
                        time, content, link));
            }
        } catch (JSONException e) {
            android.util.Log.w(TAG, "parseEastmoneySearchJson failed error=" + e.getMessage()
                    + ", rawPreview=" + preview(result.rawContent));
        }
        android.util.Log.d(TAG, "parseEastmoneySearchJson source=" + result.source.id
                + ", count=" + list.size()
                + ", rawPreview=" + preview(result.rawContent));
        return list;
    }

    private ArrayList<News> parseEastmoneyNoticeJson(Stock stock, WebPageFetchResult result) {
        ArrayList<News> list = new ArrayList<News>();
        try {
            JSONObject object = new JSONObject(unwrapJsonp(result.rawContent));
            JSONArray array = firstArray(object, "Data", "data", "List", "list");
            if (array == null) {
                android.util.Log.w(TAG, "parseEastmoneyNoticeJson no array rawPreview=" + preview(result.rawContent));
                return list;
            }
            for (int i = 0; i < array.length() && list.size() < MAX_PER_SOURCE; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String title = cleanText(stripTags(firstNonEmpty(item, "NoticeTitle", "title", "Title")));
                String content = cleanText(stripTags(firstNonEmpty(item, "NoticeContent", "content", "Content")));
                if (!isUsefulStockNews(stock, title, content) || containsTitle(list, title)) {
                    continue;
                }
                String time = cleanText(firstNonEmpty(item, "NoticeDate", "date", "Date"));
                String link = cleanText(firstNonEmpty(item, "Url", "url", "UrlLink"));
                list.add(buildNews(stock, title, result.source.name, time, content, link));
            }
        } catch (JSONException e) {
            android.util.Log.w(TAG, "parseEastmoneyNoticeJson failed error=" + e.getMessage()
                    + ", rawPreview=" + preview(result.rawContent));
        }
        android.util.Log.d(TAG, "parseEastmoneyNoticeJson source=" + result.source.id
                + ", count=" + list.size()
                + ", rawPreview=" + preview(result.rawContent));
        return list;
    }

    private ArrayList<News> parseEastmoneyColumnJson(Stock stock, WebPageFetchResult result) {
        ArrayList<News> list = new ArrayList<News>();
        try {
            JSONObject object = new JSONObject(unwrapJsonp(result.rawContent));
            JSONObject data = object.optJSONObject("data");
            JSONArray array = data == null ? null : data.optJSONArray("list");
            if (array == null) {
                array = firstArray(object, "list", "List", "data", "Data");
            }
            if (array == null) {
                android.util.Log.w(TAG, "parseEastmoneyColumnJson no array rawPreview=" + preview(result.rawContent));
                return list;
            }
            for (int i = 0; i < array.length() && list.size() < MAX_PER_SOURCE; i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String title = cleanText(stripTags(item.optString("title", "")));
                String summary = cleanText(stripTags(item.optString("summary", "")));
                if (!isUsefulStockNews(stock, title, summary) || containsTitle(list, title)) {
                    continue;
                }
                String source = cleanText(item.optString("mediaName", ""));
                String time = cleanText(item.optString("showTime", ""));
                String link = cleanText(firstNonEmpty(item, "uniqueUrl", "url"));
                list.add(buildNews(stock, title, source.length() == 0 ? result.source.name : source,
                        time, summary, link));
            }
        } catch (JSONException e) {
            android.util.Log.w(TAG, "parseEastmoneyColumnJson failed error=" + e.getMessage()
                    + ", rawPreview=" + preview(result.rawContent));
        }
        android.util.Log.d(TAG, "parseEastmoneyColumnJson source=" + result.source.id
                + ", count=" + list.size()
                + ", rawPreview=" + preview(result.rawContent));
        return list;
    }

    private ArrayList<News> parseEastmoneyHsf10News(Stock stock, WebPageFetchResult result) {
        ArrayList<News> list = new ArrayList<News>();
        String text = htmlToLines(result.rawContent);
        Pattern pattern = Pattern.compile("(20\\d{2}-\\d{2}-\\d{2})\\s+([^\\n]{6,90})");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find() && list.size() < MAX_PER_SOURCE) {
            String time = matcher.group(1);
            String title = cleanText(matcher.group(2));
            if (!isUsefulStockNews(stock, title, title) || containsTitle(list, title)) {
                continue;
            }
            list.add(buildNews(stock, title, result.source.name, time, title, result.source.url));
        }
        android.util.Log.d(TAG, "parseEastmoneyHsf10 source=" + result.source.id
                + ", rawLength=" + (result.rawContent == null ? 0 : result.rawContent.length())
                + ", count=" + list.size()
                + ", textPreview=" + preview(text));
        return list;
    }

    private ArrayList<News> parseRssNews(Stock stock, WebPageFetchResult result) {
        ArrayList<News> list = new ArrayList<News>();
        Matcher matcher = Pattern.compile("(?is)<item\\b.*?</item>").matcher(result.rawContent);
        while (matcher.find() && list.size() < MAX_PER_SOURCE) {
            String item = matcher.group();
            String title = cleanText(tagValue(item, "title"));
            String description = cleanText(stripTags(tagValue(item, "description")));
            if (!isUsefulStockNews(stock, title, description) || containsTitle(list, title)) {
                continue;
            }
            String link = cleanText(tagValue(item, "link"));
            String pubDate = cleanText(tagValue(item, "pubDate"));
            String source = cleanText(tagValue(item, "source"));
            list.add(buildNews(stock, title, source.length() == 0 ? result.source.name : source,
                    pubDate, description, link));
        }
        return list;
    }

    private News buildNews(Stock stock, String title, String source, String time, String content, String link) {
        String body = content.length() == 0 ? title : content;
        if (link.length() > 0) {
            body = body + "\n\nSource: " + link;
        }
        return new News(title, source, time.length() == 0 ? TIME_LABEL : time, keyword(stock), body);
    }

    private boolean isUsefulStockNews(Stock stock, String title, String content) {
        if (title.length() < 6 || isGenericTitle(title)) {
            return false;
        }
        String value = title + " " + content;
        return contains(value, stock.name)
                || contains(value, stock.code)
                || contains(value, stock.industry)
                || contains(value, "融资")
                || contains(value, "公告")
                || contains(value, "股东")
                || contains(value, "净利润")
                || contains(value, "营业收入")
                || contains(value, "龙虎榜")
                || contains(value, "分红")
                || contains(value, "回购")
                || contains(value, "减持")
                || contains(value, "增持")
                || contains(value, "业绩");
    }

    private boolean contains(String value, String keyword) {
        return keyword != null && keyword.length() > 0 && !"--".equals(keyword) && value.contains(keyword);
    }

    private boolean containsTitle(ArrayList<News> news, String title) {
        for (int i = 0; i < news.size(); i++) {
            if (title.equals(news.get(i).title)) {
                return true;
            }
        }
        return false;
    }

    private void addNews(ArrayList<News> target, ArrayList<News> source) {
        for (int i = 0; i < source.size() && target.size() < MAX_TOTAL_NEWS; i++) {
            News item = source.get(i);
            if (!containsTitle(target, item.title)) {
                target.add(item);
            }
        }
    }

    private JSONArray firstArray(JSONObject object, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            JSONArray array = object.optJSONArray(keys[i]);
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private String firstNonEmpty(JSONObject object, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            String value = object.optString(keys[i], "");
            if (value.length() > 0) {
                return value;
            }
        }
        return "";
    }

    private String unwrapJsonp(String value) {
        if (value == null) {
            return "{}";
        }
        int start = value.indexOf('(');
        int end = value.lastIndexOf(')');
        if (start >= 0 && end > start) {
            return value.substring(start + 1, end);
        }
        return value;
    }

    private String htmlToLines(String html) {
        if (html == null) {
            return "";
        }
        String text = html.replaceAll("(?is)<script[^>]*>.*?</script>", "\n")
                .replaceAll("(?is)<style[^>]*>.*?</style>", "\n")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</(p|li|div|tr|td|dd|dt|span|a|h[1-6])>", "\n")
                .replaceAll("(?is)<[^>]+>", " ");
        return cleanTextWithLines(text);
    }

    private String cleanTextWithLines(String value) {
        return decodeEntities(value)
                .replace("\r", "\n")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .replaceAll("\\s+\\n", "\n")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }

    private boolean isGenericTitle(String title) {
        return title.length() == 0
                || title.contains("搜索结果")
                || title.contains("Microsoft 必应")
                || title.equals("搜索");
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
        return decodeEntities(value)
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

    private String keyword(Stock stock) {
        String keyword = stock.name + " " + stock.code;
        if (stock.industry.length() > 0 && !"--".equals(stock.industry)) {
            keyword = keyword + " " + stock.industry;
        }
        return keyword;
    }

    private String preview(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    private News toNews(Stock stock, WebPageFetchResult result) {
        String title = result.title.length() == 0 ? result.source.name : result.title;
        String content = result.snippet.length() == 0 ? "Fetched by current stock keywords." : result.snippet;
        return buildNews(stock, title, result.source.name, TIME_LABEL, content, result.source.url);
    }

    private void sleepQuietly(int delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
