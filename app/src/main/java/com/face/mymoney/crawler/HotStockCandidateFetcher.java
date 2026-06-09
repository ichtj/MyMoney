package com.face.mymoney.crawler;

import android.content.Context;

import com.face.mymoney.crawler.hot.source.HotStockSource;
import com.face.mymoney.crawler.hot.source.HotStockSourceItem;
import com.face.mymoney.crawler.hot.source.HotStockSourceRegistry;
import com.face.mymoney.model.HotStockCandidate;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

public class HotStockCandidateFetcher {
    private static final String TAG = "MyMoneyHotStocks";
    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_READ_BYTES = 512 * 1024;
    private static final int TARGET_SIZE = 50;
    private static final int MIN_KLINE_RANK_LIMIT = 30;
    private static final int MAX_SINGLE_SOURCE_RESULT = 10;
    private static final int MIN_HEALTHY_EASTMONEY_COUNT = 200;
    private static final double MAX_PRICE = 50d;
    private static final double MIN_EFFECTIVE_AMOUNT_YI = 1d;
    private static final double MIN_EFFECTIVE_TURNOVER = 2d;
    private static final String USER_AGENT = "Mozilla/5.0 MyMoneyBot/1.0";

    private final Context context;
    private final SimpleHttpClient httpClient = new SimpleHttpClient();
    private boolean sourceDegraded;
    private String sourceSummary = "";

    /**
     * 构造方法：创建 HotStockCandidateFetcher 实例。
     */
    public HotStockCandidateFetcher(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 判断是否数据源degraded。
     */
    public boolean isSourceDegraded() {
        return sourceDegraded;
    }

    /**
     * 获取数据源summary。
     */
    public String getSourceSummary() {
        return sourceSummary;
    }

    /**
     * 获取top50hot股票列表。
     */
    public ArrayList<HotStockCandidate> fetchTop50HotStocks() {
        sourceDegraded = false;
        sourceSummary = "";
        ArrayList<HotStockCandidate> merged = mergeSources();
        android.util.Log.d(TAG, "fetch start merged=" + merged.size());
        Collections.sort(merged, new Comparator<HotStockCandidate>() {
            @Override
            public int compare(HotStockCandidate left, HotStockCandidate right) {
                return right.hotScore - left.hotScore;
            }
        });

        ArrayList<HotStockCandidate> filtered = new ArrayList<HotStockCandidate>();
        HashMap<String, Integer> rejectCounts = new HashMap<String, Integer>();
        int universePassed = 0;
        int scoredCount = 0;
        int maxScore = Integer.MIN_VALUE;
        int minScore = Integer.MAX_VALUE;

        // Collect candidates that pass the initial filter to enrich in parallel
        ArrayList<HotStockCandidate> candidatesToEnrich = new ArrayList<HotStockCandidate>();
        for (int i = 0; i < merged.size(); i++) {
            HotStockCandidate candidate = merged.get(i);
            if (getUniverseRejectReason(candidate) == null) {
                candidatesToEnrich.add(candidate);
            }
        }

        if (candidatesToEnrich.size() > 0) {
            int threadCount = Math.min(12, candidatesToEnrich.size());
            java.util.concurrent.ExecutorService klinePool = java.util.concurrent.Executors.newFixedThreadPool(threadCount);
            ArrayList<java.util.concurrent.Future<?>> futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < candidatesToEnrich.size(); i++) {
                final HotStockCandidate candidate = candidatesToEnrich.get(i);
                futures.add(klinePool.submit(new Runnable() {
                    @Override
                    public void run() {
                        enrichRecentFourDays(candidate);
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    futures.get(i).get();
                } catch (Exception e) {
                    android.util.Log.w(TAG, "Parallel enrich interrupted/failed: " + e.getMessage());
                }
            }
            klinePool.shutdown();
        }

        for (int i = 0; i < merged.size(); i++) {
            HotStockCandidate candidate = merged.get(i);
            String universeRejectReason = getUniverseRejectReason(candidate);
            if (universeRejectReason != null) {
                addRejectCount(rejectCounts, universeRejectReason);
                continue;
            }
            universePassed++;
            if (!candidate.hasRecentKlineData) {
                addRejectCount(rejectCounts, "kline_missing_soft");
            }
            String enrichedRejectReason = getEnrichedRejectReason(candidate);
            if (enrichedRejectReason != null) {
                addRejectCount(rejectCounts, enrichedRejectReason);
                continue;
            }
            score(candidate);
            scoredCount++;
            maxScore = Math.max(maxScore, candidate.totalScore);
            minScore = Math.min(minScore, candidate.totalScore);
            if (candidate.totalScore >= acceptThreshold(candidate) && !isHardRisk(candidate)) {
                filtered.add(candidate);
                logCandidate("accepted", candidate);
            } else {
                addRejectCount(rejectCounts, candidate.hasRecentKlineData ? "score_lt_55" : "fallback_score_lt_45");
                if (scoredCount <= 20 || candidate.totalScore >= 45) {
                    logCandidate("score_reject", candidate);
                }
            }
        }
        ArrayList<HotStockCandidate> result = buildFinalResult(filtered);
        android.util.Log.d(TAG, "fetch finish merged=" + merged.size()
                + ", universePassed=" + universePassed
                + ", scored=" + scoredCount
                + ", accepted=" + filtered.size()
                + ", result=" + result.size()
                + ", degraded=" + sourceDegraded
                + ", minScore=" + (scoredCount == 0 ? "--" : String.valueOf(minScore))
                + ", maxScore=" + (scoredCount == 0 ? "--" : String.valueOf(maxScore))
                + ", rejects=" + rejectCounts);
        logFinalRanks(result);
        if (result.size() == 0) {
            logTopCandidates(merged, 12);
        }
        return result;
    }

    /**
     * 构建finalresult。
     */
    private ArrayList<HotStockCandidate> buildFinalResult(ArrayList<HotStockCandidate> filtered) {
        ArrayList<HotStockCandidate> sorted = new ArrayList<HotStockCandidate>(filtered);
        Collections.sort(sorted, finalRankComparator());

        ArrayList<HotStockCandidate> result = new ArrayList<HotStockCandidate>();
        HashSet<String> selectedCodes = new HashSet<String>();
        int[] singleSourceCount = new int[]{0};

        addFinalCandidates(result, selectedCodes, sorted, true, true, singleSourceCount);
        addFinalCandidates(result, selectedCodes, sorted, true, false, singleSourceCount);
        int klineOnlyResultSize = result.size();
        if (result.size() < MIN_KLINE_RANK_LIMIT) {
            addFinalCandidates(result, selectedCodes, sorted, false, false, singleSourceCount);
        }

        android.util.Log.d(TAG, "rank_policy result=" + result.size()
                + ", accepted=" + filtered.size()
                + ", singleSource=" + countSingleSource(result)
                + ", klineMissing=" + countMissingKline(result)
                + ", klineAvailable=" + countRecentKline(filtered)
                + ", klineOnlyResult=" + klineOnlyResultSize
                + ", maxSingleSource=" + MAX_SINGLE_SOURCE_RESULT
                + ", minKlineRankLimit=" + MIN_KLINE_RANK_LIMIT);
        return result;
    }

    /**
     * 添加final候选股票列表。
     */
    private void addFinalCandidates(ArrayList<HotStockCandidate> result,
                                    HashSet<String> selectedCodes,
                                    ArrayList<HotStockCandidate> sorted,
                                    boolean requireKline,
                                    boolean enforceSingleSourceCap,
                                    int[] singleSourceCount) {
        for (int i = 0; i < sorted.size() && result.size() < TARGET_SIZE; i++) {
            HotStockCandidate candidate = sorted.get(i);
            if (selectedCodes.contains(candidate.code)) {
                continue;
            }
            if (requireKline && !candidate.hasRecentKlineData) {
                continue;
            }
            if (enforceSingleSourceCap
                    && candidate.sourceCount <= 1
                    && singleSourceCount[0] >= MAX_SINGLE_SOURCE_RESULT) {
                continue;
            }
            result.add(candidate);
            selectedCodes.add(candidate.code);
            if (candidate.sourceCount <= 1) {
                singleSourceCount[0]++;
            }
        }
    }

    /**
     * finalrankcomparator。
     */
    private Comparator<HotStockCandidate> finalRankComparator() {
        return new Comparator<HotStockCandidate>() {
            @Override
            public int compare(HotStockCandidate left, HotStockCandidate right) {
                int sourceCompare = sourcePriority(right) - sourcePriority(left);
                if (sourceCompare != 0) {
                    return sourceCompare;
                }
                int klineCompare = klinePriority(right) - klinePriority(left);
                if (klineCompare != 0) {
                    return klineCompare;
                }
                int scoreCompare = right.totalScore - left.totalScore;
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return right.hotScore - left.hotScore;
            }
        };
    }

    /**
     * 数据源priority。
     */
    private int sourcePriority(HotStockCandidate candidate) {
        return candidate.sourceCount >= 2 ? 1 : 0;
    }

    /**
     * klinepriority。
     */
    private int klinePriority(HotStockCandidate candidate) {
        return candidate.hasRecentKlineData ? 1 : 0;
    }

    /**
     * 统计single数据源。
     */
    private int countSingleSource(ArrayList<HotStockCandidate> candidates) {
        int count = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).sourceCount <= 1) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计缺失的kline。
     */
    private int countMissingKline(ArrayList<HotStockCandidate> candidates) {
        int count = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (!candidates.get(i).hasRecentKlineData) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计recentkline。
     */
    private int countRecentKline(ArrayList<HotStockCandidate> candidates) {
        int count = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).hasRecentKlineData) {
                count++;
            }
        }
        return count;
    }

    /**
     * merge数据源列表。
     */
    private ArrayList<HotStockCandidate> mergeSources() {
        HashMap<String, HotStockCandidate> byCode = new HashMap<String, HotStockCandidate>();
        HashMap<String, HashMap<String, Integer>> platformChannelScores = new HashMap<String, HashMap<String, Integer>>();
        HashMap<String, HashMap<String, Integer>> platformWeights = new HashMap<String, HashMap<String, Integer>>();
        HashMap<String, HashSet<String>> platformNames = new HashMap<String, HashSet<String>>();
        HashMap<String, HashSet<String>> channelNames = new HashMap<String, HashSet<String>>();
        ArrayList<HotStockSource> sources = new HotStockSourceRegistry().createSources(context);
        HashMap<String, Integer> sourceCounts = new HashMap<String, Integer>();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        for (int i = 0; i < sources.size(); i++) {
            HotStockSource source = sources.get(i);
            ArrayList<HotStockSourceItem> items = source.fetch();
            sourceCounts.put(source.id(), items.size());
            android.util.Log.d(TAG, "source=" + source.id() + ", count=" + items.size());
            for (int j = 0; j < items.size(); j++) {
                HotStockSourceItem item = items.get(j);
                if (item.code == null || item.code.length() != 6 || item.name == null || item.name.length() == 0) {
                    continue;
                }
                HotStockCandidate candidate = byCode.get(item.code);
                if (candidate == null) {
                    candidate = new HotStockCandidate();
                    candidate.code = item.code;
                    candidate.name = item.name;
                    candidate.market = item.market;
                    candidate.industry = emptyToDash(item.industry);
                    candidate.concept = emptyToDash(item.concept);
                    candidate.price = emptyToDash(item.price);
                    candidate.changePercent = emptyToDefault(item.changePercent, "0.00%");
                    candidate.turnoverRate = emptyToDash(item.turnoverRate);
                    candidate.amount = emptyToDash(item.amount);
                    candidate.volumeRatio = emptyToDash(item.volumeRatio);
                    candidate.mainNetInflow = emptyToDash(item.mainNetInflow);
                    candidate.limitUpCount5d = 0;
                    candidate.hasRecentLimitUp = false;
                    candidate.recentTwoDayLimitUp = false;
                    candidate.hasRecentKlineData = false;
                    candidate.hasDragonTiger = false;
                    candidate.hotScore = 0;
                    candidate.opportunityScore = 0;
                    candidate.moneyScore = 0;
                    candidate.activityScore = 0;
                    candidate.themeScore = 0;
                    candidate.riskDeduct = 0;
                    candidate.stageTag = "";
                    candidate.sourceQualityTag = "";
                    candidate.riskTag = buildRiskTag(candidate);
                    candidate.collectedDate = today;
                    byCode.put(item.code, candidate);
                } else {
                    fillMissing(candidate, item);
                }
                addPlatformScore(platformChannelScores, platformWeights, platformNames, channelNames, item);
            }
        }
        Integer eastmoneyCount = sourceCounts.get("eastmoney");
        Integer sinaCount = sourceCounts.get("sina");
        Integer tencentCount = sourceCounts.get("tencent");
        sourceSummary = "东方财富" + (eastmoneyCount == null ? "--" : String.valueOf(eastmoneyCount))
                + " / 新浪" + (sinaCount == null ? "--" : String.valueOf(sinaCount))
                + " / 腾讯" + (tencentCount == null ? "--" : String.valueOf(tencentCount));
        if (eastmoneyCount != null && sinaCount != null && sinaCount > 0
                && eastmoneyCount < MIN_HEALTHY_EASTMONEY_COUNT) {
            sourceDegraded = true;
            android.util.Log.w(TAG, "source_degraded reason="
                    + (eastmoneyCount == 0 ? "eastmoney_empty" : "eastmoney_low")
                    + ", eastmoney=" + eastmoneyCount
                    + ", sina=" + sinaCount
                    + ", minHealthyEastmoney=" + MIN_HEALTHY_EASTMONEY_COUNT
                    + ", sourceCounts=" + sourceCounts);
        }
        applyPlatformScores(byCode, platformChannelScores, platformWeights, platformNames, channelNames);
        logMergedSourceStats(byCode);
        return new ArrayList<HotStockCandidate>(byCode.values());
    }

    /**
     * 日志merged数据源stats。
     */
    private void logMergedSourceStats(HashMap<String, HotStockCandidate> byCode) {
        int source1 = 0;
        int source2 = 0;
        int source3Plus = 0;
        int tencentTouched = 0;
        for (Map.Entry<String, HotStockCandidate> entry : byCode.entrySet()) {
            HotStockCandidate candidate = entry.getValue();
            if (candidate.sourceCount <= 1) {
                source1++;
            } else if (candidate.sourceCount == 2) {
                source2++;
            } else {
                source3Plus++;
            }
            String sources = candidate.sourceSummary == null ? "" : candidate.sourceSummary;
            if (sources.contains("腾讯") || sources.contains("鑵捐")) {
                tencentTouched++;
            }
        }
        android.util.Log.d(TAG, "merge source stats total=" + byCode.size()
                + ", source1=" + source1
                + ", source2=" + source2
                + ", source3plus=" + source3Plus
                + ", tencentTouched=" + tencentTouched);
    }

    /**
     * 添加platformscore。
     */
    private void addPlatformScore(HashMap<String, HashMap<String, Integer>> platformChannelScores,
                                  HashMap<String, HashMap<String, Integer>> platformWeights,
                                  HashMap<String, HashSet<String>> platformNames,
                                  HashMap<String, HashSet<String>> channelNames,
                                  HotStockSourceItem item) {
        HashMap<String, Integer> channelScores = platformChannelScores.get(item.code);
        if (channelScores == null) {
            channelScores = new HashMap<String, Integer>();
            platformChannelScores.put(item.code, channelScores);
        }
        Integer currentScore = channelScores.get(item.sourceId);
        int channelWeight = item.fromLimitUpPool ? Math.min(item.channelWeight, 8) : item.channelWeight;
        channelScores.put(item.sourceId, (currentScore == null ? 0 : currentScore) + channelWeight);

        HashMap<String, Integer> weights = platformWeights.get(item.code);
        if (weights == null) {
            weights = new HashMap<String, Integer>();
            platformWeights.put(item.code, weights);
        }
        weights.put(item.sourceId, item.sourceWeight);

        HashSet<String> names = platformNames.get(item.code);
        if (names == null) {
            names = new HashSet<String>();
            platformNames.put(item.code, names);
        }
        names.add(emptyToDefault(item.sourceName, item.sourceId));

        String channelId = emptyToDefault(item.channelId, "");
        if (channelId.length() > 0) {
            HashSet<String> channels = channelNames.get(item.code);
            if (channels == null) {
                channels = new HashSet<String>();
                channelNames.put(item.code, channels);
            }
            channels.add(channelId);
        }
    }

    /**
     * 应用platformscores。
     */
    private void applyPlatformScores(HashMap<String, HotStockCandidate> byCode,
                                     HashMap<String, HashMap<String, Integer>> platformChannelScores,
                                     HashMap<String, HashMap<String, Integer>> platformWeights,
                                     HashMap<String, HashSet<String>> platformNames,
                                     HashMap<String, HashSet<String>> channelNames) {
        for (Map.Entry<String, HotStockCandidate> entry : byCode.entrySet()) {
            String code = entry.getKey();
            HotStockCandidate candidate = entry.getValue();
            HashMap<String, Integer> channelScores = platformChannelScores.get(code);
            HashMap<String, Integer> weights = platformWeights.get(code);
            if (channelScores == null || weights == null) {
                candidate.hotScore = 0;
                candidate.sourceCount = 0;
                candidate.sourceSummary = "";
                candidate.sourceChannelSummary = "";
                continue;
            }
            int score = 0;
            for (Map.Entry<String, Integer> scoreEntry : channelScores.entrySet()) {
                String sourceId = scoreEntry.getKey();
                Integer platformWeight = weights.get(sourceId);
                int cap = platformWeight == null ? 0 : platformWeight;
                score += Math.min(cap, scoreEntry.getValue());
            }
            candidate.sourceCount = channelScores.size();
            candidate.hotScore = score + Math.max(0, candidate.sourceCount - 1) * 18;
            candidate.sourceSummary = joinNames(platformNames.get(code));
            candidate.sourceChannelSummary = joinNames(channelNames.get(code));
            candidate.sourceQualityTag = buildSourceQualityTag(candidate);
        }
    }

    /**
     * joinnames。
     */
    private String joinNames(HashSet<String> names) {
        if (names == null || names.size() == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (name == null || name.length() == 0) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(name);
        }
        return builder.toString();
    }

    /**
     * fill缺失的。
     */
    private void fillMissing(HotStockCandidate candidate, HotStockSourceItem item) {
        if (isEmptyValue(candidate.industry)) {
            candidate.industry = emptyToDash(item.industry);
        }
        if (isEmptyValue(candidate.concept)) {
            candidate.concept = emptyToDash(item.concept);
        }
        if (isEmptyValue(candidate.price)) {
            candidate.price = emptyToDash(item.price);
        }
        if (isEmptyValue(candidate.turnoverRate)) {
            candidate.turnoverRate = emptyToDash(item.turnoverRate);
        }
        if (isEmptyValue(candidate.amount)) {
            candidate.amount = emptyToDash(item.amount);
        }
        if (isEmptyValue(candidate.volumeRatio)) {
            candidate.volumeRatio = emptyToDash(item.volumeRatio);
        }
        if (isEmptyValue(candidate.mainNetInflow)) {
            candidate.mainNetInflow = emptyToDash(item.mainNetInflow);
        }
        if (candidate.changePercent == null || candidate.changePercent.length() == 0 || "0.00%".equals(candidate.changePercent)) {
            candidate.changePercent = emptyToDefault(item.changePercent, "0.00%");
        }
    }

    /**
     * enrichrecentfourdays。
     */
    private void enrichRecentFourDays(HotStockCandidate candidate) {
        String url = "https://push2his.eastmoney.com/api/qt/stock/kline/get"
                + "?secid=" + secId(candidate.code)
                + "&fields1=f1,f2,f3,f4,f5,f6"
                + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                + "&klt=101&fqt=1&end=20500101&lmt=4";
        try {
            SimpleHttpClient.HttpText response = httpClient.get(url, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "application/json,text/plain,*/*", USER_AGENT);
            if (!response.isHttpSuccess()) {
                return;
            }
            JSONObject data = new JSONObject(response.body).optJSONObject("data");
            JSONArray klines = data == null ? null : data.optJSONArray("klines");
            if (klines == null || klines.length() == 0) {
                return;
            }
            double firstClose = 0d;
            double lastClose = 0d;
            int activeDays = 0;
            int upDays = 0;
            int limitCount = 0;
            int validDays = 0;
            double totalAmountYi = 0d;
            double totalTurnover = 0d;
            boolean previousLimitUp = false;
            boolean latestLimitUp = false;
            for (int i = 0; i < klines.length(); i++) {
                String row = klines.optString(i, "");
                String[] fields = row.split(",");
                if (fields.length < 11) {
                    continue;
                }
                double close = numberOrZero(parseNumber(fields[2]));
                double amountYi = numberOrZero(parseNumber(fields[6])) / 100000000d;
                double changePercent = numberOrZero(parseNumber(fields[8]));
                double turnover = numberOrZero(parseNumber(fields[10]));
                if (close <= 0d) {
                    continue;
                }
                validDays++;
                if (firstClose <= 0d) {
                    firstClose = close;
                }
                lastClose = close;
                if (amountYi >= 1d || turnover >= 3d) {
                    activeDays++;
                }
                if (changePercent > 0d) {
                    upDays++;
                }
                boolean isLimitUp = changePercent >= limitUpThreshold(candidate.code);
                if (isLimitUp) {
                    limitCount++;
                }
                previousLimitUp = latestLimitUp;
                latestLimitUp = isLimitUp;
                totalAmountYi += amountYi;
                totalTurnover += turnover;
            }
            int dayCount = validDays;
            if (firstClose > 0d && lastClose > 0d) {
                candidate.fourDayChangePercent = String.format(Locale.CHINA, "%+.2f%%", (lastClose - firstClose) * 100d / firstClose);
            }
            candidate.activeDays4d = activeDays;
            candidate.upDays4d = upDays;
            candidate.averageAmount4d = dayCount > 0
                    ? String.format(Locale.CHINA, "%.2f亿", totalAmountYi / dayCount)
                    : "--";
            candidate.averageTurnover4d = dayCount > 0
                    ? String.format(Locale.CHINA, "%.2f%%", totalTurnover / dayCount)
                    : "--";
            candidate.limitUpCount5d = limitCount;
            candidate.hasRecentLimitUp = limitCount > 0;
            candidate.recentTwoDayLimitUp = validDays >= 2 && previousLimitUp && latestLimitUp;
            candidate.hasRecentKlineData = validDays > 0;
        } catch (Exception e) {
            android.util.Log.w(TAG, "enrichRecentFourDays failed code=" + candidate.code
                    + ", error=" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * score。
     */
    private void score(HotStockCandidate candidate) {
        double change = parsePercent(candidate.changePercent);
        double fourDayChange = parsePercent(candidate.fourDayChangePercent);
        double turnover = parsePercent(candidate.turnoverRate);
        double amountYi = parseAmountYi(candidate.amount);
        double averageAmountYi = parseAmountYi(candidate.averageAmount4d);
        double effectiveAmountYi = Math.max(amountYi, averageAmountYi);
        double averageTurnover = parsePercent(candidate.averageTurnover4d);
        double effectiveTurnover = Math.max(turnover, averageTurnover);
        double volumeRatio = parseSafe(candidate.volumeRatio);
        double mainNetInflowYi = parseAmountYi(candidate.mainNetInflow);

        candidate.sourceQualityTag = buildSourceQualityTag(candidate);
        candidate.hotScore = Math.min(candidate.hotScore, 25);
        if (candidate.sourceCount >= 2) {
            candidate.hotScore += 4;
        }
        if (isHighQualitySource(candidate)) {
            candidate.hotScore += 4;
        } else if (isGainersOnlySource(candidate)) {
            candidate.hotScore -= 5;
        }
        candidate.hotScore = clamp(candidate.hotScore, 0, 25);

        candidate.moneyScore = 0;
        if (mainNetInflowYi > 0d) {
            candidate.moneyScore += mainNetInflowYi >= 1d ? 12 : 8;
        } else if (effectiveAmountYi >= 5d && change > 0d) {
            candidate.moneyScore += 5;
        }
        if (effectiveAmountYi >= 10d) {
            candidate.moneyScore += 8;
        } else if (effectiveAmountYi >= 5d) {
            candidate.moneyScore += 6;
        } else if (effectiveAmountYi >= 2d) {
            candidate.moneyScore += 4;
        }
        candidate.moneyScore = clamp(candidate.moneyScore, 0, 25);

        candidate.activityScore = 0;
        if (effectiveTurnover >= 3d && effectiveTurnover <= 18d) {
            candidate.activityScore += 8;
        } else if (effectiveTurnover > 18d && effectiveTurnover <= 28d) {
            candidate.activityScore += 3;
        }
        if (volumeRatio >= 1.2d && volumeRatio <= 4d) {
            candidate.activityScore += 7;
        } else if (volumeRatio > 4d && volumeRatio <= 7d) {
            candidate.activityScore += 3;
        }
        candidate.activityScore += Math.min(10, candidate.activeDays4d * 3);
        candidate.activityScore = clamp(candidate.activityScore, 0, 25);

        candidate.opportunityScore = 0;
        if (fourDayChange > 0d && fourDayChange <= 18d) {
            candidate.opportunityScore += 8;
        } else if (fourDayChange > 18d && fourDayChange <= 32d) {
            candidate.opportunityScore += 4;
        }
        candidate.opportunityScore += Math.min(5, candidate.upDays4d * 2);
        if (change > -3d && limitUpDistance(candidate) > 5d) {
            candidate.opportunityScore += 7;
        } else if (change > -3d && limitUpDistance(candidate) > 3d) {
            candidate.opportunityScore += 4;
        }
        if (candidate.hasRecentLimitUp) {
            candidate.opportunityScore += 2;
        }
        if (!candidate.hasRecentLimitUp && effectiveAmountYi >= 2d && candidate.activeDays4d >= 2
                && fourDayChange > -3d && fourDayChange <= 18d) {
            candidate.opportunityScore += 4;
        }
        candidate.opportunityScore = clamp(candidate.opportunityScore, 0, 20);

        candidate.themeScore = isEmptyValue(candidate.industry) ? 3 : 10;
        candidate.stageTag = buildStageTag(candidate);
        candidate.riskDeduct = riskDeduct(candidate);
        candidate.totalScore = candidate.hotScore + candidate.moneyScore + candidate.activityScore
                + candidate.opportunityScore + candidate.themeScore - candidate.riskDeduct;
        candidate.riskTag = buildRiskTag(candidate);
        candidate.reason = buildReason(candidate);
    }

    /**
     * 获取universerejectreason。
     */
    private String getUniverseRejectReason(HotStockCandidate candidate) {
        String code = candidate.code == null ? "" : candidate.code.trim();
        if (!(code.startsWith("00") || code.startsWith("30") || code.startsWith("60"))) {
            return "code_scope";
        }
        if (code.startsWith("688") || code.startsWith("8") || code.startsWith("4") || code.startsWith("9")) {
            return "excluded_board";
        }
        if (isHardRisk(candidate)) {
            return "st";
        }
        double price = parseSafe(candidate.price);
        if (price <= 0d || price > MAX_PRICE) {
            return price <= 0d ? "price_invalid" : "price_gt_50";
        }
        if (parsePercent(candidate.changePercent) >= limitUpThreshold(code)) {
            return "near_limit_up";
        }
        return null;
    }

    /**
     * 获取enrichedrejectreason。
     */
    private String getEnrichedRejectReason(HotStockCandidate candidate) {
        if (candidate.recentTwoDayLimitUp) {
            return "two_day_limit_up";
        }
        double effectiveAmountYi = Math.max(parseAmountYi(candidate.amount), parseAmountYi(candidate.averageAmount4d));
        double effectiveTurnover = Math.max(parsePercent(candidate.turnoverRate), parsePercent(candidate.averageTurnover4d));
        if (effectiveAmountYi < MIN_EFFECTIVE_AMOUNT_YI && effectiveTurnover < MIN_EFFECTIVE_TURNOVER) {
            return "liquidity_low";
        }
        return null;
    }

    /**
     * acceptthreshold。
     */
    private int acceptThreshold(HotStockCandidate candidate) {
        return candidate.hasRecentKlineData ? 55 : 45;
    }

    /**
     * 添加reject统计。
     */
    private void addRejectCount(HashMap<String, Integer> rejectCounts, String reason) {
        Integer count = rejectCounts.get(reason);
        rejectCounts.put(reason, count == null ? 1 : count + 1);
    }

    /**
     * 日志置顶候选股票列表。
     */
    private void logTopCandidates(ArrayList<HotStockCandidate> candidates, int maxCount) {
        int count = Math.min(maxCount, candidates.size());
        for (int i = 0; i < count; i++) {
            HotStockCandidate candidate = candidates.get(i);
            String universeRejectReason = getUniverseRejectReason(candidate);
            android.util.Log.d(TAG, "top_raw[" + i + "] code=" + candidate.code
                    + ", name=" + candidate.name
                    + ", hotScore=" + candidate.hotScore
                    + ", price=" + candidate.price
                    + ", change=" + candidate.changePercent
                    + ", amount=" + candidate.amount
                    + ", turnover=" + candidate.turnoverRate
                    + ", volumeRatio=" + candidate.volumeRatio
                    + ", mainNetInflow=" + candidate.mainNetInflow
                    + ", twoDayLimitUp=" + candidate.recentTwoDayLimitUp
                    + ", sources=" + candidate.sourceCount
                    + ", channels=" + candidate.sourceChannelSummary
                    + ", reject=" + (universeRejectReason == null ? "score_or_not_scored" : universeRejectReason));
        }
    }

    /**
     * 日志候选股票。
     */
    private void logCandidate(String stage, HotStockCandidate candidate) {
        android.util.Log.d(TAG, stage + " code=" + candidate.code
                + ", name=" + candidate.name
                + ", total=" + candidate.totalScore
                + ", hot=" + candidate.hotScore
                + ", money=" + candidate.moneyScore
                + ", activity=" + candidate.activityScore
                + ", opportunity=" + candidate.opportunityScore
                + ", theme=" + candidate.themeScore
                + ", riskDeduct=" + candidate.riskDeduct
                + ", stageTag=" + candidate.stageTag
                + ", sourceQuality=" + candidate.sourceQualityTag
                + ", price=" + candidate.price
                + ", change=" + candidate.changePercent
                + ", fourDayChange=" + candidate.fourDayChangePercent
                + ", activeDays4d=" + candidate.activeDays4d
                + ", upDays4d=" + candidate.upDays4d
                + ", amount=" + candidate.amount
                + ", avgAmount4d=" + candidate.averageAmount4d
                + ", avgTurnover4d=" + candidate.averageTurnover4d
                + ", turnover=" + candidate.turnoverRate
                + ", volumeRatio=" + candidate.volumeRatio
                + ", mainNetInflow=" + candidate.mainNetInflow
                + ", recentLimitUp=" + candidate.hasRecentLimitUp
                + ", twoDayLimitUp=" + candidate.recentTwoDayLimitUp
                + ", sources=" + candidate.sourceCount
                + ", channels=" + candidate.sourceChannelSummary);
    }

    /**
     * 日志finalranks。
     */
    private void logFinalRanks(ArrayList<HotStockCandidate> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            logCandidate("final_rank rank=" + (i + 1), candidates.get(i));
        }
    }

    /**
     * 构建reason。
     */
    private String buildReason(HotStockCandidate candidate) {
        return "阶段：" + emptyToDefault(candidate.stageTag, "--")
                + "，来源质量：" + emptyToDefault(candidate.sourceQualityTag, "--")
                + "，来源" + candidate.sourceCount + "个平台(" + candidate.sourceSummary + ")"
                + (candidate.hasRecentKlineData ? "" : "，近4日K线暂缺")
                + "，4日涨幅" + emptyToDefault(candidate.fourDayChangePercent, "--")
                + "，活跃" + candidate.activeDays4d + "/4天"
                + "，主力净流入" + emptyToDefault(candidate.mainNetInflow, "--")
                + "，成交额" + candidate.amount
                + "，换手" + candidate.turnoverRate
                + "，综合评分" + candidate.totalScore;
    }

    /**
     * 构建高风险创建标签控件。
     */
    private String buildRiskTag(HotStockCandidate candidate) {
        if (candidate.name != null && candidate.name.toUpperCase(Locale.US).contains("ST")) {
            return "ST风险";
        }
        double change = parsePercent(candidate.changePercent);
        double fourDayChange = parsePercent(candidate.fourDayChangePercent);
        double turnover = parsePercent(candidate.turnoverRate);
        if (change < -5d) {
            return "回撤较大";
        }
        if (limitUpDistance(candidate) <= 1d) {
            return "接近涨停";
        }
        if (fourDayChange > 32d) {
            return "短期涨幅过大";
        }
        if (turnover > 28d) {
            return "换手过高";
        }
        return "观察波动";
    }

    /**
     * 高风险deduct。
     */
    private int riskDeduct(HotStockCandidate candidate) {
        if (candidate.name != null && candidate.name.toUpperCase(Locale.US).contains("ST")) {
            return 100;
        }
        int deduct = 0;
        double change = parsePercent(candidate.changePercent);
        double fourDayChange = parsePercent(candidate.fourDayChangePercent);
        double turnover = parsePercent(candidate.turnoverRate);
        double volumeRatio = parseSafe(candidate.volumeRatio);
        if (change < -5d) {
            deduct += 18;
        }
        double limitDistance = limitUpDistance(candidate);
        if (limitDistance <= 1d) {
            deduct += 25;
        } else if (limitDistance <= 3d) {
            deduct += 16;
        } else if (limitDistance <= 5d) {
            deduct += 8;
        }
        if (fourDayChange > 32d) {
            deduct += 20;
        }
        if (turnover > 28d) {
            deduct += 15;
        }
        if (volumeRatio > 7d) {
            deduct += 10;
        }
        if (!candidate.hasRecentKlineData) {
            deduct += 8;
        }
        return deduct;
    }

    /**
     * 构建stage创建标签控件。
     */
    private String buildStageTag(HotStockCandidate candidate) {
        if (!candidate.hasRecentKlineData) {
            return "行情待确认";
        }
        double change = parsePercent(candidate.changePercent);
        double fourDayChange = parsePercent(candidate.fourDayChangePercent);
        double distance = limitUpDistance(candidate);
        if (candidate.recentTwoDayLimitUp || fourDayChange > 32d || distance <= 1d) {
            return "过热剔除";
        }
        if (distance <= 3d || fourDayChange > 22d || parsePercent(candidate.turnoverRate) > 24d) {
            return "高热谨慎";
        }
        if (candidate.sourceCount >= 2 && candidate.activeDays4d >= 2
                && fourDayChange > 0d && fourDayChange <= 18d && change > -3d) {
            return "活跃上升";
        }
        return "启动观察";
    }

    /**
     * 构建数据源quality创建标签控件。
     */
    private String buildSourceQualityTag(HotStockCandidate candidate) {
        if (isHighQualitySource(candidate)) {
            return "成交换手共振";
        }
        if (isGainersOnlySource(candidate)) {
            return "单涨幅热度";
        }
        if (candidate.sourceCount >= 2) {
            return "多来源共振";
        }
        return "单来源观察";
    }

    /**
     * 判断是否highquality数据源。
     */
    private boolean isHighQualitySource(HotStockCandidate candidate) {
        String channels = candidate.sourceChannelSummary == null ? "" : candidate.sourceChannelSummary;
        return channels.contains("amount") && channels.contains("turnover");
    }

    /**
     * 判断是否gainersonly数据源。
     */
    private boolean isGainersOnlySource(HotStockCandidate candidate) {
        String channels = candidate.sourceChannelSummary == null ? "" : candidate.sourceChannelSummary;
        return channels.contains("gainers") && !channels.contains("amount") && !channels.contains("turnover");
    }

    /**
     * limitupdistance。
     */
    private double limitUpDistance(HotStockCandidate candidate) {
        return limitUpThreshold(candidate.code) - parsePercent(candidate.changePercent);
    }

    /**
     * 判断是否hard高风险。
     */
    private boolean isHardRisk(HotStockCandidate candidate) {
        return candidate.name != null && candidate.name.toUpperCase(Locale.US).contains("ST");
    }

    /**
     * secid。
     */
    private String secId(String code) {
        String safeCode = code == null ? "" : code.trim();
        String market = safeCode.startsWith("6") || safeCode.startsWith("9") ? "1" : "0";
        return market + "." + safeCode;
    }

    /**
     * limitupthreshold。
     */
    private double limitUpThreshold(String code) {
        if (code != null && (code.startsWith("300") || code.startsWith("301"))) {
            return 19.5d;
        }
        return 9.5d;
    }

    /**
     * 解析number。
     */
    private Double parseNumber(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return null;
        }
        String text = String.valueOf(value)
                .replace("%", "")
                .replace("亿", "")
                .replace("万", "")
                .trim();
        if (text.length() == 0 || "-".equals(text) || "--".equals(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * numberorzero。
     */
    private double numberOrZero(Double value) {
        return value == null ? 0d : value;
    }

    /**
     * 解析安全。
     */
    private double parseSafe(String value) {
        Double number = parseNumber(value);
        return number == null ? 0d : number;
    }

    /**
     * 解析percent。
     */
    private double parsePercent(String value) {
        return parseSafe(value);
    }

    /**
     * 解析amountyi。
     */
    private double parseAmountYi(String value) {
        if (value == null) {
            return 0d;
        }
        Double number = parseNumber(value);
        if (number == null) {
            return 0d;
        }
        if (value.contains("亿")) {
            return number;
        }
        if (value.contains("万")) {
            return number / 10000d;
        }
        return number / 100000000d;
    }

    /**
     * empty转换为dash。
     */
    private String emptyToDash(String value) {
        return emptyToDefault(value, "--");
    }

    /**
     * empty转换为default。
     */
    private String emptyToDefault(String value, String fallback) {
        if (value == null || value.trim().length() == 0 || "-".equals(value.trim())) {
            return fallback;
        }
        return value.trim();
    }

    /**
     * 判断是否emptyvalue。
     */
    private boolean isEmptyValue(String value) {
        return value == null || value.length() == 0 || "--".equals(value);
    }

    /**
     * clamp。
     */
    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
