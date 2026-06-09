package com.face.mymoney.ratio;

import java.util.ArrayList;
import java.util.Locale;

public class WinLossRatioResult {
    public boolean hasValidPlan;
    public boolean hasAiRatio;
    public float opportunityRatio;
    public int opportunityPercent;
    public int riskPercent;
    public int adviceType;
    public String ratioText = "--";
    public String ratioExplanation = "需要目标价、止损价或通过校验的 AI 比例后才能解释胜负比。";
    public String advice = "暂无有效 AI 胜负比结果时，不展示默认 50% 占比，避免把占位值误认为分析结论。";
    public String targetPrice = "--";
    public String stopLossPrice = "--";
    public final ArrayList<WinLossRatioContribution> contributions =
            new ArrayList<WinLossRatioContribution>();

    /**
     * 判断是否有显示盈亏期望比。
     */
    public boolean hasDisplayRatio() {
        return opportunityPercent > 0 || riskPercent > 0;
    }

    /**
     * 应用scores。
     */
    void applyScores(double opportunityScore, double riskScore) {
        double total = opportunityScore + riskScore;
        if (total <= 0d) {
            return;
        }
        double ratio = riskScore <= 0d ? 99d : opportunityScore / riskScore;
        opportunityRatio = (float) (opportunityScore / total);
        opportunityPercent = Math.round(opportunityRatio * 100f);
        riskPercent = 100 - opportunityPercent;
        ratioText = String.format(Locale.CHINA, "%.2f", ratio);
        ratioExplanation = "含义：每承担 1 份风险，对应 " + ratioText + " 份机会。";
        adviceType = ratio >= 2.0d ? 2 : ratio >= 1.2d ? 1 : 0;
    }
}
