package com.face.mymoney.crawler.hot.source;

import java.util.ArrayList;

public interface HotStockSource {
    /**
     * id。
     */
    String id();

    /**
     * 权重。
     */
    int weight();

    /**
     * 获取。
     */
    ArrayList<HotStockSourceItem> fetch();
}
