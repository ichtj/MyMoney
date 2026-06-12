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
import androidx.lifecycle.ViewModelProvider;

import com.face.mymoney.R;
import com.face.mymoney.viewmodel.MainViewModel;
import com.face.mymoney.auth.LocalAuthManager;
import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.ai.DeepSeekStockAnalyzer;
import com.face.mymoney.crawler.DebugCrawlerActivity;
import com.face.mymoney.crawler.GeneralFinanceNewsFetcher;
import com.face.mymoney.crawler.HotStockCandidateFetcher;
import com.face.mymoney.crawler.MarketIndexFetcher;
import com.face.mymoney.crawler.StockBoardFetcher;
import com.face.mymoney.crawler.StockKLineFetcher;
import com.face.mymoney.crawler.StockLookupFetcher;
import com.face.mymoney.crawler.StockNewsFetcher;
import com.face.mymoney.crawler.StockQuoteFetcher;
import com.face.mymoney.data.DetailCache;
import com.face.mymoney.data.HotStockCandidateRepository;
import com.face.mymoney.data.LocalStockRepository;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.HotStockCandidate;
import com.face.mymoney.model.KLineItem;
import com.face.mymoney.model.MarketIndexQuote;
import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;
import com.face.mymoney.opinion.Opinion;
import com.face.mymoney.opinion.StockOpinionFetcher;
import com.face.mymoney.realtime.RealtimeDecisionAnalyzer;
import com.face.mymoney.ratio.WinLossRatioCalculator;
import com.face.mymoney.ratio.WinLossRatioInput;
import com.face.mymoney.ratio.WinLossRatioResult;
import com.face.mymoney.ui.MainUiKit;
import com.face.mymoney.ui.StockDisplayText;
import com.face.mymoney.ui.detail.WinLossRatioCard;
import com.face.mymoney.ui.home.HomePageBuilder;
import com.face.mymoney.ui.login.LoginPageBuilder;
import com.face.mymoney.ui.widget.KLineChartView;
import com.face.mymoney.ui.widget.PullRefreshScrollView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MyMoneyMain";
    private static final String BOARD_THEME_TAG = "MyMoneyBoardTheme";
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(234, 241, 255);
    private static final String TAB_WATCHLIST = "watchlist";
    private static final String TAB_NEWS = "news";
    private static final String TAB_HOT = "hot";
    private static final String TAB_PROFILE = "profile";
    private static final String NEWS_MODE_IMPORTANT = "important";
    private static final String NEWS_MODE_SUBSCRIBED = "subscribed";
    private static final String HOT_STATUS_LATEST = "latest";
    private static final String HOT_STATUS_CACHED = "cached";
    private static final String HOT_STATUS_DEGRADED = "degraded";
    private static final long QUOTE_AUTO_REFRESH_MILLIS = 10000L;
    private static final long QUOTE_AUTO_REFRESH_IDLE_MILLIS = 5 * 60 * 1000L;
    private static final long DETAIL_CACHE_TTL_MILLIS = 15 * 60 * 1000L;
    private static final long KLINE_CACHE_TTL_MILLIS = 30 * 60 * 1000L;

    private MainViewModel viewModel;
    private LocalAuthManager authManager;
    private LocalStockRepository stockRepository;
    private HotStockCandidateRepository hotStockRepository;
    private FrameLayout root;
    private ArrayList<Stock> stocks = new ArrayList<Stock>();
    private ArrayList<DecisionNote> notes = new ArrayList<DecisionNote>();
    private ArrayList<MarketIndexQuote> marketIndices = new ArrayList<MarketIndexQuote>();
    private ArrayList<HotStockCandidate> hotCandidates = new ArrayList<HotStockCandidate>();
    private ArrayList<News> importantNewsCache = new ArrayList<News>();
    private HashMap<String, ArrayList<News>> newsCache = new HashMap<String, ArrayList<News>>();
    private HashMap<String, ArrayList<Opinion>> opinionCache = new HashMap<String, ArrayList<Opinion>>();
    private HashMap<String, DeepSeekAnalysisResult> deepSeekAnalysisCache = new HashMap<String, DeepSeekAnalysisResult>();
    private HashMap<String, ArrayList<KLineItem>> kLineCache = new HashMap<String, ArrayList<KLineItem>>();
    private HashMap<String, Long> newsFetchedAtCache = new HashMap<String, Long>();
    private HashMap<String, Long> opinionFetchedAtCache = new HashMap<String, Long>();
    private HashMap<String, Long> analysisFetchedAtCache = new HashMap<String, Long>();
    private HashMap<String, Long> kLineFetchedAtCache = new HashMap<String, Long>();
    private HashMap<String, String> kLineErrorCache = new HashMap<String, String>();
    private HashSet<String> loadingNewsCodes = new HashSet<String>();
    private HashSet<String> loadingOpinionCodes = new HashSet<String>();
    private HashSet<String> loadingDeepSeekCodes = new HashSet<String>();
    private HashSet<String> loadingBoardCodes = new HashSet<String>();
    private HashSet<String> loadingKLineCodes = new HashSet<String>();
    private HashSet<String> addingStockCodes = new HashSet<String>();
    private boolean loadingImportantNews;
    private boolean loadingSubscribedNewsFeed;
    private boolean importantNewsLoadedOnce;
    private boolean subscribedNewsLoadedOnce;
    private boolean refreshingQuotes;
    private boolean loadingHotCandidates;
    private boolean weakNewsExpanded = false;
    private News linkedNewsEvent;
    private String hotDataStatus = "";
    private long hotDataRefreshedAtMillis;
    private String hotDataSourceSummary = "";
    private boolean homeEntryQuotesRefreshed;
    private HashMap<String, String> selectedNewsSources = new HashMap<String, String>();
    private HashMap<String, String> selectedOpinionSources = new HashMap<String, String>();
    private int pendingDetailScrollY = -1;
    private int pendingWatchlistScrollY = -1;
    private int pendingHotScrollY = -1;
    private LinearLayout currentHeroContainer;
    private LinearLayout currentKLineContainer;
    private LinearLayout currentRealtimeContainer;
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
    private boolean watchlistManageMode;
    private HashSet<String> managedWatchlistStockCodes = new HashSet<String>();
    private Stock currentStock;
    private MainUiKit ui;
    private OnBackPressedCallback detailBackCallback;
    private final Handler quoteRefreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable quoteAutoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshQuotes(false);
            scheduleQuoteAutoRefresh();
        }
    };

    /**
     * 当create时的回调处理。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.main);
        ui = new MainUiKit(this);
        authManager = new LocalAuthManager(this);
        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        stockRepository = viewModel.getStockRepository();
        hotStockRepository = viewModel.getHotStockRepository();
        initViewModel();
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
        refreshQuotesOnHomeEntry();
        if (hotCandidates.size() == 0) {
            refreshHotCandidates(false);
        }
        scheduleQuoteAutoRefresh();
    }

    /**
     * 当停止时的回调处理。
     */
    @Override
    protected void onStop() {
        quoteRefreshHandler.removeCallbacks(quoteAutoRefreshRunnable);
        super.onStop();
    }

    /**
     * 当启动时的回调处理。
     */
    @Override
    protected void onStart() {
        super.onStart();
        scheduleQuoteAutoRefresh();
    }

    /**
     * 当destroy时的回调处理。
     */
    @Override
    protected void onDestroy() {
        quoteRefreshHandler.removeCallbacks(quoteAutoRefreshRunnable);
        super.onDestroy();
    }

    /**
     * 当返回按下的时的回调处理。
     */
    private void initViewModel() {
        viewModel.getStocks().observe(this, new androidx.lifecycle.Observer<ArrayList<Stock>>() {
            @Override
            public void onChanged(ArrayList<Stock> newStocks) {
                stocks = newStocks;
                if (currentStock == null && TAB_WATCHLIST.equals(currentTab)) {
                    rememberWatchlistScroll();
                    showCurrentTab();
                }
            }
        });
        
        viewModel.getNotes().observe(this, new androidx.lifecycle.Observer<ArrayList<DecisionNote>>() {
            @Override
            public void onChanged(ArrayList<DecisionNote> newNotes) {
                notes = newNotes;
            }
        });

        viewModel.getHotCandidates().observe(this, new androidx.lifecycle.Observer<ArrayList<HotStockCandidate>>() {
            @Override
            public void onChanged(ArrayList<HotStockCandidate> newHot) {
                hotCandidates = newHot;
            }
        });

        viewModel.getMarketIndices().observe(this, new androidx.lifecycle.Observer<ArrayList<MarketIndexQuote>>() {
            @Override
            public void onChanged(ArrayList<MarketIndexQuote> newIndices) {
                marketIndices = newIndices;
            }
        });
        
        viewModel.getRefreshingQuotes().observe(this, new androidx.lifecycle.Observer<Boolean>() {
            @Override
            public void onChanged(Boolean refreshing) {
                refreshingQuotes = refreshing;
            }
        });

        viewModel.getLoadingHotCandidates().observe(this, new androidx.lifecycle.Observer<Boolean>() {
            @Override
            public void onChanged(Boolean loading) {
                loadingHotCandidates = loading;
            }
        });

        viewModel.getLoadingImportantNews().observe(this, new androidx.lifecycle.Observer<Boolean>() {
            @Override
            public void onChanged(Boolean loading) {
                loadingImportantNews = loading;
            }
        });

        viewModel.getLoadingSubscribedNews().observe(this, new androidx.lifecycle.Observer<Boolean>() {
            @Override
            public void onChanged(Boolean loading) {
                loadingSubscribedNewsFeed = loading;
            }
        });

        viewModel.getNewsLoadedEvent().observe(this, new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String event) {
                if ("important".equals(event)) {
                    importantNewsCache = viewModel.getImportantNewsCache();
                    importantNewsLoadedOnce = true;
                    if (TAB_NEWS.equals(currentTab)) {
                        finishNewsPullRefreshIfNeeded();
                        showCurrentTab();
                    }
                } else if ("subscribed".equals(event)) {
                    subscribedNewsLoadedOnce = true;
                    syncNewsCache();
                    if (TAB_NEWS.equals(currentTab)) {
                        finishNewsPullRefreshIfNeeded();
                        showCurrentTab();
                    }
                } else {
                    syncNewsCache();
                    if (currentStock != null && event.equals(currentStock.code)) {
                        refreshSourceSection(currentStock, true);
                        refreshRealtimeDecisionCard(currentStock);
                        refreshWinLossRatioCard(currentStock);
                        loadDeepSeekAnalysisIfReady(currentStock);
                    }
                }
            }
        });

        viewModel.getOpinionsLoadedEvent().observe(this, new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String stockCode) {
                syncOpinionCache();
                if (currentStock != null && stockCode.equals(currentStock.code)) {
                    refreshSourceSection(currentStock, false);
                    refreshRealtimeDecisionCard(currentStock);
                    refreshWinLossRatioCard(currentStock);
                    loadDeepSeekAnalysisIfReady(currentStock);
                }
            }
        });

        viewModel.getDeepSeekAnalysisLoadedEvent().observe(this, new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String event) {
                if (event.endsWith("_loading")) {
                    String code = event.substring(0, event.indexOf("_loading"));
                    loadingDeepSeekCodes.add(code);
                    if (currentStock != null && code.equals(currentStock.code)) {
                        refreshWinLossRatioCard(currentStock);
                    }
                } else {
                    loadingDeepSeekCodes.remove(event);
                    syncDeepSeekCache();
                    if (currentStock != null && event.equals(currentStock.code)) {
                        refreshRealtimeDecisionCard(currentStock);
                        refreshWinLossRatioCard(currentStock);
                    }
                }
            }
        });

        viewModel.getBoardLoadedEvent().observe(this, new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String event) {
                if ("all".equals(event)) {
                    if (currentStock == null && TAB_WATCHLIST.equals(currentTab)) {
                        showCurrentTab();
                    } else if (currentStock != null) {
                        refreshDetailQuoteSections(currentStock);
                    }
                } else {
                    loadingBoardCodes.remove(event);
                    if (currentStock != null && event.equals(currentStock.code)) {
                        refreshDetailQuoteSections(currentStock);
                    }
                }
            }
        });

        viewModel.getQuoteRefreshEvent().observe(this, new androidx.lifecycle.Observer<MainViewModel.QuoteRefreshEvent>() {
            @Override
            public void onChanged(MainViewModel.QuoteRefreshEvent event) {
                if (event.fetchedIndices.size() > 0) {
                    marketIndices = event.fetchedIndices;
                }
                if (event.manual) {
                    String refreshMessage = event.result.totalCount > 0
                            ? getString(R.string.refresh_quote_result, event.result.successCount, event.result.totalCount)
                            : (event.fetchedIndices.size() > 0 ? "大盘指数已刷新" : "大盘指数刷新失败");
                    Toast.makeText(MainActivity.this, refreshMessage, Toast.LENGTH_SHORT).show();
                }
                if (event.force) {
                    android.util.Log.d(TAG, "home entry quote refresh finish success="
                            + event.result.successCount + ", total=" + event.result.totalCount);
                }
                refreshVisibleQuoteUi(event.manual || event.force);
                if (event.result.failedItems.size() > 0) {
                    if (event.manual) {
                        showQuoteRefreshFailureDialog(event.result);
                    } else {
                        android.util.Log.w(TAG, "auto quote refresh failed "
                                + event.result.failedItems.size() + "/" + event.result.totalCount);
                    }
                }
            }
        });

        viewModel.getHotCandidatesRefreshEvent().observe(this, new androidx.lifecycle.Observer<MainViewModel.HotCandidatesRefreshEvent>() {
            @Override
            public void onChanged(MainViewModel.HotCandidatesRefreshEvent event) {
                hotDataStatus = event.sourceDegraded ? "degraded" : "latest";
                hotDataRefreshedAtMillis = System.currentTimeMillis();
                hotDataSourceSummary = event.sourceSummary;
                
                if (TAB_HOT.equals(currentTab)) {
                    View page = buildHotCandidatesPage();
                    tabContent.removeAllViews();
                    tabContent.addView(page, matchMatch());
                    if (page instanceof ScrollView) {
                        restoreHotScroll((ScrollView) page);
                    }
                }
                if (event.manual) {
                    String message = event.sourceDegraded
                            ? (event.usingCachedHotCandidates ? "获取热门数据降级，已加载本地缓存" : "获取热门数据降级，本地无缓存")
                            : "热门股收集完成：前 " + event.fetched.size() + " 名";
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void syncNewsCache() {
        if (stocks == null) return;
        for (Stock stock : stocks) {
            ArrayList<News> news = viewModel.getNewsCache(stock.code);
            if (news != null) {
                newsCache.put(stock.code, news);
            }
            Long t = viewModel.getNewsFetchedAt(stock.code);
            if (t != null) {
                newsFetchedAtCache.put(stock.code, t);
            }
        }
    }

    private void syncOpinionCache() {
        if (stocks == null) return;
        for (Stock stock : stocks) {
            ArrayList<Opinion> opinions = viewModel.getOpinionCache(stock.code);
            if (opinions != null) {
                opinionCache.put(stock.code, opinions);
            }
            Long t = viewModel.getOpinionFetchedAt(stock.code);
            if (t != null) {
                opinionFetchedAtCache.put(stock.code, t);
            }
        }
    }

    private void syncDeepSeekCache() {
        if (stocks == null) return;
        for (Stock stock : stocks) {
            DeepSeekAnalysisResult res = viewModel.getDeepSeekAnalysisCache(stock.code);
            if (res != null) {
                deepSeekAnalysisCache.put(stock.code, res);
            }
            Long t = viewModel.getAnalysisFetchedAt(stock.code);
            if (t != null) {
                analysisFetchedAtCache.put(stock.code, t);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (currentStock != null) {
            leaveStockDetail();
            return;
        }
        super.onBackPressed();
    }

    /**
     * install返回handler。
     */
    private void installBackHandler() {
        detailBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                leaveStockDetail();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, detailBackCallback);
    }

    /**
     * 设置详情返回启用的。
     */
    private void setDetailBackEnabled(boolean enabled) {
        if (detailBackCallback != null) {
            detailBackCallback.setEnabled(enabled);
        }
    }

    /**
     * leave股票详情。
     */
    private void leaveStockDetail() {
        currentStock = null;
        linkedNewsEvent = null;
        setDetailBackEnabled(false);
        showMainShell();
    }

    /**
     * 判断是否loggedin。
     */
    private boolean isLoggedIn() {
        return authManager.isLoggedIn();
    }

    /**
     * 弹出/显示登录。
     */
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
                refreshQuotesOnHomeEntry();
            }
        });
        root.addView(builder.build(), matchMatch());
    }

    /**
     * 弹出/显示主界面shell。
     */
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

    /**
     * 弹出/显示currenttab。
     */
    private void showCurrentTab() {
        if (tabContent == null) {
            return;
        }
        tabContent.removeAllViews();
        if (TAB_NEWS.equals(currentTab)) {
            loadNewsFeedIfNeeded();
            tabContent.addView(buildNewsFeedPage(), matchMatch());
            pendingWatchlistScrollY = -1;
        } else if (TAB_HOT.equals(currentTab)) {
            View hotPage = buildHotCandidatesPage();
            tabContent.addView(hotPage, matchMatch());
            if (hotPage instanceof ScrollView) {
                restoreHotScroll((ScrollView) hotPage);
            }
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

    /**
     * schedulequoteauto刷新。
     */
    private void scheduleQuoteAutoRefresh() {
        quoteRefreshHandler.removeCallbacks(quoteAutoRefreshRunnable);
        long delay = shouldAutoRefreshQuotes()
                ? QUOTE_AUTO_REFRESH_MILLIS
                : QUOTE_AUTO_REFRESH_IDLE_MILLIS;
        quoteRefreshHandler.postDelayed(quoteAutoRefreshRunnable, delay);
    }

    /**
     * 运行inbackground。
     */
    private void runInBackground(Runnable runnable) {
        viewModel.executeInBackground(runnable);
    }

    /**
     * 运行onUIifalive。
     */
    private void runOnUiIfAlive(Runnable runnable) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        runOnUiThread(runnable);
    }

    /**
     * 弹出/显示home。
     */
    private void showHome() {
        currentStock = null;
        setDetailBackEnabled(false);
        root.removeAllViews();
        currentTab = TAB_WATCHLIST;
        showMainShell();
    }

    /**
     * 构建watchlist页面。
     */
    private View buildWatchlistPage() {
        ArrayList<Stock> displayStocks = filterStocks();
        android.util.Log.d(BOARD_THEME_TAG, "watchlist build selectedGroup=" + selectedGroup
                + ", displayCount=" + displayStocks.size()
                + ", totalCount=" + stocks.size());
        refreshMissingBoards(displayStocks);
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
                managedWatchlistStockCodes.clear();
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
            public void onMoveStockTop(Stock stock) {
                moveStockToEdge(stock, true);
            }

            @Override
            public void onMoveStockBottom(Stock stock) {
                moveStockToEdge(stock, false);
            }

            @Override
            public void onManageModeChanged(boolean enabled) {
                setWatchlistManageMode(enabled);
            }

            @Override
            public void onManageStockToggled(Stock stock) {
                toggleManagedWatchlistStock(stock);
            }

            @Override
            public void onManageSelectAll() {
                selectAllManagedWatchlistStocks();
            }

            @Override
            public void onManageClearSelection() {
                clearManagedWatchlistStocks();
            }

            @Override
            public void onManageDeleteSelected() {
                confirmDeleteManagedWatchlistStocks();
            }

            @Override
            public void onManageMoveSelected() {
                showMoveManagedWatchlistStocksDialog();
            }

            @Override
            public void onDebugRequested() {
                if (BuildConfig.DEBUG) {
                    startActivity(new Intent(MainActivity.this, DebugCrawlerActivity.class));
                }
            }
        }, authManager.getUserName(), stocks, displayStocks, notes,
                buildWinLossOpportunityPercents(displayStocks), marketIndices,
                getGroups(), selectedGroup, countRiskStocks(),
                watchlistManageMode, managedWatchlistStockCodes);
        return builder.build();
    }

    /**
     * 构建胜率赔率opportunitypercents。
     */
    private HashMap<String, Integer> buildWinLossOpportunityPercents(ArrayList<Stock> displayStocks) {
        HashMap<String, Integer> result = new HashMap<String, Integer>();
        if (displayStocks == null || displayStocks.size() == 0) {
            return result;
        }
        WinLossRatioCalculator calculator = new WinLossRatioCalculator();
        for (int i = 0; i < displayStocks.size(); i++) {
            Stock stock = displayStocks.get(i);
            DeepSeekAnalysisResult analysis = deepSeekAnalysisCache.get(stock.code);
            Long fetchedAt = analysisFetchedAtCache.get(stock.code);
            if (analysis == null || fetchedAt == null) {
                DetailCache cache = stockRepository.loadDetailCache(stock.code);
                analysis = cache.analysis;
                fetchedAt = cache.analysisFetchedAt > 0L ? Long.valueOf(cache.analysisFetchedAt) : null;
                if (analysis != null && fetchedAt != null && !isDetailCacheExpired(fetchedAt)) {
                    deepSeekAnalysisCache.put(stock.code, analysis);
                    analysisFetchedAtCache.put(stock.code, fetchedAt);
                }
            }
            if (isValidWinLossAiReference(analysis, fetchedAt)) {
                WinLossRatioResult ratio = calculator.calculate(
                        new WinLossRatioInput(stock, getNotes(stock.code), analysis));
                if (ratio.hasDisplayRatio()) {
                    result.put(stock.code, Integer.valueOf(ratio.opportunityPercent));
                }
            }
        }
        return result;
    }

    /**
     * 判断是否有效的胜率赔率aireference。
     */
    private boolean isValidWinLossAiReference(DeepSeekAnalysisResult analysis, Long fetchedAt) {
        return analysis != null
                && analysis.success
                && !isDetailCacheExpired(fetchedAt);
    }

    /**
     * 置底tabs。
     */
    private View bottomTabs() {
        LinearLayout tabs = horizontal();
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(12), dp(7), dp(12), dp(7));
        tabs.setBackground(roundedStroke(Color.WHITE, 0, Color.rgb(226, 232, 240)));
        tabs.addView(tabItem(getString(R.string.tab_watchlist), TAB_WATCHLIST, R.drawable.ic_tab_watchlist), weightWrap(1));
        tabs.addView(spacer(8, 1));
        tabs.addView(tabItem(getString(R.string.tab_news_feed), TAB_NEWS, R.drawable.ic_tab_news), weightWrap(1));
        tabs.addView(spacer(8, 1));
        tabs.addView(tabItem("Top50热股", TAB_HOT, R.drawable.ic_tab_watchlist), weightWrap(1));
        tabs.addView(spacer(8, 1));
        tabs.addView(tabItem(getString(R.string.tab_profile), TAB_PROFILE, R.drawable.ic_tab_profile), weightWrap(1));
        return tabs;
    }

    /**
     * tabitem。
     */
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

    /**
     * 构建新闻资讯feed页面。
     */
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
        if (feedNews.size() > 0) {
            fixedTop.addView(spacer(8));
            fixedTop.addView(text("共 " + feedNews.size() + " 条资讯", 12, COLOR_SUB, false), matchWrap());
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
        ArrayList<News> sourceNews = deduplicateNewsList(feedNews, stocks);
        page.addView(buildNewsSummaryCard(sourceNews), matchWrap());
        page.addView(spacer(14));

        NewsGroup gRisk = new NewsGroup("🚨 风险预警", 15);
        NewsGroup gDirect = new NewsGroup("🎯 直接影响自选股", 15);
        NewsGroup gIndustryTheme = new NewsGroup("🔥 行业与题材催化", 8);
        NewsGroup gMacro = new NewsGroup("🌐 宏观市场环境", 8);
        NewsGroup gWeak = new NewsGroup("💤 其他相关资讯", 4);

        for (int i = 0; i < sourceNews.size(); i++) {
            News item = sourceNews.get(i);
            com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification cl = 
                com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(item, stocks);
            
            if ("硬风险".equals(cl.category) || "风险事件".equals(cl.category)) {
                gRisk.list.add(item);
            } else if (cl.relationType != null && cl.relationType.contains("直接相关")) {
                gDirect.list.add(item);
            } else if ((cl.relationType != null && cl.relationType.contains("行业相关")) || "题材催化".equals(cl.category)) {
                gIndustryTheme.list.add(item);
            } else if ("宏观影响".equals(cl.category)) {
                gMacro.list.add(item);
            } else {
                gWeak.list.add(item);
            }
        }

        // Sort items inside each group
        sortNewsByClassification(gRisk.list, stocks);
        sortNewsByClassification(gDirect.list, stocks);
        sortNewsByClassification(gIndustryTheme.list, stocks);
        sortNewsByClassification(gMacro.list, stocks);
        sortNewsByClassification(gWeak.list, stocks);

        NewsGroup[] groups = { gRisk, gDirect, gIndustryTheme, gMacro };
        for (int i = 0; i < groups.length; i++) {
            NewsGroup group = groups[i];
            if (group.list.size() > 0) {
                page.addView(groupHeader(group.iconHeader, group.list.size()), matchWrap());
                page.addView(spacer(6));
                
                int showCount = Math.min(group.list.size(), group.maxDisplayCount);
                for (int j = 0; j < showCount; j++) {
                    page.addView(feedNewsRow(group.list.get(j)), matchWrap());
                    page.addView(spacer(10));
                }
                
                if (group.list.size() > showCount) {
                    page.addView(text("    还有 " + (group.list.size() - showCount) + " 条较旧或低优资讯未展示", 11, COLOR_SUB, false), matchWrap());
                    page.addView(spacer(10));
                }
            }
        }

        if (gWeak.list.size() > 0) {
            if (weakNewsExpanded) {
                LinearLayout weakHeader = horizontal();
                weakHeader.setGravity(Gravity.CENTER_VERTICAL);
                weakHeader.setPadding(dp(2), dp(10), dp(2), dp(4));
                weakHeader.addView(text("💤 其他相关资讯", 14, COLOR_TEXT, true), weightWrap(1));
                
                TextView collapseBtn = text("收起", 12, COLOR_ACCENT, true);
                collapseBtn.setPadding(dp(10), dp(4), dp(10), dp(4));
                collapseBtn.setBackground(rounded(COLOR_ACCENT_SOFT, dp(12)));
                collapseBtn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        weakNewsExpanded = false;
                        showCurrentTab();
                    }
                });
                weakHeader.addView(collapseBtn, wrapWrap());
                page.addView(weakHeader, matchWrap());
                page.addView(spacer(6));

                int showCount = Math.min(gWeak.list.size(), 15);
                for (int j = 0; j < showCount; j++) {
                    page.addView(feedNewsRow(gWeak.list.get(j)), matchWrap());
                    page.addView(spacer(10));
                }
                if (gWeak.list.size() > showCount) {
                    page.addView(text("    还有 " + (gWeak.list.size() - showCount) + " 条资讯未展示", 11, COLOR_SUB, false), matchWrap());
                    page.addView(spacer(10));
                }
            } else {
                LinearLayout foldCard = card();
                foldCard.setPadding(dp(14), dp(12), dp(14), dp(12));
                LinearLayout foldContent = horizontal();
                foldContent.setGravity(Gravity.CENTER_VERTICAL);
                foldContent.addView(text("💤 更多弱相关资讯", 14, COLOR_TEXT, true), weightWrap(1));
                foldContent.addView(tag(gWeak.list.size() + " 条 · 点击展开", COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
                foldCard.addView(foldContent, matchWrap());
                foldCard.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        weakNewsExpanded = true;
                        showCurrentTab();
                    }
                });
                page.addView(foldCard, matchWrap());
                page.addView(spacer(10));
            }
        }

        rootPage.addView(scrollView, new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return rootPage;
    }

    /**
     * 新闻资讯modeswitchbar。
     */
    private View newsModeSwitchBar() {
        LinearLayout row = horizontal();
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        row.setBackground(rounded(Color.WHITE, dp(14)));
        row.addView(newsModeChip("重要财经", NEWS_MODE_IMPORTANT), weightWrap(1));
        row.addView(newsModeChip("自选订阅", NEWS_MODE_SUBSCRIBED), weightWrap(1));
        return row;
    }

    /**
     * 新闻资讯modechip。
     */
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

    /**
     * compact数据源barforfeed。
     */
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

    /**
     * 数据源switchbarforfeed。
     */
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

    /**
     * feed新闻资讯行布局。
     */
    private View feedNewsRow(final News item) {
        LinearLayout row = card();
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        TextView title = text(item.title, 15, COLOR_TEXT, true);
        title.setLineSpacing(dp(2), 1.0f);
        row.addView(title, matchWrap());
        row.addView(spacer(4));
        String freshnessLabel = com.face.mymoney.realtime.RealtimeDecisionAnalyzer.getFreshnessLabel(item, System.currentTimeMillis());
        String metaText = item.source + " · " + freshnessLabel + " · " + item.time;
        if (item.keyword != null && item.keyword.trim().length() > 0) {
            metaText += " · 关键词：" + item.keyword;
        }
        row.addView(text(metaText, 12, COLOR_SUB, false), matchWrap());
        row.addView(spacer(6));

        com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification classification = 
                com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(item, stocks);
        LinearLayout eventRow = horizontal();
        eventRow.setGravity(Gravity.CENTER_VERTICAL);
        eventRow.addView(realtimeChip(classification.category, classification.level), wrapWrap());
        eventRow.addView(spacer(8, 1));
        
        String explanation = classification.relationType;
        if (classification.matchedKeyword.length() > 0) {
            explanation = explanation + " · " + classification.matchedKeyword;
        }
        eventRow.addView(text(explanation, 12, COLOR_SUB, false), weightWrap(1));
        row.addView(eventRow, matchWrap());

        row.addView(spacer(6));
        LinearLayout impactRow = horizontal();
        impactRow.setGravity(Gravity.CENTER_VERTICAL);
        impactRow.addView(text("影响：", 12, COLOR_TEXT, true), wrapWrap());
        
        if (classification.matchedStocks != null && classification.matchedStocks.size() > 0) {
            for (int k = 0; k < classification.matchedStocks.size(); k++) {
                final Stock s = classification.matchedStocks.get(k);
                TextView stockTag = tag(s.name, Color.rgb(239, 246, 255), Color.rgb(29, 78, 216));
                stockTag.setPadding(dp(6), dp(2), dp(6), dp(2));
                stockTag.setTextSize(11);
                stockTag.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        linkedNewsEvent = item;
                        showStockDetail(s);
                    }
                });
                impactRow.addView(stockTag, wrapWrap());
                impactRow.addView(spacer(4, 1));
            }
        }
        
        String remainingText = "";
        String rawImpact = classification.impactObject;
        if (classification.matchedStocks != null && classification.matchedStocks.size() > 0) {
            int slashIdx = rawImpact.indexOf('/');
            if (slashIdx >= 0) {
                remainingText = rawImpact.substring(slashIdx).trim();
            }
        } else {
            remainingText = rawImpact;
        }
        
        if (remainingText.length() > 0) {
            impactRow.addView(text(remainingText, 12, COLOR_SUB, false), weightWrap(1));
        } else {
            impactRow.addView(new View(row.getContext()), weightWrap(1));
        }
        row.addView(impactRow, matchWrap());

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFeedNewsDialog(item);
            }
        });
        return row;
    }

    /**
     * 构建hot候选股票列表页面。
     */
    private View buildHotCandidatesPage() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = vertical();
        page.setPadding(dp(14), dp(14), dp(14), dp(22));
        scrollView.addView(page, pageParams(false));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = vertical();
        titleBox.addView(text("Top50 热股", 26, COLOR_TEXT, true), matchWrap());
        String subtitle = loadingHotCandidates
                ? "正在收集热度、资金、量价和4日趋势数据"
                : "主板/创业板，排除688和50元以上，按热度、资金、活跃度和机会排序";
        titleBox.addView(text(subtitle, 13, COLOR_SUB, false), matchWrap());
        titleBox.addView(spacer(4));
        titleBox.addView(text(hotDataStatusText(), 13, hotDataStatusColor(), true), matchWrap());
        header.addView(titleBox, weightWrap(1));
        Button refresh = ghostButton(loadingHotCandidates ? "刷新中" : "刷新");
        refresh.setEnabled(!loadingHotCandidates);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshHotCandidates(true);
            }
        });
        header.addView(refresh, wrapHeight(dp(36)));
        page.addView(header, matchWrap());
        page.addView(spacer(10));

        LinearLayout tip = card();
        tip.setPadding(dp(12), dp(10), dp(12), dp(10));
        RealtimeDecisionAnalyzer.MarketPulse pulse = RealtimeDecisionAnalyzer.analyzeMarket(marketIndices);
        tip.addView(text("即时环境：" + pulse.status + " · " + pulse.summary, 13,
                realtimeTextColor(pulse.level), true), matchWrap());
        tip.addView(spacer(5));
        tip.addView(text("候选池按当前可操作性标记为可关注、等回调、待确认或风险高；这里只做实时核验，不保存历史记录。", 13, COLOR_SUB, false), matchWrap());
        page.addView(tip, matchWrap());
        page.addView(spacer(10));

        if (hotCandidates.size() == 0) {
            LinearLayout empty = card();
            empty.addView(text(loadingHotCandidates ? "正在生成候选池" : "暂无Top50热股数据", 18, COLOR_TEXT, true), matchWrap());
            empty.addView(spacer(8));
            empty.addView(text(loadingHotCandidates
                    ? "首次刷新需要逐只补查近几日 K 线，请稍等。"
                    : "点击刷新后，会自动收集热门、有资金流入、交易活跃且短线仍有观察机会的股票。", 14, COLOR_SUB, false), matchWrap());
            page.addView(empty, matchWrap());
            return scrollView;
        }

        addGroupedHotCandidates(page);
        return scrollView;
    }

    /**
     * 按当前可操作状态分组展示hot候选。
     */
    private void addGroupedHotCandidates(LinearLayout page) {
        ArrayList<HotCandidateDisplayItem> focus = new ArrayList<HotCandidateDisplayItem>();
        ArrayList<HotCandidateDisplayItem> wait = new ArrayList<HotCandidateDisplayItem>();
        ArrayList<HotCandidateDisplayItem> neutral = new ArrayList<HotCandidateDisplayItem>();
        ArrayList<HotCandidateDisplayItem> risk = new ArrayList<HotCandidateDisplayItem>();
        for (int i = 0; i < hotCandidates.size(); i++) {
            HotStockCandidate candidate = hotCandidates.get(i);
            RealtimeDecisionAnalyzer.CandidateSignal signal = hotCandidateRealtimeSignal(candidate);
            HotCandidateDisplayItem item = new HotCandidateDisplayItem(i + 1, candidate, signal);
            if (signal.level == RealtimeDecisionAnalyzer.LEVEL_RISK) {
                risk.add(item);
            } else if (signal.level == RealtimeDecisionAnalyzer.LEVEL_GOOD) {
                focus.add(item);
            } else if (signal.level == RealtimeDecisionAnalyzer.LEVEL_WAIT) {
                wait.add(item);
            } else {
                neutral.add(item);
            }
        }

        page.addView(hotCandidateGroupSummary(focus.size(), wait.size(), neutral.size(), risk.size()), matchWrap());
        page.addView(spacer(10));
        addHotCandidateSection(page, "可关注", "市场、盘中状态或板块联动相对更支持，优先打开详情核验新闻和风险。", focus);
        addHotCandidateSection(page, "等回落", "当前有追高、换手、涨停距离或大盘因素，先等分歧后再看。", wait);
        addHotCandidateSection(page, "待确认", "没有硬风险，但也没有足够的实时共振证据。", neutral);
        addHotCandidateSection(page, "风险回避", "触发接近涨停、连续涨停、换手过热、板块过热或硬风险。", risk);
    }

    /**
     * hot候选分组summary。
     */
    private View hotCandidateGroupSummary(int focusCount, int waitCount, int neutralCount, int riskCount) {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(text("即时分组", 15, COLOR_TEXT, true), matchWrap());
        card.addView(spacer(5));
        TextView summary = text("可关注 " + focusCount
                + " · 等回落 " + waitCount
                + " · 待确认 " + neutralCount
                + " · 风险回避 " + riskCount, 13, COLOR_SUB, false);
        summary.setLineSpacing(dp(2), 1.0f);
        card.addView(summary, matchWrap());
        return card;
    }

    /**
     * 添加hot候选分组section。
     */
    private void addHotCandidateSection(LinearLayout page, String title, String desc,
                                        ArrayList<HotCandidateDisplayItem> items) {
        if (items.size() == 0) {
            return;
        }
        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(title + " · " + items.size(), 18, COLOR_TEXT, true), weightWrap(1));
        header.addView(tag(sectionShortLabel(title), sectionSoftColor(title), sectionTextColor(title)), wrapWrap());
        page.addView(header, matchWrap());
        TextView description = text(desc, 12, COLOR_SUB, false);
        description.setLineSpacing(dp(2), 1.0f);
        page.addView(description, matchWrap());
        page.addView(spacer(8));
        for (int i = 0; i < items.size(); i++) {
            HotCandidateDisplayItem item = items.get(i);
            page.addView(hotCandidateCard(item.rank, item.candidate, item.signal), matchWrap());
            page.addView(spacer(8));
        }
        page.addView(spacer(4));
    }

    private String sectionShortLabel(String title) {
        if ("可关注".equals(title)) {
            return "优先";
        }
        if ("等回落".equals(title)) {
            return "观察";
        }
        if ("风险回避".equals(title)) {
            return "避险";
        }
        return "核验";
    }

    private int sectionSoftColor(String title) {
        if ("可关注".equals(title)) {
            return realtimeSoftColor(RealtimeDecisionAnalyzer.LEVEL_GOOD);
        }
        if ("等回落".equals(title)) {
            return realtimeSoftColor(RealtimeDecisionAnalyzer.LEVEL_WAIT);
        }
        if ("风险回避".equals(title)) {
            return realtimeSoftColor(RealtimeDecisionAnalyzer.LEVEL_RISK);
        }
        return COLOR_ACCENT_SOFT;
    }

    private int sectionTextColor(String title) {
        if ("可关注".equals(title)) {
            return realtimeTextColor(RealtimeDecisionAnalyzer.LEVEL_GOOD);
        }
        if ("等回落".equals(title)) {
            return realtimeTextColor(RealtimeDecisionAnalyzer.LEVEL_WAIT);
        }
        if ("风险回避".equals(title)) {
            return realtimeTextColor(RealtimeDecisionAnalyzer.LEVEL_RISK);
        }
        return COLOR_ACCENT;
    }

    /**
     * hotdatastatus创建文本控件。
     */
    private String hotDataStatusText() {
        if (loadingHotCandidates) {
            return "正在获取最新数据";
        }
        String time = formatHotDataTime(hotDataRefreshedAtMillis);
        String source = hotDataSourceSummary == null || hotDataSourceSummary.length() == 0
                ? "来源未记录"
                : hotDataSourceSummary;
        if (HOT_STATUS_CACHED.equals(hotDataStatus)) {
            return "使用缓存：" + time + "，" + source;
        }
        if (HOT_STATUS_DEGRADED.equals(hotDataStatus)) {
            return "数据源不完整，实时结果仅供参考：" + time + "，" + source;
        }
        if (HOT_STATUS_LATEST.equals(hotDataStatus)) {
            return "最新数据：" + time + "，" + source;
        }
        return "尚未刷新数据";
    }

    /**
     * hotdatastatus颜色。
     */
    private int hotDataStatusColor() {
        if (HOT_STATUS_CACHED.equals(hotDataStatus) || HOT_STATUS_DEGRADED.equals(hotDataStatus)) {
            return Color.rgb(180, 83, 9);
        }
        if (HOT_STATUS_LATEST.equals(hotDataStatus)) {
            return Color.rgb(22, 101, 52);
        }
        return COLOR_SUB;
    }

    /**
     * 格式化hotdata时间。
     */
    private String formatHotDataTime(long millis) {
        if (millis <= 0L) {
            return "时间未知";
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(millis));
    }

    /**
     * hot候选股票创建卡片布局。
     */
    private View hotCandidateCard(final int rank, final HotStockCandidate candidate) {
        return hotCandidateCard(rank, candidate, hotCandidateRealtimeSignal(candidate));
    }

    /**
     * hot候选实时信号。
     */
    private RealtimeDecisionAnalyzer.CandidateSignal hotCandidateRealtimeSignal(HotStockCandidate candidate) {
        return RealtimeDecisionAnalyzer.analyzeCandidate(
                candidate, marketIndices, hotCandidates, hotDataRefreshedAtMillis,
                HOT_STATUS_DEGRADED.equals(hotDataStatus));
    }

    /**
     * hot候选股票创建卡片布局。
     */
    private View hotCandidateCard(final int rank, final HotStockCandidate candidate,
                                  RealtimeDecisionAnalyzer.CandidateSignal realtime) {
        LinearLayout card = card();
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rememberHotScroll();
                showStockDetail(stockFromHotCandidate(candidate));
            }
        });

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView rankView = singleLineText(String.valueOf(rank), 13, COLOR_ACCENT, true);
        rankView.setGravity(Gravity.CENTER);
        rankView.setBackground(rounded(COLOR_ACCENT_SOFT, dp(10)));
        top.addView(rankView, new LinearLayout.LayoutParams(dp(30), dp(30)));
        top.addView(spacer(8, 1));

        LinearLayout nameBox = vertical();
        nameBox.addView(singleLineText(candidate.name, 17, COLOR_TEXT, true), matchWrap());
        nameBox.addView(singleLineText(candidate.code + " · " + candidate.market, 12, COLOR_SUB, false), matchWrap());
        nameBox.addView(spacer(5));
        nameBox.addView(industryBadge(candidate.industry), wrapHeight(dp(26)));
        top.addView(nameBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout scoreBox = vertical();
        scoreBox.setGravity(Gravity.RIGHT);
        TextView score = singleLineText(candidate.totalScore + "分", 16, COLOR_ACCENT, true);
        score.setGravity(Gravity.RIGHT);
        scoreBox.addView(score, matchWrap());
        int changeColor = candidate.changePercent.startsWith("-") ? Color.rgb(7, 148, 85) : Color.rgb(217, 45, 32);
        TextView change = singleLineText(candidate.changePercent, 13, changeColor, true);
        change.setGravity(Gravity.RIGHT);
        scoreBox.addView(change, matchWrap());
        top.addView(scoreBox, new LinearLayout.LayoutParams(dp(76), LinearLayout.LayoutParams.WRAP_CONTENT));
        card.addView(top, matchWrap());

        card.addView(spacer(8));
        LinearLayout realtimeRow = horizontal();
        realtimeRow.setGravity(Gravity.CENTER_VERTICAL);
        realtimeRow.addView(realtimeChip(realtime.status, realtime.level), wrapWrap());
        realtimeRow.addView(spacer(8, 1));
        TextView realtimeText = text(realtime.summary, 12, COLOR_TEXT, false);
        realtimeText.setLineSpacing(dp(2), 1.0f);
        realtimeRow.addView(realtimeText, weightWrap(1));
        card.addView(realtimeRow, matchWrap());
        card.addView(spacer(7));
        card.addView(text(realtime.marketStatus + " · " + realtime.freshness
                + " · 风险触发：" + realtime.riskText, 12, COLOR_SUB, false), matchWrap());
        card.addView(spacer(7));
        card.addView(text("盘中状态：" + realtime.intradayStatus + " · "
                + realtime.intradaySummary, 12, COLOR_SUB, false), matchWrap());
        card.addView(spacer(7));
        card.addView(text("板块联动：" + realtime.sectorStatus + " · "
                + realtime.sectorSummary, 12, COLOR_SUB, false), matchWrap());
        card.addView(spacer(7));
        card.addView(text("因子克制：" + realtime.factorDiscipline, 12, COLOR_SUB, false), matchWrap());
        card.addView(spacer(7));
        card.addView(text("4日涨幅 " + candidate.fourDayChangePercent + " · 活跃 " + candidate.activeDays4d + "/4天"
                + " · 主力净流入 " + candidate.mainNetInflow + " · 成交额 " + candidate.amount, 13, COLOR_SUB, false), matchWrap());
        card.addView(text("换手 " + candidate.turnoverRate + " · 量比 " + candidate.volumeRatio
                + " · 4日均额 " + candidate.averageAmount4d, 13, COLOR_SUB, false), matchWrap());
        card.addView(spacer(5));
        card.addView(text(candidate.reason + " · 风险：" + candidate.riskTag, 12, COLOR_SUB, false), matchWrap());
        card.addView(spacer(8));

        LinearLayout actions = horizontal();
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(text("点击卡片查看详情", 12, COLOR_SUB, false), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        Button add = ghostButton(containsStockCode(candidate.code) ? "已自选" : "加入自选");
        add.setEnabled(!containsStockCode(candidate.code));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addHotCandidateToWatchlist(candidate);
            }
        });
        actions.addView(add, wrapHeight(dp(34)));
        card.addView(actions, matchWrap());
        return card;
    }

    /**
     * hot候选展示item。
     */
    private static class HotCandidateDisplayItem {
        final int rank;
        final HotStockCandidate candidate;
        final RealtimeDecisionAnalyzer.CandidateSignal signal;

        HotCandidateDisplayItem(int rank, HotStockCandidate candidate,
                                RealtimeDecisionAnalyzer.CandidateSignal signal) {
            this.rank = rank;
            this.candidate = candidate;
            this.signal = signal;
        }
    }

    /**
     * 行业徽章。
     */
    private TextView industryBadge(String industry) {
        String label = industry == null || industry.length() == 0 || "--".equals(industry)
                ? "行业待识别"
                : industry;
        TextView badge = singleLineText(label, 12, COLOR_ACCENT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(9), dp(4), dp(9), dp(4));
        badge.setBackground(rounded(COLOR_ACCENT_SOFT, dp(13)));
        return badge;
    }

    /**
     * 构建个人中心页面。
     */
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

    /**
     * 个人中心登录创建卡片布局。
     */
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

    /**
     * 个人中心创建卡片布局。
     */
    private LinearLayout profileCard() {
        LinearLayout card = card();
        card.setBackground(roundedStroke(Color.WHITE, dp(18), Color.rgb(226, 232, 240)));
        return card;
    }

    /**
     * 头像视图。
     */
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

    /**
     * 头像创建文本控件。
     */
    private String avatarText(String name) {
        if (name == null || name.trim().length() == 0) {
            return "U";
        }
        String value = name.trim();
        return value.substring(0, 1).toUpperCase(Locale.CHINA);
    }

    /**
     * 弹出/显示edit个人中心对话框。
     */
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

    /**
     * 弹出/显示股票详情。
     */
    private void showStockDetail(final Stock stock) {
        currentStock = stock;
        setDetailBackEnabled(true);
        prepareDetailCache(stock);
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
        Button refresh = ghostButton("刷新");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshCurrentStockRealtime(stock);
            }
        });
        nav.addView(refresh, wrapHeight(dp(36)));
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

        currentKLineContainer = vertical();
        currentKLineContainer.addView(kLineCard(stock), matchWrap());
        page.addView(currentKLineContainer, matchWrap());
        page.addView(spacer(10));

        currentRealtimeContainer = vertical();
        currentRealtimeContainer.addView(realtimeDecisionCard(stock), matchWrap());
        page.addView(currentRealtimeContainer, matchWrap());
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
        loadStockKLineIfNeeded(stock, false);
        refreshExpiredDetailData(stock);
        loadDeepSeekAnalysisIfReady(stock);
    }

    /**
     * quoteitem。
     */
    private View quoteItem(String label, String value, int valueColor) {
        LinearLayout box = vertical();
        box.addView(text(label, 12, Color.rgb(203, 213, 225), false), matchWrap());
        box.addView(spacer(4));
        box.addView(text(value, 18, valueColor, true), matchWrap());
        return box;
    }

    /**
     * hero创建卡片布局。
     */
    private View heroCard(Stock stock) {
        android.util.Log.d(BOARD_THEME_TAG, "detailHero " + StockDisplayText.debugSummary(this, stock, 14));
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
        sub.addView(text(/*getString(R.string.stock_board) + " " + */stockBoardText(stock), 12, Color.rgb(203, 213, 225), false), wrapWrap());
        hero.addView(sub, matchWrap());
        return hero;
    }

    /**
     * K-line chart card.
     */
    private View kLineCard(final Stock stock) {
        LinearLayout card = card();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = vertical();
        titleBox.addView(text("\u65e5 K \u7ebf", 18, COLOR_TEXT, true), matchWrap());
        titleBox.addView(text("\u8fd1 120 \u4e2a\u4ea4\u6613\u65e5\uff0c\u53ef\u6a2a\u5411\u6ed1\u52a8\u67e5\u770b\u65e7\u6570\u636e", 12, COLOR_SUB, false), matchWrap());
        header.addView(titleBox, weightWrap(1));
        Button refresh = ghostButton(loadingKLineCodes.contains(stock.code)
                ? "\u52a0\u8f7d\u4e2d"
                : "\u5237\u65b0K\u7ebf");
        refresh.setEnabled(!loadingKLineCodes.contains(stock.code));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadStockKLine(stock, true);
            }
        });
        header.addView(refresh, wrapHeight(dp(34)));
        card.addView(header, matchWrap());
        card.addView(spacer(10));

        KLineChartView chartView = new KLineChartView(this);
        chartView.setKLines(kLineCache.get(stock.code));
        chartView.setLoading(loadingKLineCodes.contains(stock.code));
        chartView.setErrorMessage(kLineErrorCache.get(stock.code));
        card.addView(chartView, new LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                dp(240)));

        card.addView(spacer(6));
        card.addView(text(kLineStatusText(stock), 11, COLOR_SUB, false), matchWrap());
        return card;
    }

    /**
     * Refresh K-line chart card.
     */
    private void refreshKLineCard(Stock stock) {
        if (currentKLineContainer == null || stock == null) {
            return;
        }
        currentKLineContainer.removeAllViews();
        currentKLineContainer.addView(kLineCard(stock), matchWrap());
    }

    /**
     * Load K-line if missing or expired.
     */
    private void loadStockKLineIfNeeded(Stock stock, boolean manual) {
        if (stock == null || stock.code == null) {
            return;
        }
        ArrayList<KLineItem> cached = kLineCache.get(stock.code);
        Long fetchedAt = kLineFetchedAtCache.get(stock.code);
        if (!manual && cached != null && cached.size() > 0 && !isKLineCacheExpired(fetchedAt)) {
            return;
        }
        loadStockKLine(stock, manual);
    }

    /**
     * Load daily K-line data.
     */
    private void loadStockKLine(final Stock stock, final boolean manual) {
        if (stock == null || stock.code == null) {
            return;
        }
        if (loadingKLineCodes.contains(stock.code)) {
            return;
        }
        loadingKLineCodes.add(stock.code);
        kLineErrorCache.remove(stock.code);
        refreshKLineCard(stock);
        if (manual) {
            Toast.makeText(this, "\u6b63\u5728\u5237\u65b0K\u7ebf", Toast.LENGTH_SHORT).show();
        }
        runInBackground(new Runnable() {
            @Override
            public void run() {
                final StockKLineFetcher.KLineResult result = new StockKLineFetcher().fetchDailyKLine(stock, 120);
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        loadingKLineCodes.remove(stock.code);
                        if (result.success && result.items.size() > 0) {
                            kLineCache.put(stock.code, result.items);
                            kLineFetchedAtCache.put(stock.code, System.currentTimeMillis());
                            kLineErrorCache.remove(stock.code);
                            if (manual) {
                                Toast.makeText(MainActivity.this, "\u004b\u7ebf\u5df2\u66f4\u65b0", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            kLineErrorCache.put(stock.code, result.message);
                            if (manual) {
                                Toast.makeText(MainActivity.this, "\u004b\u7ebf\u83b7\u53d6\u5931\u8d25", Toast.LENGTH_SHORT).show();
                            }
                        }
                        if (currentStock != null && stock.code.equals(currentStock.code)) {
                            refreshKLineCard(stock);
                        }
                    }
                });
            }
        });
    }

    /**
     * K-line cache timeout.
     */
    private boolean isKLineCacheExpired(Long fetchedAt) {
        return fetchedAt == null || fetchedAt.longValue() <= 0L
                || System.currentTimeMillis() - fetchedAt.longValue() > KLINE_CACHE_TTL_MILLIS;
    }

    /**
     * K-line status text.
     */
    private String kLineStatusText(Stock stock) {
        String text = "\u6570\u636e\u6e90\uff1a\u4e1c\u65b9\u8d22\u5bcc\u5386\u53f2\u884c\u60c5";
        if (stock == null || stock.code == null) {
            return text;
        }
        ArrayList<KLineItem> items = kLineCache.get(stock.code);
        if (items != null && items.size() > 0) {
            text = text + " | " + items.size() + " \u6761";
        }
        Long fetchedAt = kLineFetchedAtCache.get(stock.code);
        if (fetchedAt != null && fetchedAt.longValue() > 0L) {
            text = text + " | \u66f4\u65b0 " + new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(new Date(fetchedAt.longValue()));
        }
        if (loadingKLineCodes.contains(stock.code)) {
            text = text + " | \u52a0\u8f7d\u4e2d";
        }
        return text;
    }

    /**
     * 构建即时分析卡片。
     */
    private View realtimeDecisionCard(Stock stock) {
        ArrayList<News> newsList = newsCache.get(stock.code);
        if (newsList == null) {
            newsList = new ArrayList<News>();
        } else {
            newsList = new ArrayList<News>(newsList);
        }
        if (linkedNewsEvent != null) {
            boolean exists = false;
            for (int k = 0; k < newsList.size(); k++) {
                if (newsList.get(k).title.equals(linkedNewsEvent.title)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                newsList.add(0, linkedNewsEvent);
            }
        }

        RealtimeDecisionAnalyzer.StockSignal signal = RealtimeDecisionAnalyzer.analyzeStock(
                stock,
                newsList,
                opinionCache.get(stock.code),
                newsFetchedAtCache.get(stock.code),
                opinionFetchedAtCache.get(stock.code),
                deepSeekAnalysisCache.get(stock.code),
                analysisFetchedAtCache.get(stock.code),
                marketIndices,
                hotCandidates,
                System.currentTimeMillis());

        LinearLayout card = card();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = vertical();
        titleBox.addView(text("即时分析", 18, COLOR_TEXT, true), matchWrap());
        titleBox.addView(text("只基于当前行情、指数、新闻/观点和 AI 分项，不保存历史记录。", 12, COLOR_SUB, false), matchWrap());
        header.addView(titleBox, weightWrap(1));
        header.addView(realtimeChip(signal.status, signal.level), wrapWrap());
        card.addView(header, matchWrap());

        if (linkedNewsEvent != null) {
            card.addView(spacer(8));
            LinearLayout linkedBox = vertical();
            linkedBox.setPadding(dp(12), dp(10), dp(12), dp(10));
            linkedBox.setBackground(rounded(Color.rgb(254, 243, 199), dp(8)));
            
            LinearLayout linkedTitleRow = horizontal();
            linkedTitleRow.setGravity(Gravity.CENTER_VERTICAL);
            linkedTitleRow.addView(text("🔗 关联触发事件", 12, Color.rgb(180, 83, 9), true), weightWrap(1));
            
            ArrayList<Stock> singleStockList = new ArrayList<Stock>();
            singleStockList.add(stock);
            com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification linkedCl = 
                    com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(linkedNewsEvent, singleStockList);
            
            linkedTitleRow.addView(realtimeChip(linkedCl.category, linkedCl.level), wrapWrap());
            linkedBox.addView(linkedTitleRow, matchWrap());
            linkedBox.addView(spacer(4));
            
            TextView eventTitleText = text(linkedNewsEvent.title, 13, COLOR_TEXT, true);
            eventTitleText.setLineSpacing(dp(2), 1.0f);
            linkedBox.addView(eventTitleText, matchWrap());
            
            String explanation = linkedCl.relationType;
            if (linkedCl.matchedKeyword.length() > 0) {
                explanation = explanation + " · " + linkedCl.matchedKeyword;
            }
            linkedBox.addView(spacer(2));
            linkedBox.addView(text(explanation + " (" + linkedNewsEvent.source + ")", 11, COLOR_SUB, false), matchWrap());
            
            card.addView(linkedBox, matchWrap());
        }

        card.addView(spacer(10));
        TextView summary = text(signal.summary, 14, COLOR_TEXT, false);
        summary.setLineSpacing(dp(3), 1.0f);
        summary.setPadding(dp(12), dp(9), dp(12), dp(9));
        summary.setBackground(rounded(realtimeSoftColor(signal.level), dp(12)));
        card.addView(summary, matchWrap());

        card.addView(spacer(10));
        LinearLayout freshness = horizontal();
        freshness.setGravity(Gravity.CENTER_VERTICAL);
        freshness.addView(realtimeSmallChip(signal.newsFreshness), wrapWrap());
        freshness.addView(spacer(6, 1));
        freshness.addView(realtimeSmallChip(signal.opinionFreshness), wrapWrap());
        freshness.addView(spacer(6, 1));
        freshness.addView(realtimeSmallChip(signal.aiFreshness), wrapWrap());
        card.addView(freshness, matchWrap());

        card.addView(spacer(10));
        card.addView(realtimeLine("市场环境", signal.marketStatus + " · " + signal.marketSummary), matchWrap());
        card.addView(spacer(6));
        card.addView(realtimeLine("盘中状态", signal.intradayStatus + " · " + signal.intradaySummary), matchWrap());
        card.addView(spacer(6));
        card.addView(realtimeLine("板块联动", signal.sectorStatus + " · " + signal.sectorSummary), matchWrap());
        card.addView(spacer(6));
        card.addView(realtimeLine("事件强度", signal.newsStatus + " · " + signal.newsSummary), matchWrap());
        card.addView(spacer(6));
        card.addView(realtimeLine("事件细节", signal.newsDetail), matchWrap());
        card.addView(spacer(6));
        card.addView(realtimeLine("因子克制", signal.factorDiscipline), matchWrap());
        card.addView(spacer(6));
        card.addView(realtimeLine("风险触发", signal.riskText), matchWrap());
        return card;
    }

    /**
     * 刷新即时分析卡片。
     */
    private void refreshRealtimeDecisionCard(Stock stock) {
        if (currentRealtimeContainer == null || stock == null) {
            return;
        }
        currentRealtimeContainer.removeAllViews();
        currentRealtimeContainer.addView(realtimeDecisionCard(stock), matchWrap());
    }

    /**
     * 强制刷新当前个股的即时分析输入。
     */
    private void refreshCurrentStockRealtime(final Stock stock) {
        if (stock == null) {
            return;
        }
        Toast.makeText(this, "正在刷新当前个股实时数据", Toast.LENGTH_SHORT).show();
        newsFetchedAtCache.remove(stock.code);
        opinionFetchedAtCache.remove(stock.code);
        invalidateDetailAnalysis(stock.code);
        refreshDetailQuoteSections(stock);
        refreshManualStockBoardTheme(stock);
        refreshQuotes(false, true);
        loadStockKLine(stock, false);
        loadStockNews(stock);
        loadStockOpinions(stock);
    }

    /**
     * 即时分析信息行。
     */
    private View realtimeLine(String label, String value) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.TOP);
        TextView labelView = text(label, 12, COLOR_SUB, true);
        row.addView(labelView, new LinearLayout.LayoutParams(dp(66), android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView valueView = text(value, 12, COLOR_TEXT, false);
        valueView.setLineSpacing(dp(2), 1.0f);
        row.addView(valueView, weightWrap(1));
        return row;
    }

    /**
     * 即时分析状态标签。
     */
    private TextView realtimeChip(String value, int level) {
        return tag(value, realtimeSoftColor(level), realtimeTextColor(level));
    }

    /**
     * 即时分析小标签。
     */
    private TextView realtimeSmallChip(String value) {
        TextView chip = text(value, 11, COLOR_SUB, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(8), dp(4), dp(8), dp(4));
        chip.setBackground(rounded(Color.rgb(248, 250, 252), dp(12)));
        return chip;
    }

    private int realtimeSoftColor(int level) {
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

    private int realtimeTextColor(int level) {
        if (level == RealtimeDecisionAnalyzer.LEVEL_GOOD) {
            return Color.rgb(217, 45, 32);
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_RISK) {
            return Color.rgb(7, 148, 85);
        }
        if (level == RealtimeDecisionAnalyzer.LEVEL_WAIT) {
            return Color.rgb(180, 83, 9);
        }
        return COLOR_ACCENT;
    }

    /**
     * singleline创建文本控件。
     */
    private TextView singleLineText(String value, int sp, int color, boolean bold) {
        TextView view = text(value, sp, color, bold);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        view.setEllipsize(android.text.TextUtils.TruncateAt.END);
        return view;
    }

    /**
     * 胜率赔率盈亏期望比创建卡片布局。
     */
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

    /**
     * 刷新胜率赔率盈亏期望比创建卡片布局。
     */
    private void refreshWinLossRatioCard(Stock stock) {
        if (currentWinLossContainer == null) {
            return;
        }
        currentWinLossContainer.removeAllViews();
        currentWinLossContainer.addView(winLossRatioCard(stock), matchWrap());
    }

    /**
     * company创建卡片布局。
     */
    private View companyCard(Stock stock) {
        android.util.Log.d(BOARD_THEME_TAG, "detailCompany " + StockDisplayText.debugSummary(this, stock, 14));
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
        LinearLayout row3 = horizontal();
        row3.addView(compactInfo(getString(R.string.stock_board), stockBoardText(stock)), matchWrap());
        card.addView(row3, matchWrap());
        card.addView(spacer(8));
        card.addView(infoRow(getString(R.string.main_business), stock.mainBusiness), matchWrap());
        return card;
    }

    /**
     * compactinfo。
     */
    private View compactInfo(String label, String value) {
        LinearLayout box = vertical();
        box.setPadding(dp(10), dp(8), dp(10), dp(8));
        box.setBackground(rounded(Color.rgb(248, 250, 252), dp(10)));
        box.addView(singleLineText(label, 11, COLOR_SUB, false), matchWrap());
        box.addView(spacer(3));
        box.addView(singleLineText(value, 13, COLOR_TEXT, true), matchWrap());
        return box;
    }

    /**
     * 股票board创建文本控件。
     */
    private String stockBoardText(Stock stock) {
        return StockDisplayText.board(this, stock);
    }

    /**
     * 新闻资讯列表。
     */
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
            ArrayList<Stock> singleStockList = new ArrayList<Stock>();
            singleStockList.add(stock);
            sourceNews = deduplicateNewsList(sourceNews, singleStockList);
            sortNewsByClassification(sourceNews, singleStockList);
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

    /**
     * 新闻资讯行布局。
     */
    private View newsRow(final Stock stock, final News item) {
        LinearLayout row = card();
        row.setPadding(dp(12), dp(9), dp(12), dp(9));
        
        com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification classification = 
                com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(item, stocks);
        
        row.addView(singleLineText(item.title, 14, COLOR_TEXT, true), matchWrap());
        row.addView(spacer(4));
        String freshnessLabel = com.face.mymoney.realtime.RealtimeDecisionAnalyzer.getFreshnessLabel(item, System.currentTimeMillis());
        row.addView(singleLineText(item.source + " · " + freshnessLabel + " · " + item.time, 11, COLOR_SUB, false), matchWrap());
        row.addView(spacer(6));
        
        LinearLayout eventRow = horizontal();
        eventRow.setGravity(Gravity.CENTER_VERTICAL);
        eventRow.addView(realtimeChip(classification.category, classification.level), wrapWrap());
        eventRow.addView(spacer(8, 1));
        
        String explanation = classification.relationType;
        if (classification.matchedKeyword.length() > 0) {
            explanation = explanation + " · " + classification.matchedKeyword;
        }
        eventRow.addView(text(explanation, 12, COLOR_SUB, false), weightWrap(1));
        row.addView(eventRow, matchWrap());

        row.addView(spacer(6));
        LinearLayout impactRow = horizontal();
        impactRow.setGravity(Gravity.CENTER_VERTICAL);
        impactRow.addView(text("影响：", 12, COLOR_TEXT, true), wrapWrap());
        
        if (classification.matchedStocks != null && classification.matchedStocks.size() > 0) {
            for (int k = 0; k < classification.matchedStocks.size(); k++) {
                final Stock s = classification.matchedStocks.get(k);
                TextView stockTag = tag(s.name, Color.rgb(239, 246, 255), Color.rgb(29, 78, 216));
                stockTag.setPadding(dp(6), dp(2), dp(6), dp(2));
                stockTag.setTextSize(11);
                stockTag.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        linkedNewsEvent = item;
                        showStockDetail(s);
                    }
                });
                impactRow.addView(stockTag, wrapWrap());
                impactRow.addView(spacer(4, 1));
            }
        }
        
        String remainingText = "";
        String rawImpact = classification.impactObject;
        if (classification.matchedStocks != null && classification.matchedStocks.size() > 0) {
            int slashIdx = rawImpact.indexOf('/');
            if (slashIdx >= 0) {
                remainingText = rawImpact.substring(slashIdx).trim();
            }
        } else {
            remainingText = rawImpact;
        }
        
        if (remainingText.length() > 0) {
            impactRow.addView(text(remainingText, 12, COLOR_SUB, false), weightWrap(1));
        } else {
            impactRow.addView(new View(row.getContext()), weightWrap(1));
        }
        row.addView(impactRow, matchWrap());

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNewsDialog(stock, item);
            }
        });
        return row;
    }

    /**
     * 决策笔记列表。
     */
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

    /**
     * 舆情观点列表。
     */
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

    /**
     * 数据源switchbar。
     */
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

    /**
     * 滚动选中的数据源into视图。
     */
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

    /**
     * 刷新数据源section。
     */
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

    /**
     * 刷新决策笔记section。
     */
    private void refreshNoteSection(Stock stock) {
        if (currentNoteContainer == null) {
            return;
        }
        currentNoteContainer.removeAllViews();
        currentNoteContainer.addView(noteList(stock), matchWrap());
    }

    /**
     * 选中的新闻资讯数据源。
     */
    private String selectedNewsSource(Stock stock, ArrayList<String> sources) {
        String selected = selectedNewsSources.get(stock.code);
        if (selected != null && sources.contains(selected)) {
            return selected;
        }
        String first = sources.get(0);
        selectedNewsSources.put(stock.code, first);
        return first;
    }

    /**
     * 选中的舆情观点数据源。
     */
    private String selectedOpinionSource(Stock stock, ArrayList<String> sources) {
        String selected = selectedOpinionSources.get(stock.code);
        if (selected != null && sources.contains(selected)) {
            return selected;
        }
        String first = sources.get(0);
        selectedOpinionSources.put(stock.code, first);
        return first;
    }

    /**
     * 记住详情滚动。
     */
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

    /**
     * 恢复详情滚动。
     */
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

    /**
     * 记住watchlist滚动。
     */
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

    /**
     * 恢复watchlist滚动。
     */
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

    /**
     * 记住hot滚动。
     */
    private void rememberHotScroll() {
        pendingHotScrollY = -1;
        if (tabContent == null || tabContent.getChildCount() == 0) {
            return;
        }
        View child = tabContent.getChildAt(0);
        if (child instanceof ScrollView) {
            pendingHotScrollY = ((ScrollView) child).getScrollY();
        }
    }

    /**
     * 恢复hot滚动。
     */
    private void restoreHotScroll(final ScrollView scrollView) {
        if (pendingHotScrollY < 0) {
            return;
        }
        final int scrollY = pendingHotScrollY;
        pendingHotScrollY = -1;
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, scrollY);
            }
        });
    }

    /**
     * 数据源header。
     */
    private View sourceHeader(String source, int count, String suffix) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(2));
        row.addView(tag(source, COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
        row.addView(spacer(8, 1));
        row.addView(text(count + " " + suffix, 12, COLOR_SUB, false), wrapWrap());
        return row;
    }

    /**
     * 舆情观点行布局。
     */
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

    /**
     * 获取舆情观点数据源列表。
     */
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

    /**
     * 获取新闻资讯数据源列表。
     */
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

    /**
     * 获取新闻资讯根据数据源。
     */
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

    /**
     * 根据价值分类对新闻列表进行排序（硬风险、风险事件等排在前面，弱相关靠后）。
     */
    private void sortNewsByClassification(ArrayList<News> newsList, final ArrayList<Stock> stocksToMatch) {
        if (newsList == null || newsList.size() <= 1) {
            return;
        }
        java.util.Collections.sort(newsList, new java.util.Comparator<News>() {
            @Override
            public int compare(News o1, News o2) {
                int p1 = com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(o1, stocksToMatch).priority;
                int p2 = com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(o2, stocksToMatch).priority;
                return Integer.compare(p2, p1); // 降序排序
            }
        });
    }

    /**
     * 新闻分组辅助类。
     */
    private static class NewsGroup {
        String iconHeader;
        ArrayList<News> list = new ArrayList<News>();
        int maxDisplayCount;

        NewsGroup(String iconHeader, int maxDisplayCount) {
            this.iconHeader = iconHeader;
            this.maxDisplayCount = maxDisplayCount;
        }
    }

    /**
     * 渲染新闻分组表头。
     */
    private View groupHeader(String title, int count) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(10), dp(2), dp(4));
        row.addView(text(title, 14, COLOR_TEXT, true), weightWrap(1));
        row.addView(tag(count + " 条", COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
        return row;
    }

    /**
     * 构建今日新闻即时摘要卡片。
     */
    private View buildNewsSummaryCard(ArrayList<News> sourceNews) {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(roundedStroke(Color.WHITE, dp(14), Color.rgb(224, 231, 255)));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text("📊 今日订阅资讯即时摘要", 15, COLOR_TEXT, true), weightWrap(1));
        card.addView(header, matchWrap());
        card.addView(spacer(10));

        int hardRiskCount = 0;
        int positiveCount = 0;
        int themeCount = 0;
        ArrayList<String> affectedStocks = new ArrayList<String>();
        ArrayList<String> mainThemes = new ArrayList<String>();

        for (int i = 0; i < sourceNews.size(); i++) {
            News item = sourceNews.get(i);
            com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification cl = 
                com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(item, stocks);

            if ("硬风险".equals(cl.category)) {
                hardRiskCount++;
            } else if ("正面增量".equals(cl.category)) {
                positiveCount++;
            } else if ("题材催化".equals(cl.category)) {
                themeCount++;
            }

            String title = item.title == null ? "" : item.title;
            String content = item.content == null ? "" : item.content;
            String keyword = item.keyword == null ? "" : item.keyword;
            String text = (title + " " + keyword + " " + content).toUpperCase(java.util.Locale.US);

            if (stocks != null) {
                for (int j = 0; j < stocks.size(); j++) {
                    Stock s = stocks.get(j);
                    if ((s.name != null && text.contains(s.name.toUpperCase(java.util.Locale.US))) || 
                        (s.code != null && text.contains(s.code.toUpperCase(java.util.Locale.US)))) {
                        if (!affectedStocks.contains(s.name)) {
                            affectedStocks.add(s.name);
                        }
                    }
                }
            }

            String[] themeKeywords = {
                "AI", "算力", "半导体", "机器人", "低空经济", "新能源", "光模块", "CPO", "数据中心", "存储", "芯片", "军工", "消费电子", "人工智能", "低空", "商业航天", "固态电池", "大模型", "低空飞行", "新质生产力", "人形机器人"
            };
            for (int k = 0; k < themeKeywords.length; k++) {
                if (text.contains(themeKeywords[k].toUpperCase(java.util.Locale.US))) {
                    if (!mainThemes.contains(themeKeywords[k])) {
                        mainThemes.add(themeKeywords[k]);
                    }
                }
            }
        }

        LinearLayout statsRow = horizontal();
        statsRow.setGravity(Gravity.CENTER_VERTICAL);
        
        statsRow.addView(buildStatItem("硬风险", hardRiskCount + " 条", realtimeTextColor(com.face.mymoney.realtime.RealtimeDecisionAnalyzer.LEVEL_RISK)), weightWrap(1));
        statsRow.addView(spacer(1, 12), wrapHeight(dp(20)));
        statsRow.addView(buildStatItem("正面增量", positiveCount + " 条", realtimeTextColor(com.face.mymoney.realtime.RealtimeDecisionAnalyzer.LEVEL_GOOD)), weightWrap(1));
        statsRow.addView(spacer(1, 12), wrapHeight(dp(20)));
        statsRow.addView(buildStatItem("题材催化", themeCount + " 条", COLOR_ACCENT), weightWrap(1));
        card.addView(statsRow, matchWrap());
        card.addView(spacer(12));

        String stocksText = affectedStocks.size() > 0 ? joinStrings(affectedStocks, "、") : "无";
        LinearLayout stocksRow = horizontal();
        stocksRow.addView(text("受影响自选股：", 13, COLOR_TEXT, true), wrapWrap());
        stocksRow.addView(text(stocksText, 13, COLOR_SUB, false), weightWrap(1));
        card.addView(stocksRow, matchWrap());
        card.addView(spacer(6));

        String themesText = mainThemes.size() > 0 ? joinStrings(mainThemes, " / ") : "无";
        LinearLayout themesRow = horizontal();
        themesRow.addView(text("主要题材：", 13, COLOR_TEXT, true), wrapWrap());
        themesRow.addView(text(themesText, 13, COLOR_SUB, false), weightWrap(1));
        card.addView(themesRow, matchWrap());
        card.addView(spacer(10));

        String adviceText = "";
        int adviceBgColor = 0;
        int adviceTextColor = 0;
        if (hardRiskCount > 0) {
            adviceText = "重点核验风险";
            adviceBgColor = realtimeSoftColor(com.face.mymoney.realtime.RealtimeDecisionAnalyzer.LEVEL_RISK);
            adviceTextColor = realtimeTextColor(com.face.mymoney.realtime.RealtimeDecisionAnalyzer.LEVEL_RISK);
        } else if (positiveCount > 0) {
            adviceText = "关注正面增量";
            adviceBgColor = realtimeSoftColor(com.face.mymoney.realtime.RealtimeDecisionAnalyzer.LEVEL_GOOD);
            adviceTextColor = realtimeTextColor(com.face.mymoney.realtime.RealtimeDecisionAnalyzer.LEVEL_GOOD);
        } else {
            adviceText = "暂无强事件";
            adviceBgColor = Color.rgb(241, 245, 249);
            adviceTextColor = Color.rgb(71, 85, 105);
        }

        LinearLayout adviceBanner = horizontal();
        adviceBanner.setGravity(Gravity.CENTER_VERTICAL);
        adviceBanner.setPadding(dp(12), dp(8), dp(12), dp(8));
        adviceBanner.setBackground(rounded(adviceBgColor, dp(8)));
        
        adviceBanner.addView(text("当前建议：", 13, adviceTextColor, true), wrapWrap());
        adviceBanner.addView(text(adviceText, 13, adviceTextColor, true), weightWrap(1));
        card.addView(adviceBanner, matchWrap());

        return card;
    }

    private View buildStatItem(String label, String value, int valueColor) {
        LinearLayout col = vertical();
        col.setGravity(Gravity.CENTER);
        col.addView(text(label, 11, COLOR_SUB, false), wrapWrap());
        col.addView(spacer(2));
        col.addView(text(value, 15, valueColor, true), wrapWrap());
        return col;
    }

    private String joinStrings(ArrayList<String> list, String delimiter) {
        if (list == null || list.size() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    /**
     * 新闻去重逻辑，应用：标题完全相同、标题相似度高、同一股票+同一关键词+同一天合并等规则。
     */
    private ArrayList<News> deduplicateNewsList(ArrayList<News> rawNews, final ArrayList<Stock> stocksToMatch) {
        if (rawNews == null || rawNews.size() <= 1) {
            return rawNews == null ? new ArrayList<News>() : new ArrayList<News>(rawNews);
        }

        ArrayList<News> deduplicated = new ArrayList<News>();
        
        for (int i = 0; i < rawNews.size(); i++) {
            News item = rawNews.get(i);
            boolean isDuplicate = false;
            int dupIndex = -1;

            com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification clItem = 
                com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(item, stocksToMatch);
            String itemText = ((item.title == null ? "" : item.title) + " " + 
                              (item.keyword == null ? "" : item.keyword) + " " + 
                              (item.content == null ? "" : item.content)).toUpperCase(java.util.Locale.US);

            for (int j = 0; j < deduplicated.size(); j++) {
                News existing = deduplicated.get(j);
                
                // 1. 标题完全相同或相似度高
                if (isSimilarTitle(item.title, existing.title)) {
                    isDuplicate = true;
                    dupIndex = j;
                    break;
                }

                // 2. 同一股票 + 同一关键词 + 同一天
                com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification clExisting = 
                    com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(existing, stocksToMatch);
                String existingText = ((existing.title == null ? "" : existing.title) + " " + 
                                       (existing.keyword == null ? "" : existing.keyword) + " " + 
                                       (existing.content == null ? "" : existing.content)).toUpperCase(java.util.Locale.US);

                boolean sameStock = false;
                if (stocksToMatch != null) {
                    for (int k = 0; k < stocksToMatch.size(); k++) {
                        Stock s = stocksToMatch.get(k);
                        boolean itemMatches = (s.name != null && itemText.contains(s.name.toUpperCase(java.util.Locale.US))) || 
                                              (s.code != null && itemText.contains(s.code.toUpperCase(java.util.Locale.US)));
                        boolean existingMatches = (s.name != null && existingText.contains(s.name.toUpperCase(java.util.Locale.US))) || 
                                                  (s.code != null && existingText.contains(s.code.toUpperCase(java.util.Locale.US)));
                        if (itemMatches && existingMatches) {
                            sameStock = true;
                            break;
                        }
                    }
                }

                boolean sameKeyword = clItem.matchedKeyword.length() > 0 && clItem.matchedKeyword.equals(clExisting.matchedKeyword);
                
                String date1 = extractDate(item.time);
                String date2 = extractDate(existing.time);
                boolean sameDay = date1.length() > 0 && date1.equals(date2);

                if (sameStock && sameKeyword && sameDay) {
                    isDuplicate = true;
                    dupIndex = j;
                    break;
                }
            }

            if (isDuplicate) {
                // 优先保留内容更完整、来源更可靠的新闻
                News existing = deduplicated.get(dupIndex);
                if (isBetterNews(item, existing)) {
                    deduplicated.set(dupIndex, item);
                }
            } else {
                deduplicated.add(item);
            }
        }

        return deduplicated;
    }

    private boolean isSimilarTitle(String t1, String t2) {
        if (t1 == null || t2 == null) {
            return false;
        }
        t1 = t1.trim();
        t2 = t2.trim();
        if (t1.equals(t2)) {
            return true;
        }
        int lcs = getLCSLength(t1, t2);
        double ratio = (double) lcs / Math.max(t1.length(), t2.length());
        return ratio >= 0.75; // 75% 相似度阈值
    }

    private int getLCSLength(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[m][n];
    }

    private String extractDate(String timeStr) {
        if (timeStr == null || timeStr.length() < 5) {
            return "";
        }
        int spaceIdx = timeStr.indexOf(' ');
        if (spaceIdx > 0) {
            return timeStr.substring(0, spaceIdx);
        }
        if (timeStr.length() >= 10) {
            return timeStr.substring(0, 10);
        }
        return timeStr;
    }

    private boolean isBetterNews(News n1, News n2) {
        int score1 = getSourceReliabilityScore(n1.source);
        int score2 = getSourceReliabilityScore(n2.source);
        if (score1 != score2) {
            return score1 > score2;
        }
        int len1 = n1.content == null ? 0 : n1.content.trim().length();
        int len2 = n2.content == null ? 0 : n2.content.trim().length();
        if (len1 != len2) {
            return len1 > len2;
        }
        int tlen1 = n1.title == null ? 0 : n1.title.length();
        int tlen2 = n2.title == null ? 0 : n2.title.length();
        return tlen1 >= tlen2;
    }

    private int getSourceReliabilityScore(String source) {
        if (source == null) return 0;
        String s = source.toUpperCase(java.util.Locale.US);
        if (s.contains("财联社") || s.contains("CLS")) {
            return 4;
        }
        if (s.contains("东方财富") || s.contains("EASTMONEY")) {
            return 3;
        }
        if (s.contains("新浪") || s.contains("SINA")) {
            return 2;
        }
        return 1;
    }

    /**
     * 获取舆情观点列表根据数据源。
     */
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

    /**
     * info行布局。
     */
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

    /**
     * section标题。
     */
    private TextView sectionTitle(String title) {
        TextView view = text(title, 17, COLOR_TEXT, true);
        view.setIncludeFontPadding(false);
        return view;
    }

    /**
     * 弹出/显示添加股票对话框。
     */
    private void showAddStockDialog() {
        showStockFormDialog(null);
    }

    /**
     * 弹出/显示edit股票对话框。
     */
    private void showEditStockDialog(final Stock stock) {
        showStockFormDialog(stock);
    }

    /**
     * 弹出/显示股票form对话框。
     */
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
                        if (isEdit) {
                            String nameText = name.getText().toString().trim();
                            if (nameText.length() == 0) {
                                Toast.makeText(MainActivity.this, getString(R.string.stock_required), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String groupText = resolveStockGroup(groupSpinner, newGroup);
                            stockRepository.saveGroupIfNeeded(groupText);
                            editingStock.name = nameText;
                            editingStock.groupName = groupText;
                            editingStock.remark = remark.getText().toString().trim();
                            saveStocks();
                            selectedGroup = groupText;
                            dialog.dismiss();
                            showCurrentTab();
                        } else {
                            submitAddStockForm(dialog, button, code, name, groupSpinner, newGroup, remark);
                        }
                    }
                });
            }
        });
        dialog.show();
    }

    /**
     * 提交添加自选股表单，允许只填代码或名称。
     */
    private void submitAddStockForm(final AlertDialog dialog, final Button button,
                                    EditText code, EditText name, Spinner groupSpinner,
                                    EditText newGroup, EditText remark) {
        final String codeText = code.getText().toString().trim();
        final String nameText = name.getText().toString().trim();
        final String groupText = resolveStockGroup(groupSpinner, newGroup);
        final String remarkText = remark.getText().toString().trim();
        if (codeText.length() == 0 && nameText.length() == 0) {
            Toast.makeText(this, getString(R.string.stock_required), Toast.LENGTH_SHORT).show();
            return;
        }
        if (codeText.length() > 0 && !isValidStockCode(codeText)) {
            Toast.makeText(this, getString(R.string.stock_code_invalid), Toast.LENGTH_SHORT).show();
            return;
        }
        if (codeText.length() > 0 && containsStockCode(codeText)) {
            Toast.makeText(this, getString(R.string.stock_duplicate), Toast.LENGTH_SHORT).show();
            return;
        }

        button.setEnabled(false);
        button.setText("查询中");
        runInBackground(new Runnable() {
            @Override
            public void run() {
                final StockLookupFetcher.StockIdentity identity = resolveStockIdentity(codeText, nameText);
                final Stock createdStock = identity == null
                        ? null
                        : stockFromIdentity(identity, groupText, remarkText);
                ensureStockRealtimeBeforeAdd(createdStock);
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        if (!dialog.isShowing()) {
                            return;
                        }
                        button.setEnabled(true);
                        button.setText(getString(R.string.save));
                        if (identity == null || identity.code.length() == 0 || identity.name.length() == 0) {
                            Toast.makeText(MainActivity.this, "未找到匹配的A股，请补充股票代码或检查名称", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (containsStockCode(identity.code)) {
                            Toast.makeText(MainActivity.this, getString(R.string.stock_duplicate), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (createdStock == null) {
                            Toast.makeText(MainActivity.this, "股票信息解析失败，请重试", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        stockRepository.saveGroupIfNeeded(groupText);
                        stocks.add(0, createdStock);
                        saveStocks();
                        selectedGroup = groupText;
                        dialog.dismiss();
                        showCurrentTab();
                        Toast.makeText(MainActivity.this, "已添加：" + createdStock.name + " " + createdStock.code, Toast.LENGTH_SHORT).show();
                        refreshManualStockBoardTheme(createdStock);
                    }
                });
            }
        });
    }

    /**
     * 根据已解析身份创建自选股。
     */
    private Stock stockFromIdentity(StockLookupFetcher.StockIdentity identity, String groupText, String remarkText) {
        Stock stock = createDefaultStock(identity.code, identity.name, groupText, remarkText);
        if (identity.market.length() > 0) {
            stock.market = identity.market;
        }
        if (usefulCandidateText(identity.industry)) {
            stock.industry = identity.industry.trim();
        }
        return stock;
    }

    /**
     * 添加前同步行业/板块，避免刚加入列表就显示待同步行业。
     */
    private void ensureStockRealtimeBeforeAdd(Stock stock) {
        if (stock == null) {
            return;
        }
        new StockQuoteFetcher().refreshQuoteDetailed(stock);
        if (StockDisplayText.hasBoard(stock)) {
            return;
        }
        ArrayList<Stock> targets = new ArrayList<Stock>();
        targets.add(stock);
        new StockBoardFetcher().refreshBoards(targets);
    }

    /**
     * 解析股票输入。
     */
    private StockLookupFetcher.StockIdentity resolveStockIdentity(String codeText, String nameText) {
        StockLookupFetcher.StockIdentity local = resolveStockIdentityLocally(codeText, nameText);
        if (local != null) {
            return local;
        }
        return new StockLookupFetcher().resolve(codeText, nameText);
    }

    /**
     * 优先使用已有本地数据解析，减少一次网络请求。
     */
    private StockLookupFetcher.StockIdentity resolveStockIdentityLocally(String codeText, String nameText) {
        String code = codeText == null ? "" : codeText.trim();
        String name = nameText == null ? "" : nameText.trim();
        for (int i = 0; i < hotCandidates.size(); i++) {
            HotStockCandidate candidate = hotCandidates.get(i);
            if ((code.length() > 0 && code.equals(candidate.code))
                    || (name.length() > 0 && name.equals(candidate.name))) {
                return new StockLookupFetcher.StockIdentity(candidate.code, candidate.name,
                        candidate.market, candidateBoardText(candidate));
            }
        }
        if (code.length() > 0 && name.length() > 0) {
            String market = code.startsWith("6") || code.startsWith("9") ? "沪市" : "深市";
            return new StockLookupFetcher.StockIdentity(code, name, market);
        }
        return null;
    }

    /**
     * 弹出/显示添加分组对话框。
     */
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

    /**
     * 弹出/显示添加决策笔记对话框。
     */
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
                        invalidateDetailAnalysis(stock.code);
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

    /**
     * 弹出/显示新闻资讯对话框。
     */
    private void showNewsDialog(Stock stock, News news) {
        ArrayList<Stock> singleStockList = new ArrayList<Stock>();
        singleStockList.add(stock);
        com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification classification = 
                com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(news, singleStockList);
        String explanation = classification.relationType;
        if (classification.matchedKeyword.length() > 0) {
            explanation = explanation + " · " + classification.matchedKeyword;
        }

        String content = news.content == null ? "" : news.content.trim();
        if (content.length() == 0 || content.equals(news.title)) {
            content = "摘要：" + news.title;
        }

        String message = news.source + " · " + news.time + "\n"
                + "价值分类：" + classification.category + " (" + explanation + ")\n\n"
                + content + "\n\n"
                + "关键词：" + news.keyword;

        new AlertDialog.Builder(this)
                .setTitle(news.title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    /**
     * 弹出/显示feed新闻资讯对话框。
     */
    private void showFeedNewsDialog(News news) {
        com.face.mymoney.realtime.RealtimeDecisionAnalyzer.NewsValClassification classification = 
                com.face.mymoney.realtime.RealtimeDecisionAnalyzer.classifyNews(news, stocks);
        String explanation = classification.relationType;
        if (classification.matchedKeyword.length() > 0) {
            explanation = explanation + " · " + classification.matchedKeyword;
        }

        String content = news.content == null ? "" : news.content.trim();
        android.util.Log.d(TAG, "showFeedNewsDialog title=" + news.title
                + ", source=" + news.source
                + ", contentLength=" + content.length()
                + ", contentPreview=" + (content.length() > 120 ? content.substring(0, 120) : content));
        if (content.length() == 0 || content.equals(news.title)) {
            content = "摘要：" + news.title;
        }

        String message = news.source + " · " + news.time + "\n"
                + "价值分类：" + classification.category + " (" + explanation + ")\n\n"
                + content + "\n\n"
                + "关键词：" + news.keyword;

        new AlertDialog.Builder(this)
                .setTitle(news.title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    /**
     * 弹出/显示舆情观点对话框。
     */
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

    /**
     * 弹出/显示deepseekanalysis对话框。
     */
    private void showDeepSeekAnalysisDialog(Stock stock) {
        if (stock == null) {
            return;
        }
        DeepSeekAnalysisResult result = deepSeekAnalysisCache.get(stock.code);
        String message;
        if (result == null) {
            message = loadingDeepSeekCodes.contains(stock.code)
                    ? "DeepSeek 正在为信息一致性、新闻情绪等分项生成结构化评分。"
                    : "DeepSeek 分项分析尚未生成。";
        } else if (result.success && result.hasUsableFactors()) {
            message = result.displayText()
                    + "\n\n校验：DeepSeek 只输出各 AI 分项分数；最终胜负比由本地价格计划和各分项按权重合成。";
        } else {
            message = result.displayText()
                    + "\n\n校验：DeepSeek 未给出通过校验的分项评分，最终胜负比不纳入 AI 分项。";
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("DeepSeek 分项分析")
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null);
        if (!loadingDeepSeekCodes.contains(stock.code) && !isUsableDeepSeekAnalysis(result)) {
            builder.setNegativeButton("重新分析", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface dialog, int which) {
                    invalidateDetailAnalysis(stock.code);
                    loadDeepSeekAnalysisIfReady(stock);
                }
            });
        }
        builder.show();
    }

    /**
     * 确认删除股票。
     */
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

    /**
     * 移动股票转换为edge。
     */
    private void moveStockToEdge(Stock stock, boolean toTop) {
        if (stock == null || stock.code == null) {
            return;
        }
        int index = -1;
        for (int i = 0; i < stocks.size(); i++) {
            if (stock.code.equals(stocks.get(i).code)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return;
        }
        int targetIndex = toTop ? 0 : stocks.size() - 1;
        if (index == targetIndex) {
            Toast.makeText(this, stock.name + (toTop ? " 已在顶部" : " 已在底部"), Toast.LENGTH_SHORT).show();
            return;
        }
        Stock moved = stocks.remove(index);
        if (toTop) {
            stocks.add(0, moved);
        } else {
            stocks.add(moved);
        }
        saveStocks();
        rememberWatchlistScroll();
        showCurrentTab();
        Toast.makeText(this, moved.name + (toTop ? " 已置顶" : " 已置底"), Toast.LENGTH_SHORT).show();
    }

    /**
     * 刷新hot候选股票列表。
     */
    /**
     * Switches watchlist batch management mode.
     */
    private void setWatchlistManageMode(boolean enabled) {
        if (enabled && filterStocks().size() == 0) {
            Toast.makeText(this, "当前列表没有可管理的自选股", Toast.LENGTH_SHORT).show();
            return;
        }
        watchlistManageMode = enabled;
        if (!enabled) {
            managedWatchlistStockCodes.clear();
        }
        rememberWatchlistScroll();
        showCurrentTab();
    }

    /**
     * Toggles one stock in watchlist batch selection.
     */
    private void toggleManagedWatchlistStock(Stock stock) {
        if (stock == null || stock.code == null || stock.code.length() == 0) {
            return;
        }
        if (managedWatchlistStockCodes.contains(stock.code)) {
            managedWatchlistStockCodes.remove(stock.code);
        } else {
            managedWatchlistStockCodes.add(stock.code);
        }
        rememberWatchlistScroll();
        showCurrentTab();
    }

    /**
     * Selects all visible stocks in the current watchlist group.
     */
    private void selectAllManagedWatchlistStocks() {
        ArrayList<Stock> displayStocks = filterStocks();
        for (int i = 0; i < displayStocks.size(); i++) {
            Stock stock = displayStocks.get(i);
            if (stock != null && stock.code != null && stock.code.length() > 0) {
                managedWatchlistStockCodes.add(stock.code);
            }
        }
        rememberWatchlistScroll();
        showCurrentTab();
    }

    /**
     * Clears current watchlist batch selection.
     */
    private void clearManagedWatchlistStocks() {
        managedWatchlistStockCodes.clear();
        rememberWatchlistScroll();
        showCurrentTab();
    }

    /**
     * Confirms batch deletion for selected watchlist stocks.
     */
    private void confirmDeleteManagedWatchlistStocks() {
        final int selectedCount = countManagedWatchlistStocks();
        if (selectedCount == 0) {
            Toast.makeText(this, "请先选择要删除的自选股", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("删除自选股")
                .setMessage("确认删除选中的 " + selectedCount + " 只自选股？决策记录会保留，便于后续复盘。")
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        for (int i = stocks.size() - 1; i >= 0; i--) {
                            Stock stock = stocks.get(i);
                            if (stock != null && managedWatchlistStockCodes.contains(stock.code)) {
                                stocks.remove(i);
                            }
                        }
                        saveStocks();
                        managedWatchlistStockCodes.clear();
                        watchlistManageMode = false;
                        currentStock = null;
                        showCurrentTab();
                        Toast.makeText(MainActivity.this, "已删除 " + selectedCount + " 只自选股", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /**
     * Shows the batch group move dialog for selected watchlist stocks.
     */
    private void showMoveManagedWatchlistStocksDialog() {
        final int selectedCount = countManagedWatchlistStocks();
        if (selectedCount == 0) {
            Toast.makeText(this, "请先选择要移动的自选股", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(10), dp(20), 0);
        final Spinner groupSpinner = new Spinner(this);
        final ArrayList<String> stockGroups = selectableGroups();
        ArrayAdapter<String> groupAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, stockGroups);
        groupSpinner.setAdapter(groupAdapter);
        int selectedIndex = stockGroups.indexOf(selectedGroup);
        if (selectedIndex >= 0) {
            groupSpinner.setSelection(selectedIndex);
        }
        final EditText newGroup = input(getString(R.string.stock_new_group_hint));
        newGroup.setSingleLine(true);

        form.addView(text("将选中的 " + selectedCount + " 只自选股移动到：", 13, COLOR_SUB, false), matchWrap());
        form.addView(spacer(10));
        form.addView(text(getString(R.string.stock_group_select_hint), 12, COLOR_SUB, false), matchWrap());
        form.addView(groupSpinner, matchHeight(dp(48)));
        form.addView(spacer(10));
        form.addView(newGroup, matchHeight(dp(52)));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("移动分组")
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton("移动", null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String groupText = resolveStockGroup(groupSpinner, newGroup);
                        stockRepository.saveGroupIfNeeded(groupText);
                        int movedCount = 0;
                        for (int i = 0; i < stocks.size(); i++) {
                            Stock stock = stocks.get(i);
                            if (stock != null && managedWatchlistStockCodes.contains(stock.code)) {
                                stock.groupName = groupText;
                                movedCount++;
                            }
                        }
                        saveStocks();
                        selectedGroup = groupText;
                        managedWatchlistStockCodes.clear();
                        watchlistManageMode = false;
                        dialog.dismiss();
                        showCurrentTab();
                        Toast.makeText(MainActivity.this, "已移动 " + movedCount + " 只自选股", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        dialog.show();
    }

    /**
     * Counts selected stocks that still exist in the watchlist.
     */
    private int countManagedWatchlistStocks() {
        int count = 0;
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            if (stock != null && stock.code != null && managedWatchlistStockCodes.contains(stock.code)) {
                count++;
            }
        }
        return count;
    }

    private void refreshHotCandidates(final boolean manual) {
        if (manual) {
            if (TAB_HOT.equals(currentTab) && currentStock == null) {
                rememberHotScroll();
            }
            Toast.makeText(this, "正在收集Top50热股", Toast.LENGTH_SHORT).show();
        }
        viewModel.refreshHotCandidates(manual);
    }

    /**
     * 添加hot候选股票转换为watchlist。
     */
    private void addHotCandidateToWatchlist(HotStockCandidate candidate) {
        if (candidate == null || candidate.code == null || candidate.code.length() == 0) {
            return;
        }
        if (containsStockCode(candidate.code)) {
            Toast.makeText(this, getString(R.string.stock_duplicate), Toast.LENGTH_SHORT).show();
            return;
        }
        if (addingStockCodes.contains(candidate.code)) {
            Toast.makeText(this, "正在同步行情和行业标识", Toast.LENGTH_SHORT).show();
            return;
        }
        addingStockCodes.add(candidate.code);
        Toast.makeText(this, "正在同步行情和行业标识", Toast.LENGTH_SHORT).show();
        final HotStockCandidate target = candidate;
        runInBackground(new Runnable() {
            @Override
            public void run() {
                final Stock stock = stockFromHotCandidate(target);
                ensureStockRealtimeBeforeAdd(stock);
                runOnUiIfAlive(new Runnable() {
                    @Override
                    public void run() {
                        addingStockCodes.remove(target.code);
                        if (containsStockCode(target.code)) {
                            Toast.makeText(MainActivity.this, getString(R.string.stock_duplicate), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        stocks.add(0, stock);
                        stockRepository.saveGroupIfNeeded(stock.groupName);
                        saveStocks();
                        Toast.makeText(MainActivity.this, "已加入自选：" + stock.name, Toast.LENGTH_SHORT).show();
                        rememberHotScroll();
                        showCurrentTab();
                    }
                });
            }
        });
    }

    /**
     * 股票从hot候选股票。
     */
    private Stock stockFromHotCandidate(HotStockCandidate candidate) {
        Stock stock = createDefaultStock(candidate.code, candidate.name,
                getString(R.string.group_candidate), "");
        stock.market = candidate.market;
        stock.price = candidate.price;
        stock.changePercent = candidate.changePercent;
        stock.turnover = candidate.turnoverRate;
        String board = candidateBoardText(candidate);
        if (usefulCandidateText(board)) {
            stock.industry = board;
        }
        stock.mainBusiness = "Top50热股候选：" + candidate.reason;
        stock.marketValue = candidate.amount;
        stock.pe = "量比 " + candidate.volumeRatio;
        stock.revenue = "4日涨幅 " + candidate.fourDayChangePercent + "，活跃 " + candidate.activeDays4d + "/4天";
        stock.profit = "评分 " + candidate.totalScore;
        stock.riskTag = candidate.riskTag;
        android.util.Log.d(BOARD_THEME_TAG, "fromHotCandidate " + StockDisplayText.debugSummary(this, stock, 14));
        return stock;
    }

    /**
     * 候选股可用板块文本。
     */
    private String candidateBoardText(HotStockCandidate candidate) {
        if (candidate == null) {
            return "";
        }
        if (usefulCandidateText(candidate.industry)) {
            return candidate.industry.trim();
        }
        if (usefulCandidateText(candidate.concept)) {
            return candidate.concept.trim();
        }
        return "";
    }

    /**
     * 有效/有用的候选股票创建文本控件。
     */
    private boolean usefulCandidateText(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim();
        return text.length() > 0
                && !"--".equals(text)
                && !"-".equals(text)
                && !text.contains("待同步")
                && !text.contains("寰呭悓姝");
    }

    /**
     * 同步hot候选股票boards转换为股票列表。
     */
    private void syncHotCandidateBoardsToStocks(ArrayList<HotStockCandidate> candidates) {
        android.util.Log.d(BOARD_THEME_TAG, "syncHotBoard start candidateCount="
                + (candidates == null ? 0 : candidates.size())
                + ", stockCount=" + stocks.size());
        if (candidates == null || candidates.size() == 0 || stocks.size() == 0) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < candidates.size(); i++) {
            HotStockCandidate candidate = candidates.get(i);
            String board = candidateBoardText(candidate);
            if (candidate == null || !usefulCandidateText(board)) {
                if (candidate != null) {
                    android.util.Log.d(BOARD_THEME_TAG, "syncHotBoard skip candidateNoIndustry code="
                            + candidate.code + ", industry=" + candidate.industry
                            + ", concept=" + candidate.concept);
                }
                continue;
            }
            Stock stock = findStock(candidate.code);
            if (stock != null && !StockDisplayText.hasBoard(stock)) {
                stock.industry = board;
                changed = true;
                android.util.Log.d(BOARD_THEME_TAG, "syncHotBoard code=" + stock.code
                        + ", industry=" + stock.industry);
            } else if (stock != null) {
                android.util.Log.d(BOARD_THEME_TAG, "syncHotBoard skip stockAlreadyHasBoard code="
                        + stock.code + ", stockIndustry=" + stock.industry
                        + ", candidateIndustry=" + candidate.industry
                        + ", candidateConcept=" + candidate.concept);
            }
        }
        if (changed) {
            saveStocks();
            android.util.Log.d(BOARD_THEME_TAG, "syncHotBoard saved");
            if (currentStock == null && TAB_WATCHLIST.equals(currentTab)) {
                rememberWatchlistScroll();
                showCurrentTab();
            } else if (currentStock != null) {
                refreshDetailQuoteSections(currentStock);
            }
        } else {
            android.util.Log.d(BOARD_THEME_TAG, "syncHotBoard noChange");
        }
    }

    /**
     * 查找股票。
     */
    private Stock findStock(String code) {
        if (code == null) {
            return null;
        }
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            if (code.equals(stock.code)) {
                return stock;
            }
        }
        return null;
    }

    /**
     * 加载data。
     */
    private void loadData() {
        viewModel.loadData();
        stocks = stockRepository.loadStocks();
        notes = stockRepository.loadNotes();
        hotCandidates = hotStockRepository.loadCandidates();
        HotStockCandidateRepository.HotStockCandidateMeta hotMeta = hotStockRepository.loadMeta();
        hotDataStatus = hotMeta.status;
        hotDataRefreshedAtMillis = hotMeta.refreshedAtMillis;
        hotDataSourceSummary = hotMeta.sourceSummary;
        if (hotCandidates.size() > 0 && (hotDataStatus == null || hotDataStatus.length() == 0)) {
            hotDataStatus = HOT_STATUS_CACHED;
        }
        syncHotCandidateBoardsToStocks(hotCandidates);
    }

    /**
     * 数据填充股票列表ifempty。
     */
    private void seedStocksIfEmpty() {
        stockRepository.seedStocksIfEmpty();
    }

    /**
     * createdefault股票。
     */
    private Stock createDefaultStock(String code, String name, String group, String remark) {
        return stockRepository.createDefaultStock(code, name, group, remark);
    }

    /**
     * 准备详情缓存。
     */
    private void prepareDetailCache(Stock stock) {
        DetailCache cache = stockRepository.loadDetailCache(stock.code);
        if (cache.news.size() > 0 && newsCache.get(stock.code) == null) {
            newsCache.put(stock.code, cache.news);
        }
        if (cache.opinions.size() > 0 && opinionCache.get(stock.code) == null) {
            opinionCache.put(stock.code, cache.opinions);
        }
        if (cache.newsFetchedAt > 0L) {
            newsFetchedAtCache.put(stock.code, cache.newsFetchedAt);
        }
        if (cache.opinionFetchedAt > 0L) {
            opinionFetchedAtCache.put(stock.code, cache.opinionFetchedAt);
        }
        if (cache.analysisFetchedAt > 0L) {
            analysisFetchedAtCache.put(stock.code, cache.analysisFetchedAt);
        }

        boolean newsExpired = isDetailCacheExpired(newsFetchedAtCache.get(stock.code));
        boolean opinionExpired = isDetailCacheExpired(opinionFetchedAtCache.get(stock.code));
        boolean analysisExpired = isDetailCacheExpired(analysisFetchedAtCache.get(stock.code))
                || newsExpired || opinionExpired;
        if (!analysisExpired && cache.analysis != null && !isUsableDeepSeekAnalysis(cache.analysis)) {
            deepSeekAnalysisCache.remove(stock.code);
            analysisFetchedAtCache.remove(stock.code);
            stockRepository.clearDetailAnalysis(stock.code);
        } else if (!analysisExpired && cache.analysis != null && deepSeekAnalysisCache.get(stock.code) == null) {
            deepSeekAnalysisCache.put(stock.code, cache.analysis);
        } else if (analysisExpired) {
            deepSeekAnalysisCache.remove(stock.code);
            analysisFetchedAtCache.remove(stock.code);
            stockRepository.clearDetailAnalysis(stock.code);
        }
    }

    /**
     * 刷新已过期详情data。
     */
    private void refreshExpiredDetailData(Stock stock) {
        if (isDetailCacheExpired(newsFetchedAtCache.get(stock.code))) {
            loadStockNews(stock);
        }
        if (isDetailCacheExpired(opinionFetchedAtCache.get(stock.code))) {
            loadStockOpinions(stock);
        }
    }

    /**
     * 判断是否详情缓存已过期。
     */
    private boolean isDetailCacheExpired(Long fetchedAt) {
        return fetchedAt == null || fetchedAt.longValue() <= 0L
                || System.currentTimeMillis() - fetchedAt.longValue() > DETAIL_CACHE_TTL_MILLIS;
    }

    /**
     * 保存详情新闻资讯。
     */
    private void saveDetailNews(Stock stock, ArrayList<News> news) {
        long nowMillis = System.currentTimeMillis();
        newsFetchedAtCache.put(stock.code, nowMillis);
        invalidateDetailAnalysis(stock.code);
        DetailCache cache = stockRepository.loadDetailCache(stock.code);
        cache.stockCode = stock.code;
        cache.news = news;
        cache.newsFetchedAt = nowMillis;
        cache.analysis = null;
        cache.analysisFetchedAt = 0L;
        stockRepository.saveDetailCache(cache);
    }

    /**
     * 保存详情舆情观点列表。
     */
    private void saveDetailOpinions(Stock stock, ArrayList<Opinion> opinions) {
        long nowMillis = System.currentTimeMillis();
        opinionFetchedAtCache.put(stock.code, nowMillis);
        invalidateDetailAnalysis(stock.code);
        DetailCache cache = stockRepository.loadDetailCache(stock.code);
        cache.stockCode = stock.code;
        cache.opinions = opinions;
        cache.opinionFetchedAt = nowMillis;
        cache.analysis = null;
        cache.analysisFetchedAt = 0L;
        stockRepository.saveDetailCache(cache);
    }

    /**
     * 保存详情analysis。
     */
    private void saveDetailAnalysis(Stock stock, DeepSeekAnalysisResult result) {
        if (!isUsableDeepSeekAnalysis(result)) {
            analysisFetchedAtCache.remove(stock.code);
            return;
        }
        long nowMillis = System.currentTimeMillis();
        analysisFetchedAtCache.put(stock.code, nowMillis);
        DetailCache cache = stockRepository.loadDetailCache(stock.code);
        cache.stockCode = stock.code;
        cache.analysis = result;
        cache.analysisFetchedAt = nowMillis;
        stockRepository.saveDetailCache(cache);
    }

    /**
     * 使失效详情analysis。
     */
    private void invalidateDetailAnalysis(String stockCode) {
        deepSeekAnalysisCache.remove(stockCode);
        analysisFetchedAtCache.remove(stockCode);
        stockRepository.clearDetailAnalysis(stockCode);
    }

    /**
     * 判断是否可用的deepseekanalysis。
     */
    private boolean isUsableDeepSeekAnalysis(DeepSeekAnalysisResult result) {
        return result != null && result.success && result.hasUsableFactors();
    }

    /**
     * 构建新闻资讯。
     */
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

    /**
     * 构建舆情观点列表。
     */
    private ArrayList<Opinion> buildOpinions(Stock stock) {
        ArrayList<Opinion> cachedOpinions = opinionCache.get(stock.code);
        if (cachedOpinions != null) {
            return cachedOpinions;
        }
        return new ArrayList<Opinion>();
    }

    /**
     * 加载股票新闻资讯。
     */
    private void loadStockNews(final Stock stock) {
        viewModel.loadStockNews(stock);
    }

    /**
     * 刷新新闻资讯feed。
     */
    private void refreshNewsFeed() {
        refreshNewsFeed(true);
    }

    /**
     * 加载新闻资讯feedifneeded。
     */
    private void loadNewsFeedIfNeeded() {
        if (!importantNewsLoadedOnce && importantNewsCache.size() == 0 && !loadingImportantNews) {
            loadImportantNews(false);
        }
        if (!subscribedNewsLoadedOnce && !loadingSubscribedNewsFeed && hasMissingSubscribedNews()) {
            loadSubscribedNews(false);
        }
    }

    /**
     * 刷新新闻资讯feed。
     */
    private void refreshNewsFeed(boolean manual) {
        if (NEWS_MODE_SUBSCRIBED.equals(selectedNewsMode)) {
            loadSubscribedNews(manual);
            return;
        }
        loadImportantNews(manual);
    }

    /**
     * 结束新闻资讯pull刷新ifneeded。
     */
    private void finishNewsPullRefreshIfNeeded() {
        if (currentNewsScrollView != null && currentNewsScrollView.isRefreshing()) {
            currentNewsScrollView.finishRefresh();
        }
    }

    private void loadImportantNews(final boolean manual) {
        if (manual) {
            Toast.makeText(this, getString(R.string.news_loading_title), Toast.LENGTH_SHORT).show();
        }
        viewModel.loadImportantNews(manual);
    }

    /**
     * 加载订阅的新闻资讯。
     */
    private void loadSubscribedNews(final boolean manual) {
        if (stocks.size() == 0) {
            if (manual) {
                Toast.makeText(this, getString(R.string.news_feed_empty_title), Toast.LENGTH_SHORT).show();
            }
            finishNewsPullRefreshIfNeeded();
            return;
        }
        if (manual) {
            Toast.makeText(this, getString(R.string.news_loading_title), Toast.LENGTH_SHORT).show();
        }
        viewModel.loadSubscribedNews(manual);
    }

    /**
     * 判断是否有缺失的订阅的新闻资讯。
     */
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

    /**
     * 构建订阅的新闻资讯。
     */
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

    /**
     * 添加feed新闻资讯。
     */
    private void addFeedNews(ArrayList<News> target, ArrayList<News> source) {
        for (int i = 0; i < source.size(); i++) {
            News item = source.get(i);
            if (!containsNewsTitle(target, item.title)) {
                target.add(item);
            }
        }
    }

    /**
     * contains新闻资讯标题。
     */
    private boolean containsNewsTitle(ArrayList<News> news, String title) {
        for (int i = 0; i < news.size(); i++) {
            if (title.equals(news.get(i).title)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 加载股票舆情观点列表。
     */
    private void loadStockOpinions(final Stock stock) {
        viewModel.loadStockOpinions(stock);
    }

    /**
     * 加载deepseekanalysisifready。
     */
    private void loadDeepSeekAnalysisIfReady(final Stock stock) {
        viewModel.loadDeepSeekAnalysisIfReady(stock);
    }

    /**
     * 安全新闻资讯foranalysis。
     */
    private ArrayList<News> safeNewsForAnalysis(Stock stock) {
        ArrayList<News> cachedNews = newsCache.get(stock.code);
        return cachedNews == null ? new ArrayList<News>() : cachedNews;
    }

    /**
     * 刷新quotes。
     */
    private void refreshQuotes() {
        refreshQuotes(true);
    }

    /**
     * 刷新quotes。
     */
    private void refreshQuotes(final boolean manual) {
        refreshQuotes(manual, false);
    }

    /**
     * 刷新quotesonhomeentry。
     */
    private void refreshQuotesOnHomeEntry() {
        if (homeEntryQuotesRefreshed) {
            return;
        }
        homeEntryQuotesRefreshed = true;
        android.util.Log.d(TAG, "home entry quote refresh start, stockCount=" + stocks.size());
        refreshQuotes(false, true);
    }

    /**
     * 刷新quotes。
     */
    private void refreshQuotes(final boolean manual, boolean force) {
        viewModel.refreshQuotes(manual, force);
    }

    /**
     * 刷新manual股票board主题。
     */
    private void refreshManualStockBoardTheme(final Stock stock) {
        viewModel.refreshManualStockBoardTheme(stock);
    }

    /**
     * 刷新缺失的boards。
     */
    private void refreshMissingBoards(ArrayList<Stock> displayStocks) {
        viewModel.refreshMissingBoards(displayStocks);
    }

    /**
     * shouldauto刷新quotes。
     */
    private boolean shouldAutoRefreshQuotes() {
        Calendar now = Calendar.getInstance();
        int day = now.get(Calendar.DAY_OF_WEEK);
        if (day == Calendar.SATURDAY || day == Calendar.SUNDAY) {
            return false;
        }
        int hour = now.get(Calendar.HOUR_OF_DAY);
        int minute = now.get(Calendar.MINUTE);
        int minutes = hour * 60 + minute;
        return (minutes >= toMinutes(9, 25) && minutes <= toMinutes(11, 30))
                || (minutes >= toMinutes(12, 55) && minutes <= toMinutes(15, 5));
    }

    /**
     * 转换为minutes。
     */
    private int toMinutes(int hour, int minute) {
        return hour * 60 + minute;
    }

    /**
     * 刷新可见的quoteUI。
     */
    private void refreshVisibleQuoteUi(boolean manual) {
        if (currentStock != null) {
            refreshDetailQuoteSections(currentStock);
            return;
        }
        if (TAB_WATCHLIST.equals(currentTab) && clearExpiredWatchlistAiOpportunities()) {
            rememberWatchlistScroll();
            showCurrentTab();
        }
    }

    /**
     * 清除已过期watchlistaiAI机会。
     */
    private boolean clearExpiredWatchlistAiOpportunities() {
        ArrayList<Stock> displayStocks = filterStocks();
        boolean changed = false;
        for (int i = 0; i < displayStocks.size(); i++) {
            Stock stock = displayStocks.get(i);
            DeepSeekAnalysisResult memoryAnalysis = deepSeekAnalysisCache.get(stock.code);
            Long memoryFetchedAt = analysisFetchedAtCache.get(stock.code);
            if (memoryAnalysis != null && isDetailCacheExpired(memoryFetchedAt)) {
                deepSeekAnalysisCache.remove(stock.code);
                analysisFetchedAtCache.remove(stock.code);
                stockRepository.clearDetailAnalysis(stock.code);
                changed = true;
                continue;
            }

            if (memoryAnalysis == null || memoryFetchedAt == null) {
                DetailCache cache = stockRepository.loadDetailCache(stock.code);
                Long cacheFetchedAt = cache.analysisFetchedAt > 0L
                        ? Long.valueOf(cache.analysisFetchedAt)
                        : null;
                if (cache.analysis != null && isDetailCacheExpired(cacheFetchedAt)) {
                    stockRepository.clearDetailAnalysis(stock.code);
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * 刷新详情quotesections。
     */
    private void refreshDetailQuoteSections(Stock stock) {
        if (currentHeroContainer != null) {
            currentHeroContainer.removeAllViews();
            currentHeroContainer.addView(heroCard(stock), matchWrap());
        }
        if (currentCompanyContainer != null) {
            currentCompanyContainer.removeAllViews();
            currentCompanyContainer.addView(companyCard(stock), matchWrap());
        }
        refreshRealtimeDecisionCard(stock);
        refreshWinLossRatioCard(stock);
    }

    /**
     * 弹出/显示quote刷新失败结果对话框。
     */
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

    /**
     * 过滤股票列表。
     */
    private ArrayList<Stock> filterStocks() {
        return stockRepository.filterStocks(stocks, selectedGroup);
    }

    /**
     * 获取分组列表。
     */
    private ArrayList<String> getGroups() {
        return stockRepository.getGroups(stocks);
    }

    /**
     * 可选的分组分组列表。
     */
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

    /**
     * 判断是否有效的股票code。
     */
    private boolean isValidStockCode(String code) {
        return code.matches("[03689][0-9]{5}");
    }

    /**
     * contains股票code。
     */
    private boolean containsStockCode(String code) {
        for (int i = 0; i < stocks.size(); i++) {
            if (code.equals(stocks.get(i).code)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析股票分组。
     */
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

    /**
     * 统计高风险股票列表。
     */
    private int countRiskStocks() {
        return stockRepository.countRiskStocks(stocks);
    }

    /**
     * 获取决策笔记列表。
     */
    private ArrayList<DecisionNote> getNotes(String stockCode) {
        return stockRepository.getNotes(notes, stockCode);
    }

    /**
     * 保存股票列表。
     */
    private void saveStocks() {
        viewModel.saveStocksDirect(stocks);
    }

    /**
     * 保存决策笔记列表。
     */
    private void saveNotes() {
        viewModel.saveNotesDirect(notes);
    }

    /**
     * 创建垂直布局。
     */
    private LinearLayout vertical() {
        return ui.vertical();
    }

    /**
     * 创建水平布局。
     */
    private LinearLayout horizontal() {
        return ui.horizontal();
    }

    /**
     * 创建卡片布局。
     */
    private LinearLayout card() {
        return ui.card();
    }

    /**
     * 创建文本控件。
     */
    private TextView text(String value, int sp, int color, boolean bold) {
        return ui.text(value, sp, color, bold);
    }

    /**
     * 创建标签控件。
     */
    private TextView tag(String value, int bgColor, int textColor) {
        return ui.tag(value, bgColor, textColor);
    }

    /**
     * 创建输入框控件。
     */
    private EditText input(String hint) {
        return ui.input(hint);
    }

    /**
     * 主要按钮。
     */
    private Button primaryButton(String text) {
        return ui.primaryButton(text);
    }

    /**
     * 幽灵风格按钮。
     */
    private Button ghostButton(String text) {
        return ui.ghostButton(text);
    }

    /**
     * 创建圆角背景。
     */
    private GradientDrawable rounded(int color, int radius) {
        return ui.rounded(color, radius);
    }

    /**
     * 创建圆角背景描边。
     */
    private GradientDrawable roundedStroke(int color, int radius, int strokeColor) {
        return ui.roundedStroke(color, radius, strokeColor);
    }

    /**
     * 创建间距控件。
     */
    private View spacer(int height) {
        return ui.spacer(height);
    }

    /**
     * 创建间距控件。
     */
    private View spacer(int width, int height) {
        return ui.spacer(width, height);
    }

    /**
     * 创建权重间距权重。
     */
    private View spaceWeight() {
        return ui.spaceWeight();
    }

    /**
     * 填充包裹。
     */
    private LinearLayout.LayoutParams matchWrap() {
        return ui.matchWrap();
    }

    /**
     * 填充高度。
     */
    private LinearLayout.LayoutParams matchHeight(int height) {
        return ui.matchHeight(height);
    }

    /**
     * 包裹高度。
     */
    private LinearLayout.LayoutParams wrapHeight(int height) {
        return ui.wrapHeight(height);
    }

    /**
     * 包裹包裹。
     */
    private LinearLayout.LayoutParams wrapWrap() {
        return ui.wrapWrap();
    }

    /**
     * 权重包裹。
     */
    private LinearLayout.LayoutParams weightWrap(float weight) {
        return ui.weightWrap(weight);
    }

    /**
     * 填充填充。
     */
    private FrameLayout.LayoutParams matchMatch() {
        return ui.matchMatch();
    }

    /**
     * 页面布局参数。
     */
    private FrameLayout.LayoutParams pageParams(boolean detailPage) {
        return ui.pageParams(detailPage);
    }

    /**
     * 将dp值转换为像素值。
     */
    private int dp(int value) {
        return ui.dp(value);
    }

    /**
     * 获取当前时间。
     */
    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
    }

    /**
     * 创建文本控件ordefault。
     */
    private String textOrDefault(EditText editText, String defaultValue) {
        String value = editText.getText().toString().trim();
        return value.length() == 0 ? defaultValue : value;
    }

    /**
     * 隐藏键盘。
     */
    private void hideKeyboard(View view) {
        ui.hideKeyboard(view);
    }
}
