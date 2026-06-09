package com.face.mymoney.ratio;

public interface WinLossRatioFactor {
    /**
     * evaluate。
     */
    WinLossRatioContribution evaluate(WinLossRatioInput input);
}
