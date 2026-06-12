package com.face.mymoney.model;

public class KLineItem {
    public final String date;
    public final double open;
    public final double close;
    public final double high;
    public final double low;
    public final long volume;
    public final double amount;
    public final double changePercent;
    public final double turnover;

    public KLineItem(String date, double open, double close, double high, double low,
                     long volume, double amount, double changePercent, double turnover) {
        this.date = date;
        this.open = open;
        this.close = close;
        this.high = high;
        this.low = low;
        this.volume = volume;
        this.amount = amount;
        this.changePercent = changePercent;
        this.turnover = turnover;
    }

    public boolean isUp() {
        return close >= open;
    }
}
