package com.face.mymoney.ui.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.face.mymoney.R;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.Stock;
import com.face.mymoney.ui.MainUiKit;

import java.util.ArrayList;

public class HomePageBuilder {
    public interface Listener {
        void onAddStock();

        void onAddGroup();

        void onRefreshQuotes();

        void onGroupSelected(String group);

        void onStockSelected(Stock stock);

        void onEditStock(Stock stock);

        void onDeleteStock(Stock stock);

        void onDebugRequested();
    }

    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(234, 241, 255);
    private static final int COLOR_RISE = Color.rgb(217, 45, 32);
    private static final int COLOR_FALL = Color.rgb(7, 148, 85);

    private final Context context;
    private final MainUiKit ui;
    private final Listener listener;
    private final String userName;
    private final ArrayList<Stock> stocks;
    private final ArrayList<Stock> displayStocks;
    private final ArrayList<DecisionNote> notes;
    private final ArrayList<String> groups;
    private final String selectedGroup;
    private final int riskStockCount;
    private LinearLayout openActionRow;

    public HomePageBuilder(Context context, MainUiKit ui, Listener listener, String userName,
                           ArrayList<Stock> stocks, ArrayList<Stock> displayStocks,
                           ArrayList<DecisionNote> notes, ArrayList<String> groups,
                           String selectedGroup, int riskStockCount) {
        this.context = context;
        this.ui = ui;
        this.listener = listener;
        this.userName = userName;
        this.stocks = stocks;
        this.displayStocks = displayStocks;
        this.notes = notes;
        this.groups = groups;
        this.selectedGroup = selectedGroup;
        this.riskStockCount = riskStockCount;
    }

    public ScrollView build() {
        ScrollView scrollView = new ScrollView(context);
        LinearLayout page = ui.vertical();
        page.setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(24));
        page.setClickable(true);
        page.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOpenActions(null);
            }
        });
        scrollView.addView(page, ui.pageParams(false));

        LinearLayout header = ui.horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = ui.vertical();
        TextView title = ui.text(context.getString(R.string.home_title), 28, COLOR_TEXT, true);
        title.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onDebugRequested();
                return true;
            }
        });
        titleBox.addView(title, ui.matchWrap());
        titleBox.addView(ui.text(userName, 13, COLOR_SUB, false), ui.matchWrap());
        header.addView(titleBox, ui.weightWrap(1));
        page.addView(header, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(18)));

        page.addView(buildSummaryCard(), ui.matchWrap());
        page.addView(ui.spacer(ui.dp(18)));
        page.addView(buildGroupHeader(), ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));
        page.addView(buildGroupBar(), ui.matchWrap());
        page.addView(ui.spacer(ui.dp(14)));

        LinearLayout row = ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ui.text(context.getString(R.string.watchlist_title), 21, COLOR_TEXT, true), ui.weightWrap(1));
        Button refresh = ui.ghostButton(context.getString(R.string.refresh_quote_button));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onRefreshQuotes();
            }
        });
        row.addView(refresh, ui.wrapHeight(ui.dp(42)));
        row.addView(ui.spacer(ui.dp(8), ui.dp(1)));
        Button add = ui.primaryButton(context.getString(R.string.add_button));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onAddStock();
            }
        });
        row.addView(add, ui.wrapHeight(ui.dp(42)));
        page.addView(row, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));

        if (displayStocks.size() == 0) {
            LinearLayout empty = ui.card();
            empty.addView(ui.text(context.getString(R.string.empty_stock_title), 18, COLOR_TEXT, true), ui.matchWrap());
            empty.addView(ui.spacer(ui.dp(8)));
            empty.addView(ui.text(context.getString(R.string.empty_stock_desc), 14, COLOR_SUB, false), ui.matchWrap());
            page.addView(empty, ui.matchWrap());
        } else {
            for (int i = 0; i < displayStocks.size(); i++) {
                page.addView(buildStockCard(displayStocks.get(i)), ui.matchWrap());
                page.addView(ui.spacer(ui.dp(12)));
            }
        }
        return scrollView;
    }

    private View buildSummaryCard() {
        LinearLayout card = ui.card();
        GradientDrawable bg = ui.rounded(COLOR_TEXT, ui.dp(18));
        card.setBackground(bg);

        TextView title = ui.text(context.getString(R.string.summary_title), 18, Color.WHITE, true);
        TextView sub = ui.text(context.getString(R.string.summary_format, stocks.size(), notes.size(), riskStockCount), 13, Color.rgb(204, 213, 225), false);
        card.addView(title, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(8)));
        card.addView(sub, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(16)));

        LinearLayout metrics = ui.horizontal();
        metrics.addView(metricBox(context.getString(R.string.summary_news_title), context.getString(R.string.summary_news_value)), ui.weightWrap(1));
        metrics.addView(ui.spacer(ui.dp(10), ui.dp(1)));
        metrics.addView(metricBox(context.getString(R.string.summary_plan_title), context.getString(R.string.summary_plan_value)), ui.weightWrap(1));
        card.addView(metrics, ui.matchWrap());
        return card;
    }

    private View metricBox(String title, String value) {
        LinearLayout box = ui.vertical();
        box.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        box.setBackground(ui.rounded(Color.rgb(37, 47, 68), ui.dp(12)));
        box.addView(ui.text(title, 12, Color.rgb(203, 213, 225), false), ui.matchWrap());
        box.addView(ui.spacer(ui.dp(4)));
        box.addView(ui.text(value, 13, Color.WHITE, true), ui.matchWrap());
        return box;
    }

    private View buildGroupBar() {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = ui.horizontal();
        row.setPadding(0, 0, ui.dp(4), 0);

        for (int i = 0; i < groups.size(); i++) {
            final String group = groups.get(i);
            TextView chip = ui.text(group, 14, selectedGroup.equals(group) ? Color.WHITE : COLOR_TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(ui.dp(16), ui.dp(9), ui.dp(16), ui.dp(9));
            chip.setBackground(ui.rounded(selectedGroup.equals(group) ? COLOR_ACCENT : COLOR_CARD, ui.dp(24)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onGroupSelected(group);
                }
            });
            row.addView(chip, ui.wrapHeight(ui.dp(40)));
            row.addView(ui.spacer(ui.dp(8), ui.dp(1)));
        }

        scroll.addView(row, ui.wrapWrap());
        return scroll;
    }

    private View buildGroupHeader() {
        LinearLayout row = ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ui.text(context.getString(R.string.group_section_title), 18, COLOR_TEXT, true), ui.weightWrap(1));
        Button addGroup = ui.ghostButton(context.getString(R.string.add_group_button));
        addGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onAddGroup();
            }
        });
        row.addView(addGroup, ui.wrapHeight(ui.dp(40)));
        return row;
    }

    private View buildStockCard(final Stock stock) {
        final LinearLayout wrapper = ui.horizontal();
        wrapper.setGravity(Gravity.CENTER_VERTICAL);

        final LinearLayout card = ui.card();
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOpenActions(null);
                listener.onStockSelected(stock);
            }
        });
        card.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (dx < -ui.dp(44) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                        openActions(wrapper);
                        return true;
                    }
                    if (dx > ui.dp(30)) {
                        closeOpenActions(null);
                        return true;
                    }
                }
                return false;
            }
        });

        LinearLayout top = ui.horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout nameBox = ui.vertical();
        nameBox.addView(ui.text(stock.name, 19, COLOR_TEXT, true), ui.matchWrap());
        nameBox.addView(ui.text(context.getString(R.string.stock_meta_format, stock.code, stock.market, stock.industry), 12, COLOR_SUB, false), ui.matchWrap());
        top.addView(nameBox, ui.weightWrap(1));

        TextView change = ui.text(stock.changePercent, 17, stock.changePercent.startsWith("-") ? COLOR_FALL : COLOR_RISE, true);
        change.setGravity(Gravity.RIGHT);
        top.addView(change, ui.wrapWrap());
        card.addView(top, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(14)));

        LinearLayout bottom = ui.horizontal();
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.addView(ui.tag(stock.groupName, COLOR_ACCENT_SOFT, COLOR_ACCENT), ui.wrapWrap());
        bottom.addView(ui.spacer(ui.dp(8), ui.dp(1)));
        bottom.addView(ui.text(context.getString(R.string.latest_price_format, stock.price), 13, COLOR_SUB, false), ui.weightWrap(1));
        TextView noteCount = ui.text(context.getString(R.string.note_count_format, getNotes(stock.code).size()), 13, COLOR_SUB, false);
        bottom.addView(noteCount, ui.wrapWrap());
        card.addView(bottom, ui.matchWrap());

        if (stock.remark.length() > 0) {
            card.addView(ui.spacer(ui.dp(10)));
            TextView remark = ui.text(stock.remark, 14, COLOR_TEXT, false);
            remark.setLineSpacing(ui.dp(2), 1.0f);
            card.addView(remark, ui.matchWrap());
        }
        wrapper.addView(card, ui.weightWrap(1));
        wrapper.addView(actionPanel(stock), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT));
        return wrapper;
    }

    private View actionPanel(final Stock stock) {
        LinearLayout actions = ui.horizontal();
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(ui.dp(8), 0, 0, 0);
        actions.setMinimumHeight(ui.dp(92));
        Button edit = ui.ghostButton(context.getString(R.string.edit));
        edit.setMinWidth(ui.dp(54));
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOpenActions(null);
                listener.onEditStock(stock);
            }
        });
        Button delete = ui.ghostButton(context.getString(R.string.delete));
        delete.setMinWidth(ui.dp(54));
        delete.setTextColor(Color.rgb(217, 45, 32));
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOpenActions(null);
                listener.onDeleteStock(stock);
            }
        });
        actions.addView(edit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        actions.addView(ui.spacer(ui.dp(6), 1));
        actions.addView(delete, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        return actions;
    }

    private void openActions(LinearLayout wrapper) {
        LinearLayout actionRow = (LinearLayout) wrapper.getChildAt(1);
        if (openActionRow != null && openActionRow != actionRow) {
            setActionWidth(openActionRow, 0);
        }
        setActionWidth(actionRow, LinearLayout.LayoutParams.WRAP_CONTENT);
        openActionRow = actionRow;
    }

    private void closeOpenActions(LinearLayout except) {
        if (openActionRow != null && openActionRow != except) {
            setActionWidth(openActionRow, 0);
            openActionRow = null;
        }
    }

    private void setActionWidth(View view, int width) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) view.getLayoutParams();
        params.width = width;
        view.setLayoutParams(params);
    }

    private ArrayList<DecisionNote> getNotes(String stockCode) {
        ArrayList<DecisionNote> result = new ArrayList<DecisionNote>();
        for (int i = 0; i < notes.size(); i++) {
            if (stockCode.equals(notes.get(i).stockCode)) {
                result.add(notes.get(i));
            }
        }
        return result;
    }
}
