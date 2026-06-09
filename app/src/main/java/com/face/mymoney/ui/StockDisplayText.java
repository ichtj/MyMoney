package com.face.mymoney.ui;

import android.content.Context;

import com.face.mymoney.R;
import com.face.mymoney.model.Stock;

public class StockDisplayText {
    /**
     * 构造方法：创建 StockDisplayText 实例。
     */
    private StockDisplayText() {
    }

    /**
     * board。
     */
    public static String board(Context context, Stock stock) {
        return hasBoard(stock) ? stock.industry.trim() : context.getString(R.string.pending_industry);
    }

    /**
     * 主题。
     */
    public static String theme(Context context, Stock stock, int maxLength) {
        if (hasBoard(stock)) {
            return trimText(stock.industry, maxLength);
        }
        if (hasRemarkTheme(stock)) {
            return trimText(stock.remark, maxLength);
        }
        return context.getString(R.string.pending_theme);
    }

    /**
     * 判断是否有board。
     */
    public static boolean hasBoard(Stock stock) {
        return stock != null && usefulText(stock.industry);
    }

    /**
     * 判断是否有主题。
     */
    public static boolean hasTheme(Stock stock) {
        return hasBoard(stock) || hasRemarkTheme(stock);
    }

    /**
     * needsboard主题lookup。
     */
    public static boolean needsBoardThemeLookup(Stock stock) {
        return !hasBoard(stock);
    }

    /**
     * 判断是否hot候选股票metricremark。
     */
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

    /**
     * 调试summary。
     */
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

    /**
     * 判断是否有remark主题。
     */
    private static boolean hasRemarkTheme(Stock stock) {
        return stock != null
                && usefulText(stock.remark)
                && !isHotCandidateMetricRemark(stock.remark);
    }

    /**
     * 有效/有用的创建文本控件。
     */
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

    /**
     * trim创建文本控件。
     */
    private static String trimText(String value, int maxLength) {
        String text = value == null ? "" : value.trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 安全。
     */
    private static String safe(String value) {
        return value == null ? "null" : value;
    }
}
