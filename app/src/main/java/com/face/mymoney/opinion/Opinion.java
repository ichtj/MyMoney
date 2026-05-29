package com.face.mymoney.opinion;

public class Opinion {
    public String title;
    public String source;
    public String time;
    public String keyword;
    public String content;
    public String url;

    public Opinion(String title, String source, String time, String keyword, String content, String url) {
        this.title = title;
        this.source = source;
        this.time = time;
        this.keyword = keyword;
        this.content = content;
        this.url = url;
    }
}
