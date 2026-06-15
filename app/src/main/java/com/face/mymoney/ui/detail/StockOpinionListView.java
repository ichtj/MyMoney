package com.face.mymoney.ui.detail;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.face.mymoney.R;
import com.face.mymoney.model.Stock;
import com.face.mymoney.opinion.Opinion;
import com.face.mymoney.ui.MainUiKit;

import java.util.ArrayList;

public class StockOpinionListView {
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(234, 241, 255);

    public interface Listener {
        void onSourceSelected(Stock stock, String source);

        void onOpinionSelected(Stock stock, Opinion opinion);
    }

    private StockOpinionListView() {
    }

    public static View create(final Context context, final MainUiKit ui, final Stock stock,
                              ArrayList<Opinion> opinions, boolean loading,
                              String selectedSource, final Listener listener) {
        LinearLayout list = ui.vertical();
        ArrayList<Opinion> safeOpinions = opinions == null ? new ArrayList<Opinion>() : opinions;

        if (loading) {
            LinearLayout loadingCard = ui.card();
            loadingCard.setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14));
            loadingCard.addView(ui.text(context.getString(R.string.opinion_loading_title),
                    16, COLOR_TEXT, true), ui.matchWrap());
            loadingCard.addView(ui.spacer(ui.dp(5)));
            loadingCard.addView(ui.text(context.getString(R.string.opinion_loading_desc),
                    12, COLOR_SUB, false), ui.matchWrap());
            list.addView(loadingCard, ui.matchWrap());
            list.addView(ui.spacer(ui.dp(10)));
        }

        ArrayList<String> sources = sources(safeOpinions);
        if (sources.size() > 0) {
            String safeSelected = selectedSource;
            if (safeSelected == null || !sources.contains(safeSelected)) {
                safeSelected = sources.get(0);
            }
            list.addView(sourceSwitchBar(context, ui, stock, sources, safeSelected, listener), ui.matchWrap());
            list.addView(ui.spacer(ui.dp(8)));

            ArrayList<Opinion> sourceOpinions = opinionsBySource(safeOpinions, safeSelected);
            list.addView(sourceHeader(ui, safeSelected, sourceOpinions.size()), ui.matchWrap());
            list.addView(ui.spacer(ui.dp(8)));

            int displayCount = Math.min(sourceOpinions.size(), 8);
            for (int i = 0; i < displayCount; i++) {
                list.addView(opinionRow(ui, stock, sourceOpinions.get(i), listener), ui.matchWrap());
                list.addView(ui.spacer(ui.dp(10)));
            }
            if (sourceOpinions.size() > displayCount) {
                list.addView(ui.text("仅显示前 " + displayCount + " 条，切换来源查看其他内容",
                        12, COLOR_SUB, false), ui.matchWrap());
                list.addView(ui.spacer(ui.dp(10)));
            }
        }
        return list;
    }

    public static ArrayList<String> sources(ArrayList<Opinion> opinions) {
        ArrayList<String> sources = new ArrayList<String>();
        if (opinions == null) {
            return sources;
        }
        for (int i = 0; i < opinions.size(); i++) {
            String source = opinions.get(i).source;
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
        return sources;
    }

    private static View sourceSwitchBar(Context context, final MainUiKit ui, final Stock stock,
                                        ArrayList<String> sources, String selectedSource,
                                        final Listener listener) {
        final HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = ui.horizontal();
        row.setPadding(0, 0, ui.dp(4), 0);
        final View[] selectedChip = new View[1];
        for (int i = 0; i < sources.size(); i++) {
            final String source = sources.get(i);
            boolean selected = selectedSource.equals(source);
            TextView chip = ui.text(source, 12, selected ? Color.WHITE : COLOR_TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6));
            chip.setBackground(ui.rounded(selected ? COLOR_ACCENT : Color.WHITE, ui.dp(22)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onSourceSelected(stock, source);
                }
            });
            if (selected) {
                selectedChip[0] = chip;
            }
            row.addView(chip, ui.wrapHeight(ui.dp(32)));
            row.addView(ui.spacer(ui.dp(6), 1));
        }
        scroll.addView(row, ui.wrapWrap());
        scrollSelectedSourceIntoView(ui, scroll, selectedChip[0]);
        return scroll;
    }

    private static void scrollSelectedSourceIntoView(final MainUiKit ui,
                                                     final HorizontalScrollView scroll,
                                                     final View selectedChip) {
        if (selectedChip == null) {
            return;
        }
        scroll.post(new Runnable() {
            @Override
            public void run() {
                int targetX = Math.max(0, selectedChip.getLeft() - ui.dp(24));
                scroll.scrollTo(targetX, 0);
            }
        });
    }

    private static View sourceHeader(MainUiKit ui, String source, int count) {
        LinearLayout row = ui.horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(4), ui.dp(6), ui.dp(4), ui.dp(2));
        row.addView(ui.tag(source, COLOR_ACCENT_SOFT, COLOR_ACCENT), ui.wrapWrap());
        row.addView(ui.spacer(ui.dp(8), 1));
        row.addView(ui.text(count + " 条观点", 12, COLOR_SUB, false), ui.wrapWrap());
        return row;
    }

    private static View opinionRow(MainUiKit ui, final Stock stock, final Opinion item,
                                   final Listener listener) {
        LinearLayout row = ui.card();
        row.setPadding(ui.dp(12), ui.dp(9), ui.dp(12), ui.dp(9));
        row.addView(singleLineText(ui, item.title, 14, COLOR_TEXT, true), ui.matchWrap());
        row.addView(ui.spacer(ui.dp(4)));
        row.addView(singleLineText(ui, item.source + " · " + item.time,
                11, COLOR_SUB, false), ui.matchWrap());
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpinionSelected(stock, item);
            }
        });
        return row;
    }

    private static ArrayList<Opinion> opinionsBySource(ArrayList<Opinion> opinions, String source) {
        ArrayList<Opinion> result = new ArrayList<Opinion>();
        for (int i = 0; i < opinions.size(); i++) {
            Opinion opinion = opinions.get(i);
            if (source.equals(opinion.source)) {
                result.add(opinion);
            }
        }
        return result;
    }

    private static TextView singleLineText(MainUiKit ui, String value, int sp, int color, boolean bold) {
        TextView view = ui.text(value, sp, color, bold);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }
}
