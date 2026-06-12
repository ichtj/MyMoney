package com.face.mymoney.ui.home;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;

import com.face.mymoney.R;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.MarketIndexQuote;
import com.face.mymoney.model.Stock;
import com.face.mymoney.realtime.RealtimeDecisionAnalyzer;
import com.face.mymoney.ui.MainUiKit;
import com.face.mymoney.ui.StockDisplayText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class HomePageBuilder {
    private static final String BOARD_THEME_TAG = "MyMoneyBoardTheme";

    public interface Listener {
        /**
         * 当添加股票时的回调处理。
         */
        void onAddStock();

        /**
         * 当添加分组时的回调处理。
         */
        void onAddGroup();

        /**
         * 当分组选中的时的回调处理。
         */
        void onGroupSelected(String group);

        /**
         * 当股票选中的时的回调处理。
         */
        void onStockSelected(Stock stock);

        /**
         * 当edit股票时的回调处理。
         */
        void onEditStock(Stock stock);

        /**
         * 当删除股票时的回调处理。
         */
        void onDeleteStock(Stock stock);

        /**
         * 当移动股票置顶时的回调处理。
         */
        void onMoveStockTop(Stock stock);

        /**
         * 当移动股票置底时的回调处理。
         */
        void onMoveStockBottom(Stock stock);

        void onManageModeChanged(boolean enabled);

        void onManageStockToggled(Stock stock);

        void onManageSelectAll();

        void onManageClearSelection();

        void onManageDeleteSelected();

        void onManageMoveSelected();

        /**
         * 当调试requested时的回调处理。
         */
        void onDebugRequested();
    }

    private static final int COLOR_CARD = Color.WHITE;
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(234, 241, 255);
    private static final int COLOR_RISE = Color.rgb(217, 45, 32);
    private static final int COLOR_FALL = Color.rgb(7, 148, 85);
    private static final int COLOR_NEUTRAL = Color.rgb(245, 158, 11);
    private static final int ACTION_WIDTH_DP = 124;
    private static final int ACTION_TRIGGER_DP = 58;

    private final Context context;
    private final MainUiKit ui;
    private final Listener listener;
    private final String userName;
    private final ArrayList<Stock> stocks;
    private final ArrayList<Stock> displayStocks;
    private final ArrayList<DecisionNote> notes;
    private final HashMap<String, Integer> aiOpportunityPercents;
    private final ArrayList<MarketIndexQuote> marketIndices;
    private final ArrayList<String> groups;
    private final String selectedGroup;
    private final int riskStockCount;
    private final boolean manageMode;
    private final HashSet<String> managedStockCodes;
    private final int touchSlop;
    private LinearLayout openActionRow;

    public HomePageBuilder(Context context, MainUiKit ui, Listener listener, String userName,
                           ArrayList<Stock> stocks, ArrayList<Stock> displayStocks,
                           ArrayList<DecisionNote> notes, HashMap<String, Integer> aiOpportunityPercents,
                           ArrayList<MarketIndexQuote> marketIndices,
                           ArrayList<String> groups,
                           String selectedGroup, int riskStockCount,
                           boolean manageMode, HashSet<String> managedStockCodes) {
        this.context = context;
        this.ui = ui;
        this.listener = listener;
        this.userName = userName;
        this.stocks = stocks;
        this.displayStocks = displayStocks;
        this.notes = notes;
        this.aiOpportunityPercents = aiOpportunityPercents == null ? new HashMap<String, Integer>() : aiOpportunityPercents;
        this.marketIndices = marketIndices;
        this.groups = groups;
        this.selectedGroup = selectedGroup;
        this.riskStockCount = riskStockCount;
        this.manageMode = manageMode;
        this.managedStockCodes = managedStockCodes == null ? new HashSet<String>() : managedStockCodes;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public ScrollView build() {
        ScrollView scrollView = new ScrollView(context);
        LinearLayout page = ui.vertical();
        page.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(18));
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
        TextView title = ui.text(context.getString(R.string.home_title), 24, COLOR_TEXT, true);
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
        page.addView(ui.spacer(ui.dp(8)));

        page.addView(buildSummaryCard(), ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));
        page.addView(buildGroupHeader(), ui.matchWrap());
        page.addView(ui.spacer(ui.dp(6)));
        page.addView(buildGroupBar(), ui.matchWrap());
        page.addView(ui.spacer(ui.dp(8)));

        LinearLayout row = ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (manageMode) {
            row.addView(ui.text("已选 " + countManagedDisplayStocks() + " 只", 18, COLOR_TEXT, true), ui.weightWrap(1));
            Button done = ui.ghostButton("完成");
            done.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onManageModeChanged(false);
                }
            });
            row.addView(done, ui.wrapHeight(ui.dp(36)));
        } else {
            row.addView(ui.text(context.getString(R.string.watchlist_title), 18, COLOR_TEXT, true), ui.weightWrap(1));
            Button manage = ui.ghostButton("管理");
            manage.setEnabled(displayStocks.size() > 0);
            manage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onManageModeChanged(true);
                }
            });
            row.addView(manage, ui.wrapHeight(ui.dp(36)));
            row.addView(ui.spacer(ui.dp(8), 1));
            Button add = ui.primaryButton(context.getString(R.string.add_button));
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onAddStock();
                }
            });
            row.addView(add, ui.wrapHeight(ui.dp(36)));
        }
        page.addView(row, ui.matchWrap());
        if (manageMode) {
            page.addView(ui.spacer(ui.dp(7)));
            page.addView(buildManageActionBar(), ui.matchWrap());
        }
        page.addView(ui.spacer(ui.dp(7)));

        if (displayStocks.size() == 0) {
            LinearLayout empty = ui.card();
            empty.addView(ui.text(context.getString(R.string.empty_stock_title), 18, COLOR_TEXT, true), ui.matchWrap());
            empty.addView(ui.spacer(ui.dp(8)));
            empty.addView(ui.text(context.getString(R.string.empty_stock_desc), 14, COLOR_SUB, false), ui.matchWrap());
            page.addView(empty, ui.matchWrap());
        } else {
            for (int i = 0; i < displayStocks.size(); i++) {
                page.addView(buildStockCard(displayStocks.get(i)), ui.matchWrap());
                page.addView(ui.spacer(ui.dp(7)));
            }
        }
        return scrollView;
    }

    private View buildManageActionBar() {
        LinearLayout bar = ui.horizontal();
        bar.setGravity(Gravity.CENTER_VERTICAL);

        final boolean allSelected = displayStocks.size() > 0
                && countManagedDisplayStocks() == displayStocks.size();
        Button select = ui.ghostButton(allSelected ? "清空" : "全选");
        select.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (allSelected) {
                    listener.onManageClearSelection();
                } else {
                    listener.onManageSelectAll();
                }
            }
        });
        bar.addView(select, new LinearLayout.LayoutParams(0, ui.dp(36), 1));
        bar.addView(ui.spacer(ui.dp(8), 1));

        Button move = ui.ghostButton("移动分组");
        move.setEnabled(countManagedDisplayStocks() > 0);
        move.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onManageMoveSelected();
            }
        });
        bar.addView(move, new LinearLayout.LayoutParams(0, ui.dp(36), 1));
        bar.addView(ui.spacer(ui.dp(8), 1));

        Button delete = ui.ghostButton(context.getString(R.string.delete));
        delete.setTextColor(COLOR_RISE);
        delete.setEnabled(countManagedDisplayStocks() > 0);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onManageDeleteSelected();
            }
        });
        bar.addView(delete, new LinearLayout.LayoutParams(0, ui.dp(36), 1));
        return bar;
    }

    private int countManagedDisplayStocks() {
        int count = 0;
        for (int i = 0; i < displayStocks.size(); i++) {
            Stock stock = displayStocks.get(i);
            if (stock != null && stock.code != null && managedStockCodes.contains(stock.code)) {
                count++;
            }
        }
        return count;
    }

    private View buildSummaryCard() {
        LinearLayout card = ui.card();
        card.setPadding(ui.dp(12), ui.dp(10), ui.dp(12), ui.dp(10));
        GradientDrawable bg = ui.rounded(COLOR_TEXT, ui.dp(18));
        card.setBackground(bg);

        TextView title = ui.text("大盘指数", 15, Color.WHITE, true);
        TextView sub = ui.text(context.getString(R.string.summary_format, stocks.size(), notes.size(), riskStockCount), 11, Color.rgb(204, 213, 225), false);
        card.addView(title, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(5)));
        card.addView(sub, ui.matchWrap());
        RealtimeDecisionAnalyzer.MarketPulse pulse = RealtimeDecisionAnalyzer.analyzeMarket(marketIndices);
        card.addView(ui.spacer(ui.dp(5)));
        TextView marketStatus = ui.text("即时环境：" + pulse.status + " · " + pulse.summary,
                12, marketStatusColor(pulse.level), true);
        marketStatus.setLineSpacing(ui.dp(2), 1.0f);
        card.addView(marketStatus, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(8)));

        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout metrics = ui.horizontal();
        if (marketIndices.size() == 0) {
            metrics.addView(metricBox("大盘", "加载中", "--"), ui.wrapHeight(ui.dp(50)));
        } else {
            for (int i = 0; i < marketIndices.size(); i++) {
                MarketIndexQuote quote = marketIndices.get(i);
                metrics.addView(metricBox(quote.name, quote.price, quote.changePercent), ui.wrapHeight(ui.dp(50)));
                metrics.addView(ui.spacer(ui.dp(5), ui.dp(1)));
            }
        }
        scroll.addView(metrics, ui.wrapWrap());
        card.addView(scroll, ui.matchWrap());
        return card;
    }

    private int marketStatusColor(int level) {
        if (level == RealtimeDecisionAnalyzer.LEVEL_GOOD) {
            return Color.rgb(255, 138, 128);
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_RISK) {
            return Color.rgb(96, 211, 148);
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_WAIT) {
            return Color.rgb(251, 191, 36);
        }
        return Color.rgb(203, 213, 225);
    }

    private View metricBox(String title, String value, String changePercent) {
        LinearLayout box = ui.vertical();
        box.setPadding(ui.dp(9), ui.dp(5), ui.dp(9), ui.dp(5));
        box.setMinimumWidth(ui.dp(78));
        box.setBackground(ui.rounded(Color.rgb(37, 47, 68), ui.dp(12)));
        box.addView(singleLineText(title, 10, Color.rgb(203, 213, 225), false), ui.matchWrap());
        box.addView(ui.spacer(ui.dp(1)));
        box.addView(singleLineText(value, 12, Color.WHITE, true), ui.matchWrap());
        int changeColor = changePercent.startsWith("-") ? COLOR_FALL : changePercent.startsWith("+") ? COLOR_RISE : Color.rgb(203, 213, 225);
        box.addView(singleLineText(changePercent, 11, changeColor, true), ui.matchWrap());
        return box;
    }

    private TextView singleLineText(String value, int sp, int color, boolean bold) {
        TextView view = ui.text(value, sp, color, bold);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private TextView actionButton(String label, int bgColor, int textColor) {
        TextView view = singleLineText(label, 13, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(ui.rounded(bgColor, ui.dp(10)));
        view.setClickable(true);
        view.setMinWidth(ui.dp(56));
        return view;
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
            chip.setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6));
            chip.setBackground(ui.rounded(selectedGroup.equals(group) ? COLOR_ACCENT : COLOR_CARD, ui.dp(24)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onGroupSelected(group);
                }
            });
            row.addView(chip, ui.wrapHeight(ui.dp(32)));
            row.addView(ui.spacer(ui.dp(6), ui.dp(1)));
        }

        scroll.addView(row, ui.wrapWrap());
        return scroll;
    }

    private View buildGroupHeader() {
        LinearLayout row = ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(ui.text(context.getString(R.string.group_section_title), 16, COLOR_TEXT, true), ui.weightWrap(1));
        Button addGroup = ui.ghostButton(context.getString(R.string.add_group_button));
        addGroup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onAddGroup();
            }
        });
        row.addView(addGroup, ui.wrapHeight(ui.dp(34)));
        return row;
    }

    private View buildStockCard(final Stock stock) {
        final LinearLayout wrapper = ui.horizontal();
        wrapper.setGravity(Gravity.CENTER_VERTICAL);

        final LinearLayout card = ui.card();
        card.setClickable(true);
        if (manageMode) {
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onManageStockToggled(stock);
                }
            });
        } else {
            card.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private boolean swiping;
            private boolean longPressTriggered;
            private int startActionWidth;
            private final Runnable longPressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (!swiping) {
                        longPressTriggered = true;
                        closeOpenActions(null);
                        showMovePopup(card, stock);
                    }
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                LinearLayout actionRow = (LinearLayout) wrapper.getChildAt(1);
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = event.getX();
                    downY = event.getY();
                    swiping = false;
                    longPressTriggered = false;
                    startActionWidth = actionRow.getLayoutParams().width;
                    if (openActionRow != null && openActionRow != actionRow) {
                        closeOpenActions(actionRow);
                    }
                    v.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                        v.removeCallbacks(longPressRunnable);
                    }
                    if (!swiping && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        swiping = true;
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (swiping) {
                        int targetWidth = startActionWidth + (int) (-dx);
                        setActionWidth(actionRow, clampActionWidth(targetWidth));
                        return true;
                    }
                }
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    v.removeCallbacks(longPressRunnable);
                    float dx = event.getX() - downX;
                    float dy = event.getY() - downY;
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    if (longPressTriggered) {
                        return true;
                    }
                    if (swiping) {
                        int width = actionRow.getLayoutParams().width;
                        if (width >= ui.dp(ACTION_TRIGGER_DP)) {
                            openActions(wrapper);
                        } else {
                            closeOpenActions(null);
                        }
                        return true;
                    }
                    if (openActionRow == actionRow) {
                        closeOpenActions(null);
                        return true;
                    }
                    if (Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop && event.getAction() == MotionEvent.ACTION_UP) {
                        listener.onStockSelected(stock);
                        return true;
                    }
                }
                return true;
            }
        });
        }

        card.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));

        LinearLayout row = ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout nameBox = ui.vertical();
        LinearLayout titleRow = ui.horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(singleLineText(stock.name, 16, COLOR_TEXT, true),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        View opportunityBadge = opportunityBadge(stock);
        if (opportunityBadge != null) {
            titleRow.addView(ui.spacer(ui.dp(6), 1));
            titleRow.addView(opportunityBadge, new LinearLayout.LayoutParams(ui.dp(42), ui.dp(42)));
        }
        titleRow.addView(ui.spacer(ui.dp(6), 1));
        titleRow.addView(boardThemeTag(stock), ui.wrapWrap());
        nameBox.addView(titleRow, ui.matchWrap());
        String meta = stock.code + " · " + stock.market + " · " + stock.groupName
                + " · " + getNotes(stock.code).size() + "记";
        TextView metaView = singleLineText(meta, 11, COLOR_SUB, false);
        nameBox.addView(metaView, ui.matchWrap());
        RealtimeDecisionAnalyzer.IntradayState intraday = RealtimeDecisionAnalyzer.analyzeStockIntraday(stock);
        LinearLayout intradayRow = ui.horizontal();
        intradayRow.setGravity(Gravity.CENTER_VERTICAL);
        intradayRow.addView(intradayChip(intraday.status, intraday.level), ui.wrapWrap());
        intradayRow.addView(ui.spacer(ui.dp(6), 1));
        intradayRow.addView(singleLineText(intraday.summary, 11, COLOR_SUB, false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        nameBox.addView(ui.spacer(ui.dp(3)));
        nameBox.addView(intradayRow, ui.matchWrap());
        row.addView(nameBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout quoteBox = ui.vertical();
        quoteBox.setGravity(Gravity.RIGHT);
        TextView price = singleLineText(stock.price, 16, COLOR_TEXT, true);
        price.setGravity(Gravity.RIGHT);
        quoteBox.addView(price, ui.matchWrap());
        int changeColor = stock.changePercent.startsWith("-") ? COLOR_FALL : COLOR_RISE;
        TextView change = singleLineText(stock.changePercent, 13, changeColor, true);
        change.setGravity(Gravity.RIGHT);
        quoteBox.addView(change, ui.matchWrap());
        row.addView(quoteBox, new LinearLayout.LayoutParams(ui.dp(88), LinearLayout.LayoutParams.WRAP_CONTENT));

        card.addView(row, ui.matchWrap());
        if (manageMode) {
            wrapper.addView(manageCheckView(stock), new LinearLayout.LayoutParams(ui.dp(34), ui.dp(34)));
            wrapper.addView(ui.spacer(ui.dp(8), 1));
        }
        wrapper.addView(card, ui.weightWrap(1));
        if (!manageMode) {
            wrapper.addView(actionPanel(stock), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT));
        }
        return wrapper;
    }

    private TextView manageCheckView(final Stock stock) {
        final boolean selected = stock != null && stock.code != null && managedStockCodes.contains(stock.code);
        TextView check = singleLineText(selected ? "✓" : "", 16, selected ? Color.WHITE : COLOR_SUB, true);
        check.setGravity(Gravity.CENTER);
        check.setBackground(selected ? circleFill(COLOR_ACCENT) : circleStroke(Color.rgb(203, 213, 225)));
        check.setClickable(true);
        check.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onManageStockToggled(stock);
            }
        });
        return check;
    }

    private TextView intradayChip(String value, int level) {
        TextView chip = singleLineText(value, 10, intradayTextColor(level), true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(ui.dp(7), ui.dp(3), ui.dp(7), ui.dp(3));
        chip.setBackground(ui.rounded(intradaySoftColor(level), ui.dp(10)));
        return chip;
    }

    private int intradaySoftColor(int level) {
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

    private int intradayTextColor(int level) {
        if (level == RealtimeDecisionAnalyzer.LEVEL_GOOD) {
            return COLOR_RISE;
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_RISK) {
            return COLOR_FALL;
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_WAIT) {
            return COLOR_NEUTRAL;
        }
        return COLOR_ACCENT;
    }

    private TextView boardThemeTag(Stock stock) {
        String board = StockDisplayText.board(context, stock);
        Log.d(BOARD_THEME_TAG, "watchlist render boardTag code=" + stock.code
                + ", name=" + stock.name
                + ", rawIndustry=" + stock.industry
                + ", renderedBoard=" + board
                + ", " + StockDisplayText.debugSummary(context, stock, 14));
        return ui.tag(/*context.getString(R.string.stock_board) + " " + */board,
                COLOR_ACCENT_SOFT, COLOR_ACCENT);
    }

    private View opportunityBadge(Stock stock) {
        Integer percent = aiOpportunityPercents.get(stock.code);
        if (percent == null || percent.intValue() < 0 || percent.intValue() > 100) {
            return null;
        }
        int color = opportunityColor(percent.intValue());
        TextView badge = singleLineText(percent.intValue() + "%", 12, color, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(circleStroke(color));
        return badge;
    }

    private int opportunityColor(int percent) {
        if (percent > 50) {
            return COLOR_RISE;
        }
        if (percent < 50) {
            return COLOR_FALL;
        }
        return COLOR_NEUTRAL;
    }

    private GradientDrawable circleStroke(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(Color.WHITE);
        drawable.setStroke(ui.dp(2), color);
        return drawable;
    }

    private GradientDrawable circleFill(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private void showMovePopup(View anchor, final Stock stock) {
        PopupMenu menu = new PopupMenu(context, anchor);
        menu.getMenu().add("置顶");
        menu.getMenu().add("置底");
        menu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                String title = String.valueOf(item.getTitle());
                if ("置顶".equals(title)) {
                    listener.onMoveStockTop(stock);
                    return true;
                }
                if ("置底".equals(title)) {
                    listener.onMoveStockBottom(stock);
                    return true;
                }
                return false;
            }
        });
        menu.show();
    }

    private View actionPanel(final Stock stock) {
        LinearLayout actions = ui.horizontal();
        actions.setGravity(Gravity.CENTER);
        actions.setPadding(ui.dp(6), 0, 0, 0);
        actions.setMinimumHeight(ui.dp(58));

        TextView edit = actionButton(context.getString(R.string.edit), COLOR_ACCENT, Color.WHITE);
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOpenActions(null);
                listener.onEditStock(stock);
            }
        });

        TextView delete = actionButton(context.getString(R.string.delete), Color.rgb(217, 45, 32), Color.WHITE);
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeOpenActions(null);
                listener.onDeleteStock(stock);
            }
        });
        actions.addView(edit, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        actions.addView(ui.spacer(ui.dp(4), 1));
        actions.addView(delete, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1));
        return actions;
    }

    private void openActions(LinearLayout wrapper) {
        LinearLayout actionRow = (LinearLayout) wrapper.getChildAt(1);
        if (openActionRow != null && openActionRow != actionRow) {
            setActionWidth(openActionRow, 0);
        }
        setActionWidth(actionRow, ui.dp(ACTION_WIDTH_DP));
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

    private int clampActionWidth(int width) {
        return Math.max(0, Math.min(ui.dp(ACTION_WIDTH_DP), width));
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
