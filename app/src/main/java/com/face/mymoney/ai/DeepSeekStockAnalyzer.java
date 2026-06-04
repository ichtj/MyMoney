package com.face.mymoney.ai;

import android.text.TextUtils;
import android.util.Log;

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
    private static final String TAG = "DeepSeekStockAnalyzer";
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
                        ? "DeepSeek 请求失败：" + response.statusCode + " " + trim(response.body, 240)
                        : "DeepSeek 请求失败：" + response.errorMessage;
                Log.w(TAG, "request failed status=" + response.statusCode + ", body=" + trim(response.body, 800)
                        + ", error=" + response.errorMessage);
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
            Log.d(TAG, "raw content=" + trim(content, 800));
            return parseStructuredResult(content);
        } catch (Exception e) {
            Log.w(TAG, "analyze exception: " + e.getClass().getSimpleName() + " " + nullToEmpty(e.getMessage()));
            return DeepSeekAnalysisResult.error("DeepSeek 分析异常：" + e.getClass().getSimpleName() + " " + nullToEmpty(e.getMessage()));
        }
    }

    private JSONObject buildRequest(Stock stock, ArrayList<News> news,
                                    ArrayList<Opinion> opinions,
                                    ArrayList<DecisionNote> notes) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", BuildConfig.DEEPSEEK_MODEL);
        body.put("temperature", 0.0d);
        body.put("max_tokens", 1100);
        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");
        body.put("response_format", responseFormat);
        JSONObject thinking = new JSONObject();
        thinking.put("type", "disabled");
        body.put("thinking", thinking);

        JSONArray messages = new JSONArray();
        messages.put(message("system", "你是股票信息校验助手。只基于用户提供的数据为指定分项打分，不输出最终胜负比，不给买卖指令，不编造事实。必须只返回严格 JSON。"));
        messages.put(message("user", buildPrompt(stock, news, opinions, notes)));
        body.put("messages", messages);
        Log.d(TAG, "request body=" + trim(body.toString(), 1200));
        return body;
    }

    private String buildPrompt(Stock stock, ArrayList<News> news,
                               ArrayList<Opinion> opinions,
                               ArrayList<DecisionNote> notes) {
        StringBuilder builder = new StringBuilder();
        builder.append("任务：为个股详情页的两个独立 AI 分项打分。不要输出最终胜负比，不要综合所有因素给结论。\n");
        builder.append("通用硬约束：\n");
        builder.append("1. 只能使用下面提供的个股、新闻、市场观点和个人记录，不得补充外部事实。\n");
        builder.append("2. 每个分项必须单独判断，只回答该分项的问题，不能把目标价/止损价当作 AI 分项依据。\n");
        builder.append("3. valid=true 时 opportunity_score 与 risk_score 必须是 0 到 100 的整数，且相加等于 100。\n");
        builder.append("4. valid=false 时两个分数必须都是 0，reason 必须说明缺什么证据。\n");
        builder.append("5. reason 必须引用输入中的具体字段或新闻/观点关键词，不能写“总体较好、存在不确定性、建议关注、需观察”等空话。\n");
        builder.append("6. 禁止输出买入、卖出、加仓、减仓、持有等操作建议。\n");
        builder.append("7. summary 和 reason 中只能使用中文分项名“信息一致性”“新闻情绪”，禁止出现 info_consistency、news_sentiment 等英文 ID。\n\n");

        builder.append("分项定义：\n");
        builder.append("信息一致性（JSON 键名 info_consistency）：只判断公司名称、股票代码、行业、主营业务、新闻题材、市场观点关键词是否匹配。匹配弱或证据不足时增加风险，不判断新闻利好利空。\n");
        builder.append("新闻情绪（JSON 键名 news_sentiment）：只判断爬虫新闻和市场观点的方向与事件强度。正面事件增加机会，负面事件增加风险；无明确事件时返回中性或无效。\n\n");

        builder.append("只返回一个 JSON 对象，不要 Markdown，不要解释文字。格式必须是：\n");
        builder.append("{\"summary\":\"一句话用中文说明信息一致性和新闻情绪是否有效，不给最终胜负比\",");
        builder.append("\"factors\":{");
        builder.append("\"info_consistency\":{\"valid\":true,\"opportunity_score\":45,\"risk_score\":55,\"reason\":\"...\"},");
        builder.append("\"news_sentiment\":{\"valid\":true,\"opportunity_score\":60,\"risk_score\":40,\"reason\":\"...\"}");
        builder.append("}}\n\n");

        builder.append("个股：").append(nullToDash(stock.name)).append(" ").append(nullToDash(stock.code))
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
        String json = "";
        try {
            json = extractJsonObject(content);
            Log.d(TAG, "extracted json=" + trim(json, 800));
            JSONObject object = new JSONObject(json);
            String summary = trim(object.optString("summary", ""), 220);
            JSONObject factors = object.optJSONObject("factors");
            if (summary.length() == 0 || factors == null) {
                Log.w(TAG, "missing summary or factors, json=" + trim(json, 800));
                return DeepSeekAnalysisResult.error("DeepSeek 返回缺少 summary 或 factors");
            }

            DeepSeekAnalysisResult result = DeepSeekAnalysisResult.success(summary);
            result.putFactor(parseFactor(factors, DeepSeekAnalysisResult.FACTOR_INFO_CONSISTENCY));
            result.putFactor(parseFactor(factors, DeepSeekAnalysisResult.FACTOR_NEWS_SENTIMENT));
            if (!result.hasUsableFactors()) {
                Log.w(TAG, "no usable factors after validation, json=" + trim(json, 800));
                return DeepSeekAnalysisResult.error("DeepSeek 未返回任何通过校验的 AI 分项");
            }
            return result;
        } catch (Exception e) {
            Log.w(TAG, "parse failed: " + e.getClass().getSimpleName()
                    + " " + nullToEmpty(e.getMessage())
                    + ", extracted=" + trim(json, 800)
                    + ", raw=" + trim(content, 800));
            return DeepSeekAnalysisResult.error("DeepSeek 返回格式不是有效 JSON：" + trim(content, 180));
        }
    }

    private DeepSeekFactorResult parseFactor(JSONObject factors, String id) {
        JSONObject object = factors.optJSONObject(id);
        if (object == null) {
            Log.w(TAG, "missing factor " + id);
            return new DeepSeekFactorResult(id, false, 0, 0,
                    "缺少" + DeepSeekAnalysisResult.factorDisplayName(id) + "结果。");
        }
        boolean valid = object.optBoolean("valid", false);
        int opportunity = object.optInt("opportunity_score", -1);
        int risk = object.optInt("risk_score", -1);
        String reason = trim(object.optString("reason", ""), 220);
        if (!valid) {
            Log.d(TAG, "factor invalid by model id=" + id + ", reason=" + reason);
            return new DeepSeekFactorResult(id, false, 0, 0,
                    reason.length() == 0 ? "证据不足，未纳入该 AI 分项。" : reason);
        }
        if (opportunity < 0 || opportunity > 100 || risk < 0 || risk > 100
                || opportunity + risk != 100 || isVagueReason(reason)) {
            Log.w(TAG, "factor validation failed id=" + id
                    + ", opportunity=" + opportunity
                    + ", risk=" + risk
                    + ", reason=" + reason);
            return new DeepSeekFactorResult(id, false, 0, 0,
                    "DeepSeek 对" + DeepSeekAnalysisResult.factorDisplayName(id)
                            + "的分数或理由未通过校验，未纳入计算。");
        }
        Log.d(TAG, "factor usable id=" + id + ", opportunity=" + opportunity
                + ", risk=" + risk + ", reason=" + reason);
        return new DeepSeekFactorResult(id, true, opportunity, risk, reason);
    }

    private boolean isVagueReason(String reason) {
        String value = nullToEmpty(reason).trim();
        if (value.length() < 12) {
            return true;
        }
        String[] vague = new String[]{
                "总体较好", "总体一般", "存在不确定性", "建议关注", "需观察", "进一步观察",
                "需要持续关注", "不能确定", "较为模糊", "信息有限"
        };
        for (int i = 0; i < vague.length; i++) {
            if (value.equals(vague[i])) {
                return true;
            }
        }
        return false;
    }

    private String extractJsonObject(String content) {
        String value = stripMarkdownFence(nullToEmpty(content).trim());
        for (int start = value.indexOf('{'); start >= 0; start = value.indexOf('{', start + 1)) {
            int depth = 0;
            boolean inString = false;
            boolean escaped = false;
            for (int i = start; i < value.length(); i++) {
                char c = value.charAt(i);
                if (escaped) {
                    escaped = false;
                    continue;
                }
                if (c == '\\') {
                    escaped = true;
                    continue;
                }
                if (c == '"') {
                    inString = !inString;
                    continue;
                }
                if (inString) {
                    continue;
                }
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String candidate = value.substring(start, i + 1);
                        try {
                            new JSONObject(candidate);
                            return candidate;
                        } catch (Exception ignored) {
                            break;
                        }
                    }
                }
            }
        }
        return value;
    }

    private String stripMarkdownFence(String value) {
        String cleaned = value;
        if (cleaned.startsWith("```")) {
            int firstLineEnd = cleaned.indexOf('\n');
            if (firstLineEnd >= 0) {
                cleaned = cleaned.substring(firstLineEnd + 1);
            }
            int fence = cleaned.lastIndexOf("```");
            if (fence >= 0) {
                cleaned = cleaned.substring(0, fence);
            }
        }
        return cleaned.trim();
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
                    .append(trim(note.title, 80)).append("：")
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
