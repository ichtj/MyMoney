package com.face.mymoney.model;

import org.json.JSONException;
import org.json.JSONObject;

public class HotStockCandidate {
    public String code;
    public String name;
    public String market;
    public String industry;
    public String concept;
    public String price;
    public String changePercent;
    public String turnoverRate;
    public String amount;
    public String volumeRatio;
    public String mainNetInflow;
    public String fourDayChangePercent;
    public String averageAmount4d;
    public String averageTurnover4d;
    public int activeDays4d;
    public int upDays4d;
    public int limitUpCount5d;
    public boolean hasRecentLimitUp;
    public boolean recentTwoDayLimitUp;
    public boolean hasRecentKlineData;
    public boolean hasDragonTiger;
    public int hotScore;
    public int limitUpScore;
    public int opportunityScore;
    public int activityScore;
    public int moneyScore;
    public int themeScore;
    public int riskDeduct;
    public int totalScore;
    public int sourceCount;
    public String sourceSummary;
    public String sourceChannelSummary;
    public String stageTag;
    public String sourceQualityTag;
    public String reason;
    public String riskTag;
    public String collectedDate;

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("code", code);
            object.put("name", name);
            object.put("market", market);
            object.put("industry", industry);
            object.put("concept", concept);
            object.put("price", price);
            object.put("changePercent", changePercent);
            object.put("turnoverRate", turnoverRate);
            object.put("amount", amount);
            object.put("volumeRatio", volumeRatio);
            object.put("mainNetInflow", mainNetInflow);
            object.put("fourDayChangePercent", fourDayChangePercent);
            object.put("averageAmount4d", averageAmount4d);
            object.put("averageTurnover4d", averageTurnover4d);
            object.put("activeDays4d", activeDays4d);
            object.put("upDays4d", upDays4d);
            object.put("limitUpCount5d", limitUpCount5d);
            object.put("hasRecentLimitUp", hasRecentLimitUp);
            object.put("recentTwoDayLimitUp", recentTwoDayLimitUp);
            object.put("hasRecentKlineData", hasRecentKlineData);
            object.put("hasDragonTiger", hasDragonTiger);
            object.put("hotScore", hotScore);
            object.put("limitUpScore", limitUpScore);
            object.put("opportunityScore", opportunityScore);
            object.put("activityScore", activityScore);
            object.put("moneyScore", moneyScore);
            object.put("themeScore", themeScore);
            object.put("riskDeduct", riskDeduct);
            object.put("totalScore", totalScore);
            object.put("sourceCount", sourceCount);
            object.put("sourceSummary", sourceSummary);
            object.put("sourceChannelSummary", sourceChannelSummary);
            object.put("stageTag", stageTag);
            object.put("sourceQualityTag", sourceQualityTag);
            object.put("reason", reason);
            object.put("riskTag", riskTag);
            object.put("collectedDate", collectedDate);
        } catch (JSONException e) {
            return object;
        }
        return object;
    }

    public static HotStockCandidate fromJson(JSONObject object) {
        HotStockCandidate candidate = new HotStockCandidate();
        candidate.code = object.optString("code");
        candidate.name = object.optString("name");
        candidate.market = object.optString("market");
        candidate.industry = object.optString("industry", "--");
        candidate.concept = object.optString("concept", "--");
        candidate.price = object.optString("price", "--");
        candidate.changePercent = object.optString("changePercent", "0.00%");
        candidate.turnoverRate = object.optString("turnoverRate", "--");
        candidate.amount = object.optString("amount", "--");
        candidate.volumeRatio = object.optString("volumeRatio", "--");
        candidate.mainNetInflow = object.optString("mainNetInflow", "--");
        candidate.fourDayChangePercent = object.optString("fourDayChangePercent", "--");
        candidate.averageAmount4d = object.optString("averageAmount4d", "--");
        candidate.averageTurnover4d = object.optString("averageTurnover4d", "--");
        candidate.activeDays4d = object.optInt("activeDays4d", 0);
        candidate.upDays4d = object.optInt("upDays4d", 0);
        candidate.limitUpCount5d = object.optInt("limitUpCount5d", 0);
        candidate.hasRecentLimitUp = object.optBoolean("hasRecentLimitUp", false);
        candidate.recentTwoDayLimitUp = object.optBoolean("recentTwoDayLimitUp", false);
        candidate.hasRecentKlineData = object.optBoolean("hasRecentKlineData", false);
        candidate.hasDragonTiger = object.optBoolean("hasDragonTiger", false);
        candidate.hotScore = object.optInt("hotScore", 0);
        candidate.limitUpScore = object.optInt("limitUpScore", 0);
        candidate.opportunityScore = object.optInt("opportunityScore", candidate.limitUpScore);
        candidate.activityScore = object.optInt("activityScore", 0);
        candidate.moneyScore = object.optInt("moneyScore", 0);
        candidate.themeScore = object.optInt("themeScore", 0);
        candidate.riskDeduct = object.optInt("riskDeduct", 0);
        candidate.totalScore = object.optInt("totalScore", 0);
        candidate.sourceCount = object.optInt("sourceCount", 0);
        candidate.sourceSummary = object.optString("sourceSummary", "");
        candidate.sourceChannelSummary = object.optString("sourceChannelSummary", "");
        candidate.stageTag = object.optString("stageTag", "");
        candidate.sourceQualityTag = object.optString("sourceQualityTag", "");
        candidate.reason = object.optString("reason", "");
        candidate.riskTag = object.optString("riskTag", "--");
        candidate.collectedDate = object.optString("collectedDate", "");
        return candidate;
    }
}
