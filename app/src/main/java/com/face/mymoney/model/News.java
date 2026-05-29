package com.face.mymoney.model;

public class News {
    public String title;
    public String source;
    public String time;
    public String keyword;
    public String content;

    public News(String title, String source, String time, String keyword, String content) {
        this.title = title;
        this.source = source;
        this.time = time;
        this.keyword = keyword;
        this.content = content;
    }
}
