package com.face.mymoney.crawler;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class DefaultWebPageSourceLoader {
    private static final String DEFAULT_ASSET_NAME = "default_web_pages.json";

    private final Context context;

    /**
     * 构造方法：创建 DefaultWebPageSourceLoader 实例。
     */
    public DefaultWebPageSourceLoader(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 加载default数据源列表。
     */
    public ArrayList<WebPageSource> loadDefaultSources() {
        return parseSources(readAsset(DEFAULT_ASSET_NAME));
    }

    /**
     * 加载启用的数据源列表。
     */
    public ArrayList<WebPageSource> loadEnabledSources() {
        ArrayList<WebPageSource> allSources = loadDefaultSources();
        ArrayList<WebPageSource> enabledSources = new ArrayList<WebPageSource>();
        for (int i = 0; i < allSources.size(); i++) {
            WebPageSource source = allSources.get(i);
            if (source.enabled) {
                enabledSources.add(source);
            }
        }
        return enabledSources;
    }

    /**
     * 加载数据源列表根据category。
     */
    public ArrayList<WebPageSource> loadSourcesByCategory(String category) {
        ArrayList<WebPageSource> allSources = loadDefaultSources();
        ArrayList<WebPageSource> result = new ArrayList<WebPageSource>();
        for (int i = 0; i < allSources.size(); i++) {
            WebPageSource source = allSources.get(i);
            if (source.enabled && category.equals(source.category)) {
                result.add(source);
            }
        }
        return result;
    }

    /**
     * 解析数据源列表。
     */
    private ArrayList<WebPageSource> parseSources(String json) {
        ArrayList<WebPageSource> sources = new ArrayList<WebPageSource>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                WebPageSource source = WebPageSource.fromJson(array.getJSONObject(i));
                if (source.isValid()) {
                    sources.add(source);
                }
            }
        } catch (JSONException e) {
            sources.clear();
        }
        return sources;
    }

    /**
     * 读取Assets静态资源。
     */
    private String readAsset(String assetName) {
        InputStream inputStream = null;
        try {
            inputStream = context.getAssets().open(assetName);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toString("UTF-8");
        } catch (IOException e) {
            return "[]";
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
