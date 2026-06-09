package com.face.mymoney.model;

public class MarketIndexQuote {
    public String name;
    public String price;
    public String changePercent;
    public String source;

    /**
     * 构造方法：创建 MarketIndexQuote 实例。
     */
    public MarketIndexQuote(String name, String price, String changePercent, String source) {
        this.name = name;
        this.price = price;
        this.changePercent = changePercent;
        this.source = source;
    }

    /**
     * empty。
     */
    public static MarketIndexQuote empty(String name) {
        return new MarketIndexQuote(name, "--", "--", "");
    }
}
