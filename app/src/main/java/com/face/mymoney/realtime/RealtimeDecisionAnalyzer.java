package com.face.mymoney.realtime;

import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.model.HotStockCandidate;
import com.face.mymoney.model.MarketIndexQuote;
import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;
import com.face.mymoney.opinion.Opinion;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 只基于当前数据做即时判断，不写入历史记录。
 */
public class RealtimeDecisionAnalyzer {
    public static final int LEVEL_RISK = 0;
    public static final int LEVEL_WAIT = 1;
    public static final int LEVEL_NEUTRAL = 2;
    public static final int LEVEL_GOOD = 3;

    private static final long FRESH_MILLIS = 15 * 60 * 1000L;
    private static final long STALE_MILLIS = 60 * 60 * 1000L;

    public static MarketPulse analyzeMarket(ArrayList<MarketIndexQuote> indices) {
        MarketPulse pulse = new MarketPulse();
        if (indices == null || indices.size() == 0) {
            pulse.status = "环境待确认";
            pulse.summary = "大盘指数尚未刷新，个股信号先按谨慎处理。";
            pulse.level = LEVEL_WAIT;
            return pulse;
        }

        int count = 0;
        int upCount = 0;
        int downCount = 0;
        double total = 0d;
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < indices.size() && i < 3; i++) {
            MarketIndexQuote quote = indices.get(i);
            double change = parsePercent(quote.changePercent);
            if (Double.isNaN(change)) {
                continue;
            }
            count++;
            total += change;
            if (change > 0d) {
                upCount++;
            } else if (change < 0d) {
                downCount++;
            }
            if (detail.length() > 0) {
                detail.append("，");
            }
            detail.append(safe(quote.name)).append(" ").append(safe(quote.changePercent));
        }

        if (count == 0) {
            pulse.status = "环境待确认";
            pulse.summary = "指数返回不完整，先降低个股信号权重。";
            pulse.level = LEVEL_WAIT;
            return pulse;
        }

        double average = total / count;
        pulse.averageChange = average;
        pulse.upCount = upCount;
        pulse.downCount = downCount;
        if (average >= 0.8d && upCount >= 2) {
            pulse.status = "市场偏强";
            pulse.level = LEVEL_GOOD;
        } else if (average >= 0.2d && upCount >= downCount) {
            pulse.status = "市场可做";
            pulse.level = LEVEL_NEUTRAL;
        } else if (average <= -0.8d && downCount >= 2) {
            pulse.status = "市场弱势";
            pulse.level = LEVEL_RISK;
        } else if (average <= -0.2d) {
            pulse.status = "市场偏弱";
            pulse.level = LEVEL_WAIT;
        } else {
            pulse.status = "市场震荡";
            pulse.level = LEVEL_NEUTRAL;
        }
        pulse.summary = detail.length() == 0
                ? "指数变化不完整。"
                : detail.toString();
        return pulse;
    }

    public static CandidateSignal analyzeCandidate(HotStockCandidate candidate,
                                                   ArrayList<MarketIndexQuote> indices,
                                                   ArrayList<HotStockCandidate> candidates,
                                                   long hotRefreshedAtMillis,
                                                   boolean sourceDegraded) {
        CandidateSignal signal = new CandidateSignal();
        if (candidate == null) {
            signal.status = "待确认";
            signal.summary = "候选数据为空。";
            signal.level = LEVEL_WAIT;
            return signal;
        }

        MarketPulse market = analyzeMarket(indices);
        IntradayState intraday = analyzeCandidateIntraday(candidate);
        SectorPulse sector = analyzeSector(candidate.industry, candidates);
        double change = parsePercent(candidate.changePercent);
        double fourDayChange = parsePercent(candidate.fourDayChangePercent);
        double turnover = parsePercent(candidate.turnoverRate);
        double volumeRatio = parseNumber(candidate.volumeRatio);
        double distance = limitUpThreshold(candidate.code, candidate.name) - (Double.isNaN(change) ? 0d : change);
        double amountYi = Math.max(parseAmountYi(candidate.amount), parseAmountYi(candidate.averageAmount4d));

        ArrayList<String> positives = new ArrayList<String>();
        ArrayList<String> risks = new ArrayList<String>();
        int corePositiveCount = 0;

        if (candidate.sourceCount >= 2) {
            positives.add("多源共振");
            corePositiveCount++;
        }
        if (amountYi >= 5d) {
            positives.add("成交额活跃");
            corePositiveCount++;
        }
        if (candidate.activeDays4d >= 2) {
            positives.add("近4日持续活跃");
        }
        if (fourDayChange > 0d && fourDayChange <= 18d) {
            positives.add("短线未明显过热");
        }

        boolean staleData = !isFresh(hotRefreshedAtMillis, System.currentTimeMillis());
        if (sourceDegraded) {
            risks.add("数据源降级");
        }
        if (staleData) {
            risks.add("热股数据非实时");
        }
        if (candidate.recentTwoDayLimitUp) {
            risks.add("连续涨停");
        }
        if (distance <= 1d) {
            risks.add("接近涨停");
        } else if (distance <= 3d) {
            risks.add("涨停距离较近");
        }
        if (fourDayChange > 32d) {
            risks.add("4日涨幅过大");
        }
        if (turnover > 28d) {
            risks.add("换手过高");
        }
        if (volumeRatio > 7d) {
            risks.add("量比过高");
        }
        if (market.level == LEVEL_RISK) {
            risks.add("大盘弱势");
        }
        if (intraday.level == LEVEL_RISK) {
            risks.add(intraday.status);
        } else if (intraday.level == LEVEL_GOOD) {
            positives.add(intraday.status);
            corePositiveCount++;
        }
        if (sector.level == LEVEL_GOOD) {
            positives.add(sector.status);
            corePositiveCount++;
        } else if (sector.level == LEVEL_RISK) {
            risks.add(sector.status);
        }

        boolean hardRisk = containsRisk(candidate.riskTag) || candidate.recentTwoDayLimitUp
                || distance <= 1d || turnover > 35d || intraday.level == LEVEL_RISK;
        boolean overheat = fourDayChange > 24d || distance <= 3d || turnover > 24d
                || volumeRatio > 7d || sector.level == LEVEL_RISK;
        signal.factorDiscipline = buildFactorDiscipline(corePositiveCount, positives.size(),
                risks.size(), hardRisk, overheat, staleData);

        if (hardRisk) {
            signal.status = "风险高";
            signal.level = LEVEL_RISK;
        } else if (overheat) {
            signal.status = "等回调";
            signal.level = LEVEL_WAIT;
        } else if (staleData) {
            signal.status = "待确认";
            signal.level = LEVEL_WAIT;
        } else if (market.level == LEVEL_RISK && candidate.totalScore < 72) {
            signal.status = "待确认";
            signal.level = LEVEL_WAIT;
        } else if (candidate.totalScore >= 65 && corePositiveCount >= 2 && risks.size() <= 1) {
            signal.status = "可关注";
            signal.level = LEVEL_GOOD;
        } else {
            signal.status = "待确认";
            signal.level = LEVEL_NEUTRAL;
        }

        signal.marketStatus = market.status;
        signal.intradayStatus = intraday.status;
        signal.intradaySummary = intraday.summary;
        signal.sectorStatus = sector.status;
        signal.sectorSummary = sector.summary;
        signal.freshness = freshnessText(hotRefreshedAtMillis, System.currentTimeMillis(), "热股");
        signal.summary = joinLimited(positives, "、", "暂无明确优势")
                + "；" + joinLimited(risks, "、", "未见硬风险");
        signal.riskText = joinLimited(risks, "、", "无明显过热风险");
        return signal;
    }

    public static StockSignal analyzeStock(Stock stock,
                                           ArrayList<News> news,
                                           ArrayList<Opinion> opinions,
                                           Long newsFetchedAt,
                                           Long opinionFetchedAt,
                                           DeepSeekAnalysisResult aiReference,
                                           Long aiFetchedAt,
                                           ArrayList<MarketIndexQuote> indices,
                                           ArrayList<HotStockCandidate> candidates,
                                           long nowMillis) {
        StockSignal signal = new StockSignal();
        if (stock == null) {
            signal.status = "待确认";
            signal.summary = "个股为空。";
            signal.level = LEVEL_WAIT;
            return signal;
        }

        MarketPulse market = analyzeMarket(indices);
        NewsEventInsight events = analyzeEvents(stock, news, opinions);
        IntradayState intraday = analyzeStockIntraday(stock);
        SectorPulse sector = analyzeSector(stock.industry, candidates);
        double change = parsePercent(stock.changePercent);
        double turnover = parsePercent(stock.turnover);
        double distance = limitUpThreshold(stock.code, stock.name) - (Double.isNaN(change) ? 0d : change);

        ArrayList<String> positives = new ArrayList<String>();
        ArrayList<String> risks = new ArrayList<String>();
        int corePositiveCount = 0;
        if (market.level >= LEVEL_NEUTRAL) {
            positives.add(market.status);
        } else {
            risks.add(market.status);
        }
        if (events.positiveCount > events.riskCount && events.directCount > 0) {
            positives.add("信息增量偏正面");
            corePositiveCount++;
        }
        int aiOpportunity = aiReference == null ? -1 : aiReference.blendedOpportunityPercent();
        if (aiOpportunity >= 60 && isFresh(aiFetchedAt, nowMillis)) {
            positives.add("AI分项偏机会");
            corePositiveCount++;
        } else if (aiOpportunity >= 0 && aiOpportunity <= 40 && isFresh(aiFetchedAt, nowMillis)) {
            risks.add("AI分项偏风险");
        }

        if (containsRisk(stock.riskTag)) {
            risks.add(stock.riskTag);
        }
        if (distance <= 1d) {
            risks.add("接近涨停");
        } else if (distance <= 3d) {
            risks.add("涨停距离较近");
        }
        if (turnover > 28d) {
            risks.add("换手过高");
        }
        if (events.riskCount > 0) {
            risks.add("新闻/观点含风险词");
        }
        boolean staleNews = !isFresh(newsFetchedAt, nowMillis);
        boolean staleOpinion = !isFresh(opinionFetchedAt, nowMillis);
        boolean staleDecisionData = staleNews || staleOpinion;
        if (staleNews) {
            risks.add("新闻非实时");
        }
        if (staleOpinion) {
            risks.add("舆情非实时");
        }
        if (intraday.level == LEVEL_RISK) {
            risks.add(intraday.status);
        } else if (intraday.level == LEVEL_GOOD) {
            positives.add(intraday.status);
            corePositiveCount++;
        }
        if (sector.level == LEVEL_GOOD) {
            positives.add(sector.status);
            corePositiveCount++;
        } else if (sector.level == LEVEL_RISK) {
            risks.add(sector.status);
        }

        boolean hardRisk = containsRisk(stock.riskTag) || distance <= 1d || turnover > 35d
                || intraday.level == LEVEL_RISK || events.hardRiskCount > 0;
        boolean overheat = distance <= 3d || turnover > 24d || sector.level == LEVEL_RISK
                || (events.riskCount > 0 && events.riskCount >= events.positiveCount);
        signal.factorDiscipline = buildFactorDiscipline(corePositiveCount, positives.size(),
                risks.size(), hardRisk, overheat, staleDecisionData);

        if (hardRisk) {
            signal.status = "风险高";
            signal.level = LEVEL_RISK;
        } else if (overheat) {
            signal.status = "等回调";
            signal.level = LEVEL_WAIT;
        } else if (staleDecisionData) {
            signal.status = "待确认";
            signal.level = LEVEL_WAIT;
        } else if (market.level == LEVEL_RISK) {
            signal.status = "待确认";
            signal.level = LEVEL_WAIT;
        } else if (corePositiveCount >= 2 && risks.size() <= 1) {
            signal.status = "可关注";
            signal.level = LEVEL_GOOD;
        } else {
            signal.status = "待确认";
            signal.level = LEVEL_NEUTRAL;
        }

        signal.marketStatus = market.status;
        signal.marketSummary = market.summary;
        signal.intradayStatus = intraday.status;
        signal.intradaySummary = intraday.summary;
        signal.sectorStatus = sector.status;
        signal.sectorSummary = sector.summary;
        signal.newsStatus = events.status;
        signal.newsSummary = events.summary;
        signal.newsDetail = events.detail;
        signal.newsFreshness = freshnessText(newsFetchedAt, nowMillis, "新闻");
        signal.opinionFreshness = freshnessText(opinionFetchedAt, nowMillis, "观点");
        signal.aiFreshness = freshnessText(aiFetchedAt, nowMillis, "AI");
        signal.summary = joinLimited(positives, "、", "暂无明确机会")
                + "；" + joinLimited(risks, "、", "未见硬风险");
        signal.riskText = joinLimited(risks, "、", "当前未触发硬风险规则");
        return signal;
    }

    public static IntradayState analyzeStockIntraday(Stock stock) {
        if (stock == null) {
            return emptyIntraday("行情待确认", "个股为空。");
        }
        return buildIntradayState(stock.code, stock.name, stock.changePercent,
                stock.turnover, extractVolumeRatio(stock.pe), false);
    }

    public static IntradayState analyzeCandidateIntraday(HotStockCandidate candidate) {
        if (candidate == null) {
            return emptyIntraday("行情待确认", "候选为空。");
        }
        return buildIntradayState(candidate.code, candidate.name, candidate.changePercent,
                candidate.turnoverRate, candidate.volumeRatio, candidate.recentTwoDayLimitUp);
    }

    public static SectorPulse analyzeSector(String industry, ArrayList<HotStockCandidate> candidates) {
        SectorPulse pulse = new SectorPulse();
        String target = normalizeSector(industry);
        if (target.length() == 0) {
            pulse.status = "板块待识别";
            pulse.summary = "当前个股行业缺失，无法判断板块共振。";
            pulse.level = LEVEL_WAIT;
            return pulse;
        }
        if (candidates == null || candidates.size() == 0) {
            pulse.status = "板块待确认";
            pulse.summary = "Top50 热股尚未刷新，无法判断同板块联动。";
            pulse.level = LEVEL_WAIT;
            return pulse;
        }

        int total = 0;
        int upCount = 0;
        int highScoreCount = 0;
        int overheatCount = 0;
        int totalScore = 0;
        double totalChange = 0d;
        ArrayList<String> examples = new ArrayList<String>();
        for (int i = 0; i < candidates.size(); i++) {
            HotStockCandidate item = candidates.get(i);
            if (item == null || !sameSector(target, item.industry)) {
                continue;
            }
            total++;
            totalScore += item.totalScore;
            double change = parsePercent(item.changePercent);
            if (!Double.isNaN(change)) {
                totalChange += change;
                if (change > 0d) {
                    upCount++;
                }
                double distance = limitUpThreshold(item.code, item.name) - change;
                if (distance <= 3d || change >= 8d || item.recentTwoDayLimitUp) {
                    overheatCount++;
                }
            }
            if (item.totalScore >= 65) {
                highScoreCount++;
            }
            if (examples.size() < 3 && item.name != null && item.name.length() > 0) {
                examples.add(item.name);
            }
        }

        pulse.sector = target;
        pulse.hotCount = total;
        pulse.upCount = upCount;
        pulse.highScoreCount = highScoreCount;
        pulse.overheatCount = overheatCount;
        pulse.averageScore = total == 0 ? 0 : Math.round((float) totalScore / (float) total);
        pulse.averageChange = total == 0 ? 0d : totalChange / total;
        if (total == 0) {
            pulse.status = "孤立个股";
            pulse.summary = target + " 在当前 Top50 中未形成同板块联动。";
            pulse.level = LEVEL_WAIT;
        } else if (total >= 5 && overheatCount >= Math.max(2, total / 2)) {
            pulse.status = "板块过热";
            pulse.summary = target + " 同板块 " + total + " 只，过热 " + overheatCount
                    + " 只，注意一致性冲高后的兑现。";
            pulse.level = LEVEL_RISK;
        } else if (total >= 4 && upCount >= Math.max(3, total / 2) && highScoreCount >= 2) {
            pulse.status = "板块共振";
            pulse.summary = target + " 同板块 " + total + " 只，上涨 " + upCount
                    + " 只，高分 " + highScoreCount + " 只，代表：" + joinLimited(examples, "、", "--");
            pulse.level = LEVEL_GOOD;
        } else if (total >= 2 && upCount > 0) {
            pulse.status = "局部活跃";
            pulse.summary = target + " 同板块 " + total + " 只，上涨 " + upCount
                    + " 只，代表：" + joinLimited(examples, "、", "--");
            pulse.level = LEVEL_NEUTRAL;
        } else {
            pulse.status = "联动较弱";
            pulse.summary = target + " 同板块 " + total + " 只，尚未形成明确共振。";
            pulse.level = LEVEL_WAIT;
        }
        return pulse;
    }

    private static IntradayState buildIntradayState(String code, String name, String changeText,
                                                    String turnoverText, String volumeRatioText,
                                                    boolean recentTwoDayLimitUp) {
        IntradayState state = new IntradayState();
        double change = parsePercent(changeText);
        double turnover = parsePercent(turnoverText);
        double volumeRatio = parseNumber(volumeRatioText);
        if (Double.isNaN(change)) {
            return emptyIntraday("行情待刷新", "涨跌幅缺失，无法判断盘中强弱。");
        }

        double distance = limitUpThreshold(code, name) - change;
        state.limitDistanceText = distance <= 0d
                ? "已触及涨停区间"
                : String.format(Locale.CHINA, "距涨停约 %.2f 个百分点", distance);
        state.turnoverText = turnoverText == null || turnoverText.length() == 0 ? "--" : turnoverText;
        state.volumeRatioText = volumeRatioText == null || volumeRatioText.length() == 0
                ? "--"
                : volumeRatioText;

        String strength = change > 3d ? "强势上涨"
                : change > 0d ? "温和上涨"
                : change < -3d ? "明显走弱"
                : change < 0d ? "小幅回落"
                : "平盘震荡";
        String turnoverState = turnoverState(turnover);
        String volumeState = volumeRatioState(volumeRatio);

        if (recentTwoDayLimitUp || distance <= 1d || turnover > 35d || change <= -6d) {
            state.status = "风险回避";
            state.level = LEVEL_RISK;
        } else if (distance <= 3d || change >= 8d || turnover > 24d || volumeRatio > 7d) {
            state.status = "等回落确认";
            state.level = LEVEL_WAIT;
        } else if (change > 0d && turnover >= 2d && turnover <= 18d
                && (Double.isNaN(volumeRatio) || (volumeRatio >= 1.1d && volumeRatio <= 4.5d))) {
            state.status = "强势跟踪";
            state.level = LEVEL_GOOD;
        } else if (change < 0d) {
            state.status = "弱势观察";
            state.level = LEVEL_WAIT;
        } else {
            state.status = "中性震荡";
            state.level = LEVEL_NEUTRAL;
        }

        state.summary = strength + "，" + turnoverState + "，" + volumeState
                + "，" + state.limitDistanceText;
        state.riskText = buildIntradayRisk(change, turnover, volumeRatio, distance, recentTwoDayLimitUp);
        return state;
    }

    private static IntradayState emptyIntraday(String status, String summary) {
        IntradayState state = new IntradayState();
        state.status = status;
        state.summary = summary;
        state.riskText = summary;
        state.level = LEVEL_WAIT;
        state.turnoverText = "--";
        state.volumeRatioText = "--";
        state.limitDistanceText = "--";
        return state;
    }

    private static String turnoverState(double turnover) {
        if (Double.isNaN(turnover)) {
            return "换手未获取";
        }
        if (turnover < 2d) {
            return "换手偏低";
        }
        if (turnover <= 8d) {
            return "换手健康";
        }
        if (turnover <= 18d) {
            return "交易活跃";
        }
        if (turnover <= 28d) {
            return "高换手博弈";
        }
        return "换手过热";
    }

    private static String volumeRatioState(double volumeRatio) {
        if (Double.isNaN(volumeRatio)) {
            return "量比未获取";
        }
        if (volumeRatio < 1d) {
            return "未明显放量";
        }
        if (volumeRatio <= 4d) {
            return "温和放量";
        }
        if (volumeRatio <= 7d) {
            return "放量偏高";
        }
        return "极端放量";
    }

    private static String buildIntradayRisk(double change, double turnover, double volumeRatio,
                                            double distance, boolean recentTwoDayLimitUp) {
        ArrayList<String> risks = new ArrayList<String>();
        if (recentTwoDayLimitUp) {
            risks.add("连续涨停");
        }
        if (distance <= 1d) {
            risks.add("接近涨停");
        } else if (distance <= 3d) {
            risks.add("追高空间变窄");
        }
        if (change >= 8d) {
            risks.add("涨幅偏高");
        }
        if (change <= -6d) {
            risks.add("跌幅过大");
        }
        if (turnover > 28d) {
            risks.add("换手过热");
        }
        if (volumeRatio > 7d) {
            risks.add("量比过高");
        }
        return joinLimited(risks, "、", "无明显盘中过热信号");
    }

    private static String buildFactorDiscipline(int corePositiveCount, int positiveCount,
                                                int riskCount, boolean hardRisk,
                                                boolean overheat, boolean staleData) {
        if (hardRisk) {
            return "硬风险优先：正面因子不抵消硬风险。核心正向 "
                    + corePositiveCount + " 个，风险 " + riskCount + " 个。";
        }
        if (overheat) {
            return "过热降权：涨幅、换手、涨停距离或板块过热时，先降为等回落。";
        }
        if (staleData) {
            return "新鲜度降权：实时数据不完整时，不给强结论。";
        }
        if (corePositiveCount < 2) {
            return "证据不足：核心正向因子少于 2 个，不输出强结论。";
        }
        if (positiveCount + riskCount > 7) {
            return "信号过载：只采纳核心因子，避免指标堆叠导致过度分析。";
        }
        return "因子克制：核心正向 " + corePositiveCount
                + " 个，风险 " + riskCount + " 个，未触发硬风险。";
    }

    public static NewsEventInsight analyzeEvents(Stock stock,
                                                 ArrayList<News> news,
                                                 ArrayList<Opinion> opinions) {
        NewsEventInsight insight = new NewsEventInsight();
        scanEvents(stock, news, insight);
        scanOpinions(stock, opinions, insight);
        int total = insight.positiveCount + insight.riskCount + insight.neutralCount;
        if (total == 0) {
            insight.status = "事件待确认";
            insight.summary = "当前未抓到可用于判断的新闻或观点。";
            insight.detail = "需要刷新新闻/观点后再判断事件强度。";
            insight.level = LEVEL_WAIT;
            return insight;
        }

        String relevance = insight.directCount > 0
                ? "直接相关"
                : insight.industryCount > 0 ? "行业相关" : "相关性偏弱";
        String reaction = priceReaction(stock, insight);
        if (insight.hardRiskCount > 0) {
            insight.status = "硬风险优先";
            insight.level = LEVEL_RISK;
        } else if (insight.riskCount > 0 && insight.riskCount >= insight.positiveCount) {
            insight.status = "风险事件";
            insight.level = LEVEL_RISK;
        } else if (insight.positiveCount >= 2 && insight.directCount > 0) {
            insight.status = "强正面增量";
            insight.level = LEVEL_GOOD;
        } else if (insight.positiveCount > insight.riskCount && insight.directCount > 0) {
            insight.status = "正面增量";
            insight.level = LEVEL_GOOD;
        } else if (insight.themeCount > 0 && insight.directCount == 0) {
            insight.status = "题材关联";
            insight.level = LEVEL_NEUTRAL;
        } else {
            insight.status = "中性观察";
            insight.level = LEVEL_NEUTRAL;
        }
        insight.summary = relevance + "，正面 " + insight.positiveCount
                + "，风险 " + insight.riskCount
                + "，硬风险 " + insight.hardRiskCount + "。";
        insight.detail = "事件类型：" + eventTypeText(insight)
                + "；价格反应：" + reaction
                + "；证据：" + joinLimited(insight.evidence, "、", "暂无明确关键词");
        return insight;
    }

    public static EventSignal analyzeNews(Stock stock, News item) {
        if (item == null) {
            EventSignal signal = new EventSignal();
            signal.status = "事件待确认";
            signal.summary = "新闻为空。";
            signal.level = LEVEL_WAIT;
            return signal;
        }
        return classifyEvent(stock, safe(item.title) + " " + safe(item.content));
    }

    private static void scanEvents(Stock stock, ArrayList<News> news, NewsEventInsight insight) {
        if (news == null) {
            return;
        }
        for (int i = 0; i < news.size(); i++) {
            News item = news.get(i);
            accumulateEvent(classifyEvent(stock, safe(item.title) + " " + safe(item.content)), insight);
        }
    }

    private static void scanOpinions(Stock stock, ArrayList<Opinion> opinions, NewsEventInsight insight) {
        if (opinions == null) {
            return;
        }
        for (int i = 0; i < opinions.size(); i++) {
            Opinion item = opinions.get(i);
            accumulateEvent(classifyEvent(stock, safe(item.title) + " " + safe(item.content)), insight);
        }
    }

    private static EventSignal classifyEvent(Stock stock, String text) {
        EventSignal signal = new EventSignal();
        String value = safe(text);
        if (value.trim().length() == 0) {
            signal.status = "事件待确认";
            signal.summary = "文本为空。";
            signal.level = LEVEL_WAIT;
            return signal;
        }

        boolean direct = stock != null && (contains(value, stock.name) || contains(value, stock.code));
        boolean industry = stock != null && contains(value, stock.industry);
        String hardRisk = firstKeyword(value, new String[]{
                "减持", "清仓", "立案", "监管", "诉讼", "退市", "处罚", "问询", "被执行", "冻结", "爆雷", "债务", "违约", "质押"
        });
        String risk = firstKeyword(value, new String[]{
                "亏损", "预亏", "业绩下滑", "下降", "不及预期", "解禁", "高管离职", "毛利率下降", "商誉"
        });
        String positive = firstKeyword(value, new String[]{
                "订单", "中标", "合同", "签约", "增持", "回购", "分红", "业绩预增", "预增", "增长", "涨价", "获批", "批复", "合作", "突破", "扩产", "新产品"
        });
        String theme = firstKeyword(value, new String[]{
                "AI", "算力", "半导体", "机器人", "低空经济", "新能源", "光模块", "CPO", "数据中心", "存储", "芯片", "军工", "消费电子"
        });
        String announcement = firstKeyword(value, new String[]{
                "公告", "业绩预告", "定增", "重组", "投资者关系", "调研", "董事会"
        });

        signal.direct = direct;
        signal.industryRelated = industry;
        signal.themeRelated = theme.length() > 0;
        signal.announcement = announcement.length() > 0;
        if (hardRisk.length() > 0) {
            signal.status = "硬风险";
            signal.category = "硬风险";
            signal.keyword = hardRisk;
            signal.level = LEVEL_RISK;
        } else if (risk.length() > 0) {
            signal.status = "风险事件";
            signal.category = "风险";
            signal.keyword = risk;
            signal.level = LEVEL_RISK;
        } else if (positive.length() > 0) {
            signal.status = "正面增量";
            signal.category = "正面";
            signal.keyword = positive;
            signal.level = LEVEL_GOOD;
        } else if (theme.length() > 0) {
            signal.status = "题材关联";
            signal.category = "题材";
            signal.keyword = theme;
            signal.level = LEVEL_NEUTRAL;
        } else if (direct || industry) {
            signal.status = "中性相关";
            signal.category = "中性";
            signal.keyword = announcement.length() > 0 ? announcement : "";
            signal.level = LEVEL_NEUTRAL;
        } else {
            signal.status = "相关性弱";
            signal.category = "弱相关";
            signal.keyword = "";
            signal.level = LEVEL_WAIT;
        }

        String relevance = direct ? "直接相关" : industry ? "行业相关" : "未见直接关联";
        String eventType = signal.category + (signal.keyword.length() > 0 ? "/" + signal.keyword : "");
        signal.summary = relevance + " · " + eventType;
        return signal;
    }

    private static void accumulateEvent(EventSignal event, NewsEventInsight insight) {
        if (event == null) {
            return;
        }
        if (event.direct) {
            insight.directCount++;
        } else if (event.industryRelated) {
            insight.industryCount++;
        } else {
            insight.weakCount++;
        }
        if (event.themeRelated) {
            insight.themeCount++;
        }
        if (event.announcement) {
            insight.announcementCount++;
        }
        if ("硬风险".equals(event.category)) {
            insight.hardRiskCount++;
            insight.riskCount++;
        } else if ("风险".equals(event.category)) {
            insight.riskCount++;
        } else if ("正面".equals(event.category)) {
            insight.positiveCount++;
        } else if ("中性".equals(event.category) || "题材".equals(event.category)) {
            insight.neutralCount++;
        }
        if (event.keyword.length() > 0 && !insight.evidence.contains(event.keyword)) {
            insight.evidence.add(event.keyword);
        }
    }

    private static String eventTypeText(NewsEventInsight insight) {
        ArrayList<String> types = new ArrayList<String>();
        if (insight.hardRiskCount > 0) {
            types.add("硬风险");
        }
        if (insight.riskCount > insight.hardRiskCount) {
            types.add("普通风险");
        }
        if (insight.positiveCount > 0) {
            types.add("正面增量");
        }
        if (insight.themeCount > 0) {
            types.add("题材关联");
        }
        if (insight.announcementCount > 0) {
            types.add("公告/披露");
        }
        return joinLimited(types, "、", "中性信息");
    }

    private static String priceReaction(Stock stock, NewsEventInsight insight) {
        double change = stock == null ? Double.NaN : parsePercent(stock.changePercent);
        if (Double.isNaN(change)) {
            return "行情未刷新，无法判断";
        }
        if (insight.hardRiskCount > 0 || insight.riskCount > insight.positiveCount) {
            if (change <= -3d) {
                return "风险已有下跌反应";
            }
            if (change >= 3d) {
                return "上涨中夹带风险，防止兑现";
            }
            return "风险尚未明显反映";
        }
        if (insight.positiveCount > insight.riskCount) {
            if (change >= 7d) {
                return "正面信息可能已大幅反映";
            }
            if (change >= 3d) {
                return "正面信息已有部分反应";
            }
            if (change <= 0d) {
                return "正面信息尚未明显反映";
            }
            return "正面信息轻微反映";
        }
        return "暂无明确价格反应判断";
    }

    public static boolean isFresh(Long fetchedAt, long nowMillis) {
        return fetchedAt != null && isFresh(fetchedAt.longValue(), nowMillis);
    }

    public static boolean isFresh(long fetchedAt, long nowMillis) {
        return fetchedAt > 0L && nowMillis - fetchedAt <= FRESH_MILLIS;
    }

    public static String freshnessText(Long fetchedAt, long nowMillis, String label) {
        if (fetchedAt == null || fetchedAt.longValue() <= 0L) {
            return label + "未刷新";
        }
        return freshnessText(fetchedAt.longValue(), nowMillis, label);
    }

    public static String freshnessText(long fetchedAt, long nowMillis, String label) {
        if (fetchedAt <= 0L) {
            return label + "未刷新";
        }
        long age = Math.max(0L, nowMillis - fetchedAt);
        if (age <= FRESH_MILLIS) {
            return label + "实时 " + formatAge(age);
        }
        if (age <= STALE_MILLIS) {
            return label + "偏旧 " + formatAge(age);
        }
        return label + "过期 " + formatAge(age);
    }

    private static String formatAge(long ageMillis) {
        long minutes = ageMillis / 60000L;
        if (minutes <= 0L) {
            return "刚刚";
        }
        if (minutes < 60L) {
            return minutes + "分钟前";
        }
        return (minutes / 60L) + "小时前";
    }

    private static double limitUpThreshold(String code, String name) {
        String safeCode = code == null ? "" : code.trim();
        String safeName = name == null ? "" : name.trim().toUpperCase(Locale.US);
        if (safeName.contains("ST")) {
            return 5d;
        }
        if (safeCode.startsWith("300") || safeCode.startsWith("301")
                || safeCode.startsWith("688") || safeCode.startsWith("689")) {
            return 20d;
        }
        return 10d;
    }

    private static boolean containsRisk(String value) {
        String text = safe(value);
        if (text.length() == 0 || text.contains("待补充") || text.contains("待同步")) {
            return false;
        }
        return containsAny(text, new String[]{"ST", "风险", "过热", "接近涨停", "涨幅过大", "换手过高", "回撤较大"});
    }

    private static boolean containsAny(String text, String[] keywords) {
        if (text == null || text.length() == 0) {
            return false;
        }
        for (int i = 0; i < keywords.length; i++) {
            if (text.contains(keywords[i])) {
                return true;
            }
        }
        return false;
    }

    private static String firstKeyword(String text, String[] keywords) {
        if (text == null || text.length() == 0) {
            return "";
        }
        for (int i = 0; i < keywords.length; i++) {
            if (text.contains(keywords[i])) {
                return keywords[i];
            }
        }
        return "";
    }

    private static boolean contains(String text, String keyword) {
        return text != null && keyword != null && keyword.length() > 0
                && !"--".equals(keyword) && text.contains(keyword);
    }

    private static String joinLimited(ArrayList<String> values, String separator, String fallback) {
        if (values == null || values.size() == 0) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(values.size(), 4);
        for (int i = 0; i < limit; i++) {
            if (builder.length() > 0) {
                builder.append(separator);
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static double parsePercent(String value) {
        return parseNumber(value);
    }

    private static double parseNumber(String value) {
        if (value == null) {
            return Double.NaN;
        }
        String text = value.replace("%", "")
                .replace("+", "")
                .replace(",", "")
                .replace("亿", "")
                .replace("万", "")
                .trim();
        if (text.length() == 0 || "-".equals(text) || "--".equals(text)) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double parseAmountYi(String value) {
        if (value == null) {
            return 0d;
        }
        double number = parseNumber(value);
        if (Double.isNaN(number)) {
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

    private static String extractVolumeRatio(String value) {
        String text = safe(value).trim();
        if (text.startsWith("量比")) {
            return text.replace("量比", "").trim();
        }
        return "";
    }

    private static String normalizeSector(String value) {
        String text = safe(value).trim();
        if (text.length() == 0 || "--".equals(text) || text.contains("待同步") || text.contains("待识别")) {
            return "";
        }
        int dot = text.indexOf('·');
        if (dot > 0) {
            text = text.substring(0, dot).trim();
        }
        int slash = text.indexOf('/');
        if (slash > 0) {
            text = text.substring(0, slash).trim();
        }
        return text;
    }

    private static boolean sameSector(String target, String candidateSector) {
        String candidate = normalizeSector(candidateSector);
        if (target.length() == 0 || candidate.length() == 0) {
            return false;
        }
        return target.equals(candidate) || target.contains(candidate) || candidate.contains(target);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public static class MarketPulse {
        public String status = "";
        public String summary = "";
        public int level = LEVEL_NEUTRAL;
        public double averageChange;
        public int upCount;
        public int downCount;
    }

    public static class CandidateSignal {
        public String status = "";
        public String summary = "";
        public String marketStatus = "";
        public String intradayStatus = "";
        public String intradaySummary = "";
        public String sectorStatus = "";
        public String sectorSummary = "";
        public String factorDiscipline = "";
        public String freshness = "";
        public String riskText = "";
        public int level = LEVEL_NEUTRAL;
    }

    public static class StockSignal {
        public String status = "";
        public String summary = "";
        public String marketStatus = "";
        public String marketSummary = "";
        public String intradayStatus = "";
        public String intradaySummary = "";
        public String sectorStatus = "";
        public String sectorSummary = "";
        public String newsStatus = "";
        public String newsSummary = "";
        public String newsDetail = "";
        public String newsFreshness = "";
        public String opinionFreshness = "";
        public String aiFreshness = "";
        public String riskText = "";
        public String factorDiscipline = "";
        public int level = LEVEL_NEUTRAL;
    }

    public static class NewsEventInsight {
        public String status = "";
        public String summary = "";
        public int level = LEVEL_NEUTRAL;
        public int positiveCount;
        public int riskCount;
        public int neutralCount;
        public int directCount;
        public int industryCount;
        public int weakCount;
        public int hardRiskCount;
        public int themeCount;
        public int announcementCount;
        public String detail = "";
        public final ArrayList<String> evidence = new ArrayList<String>();
    }

    public static class IntradayState {
        public String status = "";
        public String summary = "";
        public String riskText = "";
        public String turnoverText = "";
        public String volumeRatioText = "";
        public String limitDistanceText = "";
        public int level = LEVEL_NEUTRAL;
    }

    public static class EventSignal {
        public String status = "";
        public String summary = "";
        public String category = "";
        public String keyword = "";
        public boolean direct;
        public boolean industryRelated;
        public boolean themeRelated;
        public boolean announcement;
        public int level = LEVEL_NEUTRAL;
    }

    public static class SectorPulse {
        public String sector = "";
        public String status = "";
        public String summary = "";
        public int hotCount;
        public int upCount;
        public int highScoreCount;
        public int overheatCount;
        public int averageScore;
        public double averageChange;
        public int level = LEVEL_NEUTRAL;
    }
}
