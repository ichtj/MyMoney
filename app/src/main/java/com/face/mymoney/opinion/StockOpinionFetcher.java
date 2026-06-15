package com.face.mymoney.opinion;

import android.net.Uri;

import com.face.mymoney.model.Stock;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StockOpinionFetcher {
    private static final String TAG = "MyMoneyOpinion";
    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_READ_BYTES = 512 * 1024;
    private static final int MAX_TOTAL = 24;
    private final com.face.mymoney.crawler.SimpleHttpClient httpClient = new com.face.mymoney.crawler.SimpleHttpClient();

    /**
     * 获取for股票。
     */
    public ArrayList<Opinion> fetchForStock(Stock stock) {
        ArrayList<Opinion> opinions = new ArrayList<Opinion>();
        addOpinions(opinions, fetchEastmoneyGuba(stock));
        addOpinions(opinions, fetchEastmoneyMobileGuba(stock));
        addOpinions(opinions, fetchTaoguba(stock));
        addOpinions(opinions, fetchTonghuashunDoctor(stock));
        addOpinions(opinions, fetchBingOpinionRss(stock));
        addOpinions(opinions, fetchXueqiuSearch(stock));
        android.util.Log.d(TAG, "fetchForStock finish code=" + stock.code + ", count=" + opinions.size());
        return opinions;
    }

    /**
     * 获取东方财富股吧。
     */
    private ArrayList<Opinion> fetchEastmoneyGuba(Stock stock) {
        String url = "https://guba.eastmoney.com/list," + stock.code + ".html";
        WebText text = fetch(url, "Eastmoney Guba");
        ArrayList<Opinion> list = new ArrayList<Opinion>();
        if (!text.success) {
            return list;
        }

        Pattern pattern = Pattern.compile("(?is)<a[^>]+href=[\"']([^\"']*news," + Pattern.quote(stock.code) + ",[^\"']+)[\"'][^>]*>(.*?)</a>");
        Matcher matcher = pattern.matcher(text.body);
        while (matcher.find() && list.size() < 12) {
            String link = normalizeUrl("https://guba.eastmoney.com", matcher.group(1));
            String title = cleanText(stripTags(matcher.group(2)));
            if (!isUsefulOpinion(stock, title) || containsTitle(list, title)) {
                continue;
            }
            String time = findNearbyTime(text.body, matcher.end());
            list.add(new Opinion(title, "东方财富股吧", time, keyword(stock), title, link));
        }

        if (list.size() == 0) {
            Pattern titlePattern = Pattern.compile("(?is)title=[\"']([^\"']{6,80})[\"']");
            Matcher titleMatcher = titlePattern.matcher(text.body);
            while (titleMatcher.find() && list.size() < 8) {
                String title = cleanText(titleMatcher.group(1));
                if (isUsefulOpinion(stock, title) && !containsTitle(list, title)) {
                    String time = findNearbyTime(text.body, titleMatcher.end());
                    list.add(new Opinion(title, "东方财富股吧", time, keyword(stock), title, url));
                }
            }
        }
        android.util.Log.d(TAG, "fetchEastmoneyGuba count=" + list.size() + ", preview=" + preview(text.body));
        return list;
    }

    /**
     * 获取东方财富移动端股吧。
     */
    private ArrayList<Opinion> fetchEastmoneyMobileGuba(Stock stock) {
        String url = "https://mguba.eastmoney.com/mguba/list/" + stock.code + "%2C99%2Cf_1";
        WebText text = fetch(url, "Eastmoney Mobile Guba");
        ArrayList<Opinion> list = new ArrayList<Opinion>();
        if (!text.success) {
            return list;
        }

        Pattern pattern = Pattern.compile("(?is)<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");
        Matcher matcher = pattern.matcher(text.body);
        while (matcher.find() && list.size() < 12) {
            String title = cleanText(stripTags(matcher.group(2)));
            if (!isUsefulOpinion(stock, title) || containsTitle(list, title)) {
                continue;
            }
            String link = normalizeUrl("https://mguba.eastmoney.com", matcher.group(1));
            String time = findNearbyTime(text.body, matcher.end());
            list.add(new Opinion(title, "东方财富移动股吧", time, keyword(stock), title, link));
        }
        android.util.Log.d(TAG, "fetchEastmoneyMobileGuba count=" + list.size() + ", preview=" + preview(text.body));
        return list;
    }

    /**
     * 获取淘股吧。
     */
    private ArrayList<Opinion> fetchTaoguba(Stock stock) {
        String prefix = stock.code.startsWith("6") ? "sh" : "sz";
        String url = "https://www.tgb.cn/quotes/" + prefix + stock.code;
        WebText text = fetch(url, "Taoguba");
        ArrayList<Opinion> list = new ArrayList<Opinion>();
        if (!text.success) {
            return list;
        }

        Pattern pattern = Pattern.compile("(?is)<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>");
        Matcher matcher = pattern.matcher(text.body);
        while (matcher.find() && list.size() < 8) {
            String title = cleanText(stripTags(matcher.group(2)));
            if (!isUsefulOpinion(stock, title) || containsTitle(list, title)) {
                continue;
            }
            String link = normalizeUrl("https://www.tgb.cn", matcher.group(1));
            String time = findNearbyTime(text.body, matcher.end());
            list.add(new Opinion(title, "淘股吧", time, keyword(stock), title, link));
        }
        android.util.Log.d(TAG, "fetchTaoguba count=" + list.size() + ", preview=" + preview(text.body));
        return list;
    }

    /**
     * 获取tonghuashun智能诊股。
     */
    private ArrayList<Opinion> fetchTonghuashunDoctor(Stock stock) {
        String url = "https://doctor.10jqka.com.cn/" + stock.code + "/";
        WebText text = fetch(url, "Tonghuashun Doctor");
        ArrayList<Opinion> list = new ArrayList<Opinion>();
        if (!text.success) {
            return list;
        }

        String pageText = previewLong(text.body, 900);
        Matcher scoreMatcher = Pattern.compile("(综合诊断[:：]?\\s*[^。；\\n]{4,80})").matcher(pageText);
        if (scoreMatcher.find()) {
            String title = cleanText(stock.name + " " + scoreMatcher.group(1));
            list.add(new Opinion(title, "同花顺诊股", "近期", keyword(stock), pageText, url));
        } else if (pageText.contains("诊股") || pageText.contains("技术面") || pageText.contains("资金面")) {
            String title = stock.name + " 同花顺诊股观点";
            list.add(new Opinion(title, "同花顺诊股", "近期", keyword(stock), pageText, url));
        }
        android.util.Log.d(TAG, "fetchTonghuashunDoctor count=" + list.size() + ", preview=" + preview(text.body));
        return list;
    }

    /**
     * 查找就近时间。
     */
    private String findNearbyTime(String html, int startIndex) {
        int start = Math.max(0, startIndex - 240);
        int end = Math.min(html.length(), startIndex + 360);
        String window = cleanText(stripTags(html.substring(start, end)));
        Matcher fullMatcher = Pattern.compile("(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2}\\s+\\d{1,2}:\\d{2})").matcher(window);
        if (fullMatcher.find()) {
            return fullMatcher.group(1);
        }
        Matcher dateMatcher = Pattern.compile("(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2})").matcher(window);
        if (dateMatcher.find()) {
            return dateMatcher.group(1);
        }
        Matcher timeMatcher = Pattern.compile("((今天|昨日|昨天)\\s*\\d{1,2}:\\d{2}|\\d{1,2}:\\d{2})").matcher(window);
        if (timeMatcher.find()) {
            return timeMatcher.group(1);
        }
        return "近期";
    }

    /**
     * 获取bing舆情观点RSS订阅。
     */
    private ArrayList<Opinion> fetchBingOpinionRss(Stock stock) {
        String query = stock.name + " " + stock.code + " 股吧 看法 观点 评论";
        String url = "https://www.bing.com/news/search?q=" + Uri.encode(query) + "&format=rss";
        WebText text = fetch(url, "Bing Opinion RSS");
        ArrayList<Opinion> list = new ArrayList<Opinion>();
        if (!text.success) {
            return list;
        }
        Matcher matcher = Pattern.compile("(?is)<item\\b.*?</item>").matcher(text.body);
        while (matcher.find() && list.size() < 8) {
            String item = matcher.group();
            String title = cleanText(tagValue(item, "title"));
            String description = cleanText(stripTags(tagValue(item, "description")));
            if (!isUsefulOpinion(stock, title + " " + description) || containsTitle(list, title)) {
                continue;
            }
            String link = cleanText(tagValue(item, "link"));
            String time = cleanText(tagValue(item, "pubDate"));
            String source = cleanText(tagValue(item, "source"));
            if (source.length() == 0) {
                source = "Bing观点搜索";
            }
            list.add(new Opinion(title, source, time.length() == 0 ? "近期" : time, keyword(stock), description, link));
        }
        android.util.Log.d(TAG, "fetchBingOpinionRss count=" + list.size() + ", preview=" + preview(text.body));
        return list;
    }

    /**
     * 获取雪球搜索。
     */
    private ArrayList<Opinion> fetchXueqiuSearch(Stock stock) {
        String symbol = stock.code.startsWith("6") ? "SH" + stock.code : "SZ" + stock.code;
        String url = "https://xueqiu.com/k?q=" + Uri.encode(symbol + " " + stock.name);
        WebText text = fetch(url, "Xueqiu Search");
        ArrayList<Opinion> list = new ArrayList<Opinion>();
        if (!text.success) {
            return list;
        }
        Pattern pattern = Pattern.compile("(?is)<a[^>]+href=[\"']([^\"']*/S/[^\"']+|[^\"']*/status/[^\"']+)[\"'][^>]*>(.*?)</a>");
        Matcher matcher = pattern.matcher(text.body);
        while (matcher.find() && list.size() < 6) {
            String link = normalizeUrl("https://xueqiu.com", matcher.group(1));
            String title = cleanText(stripTags(matcher.group(2)));
            if (!isUsefulOpinion(stock, title) || containsTitle(list, title)) {
                continue;
            }
            list.add(new Opinion(title, "雪球搜索", "近期", keyword(stock), title, link));
        }
        android.util.Log.d(TAG, "fetchXueqiuSearch count=" + list.size() + ", preview=" + preview(text.body));
        return list;
    }

    /**
     * 获取。
     */
    private WebText fetch(String url, String source) {
        com.face.mymoney.crawler.SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        android.util.Log.d(TAG, "fetch source=" + source + ", status=" + response.statusCode
                + ", success=" + response.isHttpSuccess()
                + ", length=" + response.body.length()
                + ", elapsedMs=" + response.elapsedMillis);
        if (!response.isHttpSuccess()) {
            android.util.Log.w(TAG, "fetch failed source=" + source + ", error=" + response.errorMessage);
            return new WebText(false, "");
        }
        return new WebText(true, response.body);
    }

    /**
     * 添加舆情观点列表。
     */
    private void addOpinions(ArrayList<Opinion> target, ArrayList<Opinion> source) {
        for (int i = 0; i < source.size() && target.size() < MAX_TOTAL; i++) {
            Opinion opinion = source.get(i);
            if (!containsTitle(target, opinion.title)) {
                target.add(opinion);
            }
        }
    }

    /**
     * 判断是否有效/有用的舆情观点。
     */
    private boolean isUsefulOpinion(Stock stock, String value) {
        if (value == null) {
            return false;
        }
        String cleaned = cleanText(value);
        if (cleaned.length() < 6 || cleaned.contains("验证码") || cleaned.contains("安全验证")) {
            return false;
        }
        return cleaned.contains(stock.name)
                || cleaned.contains(stock.code)
                || cleaned.contains(stock.industry)
                || cleaned.contains("看多")
                || cleaned.contains("看空")
                || cleaned.contains("买")
                || cleaned.contains("卖")
                || cleaned.contains("持有")
                || cleaned.contains("观点")
                || cleaned.contains("讨论");
    }

    /**
     * contains标题。
     */
    private boolean containsTitle(ArrayList<Opinion> opinions, String title) {
        for (int i = 0; i < opinions.size(); i++) {
            if (title.equals(opinions.get(i).title)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 规范化URL。
     */
    private String normalizeUrl(String host, String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("/")) {
            return host + value;
        }
        return host + "/" + value;
    }

    /**
     * 创建标签控件value。
     */
    private String tagValue(String xml, String tag) {
        Matcher matcher = Pattern.compile("(?is)<" + tag + "\\b[^>]*>(.*?)</" + tag + ">").matcher(xml);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * 去除标签。
     */
    private String stripTags(String value) {
        return value.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
    }

    /**
     * clean创建文本控件。
     */
    private String cleanText(String value) {
        return decodeEntities(value)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * decodeentities。
     */
    private String decodeEntities(String value) {
        return (value == null ? "" : value)
                .replace("<![CDATA[", "")
                .replace("]]>", "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    /**
     * keyword。
     */
    private String keyword(Stock stock) {
        return stock.name + " " + stock.code;
    }

    /**
     * preview。
     */
    private String preview(String value) {
        String cleaned = cleanText(stripTags(value));
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    /**
     * preview长整数。
     */
    private String previewLong(String value, int maxLength) {
        String cleaned = cleanText(stripTags(value));
        return cleaned.length() > maxLength ? cleaned.substring(0, maxLength) : cleaned;
    }

    private static class WebText {
        final boolean success;
        final String body;

        WebText(boolean success, String body) {
            this.success = success;
            this.body = body;
        }
    }
}
