package com.face.mymoney.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainUiKit {
    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_LINE = Color.rgb(226, 232, 240);
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235);

    private final Context context;

    /**
     * 构造方法：创建 MainUiKit 实例。
     */
    public MainUiKit(Context context) {
        this.context = context;
    }

    /**
     * 创建垂直布局。
     */
    public LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    /**
     * 创建水平布局。
     */
    public LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
    }

    /**
     * 创建卡片布局。
     */
    public LinearLayout card() {
        LinearLayout layout = vertical();
        layout.setPadding(dp(16), dp(16), dp(16), dp(16));
        layout.setBackground(rounded(COLOR_CARD, dp(18)));
        return layout;
    }

    /**
     * 创建文本控件。
     */
    public TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, ResponsiveMetrics.sp(context, sp));
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    /**
     * 创建标签控件。
     */
    public TextView tag(String value, int bgColor, int textColor) {
        TextView view = text(value, 12, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setBackground(rounded(bgColor, dp(18)));
        return view;
    }

    /**
     * 创建输入框控件。
     */
    public EditText input(String hint) {
        EditText editText = new EditText(context);
        editText.setHint(hint);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, ResponsiveMetrics.sp(context, 15));
        editText.setSingleLine(false);
        editText.setPadding(dp(14), 0, dp(14), 0);
        editText.setTextColor(COLOR_TEXT);
        editText.setHintTextColor(COLOR_SUB);
        editText.setBackground(roundedStroke(Color.WHITE, dp(12), COLOR_LINE));
        return editText;
    }

    /**
     * 主要按钮。
     */
    public Button primaryButton(String text) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, ResponsiveMetrics.sp(context, 14));
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(rounded(COLOR_ACCENT, dp(14)));
        return button;
    }

    /**
     * 幽灵风格按钮。
     */
    public Button ghostButton(String text) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, ResponsiveMetrics.sp(context, 14));
        button.setTextColor(COLOR_TEXT);
        button.setBackground(roundedStroke(Color.WHITE, dp(14), COLOR_LINE));
        return button;
    }

    /**
     * 创建圆角背景。
     */
    public GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    /**
     * 创建圆角背景描边。
     */
    public GradientDrawable roundedStroke(int color, int radius, int strokeColor) {
        GradientDrawable drawable = rounded(color, radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    /**
     * 创建间距控件。
     */
    public View spacer(int height) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, height));
        return view;
    }

    /**
     * 创建间距控件。
     */
    public View spacer(int width, int height) {
        View view = new View(context);
        view.setLayoutParams(new LinearLayout.LayoutParams(width, height));
        return view;
    }

    /**
     * 创建权重间距权重。
     */
    public View spaceWeight() {
        return new View(context);
    }

    /**
     * 填充包裹。
     */
    public LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 填充高度。
     */
    public LinearLayout.LayoutParams matchHeight(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    /**
     * 包裹高度。
     */
    public LinearLayout.LayoutParams wrapHeight(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
    }

    /**
     * 包裹包裹。
     */
    public LinearLayout.LayoutParams wrapWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 权重包裹。
     */
    public LinearLayout.LayoutParams weightWrap(float weight) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
    }

    /**
     * 填充填充。
     */
    public FrameLayout.LayoutParams matchMatch() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /**
     * 页面布局参数。
     */
    public FrameLayout.LayoutParams pageParams(boolean detailPage) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int maxWidth = detailPage ? ResponsiveMetrics.maxDetailPageWidth(context) : ResponsiveMetrics.maxPageWidth(context);
        int width = screenWidth > maxWidth ? maxWidth : ViewGroup.LayoutParams.MATCH_PARENT;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    /**
     * 将dp值转换为像素值。
     */
    public int dp(int value) {
        return ResponsiveMetrics.dp(context, value);
    }

    /**
     * 隐藏键盘。
     */
    public void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
