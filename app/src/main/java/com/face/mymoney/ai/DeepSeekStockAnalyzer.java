package com.face.mymoney.ai;

import android.text.TextUtils;

import com.face.mymoney.BuildConfig;
import com.face.mymoney.crawler.SimpleHttpClient;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;
import com.face.mymoney.opinion.Opinion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class DeepSeekStockAnalyzer {
    private static final String API_URL = "https://api.deepseek.com/chat/completions";
    private static final int TIMEOUT_MILLIS = 30000;
    private static final int MAX_READ_BYTES = 256 * 1024;
    private static final int MAX_NEWS = 8;
    private static final int MAX_OPINIONS = 10;
    private static final int MAX_NOTES = 6;

    private final SimpleHttpClient httpClient = new SimpleHttpClient();

    public boolean isConfigured() {
        return BuildConfig.DEEPSEEK_API_KEY != null && BuildConfig.DEEPSEEK_API_KEY.trim().length() > 0;
    }

    public DeepSeekAnalysisResult analyze(Stock stock, ArrayList<News> news,
                                          ArrayList<Opinion> opinions,
                                          ArrayList<DecisionNote> notes) {
        if (!isConfigured()) {
            return DeepSeekAnalysisResult.error("未配置 DeepSeek API Key。请在 local.properties 增加 deepseek.apiKey=你的Key");
        }

        try {
            SimpleHttpClient.HttpText response = httpClient.postJson(API_URL, TIMEOUT_MILLIS, MAX_READ_BYTES,
                    "Bearer " + BuildConfig.DEEPSEEK_API_KEY,
                    buildRequest(stock, news, opinions, notes).toString());
            if (!response.transportSuccess || response.statusCode < 200 || response.statusCode >= 300) {
                String message = response.transportSuccess
                        ? "DeepSeek 请求失败：" + response.statusCode + " " + trim(response.body, 160)
                        : "DeepSeek 请求失败：" + response.errorMessage;
                return DeepSeekAnalysisResult.error(message);
            }

            JSONObject object = new JSONObject(response.body);
            JSONArray choices = object.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return DeepSeekAnalysisResult.error("DeepSeek 未返回分析内容");
            }
            JSONObject message = choices.optJSONObject(0).optJSONObject("message");
            String content = message == null ? "" : message.optString("content", "").trim();
            if (content.length() == 0) {
                return DeepSeekAnalysisResult.error("DeepSeek 返回内容为空");
            }
            return parseStructuredResult(content);
        } catch (Exception e) {
            return DeepSeekAnalysisResult.error("DeepSeek 分析异常：" + e.getClass().getSimpleName() + " " + nullToEmpty(e.getMessage()));
        }
    }

    private JSONObject buildRequest(Stock stock, ArrayList<News> news,
                                    ArrayList<Opinion> opinions,
                                    ArrayList<DecisionNote> notes) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", BuildConfig.DEEPSEEK_MODEL);
        body.put("temperature", 0.0d);
        body.put("max_tokens", 700);
        JSONObject thinking = new JSONObject();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);

        JSONArray messages = new JSONArray();
        messages.put(message("system", "你是股票信息分析助手。只基于用户提供的爬虫资讯、市场观点和个人记录做胜负比复盘，不编造事实，不给出买卖指令。必须输出严格 JSON。"));
        messages.put(message("user", buildPrompt(stock, news, opinions, notes)));
        body.put("messages", messages);
        return body;
    }

    private String buildPrompt(Stock stock, ArrayList<News> news,
                               ArrayList<Opinion> opinions,
                               ArrayList<DecisionNote> notes) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务：为个股详情页生成唯一、明确的胜负比参考。\n");
        builder.append("准确性约束：\n");
        builder.append("1. 只能使用下面提供的爬虫新闻、市场观点和个人记录，不得编造事实。\n");
        builder.append("2. opportunity_percent 和 risk_percent 必须是 0 到 100 的整数，且相加必须等于 100。\n");
        builder.append("3. 证据不足时必须返回 has_ratio=false，两个比例都填 0，不能强行给比例。\n");
        builder.append("4. has_ratio=true 时，summary 必须解释同一组机会/风险比例，写出具体依据，避免模糊词。\n");
        builder.append("5. 不输出买入、卖出、加仓、减仓等操作指令。\n");
        builder.append("只返回 JSON，不要 Markdown：{\"has_ratio\":true,\"opportunity_percent\":60,\"risk_percent\":40,\"summary\":\"...\"}\n\n");
        builder.append("个股：").append(stock.name).append(" ").append(stock.code)
                .append("，行业：").append(nullToDash(stock.industry))
                .append("，当前价：").append(nullToDash(stock.price))
                .append("，涨跌幅：").append(nullToDash(stock.changePercent))
                .append("，换手：").append(nullToDash(stock.turnover))
                .append("，主营：").append(nullToDash(stock.mainBusiness))
                .append("，风险标签：").append(nullToDash(stock.riskTag)).append("\n\n");

        appendNews(builder, news);
        appendOpinions(builder, opinions);
        appendNotes(builder, notes);
        return builder.toString();
    }

    private DeepSeekAnalysisResult parseStructuredResult(String content) {
        try {
            JSONObject object = new JSONObject(extractJsonObject(content));
            boolean hasRatio = object.optBoolean("has_ratio", false);
            String summary = trim(object.optString("summary", ""), 260);
            int opportunityPercent = object.optInt("opportunity_percent", -1);
            int riskPercent = object.optInt("risk_percent", -1);
            if (summary.length() == 0) {
                return DeepSeekAnalysisResult.error("DeepSeek 返回缺少 summary");
            }
            if (!hasRatio) {
                return DeepSeekAnalysisResult.successWithoutRatio(summary);
            }
            if (opportunityPercent < 0 || opportunityPercent > 100
                    || riskPercent < 0 || riskPercent > 100
                    || opportunityPercent + riskPercent != 100) {
                return DeepSeekAnalysisResult.successWithoutRatio("DeepSeek 返回的机会/风险比例未通过校验，保留原胜负比；分析：" + summary);
            }
            return DeepSeekAnalysisResult.success(opportunityPercent, riskPercent, summary);
        } catch (Exception e) {
            return DeepSeekAnalysisResult.error("DeepSeek 返回格式不是有效 JSON：" + trim(content, 180));
        }
    }

    private String extractJsonObject(String content) {
        String value = nullToEmpty(content).trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private void appendNews(StringBuilder builder, ArrayList<News> news) {
        builder.append("爬虫新闻：\n");
        if (news == null || news.size() == 0) {
            builder.append("- 暂无\n\n");
            return;
        }
        for (int i = 0; i < news.size() && i < MAX_NEWS; i++) {
            News item = news.get(i);
            builder.append("- [").append(nullToDash(item.source)).append(" ")
                    .append(nullToDash(item.time)).append("] ")
                    .append(trim(item.title, 80)).append("：")
                    .append(trim(item.content, 120)).append("\n");
        }
        builder.append("\n");
    }

    private void appendOpinions(StringBuilder builder, ArrayList<Opinion> opinions) {
        builder.append("市场观点：\n");
        if (opinions == null || opinions.size() == 0) {
            builder.append("- 暂无\n\n");
            return;
        }
        for (int i = 0; i < opinions.size() && i < MAX_OPINIONS; i++) {
            Opinion item = opinions.get(i);
            builder.append("- [").append(nullToDash(item.source)).append(" ")
                    .append(nullToDash(item.time)).append("] ")
                    .append(trim(item.title, 80)).append("：")
                    .append(trim(item.content, 120)).append("\n");
        }
        builder.append("\n");
    }

    private void appendNotes(StringBuilder builder, ArrayList<DecisionNote> notes) {
        builder.append("个人决策记录：\n");
        if (notes == null || notes.size() == 0) {
            builder.append("- 暂无\n");
            return;
        }
        for (int i = 0; i < notes.size() && i < MAX_NOTES; i++) {
            DecisionNote note = notes.get(i);
            builder.append("- ").append(nullToDash(note.type)).append(" ")
                    .append(nullToDash(note.createdTime)).append("：")
                    .append(trim(note.title, 80)).append("；")
                    .append("目标价 ").append(nullToDash(note.targetPrice))
                    .append("，止损价 ").append(nullToDash(note.stopLossPrice))
                    .append("，内容 ").append(trim(note.content, 120)).append("\n");
        }
    }

    private JSONObject message(String role, String content) throws Exception {
        JSONObject object = new JSONObject();
        object.put("role", role);
        object.put("content", content);
        return object;
    }

    private String trim(String value, int maxLength) {
        String safeValue = nullToEmpty(value).replaceAll("\\s+", " ").trim();
        if (safeValue.length() <= maxLength) {
            return safeValue;
        }
        return safeValue.substring(0, maxLength) + "...";
    }

    private String nullToDash(String value) {
        return TextUtils.isEmpty(value) ? "--" : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
