package com.face.mymoney.model;

public class MarketIndexQuote {
    public String name;
    public String price;
    public String changePercent;
    public String source;

    public MarketIndexQuote(String name, String price, String changePercent, String source) {
        this.name = name;
        this.price = price;
        this.changePercent = changePercent;
        this.source = source;
    }

    public static MarketIndexQuote empty(String name) {
        return new MarketIndexQuote(name, "--", "--", "");
    }
}
