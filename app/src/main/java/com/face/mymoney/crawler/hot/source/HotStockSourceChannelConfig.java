package com.face.mymoney.crawler.hot.source;

import org.json.JSONObject;

public class HotStockSourceChannelConfig {
    public String id;
    public boolean enabled;
    public int weight;
    public String sortField;

    /**
     * 从JSON。
     */
    public static HotStockSourceChannelConfig fromJson(JSONObject object) {
        HotStockSourceChannelConfig config = new HotStockSourceChannelConfig();
        config.id = object.optString("id", "");
        config.enabled = object.optBoolean("enabled", true);
        config.weight = object.optInt("weight", 10);
        config.sortField = object.optString("sortField", "");
        return config;
    }
}
