package com.face.mymoney.model;

import org.json.JSONObject;

public class News {
    public String title;
    public String source;
    public String time;
    public String keyword;
    public String content;

    /**
     * 构造方法：创建 News 实例。
     */
    public News(String title, String source, String time, String keyword, String content) {
        this.title = title;
        this.source = source;
        this.time = time;
        this.keyword = keyword;
        this.content = content;
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
        } catch (Exception ignored) {
        }
        return object;
    }

    /**
     * 从JSON。
     */
    public static News fromJson(JSONObject object) {
        return new News(
                object.optString("title", ""),
                object.optString("source", ""),
                object.optString("time", ""),
                object.optString("keyword", ""),
                object.optString("content", ""));
    }
}
