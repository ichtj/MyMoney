package com.face.mymoney.crawler.hot.source;

import android.content.Context;

import java.util.ArrayList;

public class HotStockSourceRegistry {
    public ArrayList<HotStockSource> createSources(Context context) {
        ArrayList<HotStockSourceConfig> configs =
                new HotStockSourceConfigLoader(context).loadEnabledConfigs();
        ArrayList<HotStockSource> sources = new ArrayList<HotStockSource>();
        for (int i = 0; i < configs.size(); i++) {
            HotStockSourceConfig config = configs.get(i);
            if ("eastmoney".equals(config.type)) {
                sources.add(new EastmoneyHotStockSource(config));
            } else if ("sina".equals(config.type)) {
                sources.add(new SinaHotStockSource(config));
            } else {
                android.util.Log.w("MyMoneyHotSources", "unsupported hot stock source type=" + config.type
                        + ", id=" + config.id);
            }
        }
        return sources;
    }
}
