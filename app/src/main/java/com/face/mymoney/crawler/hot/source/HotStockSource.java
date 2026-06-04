package com.face.mymoney.crawler.hot.source;

import java.util.ArrayList;

public interface HotStockSource {
    String id();

    int weight();

    ArrayList<HotStockSourceItem> fetch();
}
