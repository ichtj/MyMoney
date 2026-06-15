package com.face.mymoney.ui.detail;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.face.mymoney.R;
import com.face.mymoney.model.Stock;
import com.face.mymoney.ui.MainUiKit;

public class StockDetailPageBuilder {
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_PAGE_BG = Color.rgb(245, 247, 251);

    public interface Listener {
        void onBack();

        void onAddNote(Stock stock);

        void onRefresh(Stock stock);

        void onDelete(Stock stock);
    }

    private final Context context;
    private final MainUiKit ui;
    private final Listener listener;
    private final Stock stock;
    private final View heroView;
    private final View kLineView;
    private final View realtimeView;
    private final View winLossView;
    private final View companyView;
    private final View newsView;
    private final View opinionView;
    private final View noteView;

    public StockDetailPageBuilder(Context context, MainUiKit ui, Listener listener, Stock stock,
                                  View heroView, View kLineView, View realtimeView,
                                  View winLossView, View companyView, View newsView,
                                  View opinionView, View noteView) {
        this.context = context;
        this.ui = ui;
        this.listener = listener;
        this.stock = stock;
        this.heroView = heroView;
        this.kLineView = kLineView;
        this.realtimeView = realtimeView;
        this.winLossView = winLossView;
        this.companyView = companyView;
        this.newsView = newsView;
        this.opinionView = opinionView;
        this.noteView = noteView;
    }

    public Result build() {
        LinearLayout detailShell = ui.vertical();
        detailShell.setBackgroundColor(COLOR_PAGE_BG);

        final ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        LinearLayout page = ui.vertical();
        page.setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(22));
        scrollView.addView(page, ui.pageParams(true));

        LinearLayout heroContainer = sectionContainer(heroView);
        page.addView(heroContainer, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));

        LinearLayout kLineContainer = sectionContainer(kLineView);
        page.addView(kLineContainer, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));

        LinearLayout realtimeContainer = sectionContainer(realtimeView);
        page.addView(realtimeContainer, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));

        LinearLayout winLossContainer = sectionContainer(winLossView);
        page.addView(winLossContainer, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));

        page.addView(sectionTitle(context.getString(R.string.company_info)), ui.matchWrap());
        LinearLayout companyContainer = sectionContainer(companyView);
        page.addView(companyContainer, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));

        page.addView(sectionTitle(context.getString(R.string.news_section)), ui.matchWrap());
        LinearLayout newsContainer = sectionContainer(newsView);
        page.addView(newsContainer, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));

        page.addView(sectionTitle(context.getString(R.string.opinion_section)), ui.matchWrap());
        LinearLayout opinionContainer = sectionContainer(opinionView);
        page.addView(opinionContainer, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(10)));

        LinearLayout noteHeader = ui.horizontal();
        noteHeader.setGravity(Gravity.CENTER_VERTICAL);
        noteHeader.addView(sectionTitle(context.getString(R.string.my_notes)), ui.weightWrap(1));
        Button addNote = ui.primaryButton(context.getString(R.string.add_note_button));
        addNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onAddNote(stock);
            }
        });
        noteHeader.addView(addNote, ui.wrapHeight(ui.dp(36)));
        page.addView(noteHeader, ui.matchWrap());
        LinearLayout noteContainer = sectionContainer(noteView);
        page.addView(noteContainer, ui.matchWrap());

        detailShell.addView(buildNav(), ui.matchWrap());
        detailShell.addView(scrollView, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));

        return new Result(detailShell, scrollView, heroContainer, kLineContainer,
                realtimeContainer, winLossContainer, companyContainer, newsContainer,
                opinionContainer, noteContainer);
    }

    private LinearLayout buildNav() {
        LinearLayout nav = ui.horizontal();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(ui.dp(14), ui.dp(8), ui.dp(14), ui.dp(8));
        nav.setBackgroundColor(COLOR_PAGE_BG);

        Button back = ui.ghostButton(context.getString(R.string.back));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onBack();
            }
        });
        nav.addView(back, ui.wrapHeight(ui.dp(36)));
        nav.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1));

        Button addTopNote = ui.primaryButton(context.getString(R.string.add_note_button));
        addTopNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onAddNote(stock);
            }
        });
        nav.addView(addTopNote, ui.wrapHeight(ui.dp(36)));
        nav.addView(ui.spacer(ui.dp(8), 1));

        Button refresh = ui.ghostButton("刷新");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onRefresh(stock);
            }
        });
        nav.addView(refresh, ui.wrapHeight(ui.dp(36)));
        nav.addView(ui.spacer(ui.dp(8), 1));

        Button delete = ui.ghostButton(context.getString(R.string.delete));
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDelete(stock);
            }
        });
        nav.addView(delete, ui.wrapHeight(ui.dp(36)));
        return nav;
    }

    private LinearLayout sectionContainer(View content) {
        LinearLayout container = ui.vertical();
        if (content != null) {
            container.addView(content, ui.matchWrap());
        }
        return container;
    }

    private TextView sectionTitle(String title) {
        TextView view = ui.text(title, 17, COLOR_TEXT, true);
        view.setIncludeFontPadding(false);
        return view;
    }

    public static class Result {
        public final View root;
        public final ScrollView scrollView;
        public final LinearLayout heroContainer;
        public final LinearLayout kLineContainer;
        public final LinearLayout realtimeContainer;
        public final LinearLayout winLossContainer;
        public final LinearLayout companyContainer;
        public final LinearLayout newsContainer;
        public final LinearLayout opinionContainer;
        public final LinearLayout noteContainer;

        Result(View root, ScrollView scrollView, LinearLayout heroContainer,
               LinearLayout kLineContainer, LinearLayout realtimeContainer,
               LinearLayout winLossContainer, LinearLayout companyContainer,
               LinearLayout newsContainer, LinearLayout opinionContainer,
               LinearLayout noteContainer) {
            this.root = root;
            this.scrollView = scrollView;
            this.heroContainer = heroContainer;
            this.kLineContainer = kLineContainer;
            this.realtimeContainer = realtimeContainer;
            this.winLossContainer = winLossContainer;
            this.companyContainer = companyContainer;
            this.newsContainer = newsContainer;
            this.opinionContainer = opinionContainer;
            this.noteContainer = noteContainer;
        }
    }
}
