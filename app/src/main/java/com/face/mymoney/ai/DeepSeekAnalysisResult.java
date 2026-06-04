package com.face.mymoney.ai;

import org.json.JSONObject;

import java.util.HashMap;

public class DeepSeekAnalysisResult {
    public static final String FACTOR_INFO_CONSISTENCY = "info_consistency";
    public static final String FACTOR_NEWS_SENTIMENT = "news_sentiment";

    public final boolean success;
    public final String summary;
    public final String errorMessage;
    private final HashMap<String, DeepSeekFactorResult> factors =
            new HashMap<String, DeepSeekFactorResult>();

    private DeepSeekAnalysisResult(boolean success, String summary, String errorMessage) {
        this.success = success;
        this.summary = summary == null ? "" : summary;
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }

    public static DeepSeekAnalysisResult success(String summary) {
        return new DeepSeekAnalysisResult(true, summary, "");
    }

    public static DeepSeekAnalysisResult error(String errorMessage) {
        return new DeepSeekAnalysisResult(false, "", errorMessage);
    }

    public DeepSeekAnalysisResult putFactor(DeepSeekFactorResult factor) {
        if (factor != null && factor.id.length() > 0) {
            factors.put(factor.id, factor);
        }
        return this;
    }

    public DeepSeekFactorResult getFactor(String id) {
        return factors.get(id);
    }

    public boolean hasUsableFactors() {
        return hasUsableFactor(FACTOR_INFO_CONSISTENCY) || hasUsableFactor(FACTOR_NEWS_SENTIMENT);
    }

    public boolean hasUsableFactor(String id) {
        DeepSeekFactorResult factor = factors.get(id);
        return factor != null && factor.isUsable();
    }

    public int blendedOpportunityPercent() {
        int count = 0;
        int total = 0;
        if (hasUsableFactor(FACTOR_INFO_CONSISTENCY)) {
            count++;
            total += factors.get(FACTOR_INFO_CONSISTENCY).opportunityScore;
        }
        if (hasUsableFactor(FACTOR_NEWS_SENTIMENT)) {
            count++;
            total += factors.get(FACTOR_NEWS_SENTIMENT).opportunityScore;
        }
        return count == 0 ? -1 : Math.round((float) total / (float) count);
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("version", 2);
            object.put("success", success);
            object.put("summary", summary);
            object.put("errorMessage", errorMessage);
            JSONObject factorObject = new JSONObject();
            for (String key : factors.keySet()) {
                factorObject.put(key, factors.get(key).toJson());
            }
            object.put("factors", factorObject);
        } catch (Exception ignored) {
        }
        return object;
    }

    public static DeepSeekAnalysisResult fromJson(JSONObject object) {
        boolean success = object.optBoolean("success", false);
        String summary = object.optString("summary", "");
        String errorMessage = object.optString("errorMessage", "");
        DeepSeekAnalysisResult result = new DeepSeekAnalysisResult(success, summary, errorMessage);
        JSONObject factors = object.optJSONObject("factors");
        if (factors != null) {
            addFactor(result, factors, FACTOR_INFO_CONSISTENCY);
            addFactor(result, factors, FACTOR_NEWS_SENTIMENT);
        }
        return result;
    }

    private static void addFactor(DeepSeekAnalysisResult result, JSONObject factors, String id) {
        JSONObject object = factors.optJSONObject(id);
        if (object != null) {
            result.putFactor(DeepSeekFactorResult.fromJson(id, object));
        }
    }

    public String displayText() {
        if (!success) {
            return errorMessage;
        }
        StringBuilder builder = new StringBuilder();
        String displaySummary = toDisplayText(summary);
        if (displaySummary.length() > 0) {
            builder.append(displaySummary);
        }
        appendFactor(builder, "信息一致性", getFactor(FACTOR_INFO_CONSISTENCY));
        appendFactor(builder, "新闻情绪", getFactor(FACTOR_NEWS_SENTIMENT));
        return builder.toString();
    }

    private void appendFactor(StringBuilder builder, String name, DeepSeekFactorResult factor) {
        if (factor == null) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(name).append("：");
        if (factor.isUsable()) {
            builder.append("机会 ").append(factor.opportunityScore)
                    .append("%，风险 ").append(factor.riskScore).append("%。");
        } else {
            builder.append("未纳入计算。");
        }
        builder.append(toDisplayText(factor.reason));
    }

    public static String factorDisplayName(String id) {
        if (FACTOR_INFO_CONSISTENCY.equals(id)) {
            return "信息一致性";
        }
        if (FACTOR_NEWS_SENTIMENT.equals(id)) {
            return "新闻情绪";
        }
        return "AI 分项";
    }

    private String toDisplayText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(FACTOR_INFO_CONSISTENCY, factorDisplayName(FACTOR_INFO_CONSISTENCY))
                .replace(FACTOR_NEWS_SENTIMENT, factorDisplayName(FACTOR_NEWS_SENTIMENT));
    }
}
