package com.face.mymoney.ui.detail;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.face.mymoney.R;
import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.Stock;
import com.face.mymoney.ratio.WinLossRatioCalculator;
import com.face.mymoney.ratio.WinLossRatioInput;
import com.face.mymoney.ratio.WinLossRatioResult;
import com.face.mymoney.ui.ResponsiveMetrics;
import com.face.mymoney.ui.widget.PieRatioView;

import java.util.ArrayList;

public class WinLossRatioCard {
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_LINE = Color.rgb(226, 232, 240);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(234, 241, 255);
    private static final int COLOR_AI_SOFT = Color.rgb(240, 253, 244);
    private static final int COLOR_OPPORTUNITY = Color.rgb(217, 45, 32);
    private static final int COLOR_RISK = Color.rgb(7, 148, 85);

    public static View create(Context context, Stock stock, ArrayList<DecisionNote> notes) {
        return create(context, stock, notes, null, false, null);
    }

    public static View create(Context context, Stock stock, ArrayList<DecisionNote> notes,
                              DeepSeekAnalysisResult aiReference, boolean loadingAiReference,
                              View.OnClickListener aiClickListener) {
        WinLossRatioResult data = new WinLossRatioCalculator()
                .calculate(new WinLossRatioInput(stock, notes, aiReference));
        applyAdvice(context, data);

        LinearLayout card = vertical(context);
        card.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16));
        card.setBackground(rounded(COLOR_CARD, dp(context, 18)));

        card.addView(text(context, context.getString(R.string.win_loss_title), 20, COLOR_TEXT, true), matchWrap());
        card.addView(spacer(context, 4));
        TextView subtitle = text(context, context.getString(R.string.win_loss_subtitle), 12, COLOR_SUB, false);
        subtitle.setLineSpacing(dp(context, 2), 1.0f);
        card.addView(subtitle, matchWrap());
        card.addView(spacer(context, 14));

        LinearLayout body = horizontal(context);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info = vertical(context);
        if (data.hasDisplayRatio()) {
            PieRatioView pie = new PieRatioView(context);
            pie.setRatio(data.opportunityRatio, COLOR_OPPORTUNITY, COLOR_RISK, COLOR_CARD);
            if (aiClickListener != null) {
                pie.setOnClickListener(aiClickListener);
                pie.setClickable(true);
            }
            body.addView(pie, new LinearLayout.LayoutParams(dp(context, 116), dp(context, 116)));
            body.addView(spacer(context, 16, 1));

            info.addView(text(context, context.getString(R.string.win_loss_ratio_format, data.ratioText), 24, COLOR_TEXT, true), matchWrap());
            info.addView(spacer(context, 4));
            TextView explanation = text(context, data.ratioExplanation, 12, COLOR_SUB, false);
            explanation.setLineSpacing(dp(context, 2), 1.0f);
            info.addView(explanation, matchWrap());
            info.addView(spacer(context, 8));
            info.addView(legend(context, context.getString(R.string.win_loss_opportunity_format, data.opportunityPercent), COLOR_OPPORTUNITY), matchWrap());
            info.addView(spacer(context, 6));
            info.addView(legend(context, context.getString(R.string.win_loss_risk_format, data.riskPercent), COLOR_RISK), matchWrap());
            if (aiClickListener != null) {
                info.setOnClickListener(aiClickListener);
                info.setClickable(true);
            }
        } else {
            info.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
            info.setBackground(rounded(COLOR_ACCENT_SOFT, dp(context, 12)));
            info.addView(text(context, "AI 分项分析待生成", 18, COLOR_TEXT, true), matchWrap());
            info.addView(spacer(context, 6));
            TextView waiting = text(context, loadingAiReference
                    ? "正在结合新闻、市场观点和个股信息生成分项评分，完成后参与本地胜负比合成。"
                    : "没有有效目标价/止损价或 AI 分项评分时不显示默认 50%，点击卡片可查看或重新触发分析。", 13, COLOR_SUB, false);
            waiting.setLineSpacing(dp(context, 3), 1.0f);
            info.addView(waiting, matchWrap());
            if (aiClickListener != null) {
                info.setOnClickListener(aiClickListener);
                info.setClickable(true);
            }
        }
        body.addView(info, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        card.addView(body, matchWrap());
        card.addView(spacer(context, 14));

        TextView advice = text(context, data.advice, 14, COLOR_TEXT, false);
        advice.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
        advice.setLineSpacing(dp(context, 3), 1.0f);
        advice.setBackground(rounded(COLOR_ACCENT_SOFT, dp(context, 12)));
        card.addView(advice, matchWrap());

        if (data.hasValidPlan) {
            card.addView(spacer(context, 10));
            card.addView(text(context, context.getString(R.string.win_loss_price_format,
                    stock.price, data.targetPrice, data.stopLossPrice), 12, COLOR_SUB, false), matchWrap());
        }
        String aiText = aiReference == null ? "" : safeText(aiReference.displayText());
        if (loadingAiReference || aiText.length() > 0) {
            card.addView(spacer(context, 12));
            LinearLayout aiBox = vertical(context);
            aiBox.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
            aiBox.setBackground(rounded(COLOR_AI_SOFT, dp(context, 12)));
            aiBox.addView(text(context, "DeepSeek 分项分析", 13, COLOR_TEXT, true), matchWrap());
            aiBox.addView(spacer(context, 5));
            TextView aiContent = text(context, loadingAiReference
                    ? "正在生成信息一致性、新闻情绪等 AI 分项评分..."
                    : aiText, 13, COLOR_TEXT, false);
            aiContent.setLineSpacing(dp(context, 3), 1.0f);
            aiBox.addView(aiContent, matchWrap());
            if (aiClickListener != null && !loadingAiReference && aiText.length() > 0) {
                aiBox.setOnClickListener(aiClickListener);
                aiBox.setClickable(true);
            }
            card.addView(aiBox, matchWrap());
        }
        card.addView(spacer(context, 10));
        TextView opportunityHint = text(context, context.getString(R.string.win_loss_opportunity_hint), 12, COLOR_SUB, false);
        opportunityHint.setPadding(dp(context, 12), dp(context, 9), dp(context, 12), dp(context, 9));
        opportunityHint.setLineSpacing(dp(context, 2), 1.0f);
        opportunityHint.setBackground(rounded(Color.rgb(255, 247, 237), dp(context, 12)));
        card.addView(opportunityHint, matchWrap());
        return card;
    }

    private static void applyAdvice(Context context, WinLossRatioResult data) {
        if (!data.hasDisplayRatio()) {
            return;
        }
        if (data.adviceType == 2) {
            data.advice = context.getString(R.string.win_loss_good);
        } else if (data.adviceType == 1) {
            data.advice = context.getString(R.string.win_loss_normal);
        } else {
            data.advice = context.getString(R.string.win_loss_weak);
        }
    }

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static LinearLayout legend(Context context, String label, int color) {
        LinearLayout row = horizontal(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = new TextView(context);
        dot.setBackground(rounded(color, dp(context, 5)));
        row.addView(dot, new LinearLayout.LayoutParams(dp(context, 10), dp(context, 10)));
        row.addView(spacer(context, 8, 1));
        row.addView(text(context, label, 13, COLOR_SUB, false), matchWrap());
        return row;
    }

    private static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    private static TextView text(Context context, String value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, ResponsiveMetrics.sp(context, sp));
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private static View spacer(Context context, int height) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(context, height)));
        return view;
    }

    private static View spacer(Context context, int width, int height) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(context, width), dp(context, height)));
        return view;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        drawable.setStroke(1, COLOR_LINE);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return ResponsiveMetrics.dp(context, value);
    }
}
