package com.face.mymoney.ratio;

public interface WinLossRatioFactor {
    WinLossRatioContribution evaluate(WinLossRatioInput input);
}
