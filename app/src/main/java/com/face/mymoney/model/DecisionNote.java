package com.face.mymoney.model;

import org.json.JSONException;
import org.json.JSONObject;

public class DecisionNote {
    public long id;
    public String stockCode;
    public String type;
    public String title;
    public String content;
    public String targetPrice;
    public String stopLossPrice;
    public String confidence;
    public String createdTime;

    /**
     * 转换为JSON。
     */
    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("id", id);
            object.put("stockCode", stockCode);
            object.put("type", type);
            object.put("title", title);
            object.put("content", content);
            object.put("targetPrice", targetPrice);
            object.put("stopLossPrice", stopLossPrice);
            object.put("confidence", confidence);
            object.put("createdTime", createdTime);
        } catch (JSONException e) {
            return object;
        }
        return object;
    }

    /**
     * 从JSON。
     */
    public static DecisionNote fromJson(JSONObject object) {
        DecisionNote note = new DecisionNote();
        note.id = object.optLong("id");
        note.stockCode = object.optString("stockCode");
        note.type = object.optString("type");
        note.title = object.optString("title");
        note.content = object.optString("content");
        note.targetPrice = object.optString("targetPrice", "-");
        note.stopLossPrice = object.optString("stopLossPrice", "-");
        note.confidence = object.optString("confidence", "5");
        note.createdTime = object.optString("createdTime");
        return note;
    }
}
