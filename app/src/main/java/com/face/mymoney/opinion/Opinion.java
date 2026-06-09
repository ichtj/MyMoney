package com.face.mymoney.opinion;

import org.json.JSONObject;

public class Opinion {
    public String title;
    public String source;
    public String time;
    public String keyword;
    public String content;
    public String url;

    /**
     * 构造方法：创建 Opinion 实例。
     */
    public Opinion(String title, String source, String time, String keyword, String content, String url) {
        this.title = title;
        this.source = source;
        this.time = time;
        this.keyword = keyword;
        this.content = content;
        this.url = url;
    }

    /**
     * 转换为JSON。
     */
    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("title", title);
            object.put("source", source);
            object.put("time", time);
            object.put("keyword", keyword);
            object.put("content", content);
            object.put("url", url);
        } catch (Exception ignored) {
        }
        return object;
    }

    /**
     * 从JSON。
     */
    public static Opinion fromJson(JSONObject object) {
        return new Opinion(
                object.optString("title", ""),
                object.optString("source", ""),
                object.optString("time", ""),
                object.optString("keyword", ""),
                object.optString("content", ""),
                object.optString("url", ""));
    }
}
