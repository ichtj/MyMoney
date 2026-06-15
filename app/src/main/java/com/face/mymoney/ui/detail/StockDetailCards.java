package com.face.mymoney.ui.detail;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.face.mymoney.R;
import com.face.mymoney.model.Stock;
import com.face.mymoney.ui.MainUiKit;
import com.face.mymoney.ui.StockDisplayText;

public class StockDetailCards {
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);

    private StockDetailCards() {
    }

    public static View hero(Context context, MainUiKit ui, Stock stock) {
        LinearLayout hero = ui.card();
        hero.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        hero.setBackground(ui.rounded(COLOR_TEXT, ui.dp(16)));

        LinearLayout top = ui.horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = ui.vertical();
        titleBox.addView(singleLineText(ui, stock.name, 22, Color.WHITE, true), ui.matchWrap());
        titleBox.addView(ui.spacer(ui.dp(3)));
        titleBox.addView(singleLineText(ui, stock.code + " · " + stock.market + " · " + stock.groupName,
                12, Color.rgb(203, 213, 225), false), ui.matchWrap());
        top.addView(titleBox, ui.weightWrap(1));

        LinearLayout priceBox = ui.vertical();
        priceBox.setGravity(Gravity.RIGHT);
        TextView price = singleLineText(ui, stock.price, 22, Color.WHITE, true);
        price.setGravity(Gravity.RIGHT);
        priceBox.addView(price, ui.matchWrap());
        int changeColor = stock.changePercent.startsWith("-") ? Color.rgb(96, 211, 148) : Color.rgb(255, 138, 128);
        TextView change = singleLineText(ui, stock.changePercent, 14, changeColor, true);
        change.setGravity(Gravity.RIGHT);
        priceBox.addView(change, ui.matchWrap());
        top.addView(priceBox, new LinearLayout.LayoutParams(ui.dp(110), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        hero.addView(top, ui.matchWrap());

        hero.addView(ui.spacer(ui.dp(8)));
        LinearLayout sub = ui.horizontal();
        sub.addView(ui.text(context.getString(R.string.quote_turnover) + " " + stock.turnover,
                12, Color.rgb(203, 213, 225), false), ui.weightWrap(1));
        sub.addView(ui.text(StockDisplayText.board(context, stock),
                12, Color.rgb(203, 213, 225), false), ui.wrapWrap());
        hero.addView(sub, ui.matchWrap());
        return hero;
    }

    public static View company(Context context, MainUiKit ui, Stock stock) {
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));

        LinearLayout row1 = ui.horizontal();
        row1.addView(compactInfo(ui, context.getString(R.string.market_value), stock.marketValue), ui.weightWrap(1));
        row1.addView(ui.spacer(ui.dp(8), 1));
        row1.addView(compactInfo(ui, context.getString(R.string.pe_label), stock.pe), ui.weightWrap(1));
        card.addView(row1, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(8)));

        LinearLayout row2 = ui.horizontal();
        row2.addView(compactInfo(ui, context.getString(R.string.revenue_profit),
                stock.revenue + " / " + stock.profit), ui.weightWrap(1));
        row2.addView(ui.spacer(ui.dp(8), 1));
        row2.addView(compactInfo(ui, context.getString(R.string.risk_tag), stock.riskTag), ui.weightWrap(1));
        card.addView(row2, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(8)));

        LinearLayout row3 = ui.horizontal();
        row3.addView(compactInfo(ui, context.getString(R.string.stock_board),
                StockDisplayText.board(context, stock)), ui.matchWrap());
        card.addView(row3, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(8)));
        card.addView(infoRow(ui, context.getString(R.string.main_business), stock.mainBusiness), ui.matchWrap());
        return card;
    }

    private static View compactInfo(MainUiKit ui, String label, String value) {
        LinearLayout box = ui.vertical();
        box.setPadding(ui.dp(10), ui.dp(8), ui.dp(10), ui.dp(8));
        box.setBackground(ui.rounded(Color.rgb(248, 250, 252), ui.dp(10)));
        box.addView(singleLineText(ui, label, 11, COLOR_SUB, false), ui.matchWrap());
        box.addView(ui.spacer(ui.dp(3)));
        box.addView(singleLineText(ui, value, 13, COLOR_TEXT, true), ui.matchWrap());
        return box;
    }

    private static View infoRow(MainUiKit ui, String label, String value) {
        LinearLayout row = ui.vertical();
        row.setPadding(0, ui.dp(7), 0, ui.dp(7));
        row.addView(ui.text(label, 12, COLOR_SUB, false), ui.matchWrap());
        row.addView(ui.spacer(ui.dp(2)));
        TextView valueView = ui.text(value, 15, COLOR_TEXT, false);
        valueView.setLineSpacing(ui.dp(2), 1.0f);
        row.addView(valueView, ui.matchWrap());
        return row;
    }

    private static TextView singleLineText(MainUiKit ui, String value, int sp, int color, boolean bold) {
        TextView view = ui.text(value, sp, color, bold);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }
}
