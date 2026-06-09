package com.face.mymoney.crawler.hot.source;

import android.content.Context;

import org.json.JSONArray;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;

public class HotStockSourceConfigLoader {
    private static final String TAG = "MyMoneyHotSourceCfg";
    private static final String ASSET_NAME = "hot_stock_sources.json";

    private final Context context;

    /**
     * 构造方法：创建 HotStockSourceConfigLoader 实例。
     */
    public HotStockSourceConfigLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 加载启用的configs。
     */
    public ArrayList<HotStockSourceConfig> loadEnabledConfigs() {
        ArrayList<HotStockSourceConfig> result = new ArrayList<HotStockSourceConfig>();
        ArrayList<HotStockSourceConfig> configs = loadConfigs();
        for (int i = 0; i < configs.size(); i++) {
            HotStockSourceConfig config = configs.get(i);
            if (config.enabled && config.id.length() > 0) {
                result.add(config);
            }
        }
        return result;
    }

    /**
     * 加载configs。
     */
    private ArrayList<HotStockSourceConfig> loadConfigs() {
        ArrayList<HotStockSourceConfig> result = new ArrayList<HotStockSourceConfig>();
        try {
            JSONArray array = new JSONArray(readAssetText());
            for (int i = 0; i < array.length(); i++) {
                result.add(HotStockSourceConfig.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "loadConfigs failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            result.clear();
            addDefaultConfigs(result);
        }
        return result;
    }

    /**
     * 添加defaultconfigs。
     */
    private void addDefaultConfigs(ArrayList<HotStockSourceConfig> result) {
        HotStockSourceConfig eastmoney = new HotStockSourceConfig();
        eastmoney.id = "eastmoney";
        eastmoney.name = "东方财富";
        eastmoney.type = "eastmoney";
        eastmoney.enabled = true;
        eastmoney.weight = 100;
        eastmoney.rankPageSize = 220;
        eastmoney.limitUpPageSize = 0;
        eastmoney.channels.add(defaultChannel("gainers", 22, "f3"));
        eastmoney.channels.add(defaultChannel("amount", 38, "f6"));
        eastmoney.channels.add(defaultChannel("turnover", 32, "f8"));
        result.add(eastmoney);

        HotStockSourceConfig tencent = new HotStockSourceConfig();
        tencent.id = "tencent";
        tencent.name = "鑵捐琛屾儏";
        tencent.type = "tencent";
        tencent.enabled = true;
        tencent.weight = 55;
        tencent.rankPageSize = 150;
        tencent.limitUpPageSize = 0;
        tencent.channels.add(defaultChannel("gainers", 12, "changepercent"));
        tencent.channels.add(defaultChannel("amount", 22, "amount"));
        tencent.channels.add(defaultChannel("turnover", 18, "turnoverratio"));
        result.add(tencent);
    }

    /**
     * defaultchannel。
     */
    private HotStockSourceChannelConfig defaultChannel(String id, int weight, String sortField) {
        HotStockSourceChannelConfig config = new HotStockSourceChannelConfig();
        config.id = id;
        config.enabled = true;
        config.weight = weight;
        config.sortField = sortField;
        return config;
    }

    /**
     * 读取Assets静态资源创建文本控件。
     */
    private String readAssetText() throws Exception {
        InputStream inputStream = context.getAssets().open(ASSET_NAME);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, length);
        }
        inputStream.close();
        return outputStream.toString("UTF-8");
    }
}
