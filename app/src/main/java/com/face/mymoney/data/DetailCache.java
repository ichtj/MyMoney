package com.face.mymoney.data;

import com.face.mymoney.ai.DeepSeekAnalysisResult;
import com.face.mymoney.model.News;
import com.face.mymoney.opinion.Opinion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;

public class DetailCache {
    public String stockCode = "";
    public long newsFetchedAt;
    public long opinionFetchedAt;
    public long analysisFetchedAt;
    public ArrayList<News> news = new ArrayList<News>();
    public ArrayList<Opinion> opinions = new ArrayList<Opinion>();
    public DeepSeekAnalysisResult analysis;

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("stockCode", stockCode);
            object.put("newsFetchedAt", newsFetchedAt);
            object.put("opinionFetchedAt", opinionFetchedAt);
            object.put("analysisFetchedAt", analysisFetchedAt);
            object.put("news", newsToJson(news));
            object.put("opinions", opinionsToJson(opinions));
            if (analysis != null) {
                object.put("analysis", analysis.toJson());
            }
        } catch (Exception ignored) {
        }
        return object;
    }

    public static DetailCache fromJson(JSONObject object) {
        DetailCache cache = new DetailCache();
        cache.stockCode = object.optString("stockCode", "");
        cache.newsFetchedAt = object.optLong("newsFetchedAt", 0L);
        cache.opinionFetchedAt = object.optLong("opinionFetchedAt", 0L);
        cache.analysisFetchedAt = object.optLong("analysisFetchedAt", 0L);
        cache.news = parseNews(object.optJSONArray("news"));
        cache.opinions = parseOpinions(object.optJSONArray("opinions"));
        JSONObject analysisObject = object.optJSONObject("analysis");
        if (analysisObject != null) {
            cache.analysis = DeepSeekAnalysisResult.fromJson(analysisObject);
        }
        return cache;
    }

    private static JSONArray newsToJson(ArrayList<News> news) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < news.size(); i++) {
            array.put(news.get(i).toJson());
        }
        return array;
    }

    private static JSONArray opinionsToJson(ArrayList<Opinion> opinions) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < opinions.size(); i++) {
            array.put(opinions.get(i).toJson());
        }
        return array;
    }

    private static ArrayList<News> parseNews(JSONArray array) {
        ArrayList<News> list = new ArrayList<News>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) {
                list.add(News.fromJson(object));
            }
        }
        return list;
    }

    private static ArrayList<Opinion> parseOpinions(JSONArray array) {
        ArrayList<Opinion> list = new ArrayList<Opinion>();
        if (array == null) {
            return list;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) {
                list.add(Opinion.fromJson(object));
            }
        }
        return list;
    }
}
