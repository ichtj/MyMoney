package com.face.mymoney.ai;

import org.json.JSONObject;

public class DeepSeekFactorResult {
    public final String id;
    public final boolean valid;
    public final int opportunityScore;
    public final int riskScore;
    public final String reason;

    public DeepSeekFactorResult(String id, boolean valid, int opportunityScore,
                                int riskScore, String reason) {
        this.id = id == null ? "" : id;
        this.valid = valid;
        this.opportunityScore = opportunityScore;
        this.riskScore = riskScore;
        this.reason = reason == null ? "" : reason;
    }

    public boolean isUsable() {
        return valid
                && opportunityScore >= 0
                && opportunityScore <= 100
                && riskScore >= 0
                && riskScore <= 100
                && opportunityScore + riskScore == 100
                && reason.trim().length() >= 12;
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("valid", valid);
            object.put("opportunityScore", opportunityScore);
            object.put("riskScore", riskScore);
            object.put("reason", reason);
        } catch (Exception ignored) {
        }
        return object;
    }

    public static DeepSeekFactorResult fromJson(String fallbackId, JSONObject object) {
        if (object == null) {
            return new DeepSeekFactorResult(fallbackId, false, 0, 0, "");
        }
        String id = object.optString("id", fallbackId);
        boolean valid = object.optBoolean("valid", false);
        int opportunityScore = object.optInt("opportunityScore",
                object.optInt("opportunity_score", 0));
        int riskScore = object.optInt("riskScore", object.optInt("risk_score", 0));
        String reason = object.optString("reason", "");
        return new DeepSeekFactorResult(id, valid, opportunityScore, riskScore, reason);
    }
}
