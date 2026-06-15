package com.face.mymoney.ui.detail;

import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.face.mymoney.model.News;
import com.face.mymoney.realtime.RealtimeDecisionAnalyzer;
import com.face.mymoney.ui.MainUiKit;

public class RealtimeDecisionCard {
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(234, 241, 255);

    private RealtimeDecisionCard() {
    }

    public static View create(MainUiKit ui, RealtimeDecisionAnalyzer.StockSignal signal,
                              News linkedNewsEvent,
                              RealtimeDecisionAnalyzer.NewsValClassification linkedClassification) {
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));

        LinearLayout header = ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = ui.vertical();
        titleBox.addView(ui.text("即时分析", 18, COLOR_TEXT, true), ui.matchWrap());
        titleBox.addView(ui.text("只基于当前行情、指数、新闻/观点和 AI 分项，不保存历史记录。",
                12, COLOR_SUB, false), ui.matchWrap());
        header.addView(titleBox, ui.weightWrap(1));
        header.addView(realtimeChip(ui, signal.status, signal.level), ui.wrapWrap());
        card.addView(header, ui.matchWrap());

        if (linkedNewsEvent != null && linkedClassification != null) {
            card.addView(ui.spacer(ui.dp(8)));
            card.addView(linkedEventBox(ui, linkedNewsEvent, linkedClassification), ui.matchWrap());
        }

        card.addView(ui.spacer(ui.dp(10)));
        TextView summary = ui.text(signal.summary, 14, COLOR_TEXT, false);
        summary.setLineSpacing(ui.dp(3), 1.0f);
        summary.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));
        summary.setBackground(ui.rounded(realtimeSoftColor(signal.level), ui.dp(12)));
        card.addView(summary, ui.matchWrap());

        card.addView(ui.spacer(ui.dp(10)));
        LinearLayout freshness = ui.horizontal();
        freshness.setGravity(Gravity.CENTER_VERTICAL);
        freshness.addView(realtimeSmallChip(ui, signal.newsFreshness), ui.wrapWrap());
        freshness.addView(ui.spacer(ui.dp(6), 1));
        freshness.addView(realtimeSmallChip(ui, signal.opinionFreshness), ui.wrapWrap());
        freshness.addView(ui.spacer(ui.dp(6), 1));
        freshness.addView(realtimeSmallChip(ui, signal.aiFreshness), ui.wrapWrap());
        card.addView(freshness, ui.matchWrap());

        card.addView(ui.spacer(ui.dp(10)));
        card.addView(realtimeLine(ui, "市场环境", signal.marketStatus + " · " + signal.marketSummary), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(6)));
        card.addView(realtimeLine(ui, "盘中状态", signal.intradayStatus + " · " + signal.intradaySummary), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(6)));
        card.addView(realtimeLine(ui, "板块联动", signal.sectorStatus + " · " + signal.sectorSummary), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(6)));
        card.addView(realtimeLine(ui, "事件强度", signal.newsStatus + " · " + signal.newsSummary), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(6)));
        card.addView(realtimeLine(ui, "事件细节", signal.newsDetail), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(6)));
        card.addView(realtimeLine(ui, "因子克制", signal.factorDiscipline), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(6)));
        card.addView(realtimeLine(ui, "风险触发", signal.riskText), ui.matchWrap());
        return card;
    }

    private static View linkedEventBox(MainUiKit ui, News linkedNewsEvent,
                                       RealtimeDecisionAnalyzer.NewsValClassification linkedClassification) {
        LinearLayout linkedBox = ui.vertical();
        linkedBox.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        linkedBox.setBackground(ui.rounded(Color.rgb(254, 243, 199), ui.dp(8)));

        LinearLayout linkedTitleRow = ui.horizontal();
        linkedTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        linkedTitleRow.addView(ui.text("🔗 关联触发事件", 12, Color.rgb(180, 83, 9), true), ui.weightWrap(1));
        linkedTitleRow.addView(realtimeChip(ui, linkedClassification.category, linkedClassification.level), ui.wrapWrap());
        linkedBox.addView(linkedTitleRow, ui.matchWrap());
        linkedBox.addView(ui.spacer(ui.dp(4)));

        TextView eventTitleText = ui.text(linkedNewsEvent.title, 13, COLOR_TEXT, true);
        eventTitleText.setLineSpacing(ui.dp(2), 1.0f);
        linkedBox.addView(eventTitleText, ui.matchWrap());

        String explanation = linkedClassification.relationType;
        if (linkedClassification.matchedKeyword.length() > 0) {
            explanation = explanation + " · " + linkedClassification.matchedKeyword;
        }
        linkedBox.addView(ui.spacer(ui.dp(2)));
        linkedBox.addView(ui.text(explanation + " (" + linkedNewsEvent.source + ")",
                11, COLOR_SUB, false), ui.matchWrap());
        return linkedBox;
    }

    private static View realtimeLine(MainUiKit ui, String label, String value) {
        LinearLayout row = ui.horizontal();
        row.setGravity(Gravity.TOP);
        TextView labelView = ui.text(label, 12, COLOR_SUB, true);
        row.addView(labelView, new LinearLayout.LayoutParams(ui.dp(66), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView valueView = ui.text(value, 12, COLOR_TEXT, false);
        valueView.setLineSpacing(ui.dp(2), 1.0f);
        row.addView(valueView, ui.weightWrap(1));
        return row;
    }

    private static TextView realtimeChip(MainUiKit ui, String value, int level) {
        return ui.tag(value, realtimeSoftColor(level), realtimeTextColor(level));
    }

    private static TextView realtimeSmallChip(MainUiKit ui, String value) {
        TextView chip = ui.text(value, 11, COLOR_SUB, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4));
        chip.setBackground(ui.rounded(Color.rgb(248, 250, 252), ui.dp(12)));
        return chip;
    }

    private static int realtimeSoftColor(int level) {
        if (level == RealtimeDecisionAnalyzer.LEVEL_GOOD) {
            return Color.rgb(254, 242, 242);
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_RISK) {
            return Color.rgb(240, 253, 244);
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_WAIT) {
            return Color.rgb(255, 247, 237);
        }
        return COLOR_ACCENT_SOFT;
    }

    private static int realtimeTextColor(int level) {
        if (level == RealtimeDecisionAnalyzer.LEVEL_GOOD) {
            return Color.rgb(217, 45, 32);
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_RISK) {
            return Color.rgb(7, 148, 85);
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_WAIT) {
            return Color.rgb(180, 83, 9);
        }
        return COLOR_ACCENT;
    }
}
