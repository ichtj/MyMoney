package com.face.mymoney.ui.detail;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.face.mymoney.model.KLineItem;
import com.face.mymoney.ui.MainUiKit;
import com.face.mymoney.ui.widget.KLineChartView;

import java.util.ArrayList;

public class StockKLineCard {
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);

    private StockKLineCard() {
    }

    public static View create(Context context, MainUiKit ui, ArrayList<KLineItem> items,
                              boolean loading, String errorMessage, String statusText,
                              View.OnClickListener refreshClickListener) {
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));

        LinearLayout header = ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = ui.vertical();
        titleBox.addView(ui.text("日 K 线", 18, COLOR_TEXT, true), ui.matchWrap());
        titleBox.addView(ui.text("近 120 个交易日，可横向滑动查看旧数据", 12, COLOR_SUB, false), ui.matchWrap());
        header.addView(titleBox, ui.weightWrap(1));

        Button refresh = ui.ghostButton(loading ? "加载中" : "刷新K线");
        refresh.setEnabled(!loading);
        refresh.setOnClickListener(refreshClickListener);
        header.addView(refresh, ui.wrapHeight(ui.dp(34)));
        card.addView(header, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(10)));

        KLineChartView chartView = new KLineChartView(context);
        chartView.setKLines(items);
        chartView.setLoading(loading);
        chartView.setErrorMessage(errorMessage);
        card.addView(chartView, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ui.dp(240)));

        card.addView(ui.spacer(ui.dp(6)));
        card.addView(ui.text(statusText, 11, COLOR_SUB, false), ui.matchWrap());
        return card;
    }
}
