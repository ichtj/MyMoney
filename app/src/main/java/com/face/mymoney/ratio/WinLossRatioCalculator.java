package com.face.mymoney.ratio;

import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.ai.DeepSeekFactorResult;
import com.face.mymoney.model.DecisionNote;

import java.util.ArrayList;

public class WinLossRatioCalculator {
    private final ArrayList<WinLossRatioFactor> factors = new ArrayList<WinLossRatioFactor>();

    /**
     * 构造方法：创建 WinLossRatioCalculator 实例。
     */
    public WinLossRatioCalculator() {
        factors.add(new PricePlanFactor(0.25d));
        factors.add(new AiSubFactor(DeepSeekAnalysisResult.FACTOR_INFO_CONSISTENCY,
                "信息一致性", 0.25d));
        factors.add(new AiSubFactor(DeepSeekAnalysisResult.FACTOR_NEWS_SENTIMENT,
                "新闻情绪", 0.25d));
        factors.add(new LimitApproachFactor(0.25d));
    }

    /**
     * 添加因子。
     */
    public WinLossRatioCalculator addFactor(WinLossRatioFactor factor) {
        if (factor != null) {
            factors.add(factor);
        }
        return this;
    }

    /**
     * 计算。
     */
    public WinLossRatioResult calculate(WinLossRatioInput input) {
        WinLossRatioResult result = new WinLossRatioResult();
        ArrayList<WinLossRatioContribution> selected = new ArrayList<WinLossRatioContribution>();
        for (int i = 0; i < factors.size(); i++) {
            WinLossRatioContribution contribution = factors.get(i).evaluate(input);
            if (contribution != null && contribution.isValid()) {
                selected.add(contribution);
            }
        }
        if (selected.size() == 0) {
            return result;
        }

        double opportunityScore = 0d;
        double riskScore = 0d;
        for (int i = 0; i < selected.size(); i++) {
            WinLossRatioContribution contribution = selected.get(i);
            result.contributions.add(contribution);
            double total = contribution.opportunityScore + contribution.riskScore;
            opportunityScore += contribution.opportunityScore / total * 100d * contribution.weight;
            riskScore += contribution.riskScore / total * 100d * contribution.weight;
            if (contribution.pricePlan) {
                result.hasValidPlan = true;
                result.targetPrice = contribution.targetPrice;
                result.stopLossPrice = contribution.stopLossPrice;
            }
            if (contribution.aiReference) {
                result.hasAiRatio = true;
            }
        }

        result.applyScores(opportunityScore, riskScore);
        if (result.hasAiRatio) {
            result.ratioExplanation = "含义：每承担 1 份风险，对应 " + result.ratioText
                    + " 份机会；AI 分项只参与本地权重合成，不直接给最终胜负比。";
        }
        return result;
    }

    private static class PricePlanFactor implements WinLossRatioFactor {
        private final double weight;

        PricePlanFactor(double weight) {
            this.weight = weight;
        }

        /**
         * evaluate。
         */
        public WinLossRatioContribution evaluate(WinLossRatioInput input) {
            if (input == null || input.stock == null) {
                return null;
            }
            double current = parsePrice(input.stock.price);
            DecisionNote plan = findLatestPlan(input.notes);
            if (plan == null || current <= 0d) {
                return null;
            }

            double target = parsePrice(plan.targetPrice);
            double stopLoss = parsePrice(plan.stopLossPrice);
            if (target <= current || stopLoss <= 0d || stopLoss >= current) {
                return null;
            }

            return new WinLossRatioContribution.Builder()
                    .id("price_plan")
                    .name("目标价/止损价")
                    .weight(weight)
                    .scores(target - current, current - stopLoss)
                    .targetPrice(plan.targetPrice)
                    .stopLossPrice(plan.stopLossPrice)
                    .pricePlan(true)
                    .build();
        }
    }

    private static class AiSubFactor implements WinLossRatioFactor {
        private final String factorId;
        private final String name;
        private final double weight;

        AiSubFactor(String factorId, String name, double weight) {
            this.factorId = factorId;
            this.name = name;
            this.weight = weight;
        }

        /**
         * evaluate。
         */
        public WinLossRatioContribution evaluate(WinLossRatioInput input) {
            if (input == null || input.aiReference == null || !input.aiReference.success) {
                return null;
            }
            DeepSeekFactorResult factor = input.aiReference.getFactor(factorId);
            if (factor == null || !factor.isUsable()) {
                return null;
            }
            return new WinLossRatioContribution.Builder()
                    .id(factorId)
                    .name(name)
                    .weight(weight)
                    .scores(factor.opportunityScore, factor.riskScore)
                    .summary(factor.reason)
                    .aiReference(true)
                    .build();
        }
    }

    private static class LimitApproachFactor implements WinLossRatioFactor {
        private final double weight;

        LimitApproachFactor(double weight) {
            this.weight = weight;
        }

        /**
         * evaluate。
         */
        public WinLossRatioContribution evaluate(WinLossRatioInput input) {
            if (input == null || input.stock == null) {
                return null;
            }
            double change = parsePercent(input.stock.changePercent);
            if (Double.isNaN(change)) {
                return null;
            }
            double threshold = limitUpThreshold(input.stock.code, input.stock.name);
            double distance = threshold - change;
            if (distance > 5d) {
                return newContribution(50, 50, distance);
            }
            if (distance > 3d) {
                return newContribution(45, 55, distance);
            }
            if (distance > 1d) {
                return newContribution(35, 65, distance);
            }
            if (distance > 0d) {
                return newContribution(20, 80, distance);
            }
            return newContribution(10, 90, distance);
        }

        /**
         * newcontribution。
         */
        private WinLossRatioContribution newContribution(int opportunity, int risk, double distance) {
            return new WinLossRatioContribution.Builder()
                    .id("limit_approach")
                    .name("涨停接近")
                    .weight(weight)
                    .scores(opportunity, risk)
                    .summary(String.format(java.util.Locale.CHINA,
                            "距离涨停约 %.2f 个百分点，越接近涨停追高风险越高。", distance))
                    .build();
        }
    }

    private static DecisionNote findLatestPlan(ArrayList<DecisionNote> notes) {
        if (notes == null) {
            return null;
        }
        for (int i = 0; i < notes.size(); i++) {
            DecisionNote note = notes.get(i);
            if (parsePrice(note.targetPrice) > 0d && parsePrice(note.stopLossPrice) > 0d) {
                return note;
            }
        }
        return null;
    }

    private static double parsePrice(String value) {
        if (value == null) {
            return -1d;
        }
        String cleaned = value.replace(",", "")
                .replace(String.valueOf('\u5143'), "")
                .replace(String.valueOf('\uffe5'), "")
                .trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return -1d;
        }
    }

    private static double parsePercent(String value) {
        if (value == null) {
            return Double.NaN;
        }
        String cleaned = value.replace("%", "").replace("+", "").trim();
        if (cleaned.length() == 0 || "--".equals(cleaned)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double limitUpThreshold(String code, String name) {
        String safeCode = code == null ? "" : code.trim();
        String safeName = name == null ? "" : name.trim().toUpperCase(java.util.Locale.US);
        if (safeName.contains("ST")) {
            return 5d;
        }
        if (safeCode.startsWith("8") || safeCode.startsWith("4")) {
            return 30d;
        }
        if (safeCode.startsWith("300") || safeCode.startsWith("301")
                || safeCode.startsWith("688") || safeCode.startsWith("689")) {
            return 20d;
        }
        return 10d;
    }
}
