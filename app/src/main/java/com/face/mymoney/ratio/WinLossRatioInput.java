package com.face.mymoney.ratio;

import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.Stock;

import java.util.ArrayList;

public class WinLossRatioInput {
    public final Stock stock;
    public final ArrayList<DecisionNote> notes;
    public final DeepSeekAnalysisResult aiReference;

    /**
     * 构造方法：创建 WinLossRatioInput 实例。
     */
    public WinLossRatioInput(Stock stock, ArrayList<DecisionNote> notes,
                             DeepSeekAnalysisResult aiReference) {
        this.stock = stock;
        this.notes = notes == null ? new ArrayList<DecisionNote>() : notes;
        this.aiReference = aiReference;
    }
}
