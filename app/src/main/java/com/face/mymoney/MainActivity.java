package com.face.mymoney;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.face.mymoney.R;
import com.face.mymoney.auth.LocalAuthManager;
import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.ai.DeepSeekStockAnalyzer;
import com.face.mymoney.crawler.DebugCrawlerActivity;
import com.face.mymoney.crawler.GeneralFinanceNewsFetcher;
import com.face.mymoney.crawler.MarketIndexFetcher;
import com.face.mymoney.crawler.StockNewsFetcher;
import com.face.mymoney.crawler.StockQuoteFetcher;
import com.face.mymoney.data.LocalStockRepository;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.MarketIndexQuote;
import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;
import com.face.mymoney.opinion.Opinion;
import com.face.mymoney.opinion.StockOpinionFetcher;
import com.face.mymoney.ui.MainUiKit;
import com.face.mymoney.ui.detail.WinLossRatioCard;
import com.face.mymoney.ui.home.HomePageBuilder;
import com.face.mymoney.ui.login.LoginPageBuilder;
import com.face.mymoney.ui.widget.PullRefreshScrollView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MyMoneyMain";
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(234, 241, 255);
    private static final String TAB_WATCHLIST = "watchlist";
    private static final String TAB_NEWS = "news";
    private static final String TAB_PROFILE = "profile";
    private static final String NEWS_MODE_IMPORTANT = "important";
    private static final String NEWS_MODE_SUBSCRIBED = "subscribed";
    private static final long QUOTE_AUTO_REFRESH_MILLIS = 10000L;

    private LocalAuthManager authManager;
    private LocalStockRepository stockRepository;
    private FrameLayout root;
    private ArrayList<Stock> stocks = new ArrayList<Stock>();
    private ArrayList<DecisionNote> notes = new ArrayList<DecisionNote>();
    private ArrayList<MarketIndexQuote> marketIndices = new ArrayList<MarketIndexQuote>();
    private ArrayList<News> importantNewsCache = new ArrayList<News>();
    private HashMap<String, ArrayList<News>> newsCache = new HashMap<String, ArrayList<News>>();
    private HashMap<String, ArrayList<Opinion>> opinionCache = new HashMap<String, ArrayList<Opinion>>();
    private HashMap<String, DeepSeekAnalysisResult> deepSeekAnalysisCache = new HashMap<String, DeepSeekAnalysisResult>();
    private HashSet<String> loadingNewsCodes = new HashSet<String>();
    private HashSet<String> loadingOpinionCodes = new HashSet<String>();
    private HashSet<String> loadingDeepSeekCodes = new HashSet<String>();
    private boolean loadingImportantNews;
    private boolean loadingSubscribedNewsFeed;
    private boolean importantNewsLoadedOnce;
    private boolean subscribedNewsLoadedOnce;
    private boolean refreshingQuotes;
    private HashMap<String, String> selectedNewsSources = new HashMap<String, String>();
    private HashMap<String, String> selectedOpinionSources = new HashMap<String, String>();
    private int pendingDetailScrollY = -1;
    private int pendingWatchlistScrollY = -1;
    private LinearLayout currentHeroContainer;
    private LinearLayout currentCompanyContainer;
    private LinearLayout currentWinLossContainer;
    private LinearLayout currentNewsContainer;
    private LinearLayout currentOpinionContainer;
    private LinearLayout currentNoteContainer;
    private PullRefreshScrollView currentNewsScrollView;
    private String currentTab = TAB_WATCHLIST;
    private String selectedNewsMode = NEWS_MODE_IMPORTANT;
    private LinearLayout tabContent;
    private String selectedGroup = "";
    private Stock currentStock;
    private MainUiKit ui;
    private OnBackPressedCallback detailBackCallback;
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(3);
    private final Handler quoteRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable quoteAutoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshQuotes(false);
            scheduleQuoteAutoRefresh();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.main);
        ui = new MainUiKit(this);
        authManager = new LocalAuthManager(this);
        stockRepository = new LocalStockRepository(this);
        selectedGroup = stockRepository.getAllGroup();
        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            }
        });
        stockRepository.removeSampleStocksOnce();
        installBackHandler();
        loadData();
        showMainShell();
        refreshQuotes(false);
        scheduleQuoteAutoRefresh();
    }

    @Override
    protected void onStop() {
        quoteRefreshHandler.removeCallbacks(quoteAutoRefreshRunnable);
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        scheduleQuoteAutoRefresh();
    }

    @Override
    protected void onDestroy() {
        quoteRefreshHandler.removeCallbacks(quoteAutoRefreshRunnable);
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (currentStock != null) {
            leaveStockDetail();
            return;
        }
        super.onBackPressed();
    }

    private void installBackHandler() {
        detailBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                leaveStockDetail();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, detailBackCallback);
    }

    private void setDetailBackEnabled(boolean enabled) {
        if (detailBackCallback != null) {
            detailBackCallback.setEnabled(enabled);
        }
    }

    private void leaveStockDetail() {
        currentStock = null;
        setDetailBackEnabled(false);
        currentTab = TAB_WATCHLIST;
        showMainShell();
    }

    private boolean isLoggedIn() {
        return authManager.isLoggedIn();
    }

    private void showLogin() {
        currentStock = null;
        setDetailBackEnabled(false);
        root.removeAllViews();
        LoginPageBuilder builder = new LoginPageBuilder(this, ui, new LoginPageBuilder.Listener() {
            @Override
            public void onLogin(String account, View anchorView) {
                authManager.login(account);
                seedStocksIfEmpty();
                loadData();
                hideKeyboard(anchorView);
                showHome();
            }
        });
        root.addView(builder.build(), matchMatch());
    }

    private void showMainShell() {
        currentStock = null;
        setDetailBackEnabled(false);
        root.removeAllViews();
        LinearLayout shell = vertical();
        shell.setBackgroundColor(Color.rgb(245, 247, 251));
        tabContent = vertical();
        shell.addView(tabContent, new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomTabs(), matchHeight(dp(62)));
        root.addView(shell, matchMatch());
        showCurrentTab();
    }

    private void showCurrentTab() {
        if (tabContent == null) {
            return;
        }
        tabContent.removeAllViews();
        if (TAB_NEWS.equals(currentTab)) {
            loadNewsFeedIfNeeded();
            tabContent.addView(buildNewsFeedPage(), matchMatch());
            pendingWatchlistScrollY = -1;
        } else if (TAB_PROFILE.equals(currentTab)) {
            tabContent.addView(buildProfilePage(), matchMatch());
            pendingWatchlistScrollY = -1;
        } else {
            View watchlistPage = buildWatchlistPage();
            tabContent.addView(watchlistPage, matchMatch());
            if (watchlistPage instanceof ScrollView) {
                restoreWatchlistScroll((ScrollView) watchlistPage);
            }
        }
    }

    private void scheduleQuoteAutoRefresh() {
        quoteRefreshHandler.removeCallbacks(quoteAutoRefreshRunnable);
        quoteRefreshHandler.postDelayed(quoteAutoRefreshRunnable, QUOTE_AUTO_REFRESH_MILLIS);
    }

    private void runInBackground(Runnable runnable) {
        backgroundExecutor.execute(runnable);
    }

    private void runOnUiIfAlive(Runnable runnable) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        runOnUiThread(runnable);
    }

    private void showHome() {
        currentStock = null;
        setDetailBackEnabled(false);
        root.removeAllViews();
        currentTab = TAB_WATCHLIST;
        showMainShell();
    }

    private View buildWatchlistPage() {
        ArrayList<Stock> displayStocks = filterStocks();
        HomePageBuilder builder = new HomePageBuilder(this, ui, new HomePageBuilder.Listener() {
            @Override
            public void onAddStock() {
                showAddStockDialog();
            }

            @Override
            public void onAddGroup() {
                showAddGroupDialog();
            }

            @Override
            public void onGroupSelected(String group) {
                selectedGroup = group;
                showCurrentTab();
            }

            @Override
            public void onStockSelected(Stock stock) {
                currentStock = stock;
                setDetailBackEnabled(true);
                showStockDetail(stock);
            }

            @Override
            public void onEditStock(Stock stock) {
                showEditStockDialog(stock);
            }

            @Override
            public void onDeleteStock(Stock stock) {
                confirmDeleteStock(stock);
            }

            @Override
            public void onDebugRequested() {
                if (BuildConfig.DEBUG) {
                    startActivity(new Intent(MainActivity.this, DebugCrawlerActivity.class));
                }
            }
        }, authManager.getUserName(), stocks, displayStocks, notes, marketIndices, getGroups(), selectedGroup, countRiskStocks());
        return builder.build();
    }

    private View bottomTabs() {
        LinearLayout tabs = horizontal();
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(12), dp(7), dp(12), dp(7));
        tabs.setBackground(roundedStroke(Color.WHITE, 0, Color.rgb(226, 232, 240)));
        tabs.addView(tabItem(getString(R.string.tab_watchlist), TAB_WATCHLIST, R.drawable.ic_tab_watchlist), weightWrap(1));
        tabs.addView(spacer(8, 1));
        tabs.addView(tabItem(getString(R.string.tab_news_feed), TAB_NEWS, R.drawable.ic_tab_news), weightWrap(1));
        tabs.addView(spacer(8, 1));
        tabs.addView(tabItem(getString(R.string.tab_profile), TAB_PROFILE, R.drawable.ic_tab_profile), weightWrap(1));
        return tabs;
    }

    private View tabItem(String label, final String tab, int iconResId) {
        boolean selected = currentTab.equals(tab);
        int activeText = COLOR_ACCENT;
        int inactiveText = COLOR_SUB;

        LinearLayout item = vertical();
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(5), dp(8), dp(5));
        item.setBackground(rounded(selected ? COLOR_ACCENT_SOFT : Color.TRANSPARENT, dp(14)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setColorFilter(selected ? activeText : inactiveText);
        item.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView textView = text(label, 11, selected ? activeText : inactiveText, selected);
        textView.setGravity(Gravity.CENTER);
        item.addView(textView, wrapWrap());

        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab = tab;
                showMainShell();
            }
        });
        return item;
    }

    private View buildNewsFeedPage() {
        LinearLayout rootPage = vertical();
        rootPage.setBackgroundColor(Color.rgb(245, 247, 251));

        LinearLayout fixedTop = vertical();
        fixedTop.setPadding(dp(18), dp(16), dp(18), dp(10));
        fixedTop.setBackgroundColor(Color.rgb(245, 247, 251));

        boolean importantMode = NEWS_MODE_IMPORTANT.equals(selectedNewsMode);
        ArrayList<News> feedNews = importantMode ? importantNewsCache : buildSubscribedNews();
        ArrayList<String> sources = getNewsSources(feedNews);
        String feedKey = importantMode ? "important" : "feed";
        String selectedSource = selectedNewsSources.get(feedKey);
        if (sources.size() > 0 && (selectedSource == null || !sources.contains(selectedSource))) {
            selectedSource = sources.get(0);
            selectedNewsSources.put(feedKey, selectedSource);
        }

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(getString(R.string.news_feed_title), 28, COLOR_TEXT, true), weightWrap(1));
        fixedTop.addView(header, matchWrap());
        fixedTop.addView(spacer(10));
        fixedTop.addView(newsModeSwitchBar(), matchWrap());
        if (sources.size() > 0) {
            fixedTop.addView(spacer(8));
            fixedTop.addView(compactSourceBarForFeed(sources, selectedSource, feedKey), matchWrap());
            fixedTop.addView(spacer(6));
            fixedTop.addView(text(selectedSource + " · " + getNewsBySource(feedNews, selectedSource).size() + " 条", 12, COLOR_SUB, false), matchWrap());
        }
        rootPage.addView(fixedTop, matchWrap());

        PullRefreshScrollView scrollView = new PullRefreshScrollView(this);
        currentNewsScrollView = scrollView;
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(4), dp(18), dp(24));
        scrollView.addView(page, pageParams(false));
        TextView pullRefresh = text("下拉刷新", 13, COLOR_SUB, false);
        pullRefresh.setGravity(Gravity.CENTER);
        pullRefresh.setBackground(rounded(Color.WHITE, dp(12)));
        page.addView(pullRefresh, new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0));
        scrollView.setPullRefresh(pullRefresh, dp(72), dp(88), new PullRefreshScrollView.Listener() {
            @Override
            public void onRefresh() {
                refreshNewsFeed(true);
            }
        });
        if (feedNews.size() == 0) {
            android.util.Log.d(TAG, "buildNewsFeedPage empty mode=" + selectedNewsMode
                    + ", loadingImportant=" + loadingImportantNews
                    + ", importantLoadedOnce=" + importantNewsLoadedOnce
                    + ", loadingSubscribed=" + loadingSubscribedNewsFeed
                    + ", importantCount=" + importantNewsCache.size()
                    + ", subscribedCount=" + buildSubscribedNews().size());
            LinearLayout empty = card();
            boolean loading = importantMode
                    ? (loadingImportantNews || (!importantNewsLoadedOnce && importantNewsCache.size() == 0))
                    : (loadingSubscribedNewsFeed || (!subscribedNewsLoadedOnce && hasMissingSubscribedNews()));
            String emptyTitle;
            if (loading) {
                emptyTitle = importantMode ? "正在加载重要财经" : "正在加载自选订阅";
            } else {
                emptyTitle = importantMode ? "暂无重要财经" : getString(R.string.news_feed_empty_title);
            }
            empty.addView(text(emptyTitle, 18, COLOR_TEXT, true), matchWrap());
            empty.addView(spacer(8));
            empty.addView(text(importantMode
                    ? (loading ? "正在从东方财富、财联社、新浪财经和 Bing 国际新闻抓取。" : "多渠道暂未抓到重要财经，请稍后刷新。")
                    : getString(R.string.news_feed_empty_desc), 14, COLOR_SUB, false), matchWrap());
            page.addView(empty, matchWrap());
            rootPage.addView(scrollView, new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
            return rootPage;
        }

        android.util.Log.d(TAG, "buildNewsFeedPage mode=" + selectedNewsMode
                + ", feedCount=" + feedNews.size()
                + ", sources=" + sources);
        ArrayList<News> sourceNews = getNewsBySource(feedNews, selectedSource);
        int displayCount = Math.min(sourceNews.size(), 12);
        for (int i = 0; i < displayCount; i++) {
            page.addView(feedNewsRow(sourceNews.get(i)), matchWrap());
            page.addView(spacer(10));
        }
        rootPage.addView(scrollView, new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return rootPage;
    }

    private View newsModeSwitchBar() {
        LinearLayout row = horizontal();
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        row.setBackground(rounded(Color.WHITE, dp(14)));
        row.addView(newsModeChip("重要财经", NEWS_MODE_IMPORTANT), weightWrap(1));
        row.addView(newsModeChip("自选订阅", NEWS_MODE_SUBSCRIBED), weightWrap(1));
        return row;
    }

    private TextView newsModeChip(String label, final String mode) {
        boolean selected = selectedNewsMode.equals(mode);
        TextView chip = text(label, 13, selected ? Color.WHITE : COLOR_TEXT, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(8), dp(10), dp(8));
        chip.setBackground(rounded(selected ? COLOR_ACCENT : Color.TRANSPARENT, dp(12)));
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectedNewsMode = mode;
                showCurrentTab();
            }
        });
        return chip;
    }

    private View compactSourceBarForFeed(ArrayList<String> sources, String selectedSource, final String feedKey) {
        final android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        row.setPadding(0, 0, dp(4), 0);
        final View[] selectedChip = new View[1];
        for (int i = 0; i < sources.size(); i++) {
            final String source = sources.get(i);
            boolean selected = selectedSource.equals(source);
            TextView chip = text(source, 12, selected ? COLOR_ACCENT : COLOR_SUB, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setBackground(rounded(selected ? COLOR_ACCENT_SOFT : Color.TRANSPARENT, dp(14)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedNewsSources.put(feedKey, source);
                    showCurrentTab();
                }
            });
            if (selected) {
                selectedChip[0] = chip;
            }
            row.addView(chip, wrapHeight(dp(32)));
            row.addView(spacer(6, 1));
        }
        scroll.addView(row, wrapWrap());
        scrollSelectedSourceIntoView(scroll, selectedChip[0]);
        return scroll;
    }

    private View sourceSwitchBarForFeed(ArrayList<String> sources, String selectedSource, final String feedKey) {
        final android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        row.setPadding(0, 0, dp(4), 0);
        final View[] selectedChip = new View[1];
        for (int i = 0; i < sources.size(); i++) {
            final String source = sources.get(i);
            boolean selected = selectedSource.equals(source);
            TextView chip = text(source, 12, selected ? Color.WHITE : COLOR_TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setBackground(rounded(selected ? COLOR_ACCENT : Color.WHITE, dp(22)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedNewsSources.put(feedKey, source);
                    showCurrentTab();
                }
            });
            if (selected) {
                selectedChip[0] = chip;
            }
            row.addView(chip, wrapHeight(dp(32)));
            row.addView(spacer(6, 1));
        }
        scroll.addView(row, wrapWrap());
        scrollSelectedSourceIntoView(scroll, selectedChip[0]);
        return scroll;
    }

    private View feedNewsRow(final News item) {
        LinearLayout row = card();
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView title = text(item.title, 15, COLOR_TEXT, true);
        title.setLineSpacing(dp(2), 1.0f);
        row.addView(title, matchWrap());
        row.addView(spacer(4));
        row.addView(text(getString(R.string.news_meta_format, item.source, item.time, item.keyword), 12, COLOR_SUB, false), matchWrap());
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFeedNewsDialog(item);
            }
        });
        return row;
    }

    private View buildProfilePage() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(16), dp(18), dp(24));
        scrollView.addView(page, pageParams(false));
        page.addView(text(getString(R.string.profile_title), 28, COLOR_TEXT, true), matchWrap());
        page.addView(spacer(14));

        if (isLoggedIn()) {
            LinearLayout card = profileCard();
            LinearLayout top = horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(avatarView(authManager.getUserName(), authManager.getAvatarStyle(), dp(72)), wrapWrap());
            top.addView(spacer(14, 1));
            LinearLayout info = vertical();
            info.addView(text(authManager.getUserName(), 22, COLOR_TEXT, true), matchWrap());
            info.addView(spacer(4));
            info.addView(tag(getString(R.string.profile_logged_in), COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
            top.addView(info, weightWrap(1));
            card.addView(top, matchWrap());
            card.addView(spacer(18));
            LinearLayout actions = horizontal();
            Button edit = primaryButton(getString(R.string.profile_edit_title));
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditProfileDialog();
                }
            });
            actions.addView(edit, weightWrap(1));
            actions.addView(spacer(10, 1));
            Button logout = ghostButton(getString(R.string.logout));
            logout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    authManager.logout();
                    showCurrentTab();
                }
            });
            actions.addView(logout, weightWrap(1));
            card.addView(actions, matchWrap());
            page.addView(card, matchWrap());
            return scrollView;
        }

        LinearLayout guest = profileCard();
        LinearLayout guestTop = horizontal();
        guestTop.setGravity(Gravity.CENTER_VERTICAL);
        guestTop.addView(avatarView("?", 3, dp(72)), wrapWrap());
        guestTop.addView(spacer(14, 1));
        LinearLayout guestInfo = vertical();
        guestInfo.addView(text(getString(R.string.profile_not_logged_in), 22, COLOR_TEXT, true), matchWrap());
        guestInfo.addView(spacer(4));
        guestInfo.addView(text(getString(R.string.login_intro), 13, COLOR_SUB, false), matchWrap());
        guestTop.addView(guestInfo, weightWrap(1));
        guest.addView(guestTop, matchWrap());
        page.addView(guest, matchWrap());
        page.addView(spacer(14));
        page.addView(profileLoginCard(), matchWrap());
        return scrollView;
    }

    private View profileLoginCard() {
        LinearLayout card = profileCard();
        card.addView(text(getString(R.string.login_title), 22, COLOR_TEXT, true), matchWrap());
        card.addView(spacer(12));
        final EditText accountInput = input(getString(R.string.login_account_hint));
        final EditText passwordInput = input(getString(R.string.login_password_hint));
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(accountInput, matchHeight(dp(54)));
        card.addView(spacer(12));
        card.addView(passwordInput, matchHeight(dp(54)));
        card.addView(spacer(16));
        Button loginButton = primaryButton(getString(R.string.login_button));
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String account = accountInput.getText().toString().trim();
                if (account.length() == 0) {
                    Toast.makeText(MainActivity.this, getString(R.string.login_empty_account), Toast.LENGTH_SHORT).show();
                    return;
                }
                authManager.login(account);
                hideKeyboard(accountInput);
                showCurrentTab();
            }
        });
        card.addView(loginButton, matchHeight(dp(50)));
        card.addView(spacer(10));
        card.addView(text(getString(R.string.login_tip), 12, COLOR_SUB, false), matchWrap());
        return card;
    }

    private LinearLayout profileCard() {
        LinearLayout card = card();
        card.setBackground(roundedStroke(Color.WHITE, dp(18), Color.rgb(226, 232, 240)));
        return card;
    }

    private TextView avatarView(String name, int style, int size) {
        int[] colors = new int[]{
                Color.rgb(37, 99, 235),
                Color.rgb(217, 45, 32),
                Color.rgb(7, 148, 85),
                Color.rgb(124, 58, 237)
        };
        int color = colors[Math.abs(style) % colors.length];
        TextView avatar = text(avatarText(name), 24, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(color, size / 2));
        avatar.setIncludeFontPadding(false);
        avatar.setMinWidth(size);
        avatar.setMinHeight(size);
        return avatar;
    }

    private String avatarText(String name) {
        if (name == null || name.trim().length() == 0) {
            return "U";
        }
        String value = name.trim();
        return value.substring(0, 1).toUpperCase(Locale.CHINA);
    }

    private void showEditProfileDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        final EditText name = input(getString(R.string.profile_name_hint));
        name.setText(authManager.getUserName());
        final Spinner avatarSpinner = new Spinner(this);
        String[] avatars = new String[]{"蓝色", "红色", "绿色", "紫色"};
        avatarSpinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, avatars));
        avatarSpinner.setSelection(authManager.getAvatarStyle() % avatars.length);
        form.addView(name, matchWrap());
        form.addView(spacer(10));
        form.addView(text(getString(R.string.profile_avatar_label), 12, COLOR_SUB, false), matchWrap());
        form.addView(avatarSpinner, matchHeight(dp(48)));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_edit_title))
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String userName = name.getText().toString().trim();
                        if (userName.length() == 0) {
                            Toast.makeText(MainActivity.this, getString(R.string.login_empty_account), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        authManager.updateProfile(userName, avatarSpinner.getSelectedItemPosition());
                        Toast.makeText(MainActivity.this, getString(R.string.profile_save_success), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        showCurrentTab();
                    }
                });
            }
        });
        dialog.show();
    }

    private void showStockDetail(final Stock stock) {
        currentStock = stock;
        setDetailBackEnabled(true);
        android.util.Log.d(TAG, "showStockDetail code=" + stock.code
                + ", name=" + stock.name
                + ", cachedNews=" + (newsCache.get(stock.code) == null ? "null" : newsCache.get(stock.code).size())
                + ", loading=" + loadingNewsCodes.contains(stock.code));
        root.removeAllViews();

        LinearLayout detailShell = new LinearLayout(this);
        detailShell.setOrientation(LinearLayout.VERTICAL);
        detailShell.setBackgroundColor(Color.rgb(245, 247, 251));
        final ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        LinearLayout page = vertical();
        page.setPadding(dp(14), dp(10), dp(14), dp(22));
        scrollView.addView(page, pageParams(true));

        LinearLayout nav = horizontal();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setPadding(dp(14), dp(8), dp(14), dp(8));
        nav.setBackgroundColor(Color.rgb(245, 247, 251));
        Button back = ghostButton(getString(R.string.back));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                leaveStockDetail();
            }
        });
        nav.addView(back, wrapHeight(dp(36)));
        nav.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        Button addTopNote = primaryButton(getString(R.string.add_note_button));
        addTopNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddNoteDialog(stock);
            }
        });
        nav.addView(addTopNote, wrapHeight(dp(36)));
        nav.addView(spacer(8, 1));
        Button delete = ghostButton(getString(R.string.delete));
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteStock(stock);
            }
        });
        nav.addView(delete, wrapHeight(dp(36)));
        currentHeroContainer = vertical();
        currentHeroContainer.addView(heroCard(stock), matchWrap());
        page.addView(currentHeroContainer, matchWrap());
        page.addView(spacer(10));

        currentWinLossContainer = vertical();
        currentWinLossContainer.addView(winLossRatioCard(stock), matchWrap());
        page.addView(currentWinLossContainer, matchWrap());
        page.addView(spacer(10));

        page.addView(sectionTitle(getString(R.string.company_info)), matchWrap());
        currentCompanyContainer = vertical();
        currentCompanyContainer.addView(companyCard(stock), matchWrap());
        page.addView(currentCompanyContainer, matchWrap());
        page.addView(spacer(10));

        page.addView(sectionTitle(getString(R.string.news_section)), matchWrap());
        currentNewsContainer = vertical();
        currentNewsContainer.addView(newsList(stock), matchWrap());
        page.addView(currentNewsContainer, matchWrap());
        page.addView(spacer(10));

        page.addView(sectionTitle(getString(R.string.opinion_section)), matchWrap());
        currentOpinionContainer = vertical();
        currentOpinionContainer.addView(opinionList(stock), matchWrap());
        page.addView(currentOpinionContainer, matchWrap());
        page.addView(spacer(10));

        LinearLayout noteHeader = horizontal();
        noteHeader.setGravity(Gravity.CENTER_VERTICAL);
        noteHeader.addView(sectionTitle(getString(R.string.my_notes)), weightWrap(1));
        Button addNote = primaryButton(getString(R.string.add_note_button));
        addNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddNoteDialog(stock);
            }
        });
        noteHeader.addView(addNote, wrapHeight(dp(36)));
        page.addView(noteHeader, matchWrap());
        currentNoteContainer = vertical();
        currentNoteContainer.addView(noteList(stock), matchWrap());
        page.addView(currentNoteContainer, matchWrap());

        detailShell.addView(nav, matchWrap());
        detailShell.addView(scrollView, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1));
        root.addView(detailShell, matchMatch());
        restoreDetailScroll(scrollView);
        loadDeepSeekAnalysisIfReady(stock);
    }

    private View quoteItem(String label, String value, int valueColor) {
        LinearLayout box = vertical();
        box.addView(text(label, 12, Color.rgb(203, 213, 225), false), matchWrap());
        box.addView(spacer(4));
        box.addView(text(value, 18, valueColor, true), matchWrap());
        return box;
    }

    private View heroCard(Stock stock) {
        LinearLayout hero = card();
        hero.setPadding(dp(14), dp(12), dp(14), dp(12));
        hero.setBackground(rounded(COLOR_TEXT, dp(16)));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = vertical();
        titleBox.addView(singleLineText(stock.name, 22, Color.WHITE, true), matchWrap());
        titleBox.addView(spacer(3));
        titleBox.addView(singleLineText(stock.code + " · " + stock.market + " · " + stock.groupName,
                12, Color.rgb(203, 213, 225), false), matchWrap());
        top.addView(titleBox, weightWrap(1));

        LinearLayout priceBox = vertical();
        priceBox.setGravity(Gravity.RIGHT);
        TextView price = singleLineText(stock.price, 22, Color.WHITE, true);
        price.setGravity(Gravity.RIGHT);
        priceBox.addView(price, matchWrap());
        int changeColor = stock.changePercent.startsWith("-") ? Color.rgb(96, 211, 148) : Color.rgb(255, 138, 128);
        TextView change = singleLineText(stock.changePercent, 14, changeColor, true);
        change.setGravity(Gravity.RIGHT);
        priceBox.addView(change, matchWrap());
        top.addView(priceBox, new LinearLayout.LayoutParams(dp(110), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        hero.addView(top, matchWrap());

        hero.addView(spacer(8));
        LinearLayout sub = horizontal();
        sub.addView(text(getString(R.string.quote_turnover) + " " + stock.turnover, 12, Color.rgb(203, 213, 225), false), weightWrap(1));
        sub.addView(text(stock.industry, 12, Color.rgb(203, 213, 225), false), wrapWrap());
        hero.addView(sub, matchWrap());
        return hero;
    }

    private TextView singleLineText(String value, int sp, int color, boolean bold) {
        TextView view = text(value, sp, color, bold);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    private View winLossRatioCard(Stock stock) {
        return WinLossRatioCard.create(this, stock, getNotes(stock.code),
                deepSeekAnalysisCache.get(stock.code), loadingDeepSeekCodes.contains(stock.code),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showDeepSeekAnalysisDialog(stock);
                    }
                });
    }

    private void refreshWinLossRatioCard(Stock stock) {
        if (currentWinLossContainer == null) {
            return;
        }
        currentWinLossContainer.removeAllViews();
        currentWinLossContainer.addView(winLossRatioCard(stock), matchWrap());
    }

    private View companyCard(Stock stock) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout row1 = horizontal();
        row1.addView(compactInfo(getString(R.string.market_value), stock.marketValue), weightWrap(1));
        row1.addView(spacer(8, 1));
        row1.addView(compactInfo(getString(R.string.pe_label), stock.pe), weightWrap(1));
        card.addView(row1, matchWrap());
        card.addView(spacer(8));
        LinearLayout row2 = horizontal();
        row2.addView(compactInfo(getString(R.string.revenue_profit), stock.revenue + " / " + stock.profit), weightWrap(1));
        row2.addView(spacer(8, 1));
        row2.addView(compactInfo(getString(R.string.risk_tag), stock.riskTag), weightWrap(1));
        card.addView(row2, matchWrap());
        card.addView(spacer(8));
        card.addView(infoRow(getString(R.string.main_business), stock.mainBusiness), matchWrap());
        return card;
    }

    private View compactInfo(String label, String value) {
        LinearLayout box = vertical();
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(rounded(Color.rgb(248, 250, 252), dp(10)));
        box.addView(singleLineText(label, 11, COLOR_SUB, false), matchWrap());
        box.addView(spacer(3));
        box.addView(singleLineText(value, 13, COLOR_TEXT, true), matchWrap());
        return box;
    }

    private View newsList(final Stock stock) {
        LinearLayout list = vertical();
        ArrayList<News> news = buildNews(stock);
        android.util.Log.d(TAG, "newsList code=" + stock.code
                + ", displayNewsCount=" + news.size()
                + ", hasCache=" + (newsCache.get(stock.code) != null));
        if (newsCache.get(stock.code) == null) {
            LinearLayout loading = card();
            loading.setPadding(dp(16), dp(14), dp(16), dp(14));
            loading.addView(text(getString(R.string.news_loading_title), 16, COLOR_TEXT, true), matchWrap());
            loading.addView(spacer(5));
            loading.addView(text(getString(R.string.news_loading_desc), 12, COLOR_SUB, false), matchWrap());
            list.addView(loading, matchWrap());
            list.addView(spacer(10));
            loadStockNews(stock);
        }
        ArrayList<String> sources = getNewsSources(news);
        if (sources.size() > 0) {
            String selectedSource = selectedNewsSource(stock, sources);
            list.addView(sourceSwitchBar(stock, sources, selectedSource, true), matchWrap());
            list.addView(spacer(8));
            ArrayList<News> sourceNews = getNewsBySource(news, selectedSource);
            list.addView(sourceHeader(selectedSource, sourceNews.size(), "条资讯"), matchWrap());
            list.addView(spacer(8));
            int displayCount = Math.min(sourceNews.size(), 8);
            for (int j = 0; j < displayCount; j++) {
                list.addView(newsRow(stock, sourceNews.get(j)), matchWrap());
                list.addView(spacer(10));
            }
            if (sourceNews.size() > displayCount) {
                list.addView(text("仅显示前 " + displayCount + " 条，切换来源查看其他内容", 12, COLOR_SUB, false), matchWrap());
                list.addView(spacer(10));
            }
        }
        return list;
    }

    private View newsRow(final Stock stock, final News item) {
        LinearLayout row = card();
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        row.addView(singleLineText(item.title, 14, COLOR_TEXT, true), matchWrap());
        row.addView(spacer(4));
        row.addView(singleLineText(item.source + " · " + item.time, 11, COLOR_SUB, false), matchWrap());
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNewsDialog(stock, item);
            }
        });
        return row;
    }

    private View noteList(Stock stock) {
        LinearLayout list = vertical();
        ArrayList<DecisionNote> stockNotes = getNotes(stock.code);
        if (stockNotes.size() == 0) {
            LinearLayout empty = card();
            empty.addView(text(getString(R.string.no_note_title), 16, COLOR_TEXT, true), matchWrap());
            empty.addView(spacer(6));
            empty.addView(text(getString(R.string.no_note_desc), 13, COLOR_SUB, false), matchWrap());
            list.addView(empty, matchWrap());
            return list;
        }

        for (int i = 0; i < stockNotes.size(); i++) {
            DecisionNote note = stockNotes.get(i);
            LinearLayout card = card();
            card.setPadding(dp(12), dp(10), dp(12), dp(10));
            LinearLayout top = horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(tag(note.type, COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
            top.addView(spacer(8, 1));
            top.addView(singleLineText(note.title, 15, COLOR_TEXT, true), weightWrap(1));
            card.addView(top, matchWrap());
            card.addView(spacer(5));
            card.addView(singleLineText(getString(R.string.note_meta_format, note.targetPrice, note.stopLossPrice, note.confidence)
                    + " · " + note.createdTime, 12, COLOR_SUB, false), matchWrap());
            if (note.content.length() > 0) {
                card.addView(spacer(5));
                card.addView(singleLineText(note.content, 13, COLOR_TEXT, false), matchWrap());
            }
            list.addView(card, matchWrap());
            list.addView(spacer(7));
        }
        return list;
    }

    private View opinionList(final Stock stock) {
        LinearLayout list = vertical();
        ArrayList<Opinion> opinions = buildOpinions(stock);
        if (opinionCache.get(stock.code) == null) {
            LinearLayout loading = card();
            loading.setPadding(dp(16), dp(14), dp(16), dp(14));
            loading.addView(text(getString(R.string.opinion_loading_title), 16, COLOR_TEXT, true), matchWrap());
            loading.addView(spacer(5));
            loading.addView(text(getString(R.string.opinion_loading_desc), 12, COLOR_SUB, false), matchWrap());
            list.addView(loading, matchWrap());
            list.addView(spacer(10));
            loadStockOpinions(stock);
        }
        ArrayList<String> sources = getOpinionSources(opinions);
        if (sources.size() > 0) {
            String selectedSource = selectedOpinionSource(stock, sources);
            list.addView(sourceSwitchBar(stock, sources, selectedSource, false), matchWrap());
            list.addView(spacer(8));
            ArrayList<Opinion> sourceOpinions = getOpinionsBySource(opinions, selectedSource);
            list.addView(sourceHeader(selectedSource, sourceOpinions.size(), "条观点"), matchWrap());
            list.addView(spacer(8));
            int displayCount = Math.min(sourceOpinions.size(), 8);
            for (int j = 0; j < displayCount; j++) {
                list.addView(opinionRow(stock, sourceOpinions.get(j)), matchWrap());
                list.addView(spacer(10));
            }
            if (sourceOpinions.size() > displayCount) {
                list.addView(text("仅显示前 " + displayCount + " 条，切换来源查看其他内容", 12, COLOR_SUB, false), matchWrap());
                list.addView(spacer(10));
            }
        }
        return list;
    }

    private View sourceSwitchBar(final Stock stock, ArrayList<String> sources, String selectedSource, final boolean news) {
        final android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        row.setPadding(0, 0, dp(4), 0);
        final View[] selectedChip = new View[1];
        for (int i = 0; i < sources.size(); i++) {
            final String source = sources.get(i);
            boolean selected = selectedSource.equals(source);
            TextView chip = text(source, 12, selected ? Color.WHITE : COLOR_TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setBackground(rounded(selected ? COLOR_ACCENT : Color.WHITE, dp(22)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (news) {
                        selectedNewsSources.put(stock.code, source);
                    } else {
                        selectedOpinionSources.put(stock.code, source);
                    }
                    if (currentStock != null && stock.code.equals(currentStock.code)) {
                        refreshSourceSection(stock, news);
                    }
                }
            });
            if (selected) {
                selectedChip[0] = chip;
            }
            row.addView(chip, wrapHeight(dp(32)));
            row.addView(spacer(6, 1));
        }
        scroll.addView(row, wrapWrap());
        scrollSelectedSourceIntoView(scroll, selectedChip[0]);
        return scroll;
    }

    private void scrollSelectedSourceIntoView(final android.widget.HorizontalScrollView scroll, final View selectedChip) {
        if (selectedChip == null) {
            return;
        }
        scroll.post(new Runnable() {
            @Override
            public void run() {
                int targetX = Math.max(0, selectedChip.getLeft() - dp(24));
                scroll.scrollTo(targetX, 0);
            }
        });
    }

    private void refreshSourceSection(Stock stock, boolean news) {
        if (news) {
            if (currentNewsContainer != null) {
                currentNewsContainer.removeAllViews();
                currentNewsContainer.addView(newsList(stock), matchWrap());
            }
            return;
        }
        if (currentOpinionContainer != null) {
            currentOpinionContainer.removeAllViews();
            currentOpinionContainer.addView(opinionList(stock), matchWrap());
        }
    }

    private void refreshNoteSection(Stock stock) {
        if (currentNoteContainer == null) {
            return;
        }
        currentNoteContainer.removeAllViews();
        currentNoteContainer.addView(noteList(stock), matchWrap());
    }

    private String selectedNewsSource(Stock stock, ArrayList<String> sources) {
        String selected = selectedNewsSources.get(stock.code);
        if (selected != null && sources.contains(selected)) {
            return selected;
        }
        String first = sources.get(0);
        selectedNewsSources.put(stock.code, first);
        return first;
    }

    private String selectedOpinionSource(Stock stock, ArrayList<String> sources) {
        String selected = selectedOpinionSources.get(stock.code);
        if (selected != null && sources.contains(selected)) {
            return selected;
        }
        String first = sources.get(0);
        selectedOpinionSources.put(stock.code, first);
        return first;
    }

    private void rememberDetailScroll() {
        if (root.getChildCount() == 0) {
            pendingDetailScrollY = -1;
            return;
        }
        View child = root.getChildAt(0);
        if (child instanceof ScrollView) {
            pendingDetailScrollY = ((ScrollView) child).getScrollY();
        } else {
            pendingDetailScrollY = -1;
        }
    }

    private void restoreDetailScroll(final ScrollView scrollView) {
        if (pendingDetailScrollY < 0) {
            return;
        }
        final int scrollY = pendingDetailScrollY;
        pendingDetailScrollY = -1;
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, scrollY);
            }
        });
    }

    private void rememberWatchlistScroll() {
        pendingWatchlistScrollY = -1;
        if (tabContent == null || tabContent.getChildCount() == 0) {
            return;
        }
        View child = tabContent.getChildAt(0);
        if (child instanceof ScrollView) {
            pendingWatchlistScrollY = ((ScrollView) child).getScrollY();
        }
    }

    private void restoreWatchlistScroll(final ScrollView scrollView) {
        if (pendingWatchlistScrollY < 0) {
            return;
        }
        final int scrollY = pendingWatchlistScrollY;
        pendingWatchlistScrollY = -1;
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, scrollY);
            }
        });
    }

    private View sourceHeader(String source, int count, String suffix) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(2));
        row.addView(tag(source, COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
        row.addView(spacer(8, 1));
        row.addView(text(count + " " + suffix, 12, COLOR_SUB, false), wrapWrap());
        return row;
    }

    private View opinionRow(final Stock stock, final Opinion item) {
            LinearLayout row = card();
            row.setPadding(dp(12), dp(9), dp(12), dp(9));
            row.addView(singleLineText(item.title, 14, COLOR_TEXT, true), matchWrap());
            row.addView(spacer(4));
            row.addView(singleLineText(item.source + " · " + item.time, 11, COLOR_SUB, false), matchWrap());
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showOpinionDialog(stock, item);
                }
            });
            return row;
    }

    private ArrayList<String> getOpinionSources(ArrayList<Opinion> opinions) {
        ArrayList<String> sources = new ArrayList<String>();
        for (int i = 0; i < opinions.size(); i++) {
            String source = opinions.get(i).source;
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
        return sources;
    }

    private ArrayList<String> getNewsSources(ArrayList<News> news) {
        ArrayList<String> sources = new ArrayList<String>();
        for (int i = 0; i < news.size(); i++) {
            String source = news.get(i).source;
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
        return sources;
    }

    private ArrayList<News> getNewsBySource(ArrayList<News> news, String source) {
        ArrayList<News> result = new ArrayList<News>();
        for (int i = 0; i < news.size(); i++) {
            News item = news.get(i);
            if (source.equals(item.source)) {
                result.add(item);
            }
        }
        return result;
    }

    private ArrayList<Opinion> getOpinionsBySource(ArrayList<Opinion> opinions, String source) {
        ArrayList<Opinion> result = new ArrayList<Opinion>();
        for (int i = 0; i < opinions.size(); i++) {
            Opinion opinion = opinions.get(i);
            if (source.equals(opinion.source)) {
                result.add(opinion);
            }
        }
        return result;
    }

    private View infoRow(String label, String value) {
        LinearLayout row = vertical();
        row.setPadding(0, dp(7), 0, dp(7));
        row.addView(text(label, 12, COLOR_SUB, false), matchWrap());
        row.addView(spacer(2));
        TextView valueView = text(value, 15, COLOR_TEXT, false);
        valueView.setLineSpacing(dp(2), 1.0f);
        row.addView(valueView, matchWrap());
        return row;
    }

    private TextView sectionTitle(String title) {
        TextView view = text(title, 17, COLOR_TEXT, true);
        view.setIncludeFontPadding(false);
        return view;
    }

    private void showAddStockDialog() {
        showStockFormDialog(null);
    }

    private void showEditStockDialog(final Stock stock) {
        showStockFormDialog(stock);
    }

    private void showStockFormDialog(final Stock editingStock) {
        final boolean isEdit = editingStock != null;
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(10), dp(20), 0);
        final EditText code = input(getString(R.string.stock_code_hint));
        code.setSingleLine(true);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (isEdit) {
            code.setText(editingStock.code);
            code.setEnabled(false);
        }
        final EditText name = input(getString(R.string.stock_name_hint));
        name.setSingleLine(true);
        if (isEdit) {
            name.setText(editingStock.name);
        }
        final Spinner groupSpinner = new Spinner(this);
        final ArrayList<String> stockGroups = selectableGroups();
        ArrayAdapter<String> groupAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, stockGroups);
        groupSpinner.setAdapter(groupAdapter);
        String currentGroup = isEdit ? editingStock.groupName : selectedGroup;
        int selectedIndex = stockGroups.indexOf(currentGroup);
        if (selectedIndex >= 0) {
            groupSpinner.setSelection(selectedIndex);
        }
        final EditText newGroup = input(getString(R.string.stock_new_group_hint));
        newGroup.setSingleLine(true);
        final EditText remark = input(getString(R.string.stock_remark_hint));
        remark.setMinLines(2);
        remark.setGravity(Gravity.TOP);
        if (isEdit) {
            remark.setText(editingStock.remark);
        }

        form.addView(code, matchHeight(dp(52)));
        form.addView(spacer(10));
        form.addView(name, matchHeight(dp(52)));
        form.addView(spacer(10));
        form.addView(text(getString(R.string.stock_group_select_hint), 12, COLOR_SUB, false), matchWrap());
        form.addView(groupSpinner, matchHeight(dp(48)));
        form.addView(spacer(10));
        form.addView(newGroup, matchHeight(dp(52)));
        form.addView(spacer(10));
        form.addView(remark, matchHeight(dp(82)));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEdit ? getString(R.string.edit_stock_title) : getString(R.string.add_stock_title))
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String codeText = code.getText().toString().trim();
                        String nameText = name.getText().toString().trim();
                        if ((!isEdit && codeText.length() == 0) || nameText.length() == 0) {
                            Toast.makeText(MainActivity.this, getString(R.string.stock_required), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!isEdit && !isValidStockCode(codeText)) {
                            Toast.makeText(MainActivity.this, getString(R.string.stock_code_invalid), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!isEdit && containsStockCode(codeText)) {
                            Toast.makeText(MainActivity.this, getString(R.string.stock_duplicate), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String groupText = resolveStockGroup(groupSpinner, newGroup);
                        stockRepository.saveGroupIfNeeded(groupText);
                        if (isEdit) {
                            editingStock.name = nameText;
                            editingStock.groupName = groupText;
                            editingStock.remark = remark.getText().toString().trim();
                        } else {
                            Stock stock = createDefaultStock(codeText, nameText, groupText, remark.getText().toString().trim());
                            stocks.add(0, stock);
                        }
                        saveStocks();
                        selectedGroup = groupText;
                        dialog.dismiss();
                        showCurrentTab();
                    }
                });
            }
        });
        dialog.show();
    }

    private void showAddGroupDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        final EditText groupName = input(getString(R.string.group_name_hint));
        form.addView(groupName, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_group_title))
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String groupText = groupName.getText().toString().trim();
                        if (groupText.length() == 0) {
                            Toast.makeText(MainActivity.this, getString(R.string.group_required), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        stockRepository.saveGroupIfNeeded(groupText);
                        selectedGroup = groupText;
                        dialog.dismiss();
                        showHome();
                    }
                });
            }
        });
        dialog.show();
    }

    private void showAddNoteDialog(final Stock stock) {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        final Spinner typeSpinner = new Spinner(this);
        String[] types = new String[]{getString(R.string.note_type_observe), getString(R.string.note_type_buy), getString(R.string.note_type_sell), getString(R.string.note_type_risk), getString(R.string.note_type_review)};
        typeSpinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, types));
        final EditText title = input(getString(R.string.note_title_hint));
        final EditText content = input(getString(R.string.note_content_hint));
        content.setMinLines(3);
        content.setGravity(Gravity.TOP);
        final EditText target = input(getString(R.string.target_price_hint));
        target.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText stop = input(getString(R.string.stop_loss_price_hint));
        stop.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText confidence = input(getString(R.string.confidence_hint));
        confidence.setInputType(InputType.TYPE_CLASS_NUMBER);

        form.addView(typeSpinner, matchHeight(dp(48)));
        form.addView(spacer(10));
        form.addView(title, matchWrap());
        form.addView(spacer(10));
        form.addView(content, matchHeight(dp(110)));
        form.addView(spacer(10));
        form.addView(target, matchWrap());
        form.addView(spacer(10));
        form.addView(stop, matchWrap());
        form.addView(spacer(10));
        form.addView(confidence, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_note_title))
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (title.getText().toString().trim().length() == 0) {
                            Toast.makeText(MainActivity.this, getString(R.string.note_title_required), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        DecisionNote note = new DecisionNote();
                        note.id = System.currentTimeMillis();
                        note.stockCode = stock.code;
                        note.type = typeSpinner.getSelectedItem().toString();
                        note.title = title.getText().toString().trim();
                        note.content = content.getText().toString().trim();
                        note.targetPrice = textOrDefault(target, "-");
                        note.stopLossPrice = textOrDefault(stop, "-");
                        note.confidence = textOrDefault(confidence, "5");
                        note.createdTime = now();
                        notes.add(0, note);
                        deepSeekAnalysisCache.remove(stock.code);
                        saveNotes();
                        dialog.dismiss();
                        refreshNoteSection(stock);
                        refreshWinLossRatioCard(stock);
                        loadDeepSeekAnalysisIfReady(stock);
                    }
                });
            }
        });
        dialog.show();
    }

    private void showNewsDialog(Stock stock, News news) {
        String message = getString(R.string.news_dialog_format, news.source, news.time, news.content, stock.name, stock.code, news.keyword);
        new AlertDialog.Builder(this)
                .setTitle(news.title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void showFeedNewsDialog(News news) {
        String content = news.content == null ? "" : news.content.trim();
        android.util.Log.d(TAG, "showFeedNewsDialog title=" + news.title
                + ", source=" + news.source
                + ", contentLength=" + content.length()
                + ", contentPreview=" + (content.length() > 120 ? content.substring(0, 120) : content));
        if (content.length() == 0 || content.equals(news.title)) {
            content = "摘要：" + news.title
                    + "\n\n来源：" + news.source
                    + "\n时间：" + news.time
                    + "\n关键词：" + news.keyword;
        }
        new AlertDialog.Builder(this)
                .setTitle(news.title)
                .setMessage(news.source + " · " + news.time + "\n\n" + content + "\n\n关键词：" + news.keyword)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void showOpinionDialog(Stock stock, Opinion opinion) {
        String message = getString(R.string.opinion_dialog_format, opinion.source, opinion.time, opinion.content, stock.name, stock.code, opinion.keyword);
        if (opinion.url.length() > 0) {
            message = message + "\n\n来源链接：" + opinion.url;
        }
        new AlertDialog.Builder(this)
                .setTitle(opinion.title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void showDeepSeekAnalysisDialog(Stock stock) {
        if (stock == null) {
            return;
        }
        DeepSeekAnalysisResult result = deepSeekAnalysisCache.get(stock.code);
        String message;
        if (result == null) {
            message = loadingDeepSeekCodes.contains(stock.code)
                    ? "DeepSeek 正在结合爬虫信息生成胜负比参考。"
                    : "DeepSeek 胜负比参考尚未生成。";
        } else if (result.success && result.hasRatio) {
            message = "AI胜负比：" + result.ratioText
                    + "\n含义：每承担 1 份风险，对应 " + result.ratioText + " 份机会。"
                    + "\n机会：" + result.opportunityPercent + "%"
                    + "\n风险：" + result.riskPercent + "%"
                    + "\n\n依据：" + result.summary
                    + "\n\n校验：饼图比例、机会/风险百分比和胜负比文字均来自这次 DeepSeek JSON 结果，且已通过 0-100、合计100 的校验。";
        } else {
            message = result.displayText()
                    + "\n\n校验：DeepSeek 未给出可用比例或比例未通过校验，饼图保留目标价/止损价计算结果。";
        }
        new AlertDialog.Builder(this)
                .setTitle("DeepSeek 胜负比参考")
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void confirmDeleteStock(final Stock stock) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_stock_title))
                .setMessage(getString(R.string.delete_stock_message, stock.name))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        for (int i = stocks.size() - 1; i >= 0; i--) {
                            if (stocks.get(i).code.equals(stock.code)) {
                                stocks.remove(i);
                            }
                        }
                        saveStocks();
                        currentStock = null;
                        showHome();
                    }
                })
                .show();
    }

    private void loadData() {
        stocks = stockRepository.loadStocks();
        notes = stockRepository.loadNotes();
    }

    private void seedStocksIfEmpty() {
        stockRepository.seedStocksIfEmpty();
    }

    private Stock createDefaultStock(String code, String name, String group, String remark) {
        return stockRepository.createDefaultStock(code, name, group, remark);
    }

    private ArrayList<News> buildNews(Stock stock) {
        ArrayList<News> cachedNews = newsCache.get(stock.code);
        if (cachedNews != null && cachedNews.size() > 0) {
            android.util.Log.d(TAG, "buildNews use fetched news code=" + stock.code + ", count=" + cachedNews.size());
            return cachedNews;
        }
        android.util.Log.d(TAG, "buildNews use mock news code=" + stock.code
                + ", cacheExists=" + (cachedNews != null)
                + ", cacheCount=" + (cachedNews == null ? -1 : cachedNews.size()));
        return stockRepository.buildNews(stock);
    }

    private ArrayList<Opinion> buildOpinions(Stock stock) {
        ArrayList<Opinion> cachedOpinions = opinionCache.get(stock.code);
        if (cachedOpinions != null) {
            return cachedOpinions;
        }
        return new ArrayList<Opinion>();
    }

    private void loadStockNews(final Stock stock) {
        if (loadingNewsCodes.contains(stock.code)) {
            android.util.Log.d(TAG, "loadStockNews ignored because loading code=" + stock.code);
            return;
        }
        loadingNewsCodes.add(stock.code);
        android.util.Log.d(TAG, "loadStockNews start code=" + stock.code + ", name=" + stock.name);
        runInBackground(new Runnable() {
            @Override
            public void run() {
                StockNewsFetcher fetcher = new StockNewsFetcher(MainActivity.this);
                final ArrayList<News> fetchedNews = fetcher.fetchForStock(stock);
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        loadingNewsCodes.remove(stock.code);
                        newsCache.put(stock.code, fetchedNews);
                        android.util.Log.d(TAG, "loadStockNews finish code=" + stock.code
                                + ", fetchedCount=" + fetchedNews.size()
                                + ", currentStock=" + (currentStock == null ? "null" : currentStock.code));
                        if (currentStock != null && stock.code.equals(currentStock.code)) {
                            refreshSourceSection(currentStock, true);
                            loadDeepSeekAnalysisIfReady(currentStock);
                        }
                    }
                });
            }
        });
    }

    private void refreshNewsFeed() {
        refreshNewsFeed(true);
    }

    private void loadNewsFeedIfNeeded() {
        if (!importantNewsLoadedOnce && importantNewsCache.size() == 0 && !loadingImportantNews) {
            loadImportantNews(false);
        }
        if (!subscribedNewsLoadedOnce && !loadingSubscribedNewsFeed && hasMissingSubscribedNews()) {
            loadSubscribedNews(false);
        }
    }

    private void refreshNewsFeed(boolean manual) {
        if (NEWS_MODE_SUBSCRIBED.equals(selectedNewsMode)) {
            loadSubscribedNews(manual);
            return;
        }
        loadImportantNews(manual);
    }

    private void finishNewsPullRefreshIfNeeded() {
        if (currentNewsScrollView != null && currentNewsScrollView.isRefreshing()) {
            currentNewsScrollView.finishRefresh();
        }
    }

    private void loadImportantNews(final boolean manual) {
        if (loadingImportantNews) {
            if (manual) {
                Toast.makeText(this, "重要财经正在加载中", Toast.LENGTH_SHORT).show();
            }
            finishNewsPullRefreshIfNeeded();
            return;
        }
        if (manual) {
            importantNewsLoadedOnce = false;
        }
        loadingImportantNews = true;
        android.util.Log.d(TAG, "loadImportantNews start manual=" + manual);
        if (manual) {
            Toast.makeText(this, getString(R.string.news_loading_title), Toast.LENGTH_SHORT).show();
        }
        runInBackground(new Runnable() {
            @Override
            public void run() {
                GeneralFinanceNewsFetcher fetcher = new GeneralFinanceNewsFetcher();
                final ArrayList<News> fetchedNews = fetcher.fetchImportantNews();
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        loadingImportantNews = false;
                        importantNewsLoadedOnce = true;
                        importantNewsCache = fetchedNews;
                        android.util.Log.d(TAG, "loadImportantNews finish count=" + fetchedNews.size()
                                + ", currentTab=" + currentTab
                                + ", selectedMode=" + selectedNewsMode);
                        if (TAB_NEWS.equals(currentTab)) {
                            finishNewsPullRefreshIfNeeded();
                            showCurrentTab();
                        }
                        if (manual) {
                            Toast.makeText(MainActivity.this, "重要财经刷新完成：" + fetchedNews.size() + " 条", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private void loadSubscribedNews(final boolean manual) {
        if (stocks.size() == 0) {
            if (manual) {
                Toast.makeText(this, getString(R.string.news_feed_empty_title), Toast.LENGTH_SHORT).show();
            }
            finishNewsPullRefreshIfNeeded();
            return;
        }
        if (loadingSubscribedNewsFeed) {
            if (manual) {
                Toast.makeText(this, "自选订阅正在加载中", Toast.LENGTH_SHORT).show();
            }
            finishNewsPullRefreshIfNeeded();
            return;
        }
        if (manual) {
            subscribedNewsLoadedOnce = false;
        }
        loadingSubscribedNewsFeed = true;
        android.util.Log.d(TAG, "loadSubscribedNews start manual=" + manual + ", stockCount=" + stocks.size());
        if (manual) {
            Toast.makeText(this, getString(R.string.news_loading_title), Toast.LENGTH_SHORT).show();
        }
        runInBackground(new Runnable() {
            @Override
            public void run() {
                final HashMap<String, ArrayList<News>> fetched = new HashMap<String, ArrayList<News>>();
                StockNewsFetcher fetcher = new StockNewsFetcher(MainActivity.this);
                for (int i = 0; i < stocks.size(); i++) {
                    Stock stock = stocks.get(i);
                    fetched.put(stock.code, fetcher.fetchForStock(stock));
                }
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        loadingSubscribedNewsFeed = false;
                        subscribedNewsLoadedOnce = true;
                        newsCache.putAll(fetched);
                        android.util.Log.d(TAG, "loadSubscribedNews finish fetchedStocks=" + fetched.size()
                                + ", subscribedCount=" + buildSubscribedNews().size());
                        if (TAB_NEWS.equals(currentTab)) {
                            finishNewsPullRefreshIfNeeded();
                            showCurrentTab();
                        }
                        if (manual) {
                            Toast.makeText(MainActivity.this, "自选订阅刷新完成", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }
        });
    }

    private boolean hasMissingSubscribedNews() {
        if (stocks.size() == 0) {
            return false;
        }
        for (int i = 0; i < stocks.size(); i++) {
            if (newsCache.get(stocks.get(i).code) == null) {
                return true;
            }
        }
        return false;
    }

    private ArrayList<News> buildSubscribedNews() {
        ArrayList<News> result = new ArrayList<News>();
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            ArrayList<News> cached = newsCache.get(stock.code);
            if (cached != null) {
                addFeedNews(result, cached);
            }
        }
        return result;
    }

    private void addFeedNews(ArrayList<News> target, ArrayList<News> source) {
        for (int i = 0; i < source.size(); i++) {
            News item = source.get(i);
            if (!containsNewsTitle(target, item.title)) {
                target.add(item);
            }
        }
    }

    private boolean containsNewsTitle(ArrayList<News> news, String title) {
        for (int i = 0; i < news.size(); i++) {
            if (title.equals(news.get(i).title)) {
                return true;
            }
        }
        return false;
    }

    private void loadStockOpinions(final Stock stock) {
        if (loadingOpinionCodes.contains(stock.code)) {
            return;
        }
        loadingOpinionCodes.add(stock.code);
        runInBackground(new Runnable() {
            @Override
            public void run() {
                StockOpinionFetcher fetcher = new StockOpinionFetcher();
                final ArrayList<Opinion> fetchedOpinions = fetcher.fetchForStock(stock);
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        loadingOpinionCodes.remove(stock.code);
                        opinionCache.put(stock.code, fetchedOpinions);
                        if (currentStock != null && stock.code.equals(currentStock.code)) {
                            refreshSourceSection(currentStock, false);
                            loadDeepSeekAnalysisIfReady(currentStock);
                        }
                    }
                });
            }
        });
    }

    private void loadDeepSeekAnalysisIfReady(final Stock stock) {
        if (stock == null || deepSeekAnalysisCache.containsKey(stock.code) || loadingDeepSeekCodes.contains(stock.code)) {
            return;
        }
        if (newsCache.get(stock.code) == null || opinionCache.get(stock.code) == null) {
            return;
        }
        loadingDeepSeekCodes.add(stock.code);
        refreshWinLossRatioCard(stock);
        runInBackground(new Runnable() {
            @Override
            public void run() {
                DeepSeekStockAnalyzer analyzer = new DeepSeekStockAnalyzer();
                final DeepSeekAnalysisResult result = analyzer.analyze(stock,
                        safeNewsForAnalysis(stock), buildOpinions(stock), getNotes(stock.code));
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        loadingDeepSeekCodes.remove(stock.code);
                        deepSeekAnalysisCache.put(stock.code, result);
                        if (currentStock != null && stock.code.equals(currentStock.code)) {
                            refreshWinLossRatioCard(currentStock);
                        }
                    }
                });
            }
        });
    }

    private ArrayList<News> safeNewsForAnalysis(Stock stock) {
        ArrayList<News> cachedNews = newsCache.get(stock.code);
        return cachedNews == null ? new ArrayList<News>() : cachedNews;
    }

    private void refreshQuotes() {
        refreshQuotes(true);
    }

    private void refreshQuotes(final boolean manual) {
        if (stocks.size() == 0) {
            if (manual) {
                Toast.makeText(this, "正在刷新大盘指数，暂无自选股可刷新", Toast.LENGTH_SHORT).show();
            }
        }
        if (refreshingQuotes) {
            if (manual) {
                Toast.makeText(this, "行情正在刷新中", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        refreshingQuotes = true;
        if (manual) {
            Toast.makeText(this, getString(R.string.refresh_quote_loading), Toast.LENGTH_SHORT).show();
        }
        runInBackground(new Runnable() {
            @Override
            public void run() {
                StockQuoteFetcher fetcher = new StockQuoteFetcher();
                final StockQuoteFetcher.QuoteRefreshResult result = fetcher.refreshQuotesDetailed(stocks);
                MarketIndexFetcher indexFetcher = new MarketIndexFetcher();
                final ArrayList<MarketIndexQuote> fetchedIndices = indexFetcher.fetchDefaultIndices();
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        refreshingQuotes = false;
                        if (fetchedIndices.size() > 0) {
                            marketIndices = fetchedIndices;
                        }
                        saveStocks();
                        if (manual) {
                            String refreshMessage = result.totalCount > 0
                                    ? getString(R.string.refresh_quote_result, result.successCount, result.totalCount)
                                    : (fetchedIndices.size() > 0 ? "大盘指数已刷新" : "大盘指数刷新失败");
                            Toast.makeText(MainActivity.this, refreshMessage, Toast.LENGTH_SHORT).show();
                        }
                        refreshVisibleQuoteUi(manual);
                        if (result.failedItems.size() > 0) {
                            if (manual) {
                                showQuoteRefreshFailureDialog(result);
                            } else {
                                Toast.makeText(MainActivity.this, "自动刷新行情失败：" + result.failedItems.size() + "/" + result.totalCount, Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }
        });
    }

    private void refreshVisibleQuoteUi(boolean manual) {
        if (currentStock != null) {
            refreshDetailQuoteSections(currentStock);
            return;
        }
        if (manual && TAB_WATCHLIST.equals(currentTab)) {
            rememberWatchlistScroll();
            showCurrentTab();
        }
    }

    private void refreshDetailQuoteSections(Stock stock) {
        if (currentHeroContainer != null) {
            currentHeroContainer.removeAllViews();
            currentHeroContainer.addView(heroCard(stock), matchWrap());
        }
        if (currentCompanyContainer != null) {
            currentCompanyContainer.removeAllViews();
            currentCompanyContainer.addView(companyCard(stock), matchWrap());
        }
        refreshWinLossRatioCard(stock);
    }

    private void showQuoteRefreshFailureDialog(StockQuoteFetcher.QuoteRefreshResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前行情主渠道：东方财富 push2\n");
        builder.append("备用渠道：新浪行情 hq.sinajs\n\n");
        builder.append("刷新成功：").append(result.successCount).append("/").append(result.totalCount).append("\n");
        builder.append("失败明细：\n");
        int maxCount = Math.min(result.failedItems.size(), 8);
        for (int i = 0; i < maxCount; i++) {
            StockQuoteFetcher.QuoteFailure item = result.failedItems.get(i);
            builder.append("- ").append(item.name).append(" ").append(item.code)
                    .append("：").append(item.reason).append("\n");
        }
        if (result.failedItems.size() > maxCount) {
            builder.append("另有 ").append(result.failedItems.size() - maxCount).append(" 只失败，可查看日志 MyMoneyQuote。\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("行情刷新失败")
                .setMessage(builder.toString())
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private ArrayList<Stock> filterStocks() {
        return stockRepository.filterStocks(stocks, selectedGroup);
    }

    private ArrayList<String> getGroups() {
        return stockRepository.getGroups(stocks);
    }

    private ArrayList<String> selectableGroups() {
        ArrayList<String> result = new ArrayList<String>();
        ArrayList<String> groups = getGroups();
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);
            if (!stockRepository.getAllGroup().equals(group)) {
                result.add(group);
            }
        }
        if (result.size() == 0) {
            result.add(getString(R.string.group_candidate));
        }
        return result;
    }

    private boolean isValidStockCode(String code) {
        return code.matches("[03689][0-9]{5}");
    }

    private boolean containsStockCode(String code) {
        for (int i = 0; i < stocks.size(); i++) {
            if (code.equals(stocks.get(i).code)) {
                return true;
            }
        }
        return false;
    }

    private String resolveStockGroup(Spinner groupSpinner, EditText newGroup) {
        String groupText = newGroup.getText().toString().trim();
        if (groupText.length() > 0) {
            return groupText;
        }
        Object selected = groupSpinner.getSelectedItem();
        if (selected == null || selected.toString().trim().length() == 0) {
            return getString(R.string.group_candidate);
        }
        return selected.toString().trim();
    }

    private int countRiskStocks() {
        return stockRepository.countRiskStocks(stocks);
    }

    private ArrayList<DecisionNote> getNotes(String stockCode) {
        return stockRepository.getNotes(notes, stockCode);
    }

    private void saveStocks() {
        stockRepository.saveStocks(stocks);
    }

    private void saveNotes() {
        stockRepository.saveNotes(notes);
    }

    private LinearLayout vertical() {
        return ui.vertical();
    }

    private LinearLayout horizontal() {
        return ui.horizontal();
    }

    private LinearLayout card() {
        return ui.card();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        return ui.text(value, sp, color, bold);
    }

    private TextView tag(String value, int bgColor, int textColor) {
        return ui.tag(value, bgColor, textColor);
    }

    private EditText input(String hint) {
        return ui.input(hint);
    }

    private Button primaryButton(String text) {
        return ui.primaryButton(text);
    }

    private Button ghostButton(String text) {
        return ui.ghostButton(text);
    }

    private GradientDrawable rounded(int color, int radius) {
        return ui.rounded(color, radius);
    }

    private GradientDrawable roundedStroke(int color, int radius, int strokeColor) {
        return ui.roundedStroke(color, radius, strokeColor);
    }

    private View spacer(int height) {
        return ui.spacer(height);
    }

    private View spacer(int width, int height) {
        return ui.spacer(width, height);
    }

    private View spaceWeight() {
        return ui.spaceWeight();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return ui.matchWrap();
    }

    private LinearLayout.LayoutParams matchHeight(int height) {
        return ui.matchHeight(height);
    }

    private LinearLayout.LayoutParams wrapHeight(int height) {
        return ui.wrapHeight(height);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return ui.wrapWrap();
    }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        return ui.weightWrap(weight);
    }

    private FrameLayout.LayoutParams matchMatch() {
        return ui.matchMatch();
    }

    private FrameLayout.LayoutParams pageParams(boolean detailPage) {
        return ui.pageParams(detailPage);
    }

    private int dp(int value) {
        return ui.dp(value);
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
    }

    private String textOrDefault(EditText editText, String defaultValue) {
        String value = editText.getText().toString().trim();
        return value.length() == 0 ? defaultValue : value;
    }

    private void hideKeyboard(View view) {
        ui.hideKeyboard(view);
    }
}
