package com.face.mymoney.ai;

public class DeepSeekAnalysisResult {
    public final boolean success;
    public final boolean hasRatio;
    public final int opportunityPercent;
    public final int riskPercent;
    public final String ratioText;
    public final String summary;
    public final String errorMessage;

    private DeepSeekAnalysisResult(boolean success, boolean hasRatio, int opportunityPercent,
                                   int riskPercent, String ratioText, String summary,
                                   String errorMessage) {
        this.success = success;
        this.hasRatio = hasRatio;
        this.opportunityPercent = opportunityPercent;
        this.riskPercent = riskPercent;
        this.ratioText = ratioText;
        this.summary = summary;
        this.errorMessage = errorMessage;
    }

    public static DeepSeekAnalysisResult success(int opportunityPercent, int riskPercent, String summary) {
        double ratio = riskPercent <= 0 ? 99d : (double) opportunityPercent / (double) riskPercent;
        return new DeepSeekAnalysisResult(true, true, opportunityPercent, riskPercent,
                String.format(java.util.Locale.CHINA, "%.2f", ratio), summary, "");
    }

    public static DeepSeekAnalysisResult successWithoutRatio(String summary) {
        return new DeepSeekAnalysisResult(true, false, 0, 0, "--", summary, "");
    }

    public static DeepSeekAnalysisResult error(String errorMessage) {
        return new DeepSeekAnalysisResult(false, false, 0, 0, "--", "", errorMessage);
    }

    public String displayText() {
        if (!success) {
            return errorMessage;
        }
        return summary;
    }
}
