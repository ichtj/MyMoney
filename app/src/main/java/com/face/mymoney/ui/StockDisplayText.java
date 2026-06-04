package com.face.mymoney.ui;

import android.content.Context;

import com.face.mymoney.R;
import com.face.mymoney.model.Stock;

public class StockDisplayText {
    private StockDisplayText() {
    }

    public static String board(Context context, Stock stock) {
        return hasBoard(stock) ? stock.industry.trim() : context.getString(R.string.pending_industry);
    }

    public static String theme(Context context, Stock stock, int maxLength) {
        if (hasBoard(stock)) {
            return trimText(stock.industry, maxLength);
        }
        if (hasRemarkTheme(stock)) {
            return trimText(stock.remark, maxLength);
        }
        return context.getString(R.string.pending_theme);
    }

    public static boolean hasBoard(Stock stock) {
        return stock != null && usefulText(stock.industry);
    }

    public static boolean hasTheme(Stock stock) {
        return hasBoard(stock) || hasRemarkTheme(stock);
    }

    public static boolean needsBoardThemeLookup(Stock stock) {
        return !hasBoard(stock);
    }

    public static boolean isHotCandidateMetricRemark(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        return text.contains("来源") && text.contains("平台")
                && (text.contains("4日涨幅")
                || text.contains("活跃")
                || text.contains("主力净流入")
                || text.contains("成交额")
                || text.contains("综合评分"));
    }

    public static String debugSummary(Context context, Stock stock, int maxLength) {
        return "code=" + safe(stock.code)
                + ", name=" + safe(stock.name)
                + ", rawIndustry=" + safe(stock.industry)
                + ", rawGroup=" + safe(stock.groupName)
                + ", rawRemark=" + safe(stock.remark)
                + ", rawRiskTag=" + safe(stock.riskTag)
                + ", rawMainBusiness=" + safe(stock.mainBusiness)
                + ", displayBoard=" + board(context, stock)
                + ", displayTheme=" + theme(context, stock, maxLength);
    }

    private static boolean hasRemarkTheme(Stock stock) {
        return stock != null
                && usefulText(stock.remark)
                && !isHotCandidateMetricRemark(stock.remark);
    }

    private static boolean usefulText(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        return text.length() > 0
                && !"--".equals(text)
                && !text.contains("待同步")
                && !text.contains("寰呭悓姝");
    }

    private static String trimText(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private static String safe(String value) {
        return value == null ? "null" : value;
    }
}
