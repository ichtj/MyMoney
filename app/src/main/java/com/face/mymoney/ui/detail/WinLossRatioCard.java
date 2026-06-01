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
import com.face.mymoney.ui.ResponsiveMetrics;
import com.face.mymoney.ui.widget.PieRatioView;

import java.util.ArrayList;
import java.util.Locale;

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
        RatioData data = calculate(context, stock, notes);
        applyAiRatio(data, aiReference);

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

        PieRatioView pie = new PieRatioView(context);
        pie.setRatio(data.opportunityRatio, COLOR_OPPORTUNITY, COLOR_RISK, COLOR_CARD);
        if (aiClickListener != null) {
            pie.setOnClickListener(aiClickListener);
            pie.setClickable(true);
        }
        body.addView(pie, new LinearLayout.LayoutParams(dp(context, 116), dp(context, 116)));
        body.addView(spacer(context, 16, 1));

        LinearLayout info = vertical(context);
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
            card.addView(text(context, context.getString(R.string.win_loss_price_format, stock.price, data.targetPrice, data.stopLossPrice), 12, COLOR_SUB, false), matchWrap());
        }
        String aiText = aiReference == null ? "" : safeText(aiReference.displayText());
        if (loadingAiReference || aiText.length() > 0) {
            card.addView(spacer(context, 12));
            LinearLayout aiBox = vertical(context);
            aiBox.setPadding(dp(context, 12), dp(context, 10), dp(context, 12), dp(context, 10));
            aiBox.setBackground(rounded(COLOR_AI_SOFT, dp(context, 12)));
            aiBox.addView(text(context, aiReference != null && aiReference.hasRatio ? "DeepSeek 胜负比参考" : "DeepSeek 信息分析", 13, COLOR_TEXT, true), matchWrap());
            aiBox.addView(spacer(context, 5));
            TextView aiContent = text(context, loadingAiReference ? "正在结合爬虫新闻、市场观点和个人记录生成胜负比参考..." : aiText, 13, COLOR_TEXT, false);
            aiContent.setLineSpacing(dp(context, 3), 1.0f);
            aiBox.addView(aiContent, matchWrap());
            if (aiClickListener != null && !loadingAiReference && aiText.length() > 0) {
                aiBox.setOnClickListener(aiClickListener);
                aiBox.setClickable(true);
            }
            card.addView(aiBox, matchWrap());
        }
        return card;
    }

    private static RatioData calculate(Context context, Stock stock, ArrayList<DecisionNote> notes) {
        RatioData data = new RatioData();
        data.opportunityRatio = 0.5f;
        data.opportunityPercent = 50;
        data.riskPercent = 50;
        data.ratioText = "--";
        data.ratioExplanation = "需要目标价、止损价或通过校验的 AI 比例后才能解释胜负比。";
        data.advice = context.getString(R.string.win_loss_need_plan);

        double current = parsePrice(stock.price);
        DecisionNote plan = findLatestPlan(notes);
        if (plan == null || current <= 0d) {
            return data;
        }

        double target = parsePrice(plan.targetPrice);
        double stopLoss = parsePrice(plan.stopLossPrice);
        data.targetPrice = plan.targetPrice;
        data.stopLossPrice = plan.stopLossPrice;
        if (target <= current || stopLoss <= 0d || stopLoss >= current) {
            return data;
        }

        double opportunity = target - current;
        double risk = current - stopLoss;
        double total = opportunity + risk;
        if (total <= 0d) {
            return data;
        }

        double ratio = opportunity / risk;
        data.hasValidPlan = true;
        data.opportunityRatio = (float) (opportunity / total);
        data.opportunityPercent = Math.round(data.opportunityRatio * 100f);
        data.riskPercent = 100 - data.opportunityPercent;
        data.ratioText = String.format(Locale.CHINA, "%.2f", ratio);
        data.ratioExplanation = "含义：每承担 1 份风险，对应 " + data.ratioText + " 份机会。";
        data.adviceType = ratio >= 2.0d ? 2 : ratio >= 1.2d ? 1 : 0;
        if (data.adviceType == 2) {
            data.advice = context.getString(R.string.win_loss_good);
        } else if (data.adviceType == 1) {
            data.advice = context.getString(R.string.win_loss_normal);
        } else {
            data.advice = context.getString(R.string.win_loss_weak);
        }
        return data;
    }

    private static void applyAiRatio(RatioData data, DeepSeekAnalysisResult aiReference) {
        if (aiReference == null || !aiReference.success || !aiReference.hasRatio) {
            return;
        }
        data.hasAiRatio = true;
        data.opportunityPercent = aiReference.opportunityPercent;
        data.riskPercent = aiReference.riskPercent;
        data.opportunityRatio = aiReference.opportunityPercent / 100f;
        data.ratioText = aiReference.ratioText;
        data.ratioExplanation = "AI含义：每承担 1 份风险，对应 " + data.ratioText + " 份机会。";
        data.advice = aiReference.summary;
    }

    private static DecisionNote findLatestPlan(ArrayList<DecisionNote> notes) {
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

    private static class RatioData {
        boolean hasValidPlan;
        float opportunityRatio;
        int opportunityPercent;
        int riskPercent;
        int adviceType;
        String ratioText;
        String ratioExplanation;
        String advice;
        String targetPrice = "--";
        String stopLossPrice = "--";
        boolean hasAiRatio;
    }
}
