package com.face.mymoney.ratio;

public class WinLossRatioContribution {
    public final String id;
    public final String name;
    public final double weight;
    public final double opportunityScore;
    public final double riskScore;
    public final String summary;
    public final String targetPrice;
    public final String stopLossPrice;
    public final boolean pricePlan;
    public final boolean aiReference;
    public final boolean exclusive;

    private WinLossRatioContribution(Builder builder) {
        id = builder.id;
        name = builder.name;
        weight = builder.weight;
        opportunityScore = builder.opportunityScore;
        riskScore = builder.riskScore;
        summary = builder.summary;
        targetPrice = builder.targetPrice;
        stopLossPrice = builder.stopLossPrice;
        pricePlan = builder.pricePlan;
        aiReference = builder.aiReference;
        exclusive = builder.exclusive;
    }

    public boolean isValid() {
        return weight > 0d && opportunityScore >= 0d && riskScore >= 0d
                && opportunityScore + riskScore > 0d;
    }

    public static class Builder {
        private String id = "";
        private String name = "";
        private double weight = 1d;
        private double opportunityScore;
        private double riskScore;
        private String summary = "";
        private String targetPrice = "--";
        private String stopLossPrice = "--";
        private boolean pricePlan;
        private boolean aiReference;
        private boolean exclusive;

        public Builder id(String value) {
            id = value;
            return this;
        }

        public Builder name(String value) {
            name = value;
            return this;
        }

        public Builder weight(double value) {
            weight = value;
            return this;
        }

        public Builder scores(double opportunity, double risk) {
            opportunityScore = opportunity;
            riskScore = risk;
            return this;
        }

        public Builder summary(String value) {
            summary = value == null ? "" : value;
            return this;
        }

        public Builder targetPrice(String value) {
            targetPrice = value == null || value.trim().length() == 0 ? "--" : value;
            return this;
        }

        public Builder stopLossPrice(String value) {
            stopLossPrice = value == null || value.trim().length() == 0 ? "--" : value;
            return this;
        }

        public Builder pricePlan(boolean value) {
            pricePlan = value;
            return this;
        }

        public Builder aiReference(boolean value) {
            aiReference = value;
            return this;
        }

        public Builder exclusive(boolean value) {
            exclusive = value;
            return this;
        }

        public WinLossRatioContribution build() {
            return new WinLossRatioContribution(this);
        }
    }
}
