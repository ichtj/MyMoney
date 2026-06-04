package com.face.mymoney.crawler.hot.source;

import org.json.JSONObject;

import java.util.ArrayList;

public class HotStockSourceConfig {
    public String id;
    public String name;
    public String type;
    public boolean enabled;
    public int weight;
    public int rankPageSize;
    public int limitUpPageSize;
    public ArrayList<HotStockSourceChannelConfig> channels = new ArrayList<HotStockSourceChannelConfig>();

    public static HotStockSourceConfig fromJson(JSONObject object) {
        HotStockSourceConfig config = new HotStockSourceConfig();
        config.id = object.optString("id", "");
        config.name = object.optString("name", config.id);
        config.type = object.optString("type", config.id);
        config.enabled = object.optBoolean("enabled", true);
        config.weight = object.optInt("weight", 10);
        config.rankPageSize = object.optInt("rankPageSize", 120);
        config.limitUpPageSize = object.optInt("limitUpPageSize", 100);
        org.json.JSONArray array = object.optJSONArray("channels");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject channel = array.optJSONObject(i);
                if (channel != null) {
                    config.channels.add(HotStockSourceChannelConfig.fromJson(channel));
                }
            }
        }
        return config;
    }
}
