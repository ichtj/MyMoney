package com.face.mymoney.crawler.hot.source;

import org.json.JSONObject;

import java.util.Locale;

class HotStockFormat {
    /**
     * 市场name。
     */
    static String marketName(String code) {
        if (code == null) {
            return "--";
        }
        if (code.startsWith("6") || code.startsWith("9")) {
            return "沪市";
        }
        if (code.startsWith("3")) {
            return "创业板";
        }
        return "深市";
    }

    /**
     * 格式化price。
     */
    static String formatPrice(Object value) {
        Double number = parseNumber(value);
        if (number == null) {
            return "--";
        }
        return String.format(Locale.CHINA, "%.2f", number);
    }

    /**
     * 格式化percent。
     */
    static String formatPercent(Object value) {
        Double number = parseNumber(value);
        if (number == null) {
            return "--";
        }
        String prefix = number > 0 ? "+" : "";
        return prefix + String.format(Locale.CHINA, "%.2f%%", number);
    }

    /**
     * 格式化number。
     */
    static String formatNumber(Object value, int scale) {
        Double number = parseNumber(value);
        if (number == null) {
            return "--";
        }
        return String.format(Locale.CHINA, "%." + scale + "f", number);
    }

    /**
     * 格式化amount。
     */
    static String formatAmount(Object value) {
        Double number = parseNumber(value);
        if (number == null) {
            return "--";
        }
        if (Math.abs(number) >= 100000000d) {
            return String.format(Locale.CHINA, "%.2f亿", number / 100000000d);
        }
        if (Math.abs(number) >= 10000d) {
            return String.format(Locale.CHINA, "%.2f万", number / 10000d);
        }
        return String.format(Locale.CHINA, "%.0f", number);
    }

    /**
     * 解析number。
     */
    static Double parseNumber(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        String text = String.valueOf(value)
                .replace("%", "")
                .replace("亿", "")
                .replace("万", "")
                .trim();
        if (text.length() == 0 || "-".equals(text) || "--".equals(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * clean。
     */
    static String clean(String value) {
        if (value == null) {
            return "--";
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.length() == 0 || "-".equals(cleaned) ? "--" : cleaned;
    }
}
