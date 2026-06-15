package com.face.mymoney.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.ai.DeepSeekStockAnalyzer;
import com.face.mymoney.crawler.GeneralFinanceNewsFetcher;
import com.face.mymoney.crawler.HotStockCandidateFetcher;
import com.face.mymoney.crawler.MarketIndexFetcher;
import com.face.mymoney.crawler.StockBoardFetcher;
import com.face.mymoney.crawler.StockNewsFetcher;
import com.face.mymoney.crawler.StockQuoteFetcher;
import com.face.mymoney.data.DetailCache;
import com.face.mymoney.data.HotStockCandidateRepository;
import com.face.mymoney.data.LocalStockRepository;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.HotStockCandidate;
import com.face.mymoney.model.MarketIndexQuote;
import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;
import com.face.mymoney.opinion.Opinion;
import com.face.mymoney.opinion.StockOpinionFetcher;
import com.face.mymoney.ratio.WinLossRatioCalculator;
import com.face.mymoney.ratio.WinLossRatioInput;
import com.face.mymoney.ratio.WinLossRatioResult;
import com.face.mymoney.ui.StockDisplayText;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "MainViewModel";
    private static final long DETAIL_CACHE_TTL_MILLIS = 15 * 60 * 1000L;

    private final LocalStockRepository stockRepository;
    private final HotStockCandidateRepository hotStockRepository;
    private final ExecutorService backgroundExecutor;

    // LiveData states
    private final MutableLiveData<ArrayList<Stock>> stocks = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ArrayList<DecisionNote>> notes = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ArrayList<HotStockCandidate>> hotCandidates = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<ArrayList<MarketIndexQuote>> marketIndices = new MutableLiveData<>(new ArrayList<>());
    
    private final MutableLiveData<Boolean> loadingImportantNews = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingSubscribedNews = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> loadingHotCandidates = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> refreshingQuotes = new MutableLiveData<>(false);

    // LiveData Events for UI notifications
    private final MutableLiveData<String> newsLoadedEvent = new MutableLiveData<>();
    private final MutableLiveData<String> opinionsLoadedEvent = new MutableLiveData<>();
    private final MutableLiveData<String> deepSeekAnalysisLoadedEvent = new MutableLiveData<>();
    private final MutableLiveData<String> boardLoadedEvent = new MutableLiveData<>();
    private final MutableLiveData<QuoteRefreshEvent> quoteRefreshEvent = new MutableLiveData<>();
    private final MutableLiveData<HotCandidatesRefreshEvent> hotCandidatesRefreshEvent = new MutableLiveData<>();

    // Caches (Thread Safe)
    private final Map<String, ArrayList<News>> newsCache = new ConcurrentHashMap<>();
    private final Map<String, ArrayList<Opinion>> opinionCache = new ConcurrentHashMap<>();
    private final Map<String, DeepSeekAnalysisResult> deepSeekAnalysisCache = new ConcurrentHashMap<>();
    private final Map<String, Long> newsFetchedAtCache = new ConcurrentHashMap<>();
    private final Map<String, Long> opinionFetchedAtCache = new ConcurrentHashMap<>();
    private final Map<String, Long> analysisFetchedAtCache = new ConcurrentHashMap<>();
    private final ArrayList<News> importantNewsCache = new ArrayList<>();

    // Loading states for individual stocks
    private final Set<String> loadingNewsCodes = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> loadingOpinionCodes = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> loadingDeepSeekCodes = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> loadingBoardCodes = Collections.synchronizedSet(new HashSet<>());

    private volatile String hotDataStatus = "";
    private volatile long hotDataRefreshedAtMillis = 0L;
    private volatile String hotDataSourceSummary = "";

    public MainViewModel(@NonNull Application application) {
        super(application);
        stockRepository = new LocalStockRepository(application);
        hotStockRepository = new HotStockCandidateRepository(application);
        backgroundExecutor = Executors.newFixedThreadPool(3);
    }

    @Override
    protected void onCleared() {
        backgroundExecutor.shutdownNow();
        super.onCleared();
    }

    public LocalStockRepository getStockRepository() {
        return stockRepository;
    }

    public HotStockCandidateRepository getHotStockRepository() {
        return hotStockRepository;
    }

    // --- Getters for LiveData ---
    public LiveData<ArrayList<Stock>> getStocks() { return stocks; }
    public LiveData<ArrayList<DecisionNote>> getNotes() { return notes; }
    public LiveData<ArrayList<HotStockCandidate>> getHotCandidates() { return hotCandidates; }
    public LiveData<ArrayList<MarketIndexQuote>> getMarketIndices() { return marketIndices; }
    public LiveData<Boolean> getLoadingImportantNews() { return loadingImportantNews; }
    public LiveData<Boolean> getLoadingSubscribedNews() { return loadingSubscribedNews; }
    public LiveData<Boolean> getLoadingHotCandidates() { return loadingHotCandidates; }
    public LiveData<Boolean> getRefreshingQuotes() { return refreshingQuotes; }

    public LiveData<String> getNewsLoadedEvent() { return newsLoadedEvent; }
    public LiveData<String> getOpinionsLoadedEvent() { return opinionsLoadedEvent; }
    public LiveData<String> getDeepSeekAnalysisLoadedEvent() { return deepSeekAnalysisLoadedEvent; }
    public LiveData<String> getBoardLoadedEvent() { return boardLoadedEvent; }
    public LiveData<QuoteRefreshEvent> getQuoteRefreshEvent() { return quoteRefreshEvent; }
    public LiveData<HotCandidatesRefreshEvent> getHotCandidatesRefreshEvent() { return hotCandidatesRefreshEvent; }

    // --- Getters for caches ---
    public ArrayList<News> getNewsCache(String code) { return newsCache.get(code); }
    public ArrayList<Opinion> getOpinionCache(String code) { return opinionCache.get(code); }
    public DeepSeekAnalysisResult getDeepSeekAnalysisCache(String code) { return deepSeekAnalysisCache.get(code); }
    public Long getNewsFetchedAt(String code) { return newsFetchedAtCache.get(code); }
    public Long getOpinionFetchedAt(String code) { return opinionFetchedAtCache.get(code); }
    public Long getAnalysisFetchedAt(String code) { return analysisFetchedAtCache.get(code); }
    public ArrayList<News> getImportantNewsCache() { return importantNewsCache; }

    public boolean isLoadingNews(String code) { return loadingNewsCodes.contains(code); }
    public boolean isLoadingOpinion(String code) { return loadingOpinionCodes.contains(code); }
    public boolean isLoadingDeepSeek(String code) { return loadingDeepSeekCodes.contains(code); }
    public boolean isLoadingBoard(String code) { return loadingBoardCodes.contains(code); }

    public String getHotDataStatus() { return hotDataStatus; }
    public long getHotDataRefreshedAtMillis() { return hotDataRefreshedAtMillis; }
    public String getHotDataSourceSummary() { return hotDataSourceSummary; }

    public void setHotDataStatus(String status) { this.hotDataStatus = status; }
    public void setHotDataRefreshedAtMillis(long t) { this.hotDataRefreshedAtMillis = t; }
    public void setHotDataSourceSummary(String sum) { this.hotDataSourceSummary = sum; }

    // --- Data Loading and Operations ---

    public void loadData() {
        ArrayList<Stock> loadedStocks = stockRepository.loadStocks();
        ArrayList<DecisionNote> loadedNotes = stockRepository.loadNotes();
        ArrayList<HotStockCandidate> loadedHot = hotStockRepository.loadCandidates();
        HotStockCandidateRepository.HotStockCandidateMeta hotMeta = hotStockRepository.loadMeta();
        hotDataStatus = hotMeta.status;
        hotDataRefreshedAtMillis = hotMeta.refreshedAtMillis;
        hotDataSourceSummary = hotMeta.sourceSummary;
        
        if (loadedHot.size() > 0 && (hotDataStatus == null || hotDataStatus.length() == 0)) {
            hotDataStatus = "cached";
        }
        
        stocks.setValue(loadedStocks);
        notes.setValue(loadedNotes);
        hotCandidates.setValue(loadedHot);
        syncHotCandidateBoardsToStocks(loadedHot);
    }

    public void saveStocksDirect(ArrayList<Stock> newStocks) {
        stocks.setValue(newStocks);
        stockRepository.saveStocks(newStocks);
    }

    public void saveNotesDirect(ArrayList<DecisionNote> newNotes) {
        notes.setValue(newNotes);
        stockRepository.saveNotes(newNotes);
    }

    public void prepareDetailCache(Stock stock) {
        if (stock == null) return;
        DetailCache cache = stockRepository.loadDetailCache(stock.code);
        if (cache.news != null && cache.news.size() > 0 && newsCache.get(stock.code) == null) {
            newsCache.put(stock.code, cache.news);
        }
        if (cache.opinions != null && cache.opinions.size() > 0 && opinionCache.get(stock.code) == null) {
            opinionCache.put(stock.code, cache.opinions);
        }
        if (cache.newsFetchedAt > 0L) {
            newsFetchedAtCache.put(stock.code, cache.newsFetchedAt);
        }
        if (cache.opinionFetchedAt > 0L) {
            opinionFetchedAtCache.put(stock.code, cache.opinionFetchedAt);
        }
        
        boolean newsExpired = isDetailCacheExpired(cache.newsFetchedAt);
        boolean opinionExpired = isDetailCacheExpired(cache.opinionFetchedAt);
        boolean analysisExpired = isDetailCacheExpired(cache.analysisFetchedAt)
                || newsExpired || opinionExpired;
        if (!analysisExpired && cache.analysis != null && !isUsableDeepSeekAnalysis(cache.analysis)) {
            stockRepository.clearDetailAnalysis(stock.code);
        } else if (!analysisExpired && cache.analysis != null) {
            deepSeekAnalysisCache.put(stock.code, cache.analysis);
            analysisFetchedAtCache.put(stock.code, cache.analysisFetchedAt);
        } else if (analysisExpired) {
            deepSeekAnalysisCache.remove(stock.code);
            analysisFetchedAtCache.remove(stock.code);
            if (cache.analysis != null) {
                stockRepository.clearDetailAnalysis(stock.code);
            }
        }
    }

    // --- Background Operations ---

    public void refreshHotCandidates(final boolean manual) {
        if (Boolean.TRUE.equals(loadingHotCandidates.getValue())) {
            return;
        }
        loadingHotCandidates.setValue(true);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                HotStockCandidateFetcher fetcher = new HotStockCandidateFetcher(getApplication());
                ArrayList<HotStockCandidate> fetchedResult = fetcher.fetchTop50HotStocks();
                final boolean sourceDegraded = fetcher.isSourceDegraded();
                final String sourceSummary = fetcher.getSourceSummary();
                final boolean usingCachedHotCandidates;
                
                if (sourceDegraded) {
                    ArrayList<HotStockCandidate> cached = hotStockRepository.loadCandidates();
                    if (cached.size() > 0) {
                        fetchedResult = cached;
                        usingCachedHotCandidates = true;
                    } else {
                        usingCachedHotCandidates = false;
                    }
                } else {
                    usingCachedHotCandidates = false;
                }
                
                final ArrayList<HotStockCandidate> fetched = fetchedResult;
                final long refreshedAtMillis = System.currentTimeMillis();
                
                // Write cache to database in background
                if (!sourceDegraded && fetched.size() > 0) {
                    hotStockRepository.saveCandidates(fetched);
                    hotStockRepository.saveMeta("latest", refreshedAtMillis, sourceSummary);
                } else if (sourceDegraded && fetched.size() > 0 && usingCachedHotCandidates) {
                    hotStockRepository.saveMeta("degraded", refreshedAtMillis, sourceSummary);
                }
                
                // Post back to Main Thread
                loadingHotCandidates.postValue(false);
                hotCandidates.postValue(fetched);
                hotDataStatus = sourceDegraded ? "degraded" : "latest";
                hotDataRefreshedAtMillis = refreshedAtMillis;
                hotDataSourceSummary = sourceSummary;
                
                // Notify UI
                hotCandidatesRefreshEvent.postValue(new HotCandidatesRefreshEvent(
                    manual, sourceDegraded, sourceSummary, usingCachedHotCandidates, fetched
                ));
                
                syncHotCandidateBoardsToStocks(fetched);
            }
        });
    }

    public void loadImportantNews(final boolean manual) {
        if (Boolean.TRUE.equals(loadingImportantNews.getValue())) {
            return;
        }
        loadingImportantNews.setValue(true);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                GeneralFinanceNewsFetcher fetcher = new GeneralFinanceNewsFetcher();
                final ArrayList<News> fetchedNews = fetcher.fetchImportantNews();
                
                importantNewsCache.clear();
                importantNewsCache.addAll(fetchedNews);
                
                loadingImportantNews.postValue(false);
                // Trigger UI refresh
                newsLoadedEvent.postValue("important");
            }
        });
    }

    public void loadSubscribedNews(final boolean manual) {
        final ArrayList<Stock> currentStocks = stocks.getValue();
        if (currentStocks == null || currentStocks.size() == 0) {
            return;
        }
        if (Boolean.TRUE.equals(loadingSubscribedNews.getValue())) {
            return;
        }
        loadingSubscribedNews.setValue(true);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final HashMap<String, ArrayList<News>> fetched = new HashMap<>();
                StockNewsFetcher fetcher = new StockNewsFetcher(getApplication());
                for (int i = 0; i < currentStocks.size(); i++) {
                    Stock stock = currentStocks.get(i);
                    fetched.put(stock.code, fetcher.fetchForStock(stock));
                }
                
                newsCache.putAll(fetched);
                for (int i = 0; i < currentStocks.size(); i++) {
                    Stock stock = currentStocks.get(i);
                    ArrayList<News> stockNews = fetched.get(stock.code);
                    if (stockNews != null) {
                        saveDetailNews(stock, stockNews);
                    }
                }
                
                loadingSubscribedNews.postValue(false);
                newsLoadedEvent.postValue("subscribed");
            }
        });
    }

    public void loadStockNews(final Stock stock) {
        if (stock == null || loadingNewsCodes.contains(stock.code)) {
            return;
        }
        loadingNewsCodes.add(stock.code);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                StockNewsFetcher fetcher = new StockNewsFetcher(getApplication());
                ArrayList<News> fetchedNews = fetcher.fetchForStock(stock);
                loadingNewsCodes.remove(stock.code);
                newsCache.put(stock.code, fetchedNews);
                saveDetailNews(stock, fetchedNews);
                
                newsLoadedEvent.postValue(stock.code);
            }
        });
    }

    public void loadStockOpinions(final Stock stock) {
        if (stock == null || loadingOpinionCodes.contains(stock.code)) {
            return;
        }
        loadingOpinionCodes.add(stock.code);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                StockOpinionFetcher fetcher = new StockOpinionFetcher();
                ArrayList<Opinion> fetchedOpinions = fetcher.fetchForStock(stock);
                loadingOpinionCodes.remove(stock.code);
                opinionCache.put(stock.code, fetchedOpinions);
                saveDetailOpinions(stock, fetchedOpinions);
                
                opinionsLoadedEvent.postValue(stock.code);
            }
        });
    }

    public void loadDeepSeekAnalysisIfReady(final Stock stock) {
        if (stock == null || loadingDeepSeekCodes.contains(stock.code)) {
            return;
        }
        if (loadingNewsCodes.contains(stock.code) || loadingOpinionCodes.contains(stock.code)) {
            return;
        }
        if (deepSeekAnalysisCache.containsKey(stock.code)) {
            DeepSeekAnalysisResult cachedAnalysis = deepSeekAnalysisCache.get(stock.code);
            if (!isUsableDeepSeekAnalysis(cachedAnalysis)) {
                deepSeekAnalysisCache.remove(stock.code);
                analysisFetchedAtCache.remove(stock.code);
                stockRepository.clearDetailAnalysis(stock.code);
            } else if (!isDetailCacheExpired(analysisFetchedAtCache.get(stock.code))) {
                return;
            } else {
                invalidateDetailAnalysis(stock.code);
            }
        }
        if (isDetailCacheExpired(newsFetchedAtCache.get(stock.code))
                || isDetailCacheExpired(opinionFetchedAtCache.get(stock.code))) {
            return;
        }
        if (newsCache.get(stock.code) == null || opinionCache.get(stock.code) == null) {
            return;
        }
        
        loadingDeepSeekCodes.add(stock.code);
        // We notify UI that we started deepseek loading
        deepSeekAnalysisLoadedEvent.postValue(stock.code + "_loading");
        
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                DeepSeekStockAnalyzer analyzer = new DeepSeekStockAnalyzer();
                DeepSeekAnalysisResult result = analyzer.analyze(stock,
                        safeNewsForAnalysis(stock), buildOpinions(stock), getNotesForStock(stock.code));
                
                loadingDeepSeekCodes.remove(stock.code);
                deepSeekAnalysisCache.put(stock.code, result);
                saveDetailAnalysis(stock, result);
                
                deepSeekAnalysisLoadedEvent.postValue(stock.code);
            }
        });
    }

    public void refreshQuotes(final boolean manual, final boolean force) {
        final ArrayList<Stock> currentStocks = stocks.getValue();
        if (currentStocks == null || currentStocks.size() == 0) {
            if (manual || force) {
                quoteRefreshEvent.postValue(new QuoteRefreshEvent(manual, force,
                        new StockQuoteFetcher.QuoteRefreshResult(0), new ArrayList<MarketIndexQuote>()));
            }
            return;
        }
        if (Boolean.TRUE.equals(refreshingQuotes.getValue())) {
            return;
        }
        refreshingQuotes.setValue(true);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                StockQuoteFetcher fetcher = new StockQuoteFetcher();
                final StockQuoteFetcher.QuoteRefreshResult result = fetcher.refreshQuotesDetailed(currentStocks != null ? currentStocks : new ArrayList<Stock>());
                MarketIndexFetcher indexFetcher = new MarketIndexFetcher();
                final ArrayList<MarketIndexQuote> fetchedIndices = indexFetcher.fetchDefaultIndices();
                
                refreshingQuotes.postValue(false);
                if (fetchedIndices.size() > 0) {
                    marketIndices.postValue(fetchedIndices);
                }
                
                if (currentStocks != null) {
                    stockRepository.saveStocks(currentStocks);
                    stocks.postValue(new ArrayList<Stock>(currentStocks));
                }
                
                quoteRefreshEvent.postValue(new QuoteRefreshEvent(manual, force, result, fetchedIndices));
            }
        });
    }

    public void refreshManualStockBoardTheme(final Stock stock) {
        if (stock == null || loadingBoardCodes.contains(stock.code)) {
            return;
        }
        loadingBoardCodes.add(stock.code);
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                ArrayList<Stock> boardTargets = new ArrayList<>();
                boardTargets.add(stock);
                StockBoardFetcher boardFetcher = new StockBoardFetcher();
                boardFetcher.refreshBoards(boardTargets);
                StockQuoteFetcher fetcher = new StockQuoteFetcher();
                final StockQuoteFetcher.QuoteResult result = fetcher.refreshQuoteDetailed(stock);
                loadingBoardCodes.remove(stock.code);
                
                ArrayList<Stock> currentStocks = stocks.getValue();
                if (currentStocks != null) {
                    stockRepository.saveStocks(currentStocks);
                    stocks.postValue(currentStocks);
                }
                
                boardLoadedEvent.postValue(stock.code);
            }
        });
    }

    public void refreshMissingBoards(final ArrayList<Stock> displayStocks) {
        if (displayStocks == null || displayStocks.size() == 0) {
            return;
        }
        final ArrayList<Stock> targets = new ArrayList<>();
        for (int i = 0; i < displayStocks.size(); i++) {
            Stock stock = displayStocks.get(i);
            if (stock == null || stock.code == null || stock.code.length() == 0) {
                continue;
            }
            boolean needsLookup = StockDisplayText.needsBoardThemeLookup(stock);
            boolean loading = loadingBoardCodes.contains(stock.code);
            if (needsLookup && !loading) {
                loadingBoardCodes.add(stock.code);
                targets.add(stock);
            }
        }
        if (targets.size() == 0) {
            return;
        }
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                StockBoardFetcher fetcher = new StockBoardFetcher();
                int updatedCount = fetcher.refreshBoards(targets);
                
                for (int i = 0; i < targets.size(); i++) {
                    loadingBoardCodes.remove(targets.get(i).code);
                }
                
                if (updatedCount > 0) {
                    ArrayList<Stock> currentStocks = stocks.getValue();
                    if (currentStocks != null) {
                        stockRepository.saveStocks(currentStocks);
                        stocks.postValue(currentStocks);
                    }
                    boardLoadedEvent.postValue("all");
                }
            }
        });
    }

    // --- Helper Methods ---

    public boolean isDetailCacheExpired(Long fetchedAt) {
        return fetchedAt == null || fetchedAt <= 0L
                || System.currentTimeMillis() - fetchedAt > DETAIL_CACHE_TTL_MILLIS;
    }

    public void invalidateDetailInputs(String stockCode) {
        if (stockCode == null || stockCode.length() == 0) {
            return;
        }
        newsFetchedAtCache.remove(stockCode);
        opinionFetchedAtCache.remove(stockCode);
        invalidateDetailAnalysis(stockCode);
    }

    public void clearDetailAnalysis(String stockCode) {
        if (stockCode == null || stockCode.length() == 0) {
            return;
        }
        invalidateDetailAnalysis(stockCode);
    }

    public boolean clearExpiredAnalysisIfNeeded(String stockCode) {
        if (stockCode == null || stockCode.length() == 0) {
            return false;
        }
        DeepSeekAnalysisResult memoryAnalysis = deepSeekAnalysisCache.get(stockCode);
        Long memoryFetchedAt = analysisFetchedAtCache.get(stockCode);
        if (memoryAnalysis != null && isDetailCacheExpired(memoryFetchedAt)) {
            invalidateDetailAnalysis(stockCode);
            return true;
        }

        if (memoryAnalysis == null || memoryFetchedAt == null) {
            DetailCache cache = stockRepository.loadDetailCache(stockCode);
            Long cacheFetchedAt = cache.analysisFetchedAt > 0L
                    ? Long.valueOf(cache.analysisFetchedAt)
                    : null;
            if (cache.analysis != null && isDetailCacheExpired(cacheFetchedAt)) {
                stockRepository.clearDetailAnalysis(stockCode);
                return true;
            }
        }
        return false;
    }

    private boolean isUsableDeepSeekAnalysis(DeepSeekAnalysisResult result) {
        return result != null && result.success && result.hasUsableFactors();
    }

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

    private void invalidateDetailAnalysis(String stockCode) {
        deepSeekAnalysisCache.remove(stockCode);
        analysisFetchedAtCache.remove(stockCode);
        stockRepository.clearDetailAnalysis(stockCode);
    }

    public ArrayList<News> safeNewsForAnalysis(Stock stock) {
        ArrayList<News> cachedNews = newsCache.get(stock.code);
        return cachedNews == null ? new ArrayList<>() : cachedNews;
    }

    public ArrayList<Opinion> buildOpinions(Stock stock) {
        ArrayList<Opinion> cachedOpinions = opinionCache.get(stock.code);
        if (cachedOpinions != null) {
            return cachedOpinions;
        }
        return new ArrayList<>();
    }

    public ArrayList<DecisionNote> getNotesForStock(String stockCode) {
        ArrayList<DecisionNote> allNotes = notes.getValue();
        if (allNotes == null) return new ArrayList<>();
        return stockRepository.getNotes(allNotes, stockCode);
    }

    private void syncHotCandidateBoardsToStocks(ArrayList<HotStockCandidate> candidates) {
        ArrayList<Stock> currentStocks = stocks.getValue();
        if (candidates == null || candidates.size() == 0 || currentStocks == null || currentStocks.size() == 0) {
            return;
        }
        boolean changed = false;
        for (int i = 0; i < candidates.size(); i++) {
            HotStockCandidate candidate = candidates.get(i);
            String board = candidateBoardText(candidate);
            if (candidate == null || !usefulCandidateText(board)) {
                continue;
            }
            Stock stock = findStock(candidate.code);
            if (stock != null && !StockDisplayText.hasBoard(stock)) {
                stock.industry = board;
                changed = true;
            }
        }
        if (changed) {
            stockRepository.saveStocks(currentStocks);
            stocks.postValue(currentStocks);
            boardLoadedEvent.postValue("all");
        }
    }

    private Stock findStock(String code) {
        ArrayList<Stock> currentStocks = stocks.getValue();
        if (code == null || currentStocks == null) {
            return null;
        }
        for (int i = 0; i < currentStocks.size(); i++) {
            Stock stock = currentStocks.get(i);
            if (code.equals(stock.code)) {
                return stock;
            }
        }
        return null;
    }

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

    public boolean shouldAutoRefreshQuotes() {
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

    private int toMinutes(int hour, int minute) {
        return hour * 60 + minute;
    }

    public void executeInBackground(Runnable runnable) {
        backgroundExecutor.execute(runnable);
    }

    // Event nested classes
    public static class QuoteRefreshEvent {
        public final boolean manual;
        public final boolean force;
        public final StockQuoteFetcher.QuoteRefreshResult result;
        public final ArrayList<MarketIndexQuote> fetchedIndices;
        public QuoteRefreshEvent(boolean manual, boolean force, StockQuoteFetcher.QuoteRefreshResult result, ArrayList<MarketIndexQuote> fetchedIndices) {
            this.manual = manual;
            this.force = force;
            this.result = result;
            this.fetchedIndices = fetchedIndices;
        }
    }

    public static class HotCandidatesRefreshEvent {
        public final boolean manual;
        public final boolean sourceDegraded;
        public final String sourceSummary;
        public final boolean usingCachedHotCandidates;
        public final ArrayList<HotStockCandidate> fetched;
        public HotCandidatesRefreshEvent(boolean manual, boolean sourceDegraded, String sourceSummary, boolean usingCachedHotCandidates, ArrayList<HotStockCandidate> fetched) {
            this.manual = manual;
            this.sourceDegraded = sourceDegraded;
            this.sourceSummary = sourceSummary;
            this.usingCachedHotCandidates = usingCachedHotCandidates;
            this.fetched = fetched;
        }
    }
}
