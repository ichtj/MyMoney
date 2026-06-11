package com.face.mymoney.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import org.json.JSONException;
import org.json.JSONObject;

@Entity(tableName = "stocks")
public class Stock {
    @PrimaryKey
    @NonNull
    public String code;
    public String name;
    public String market;
    public String groupName;
    public String remark;
    public String price;
    public String changePercent;
    public String turnover;
    public String industry;
    public String mainBusiness;
    public String marketValue;
    public String pe;
    public String revenue;
    public String profit;
    public String riskTag;

    /**
     * 转换为JSON。
     */
    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        try {
            object.put("code", code);
            object.put("name", name);
            object.put("market", market);
            object.put("groupName", groupName);
            object.put("remark", remark);
            object.put("price", price);
            object.put("changePercent", changePercent);
            object.put("turnover", turnover);
            object.put("industry", industry);
            object.put("mainBusiness", mainBusiness);
            object.put("marketValue", marketValue);
            object.put("pe", pe);
            object.put("revenue", revenue);
            object.put("profit", profit);
            object.put("riskTag", riskTag);
        } catch (JSONException e) {
            return object;
        }
        return object;
    }

    /**
     * 从JSON。
     */
    public static Stock fromJson(JSONObject object) {
        Stock stock = new Stock();
        stock.code = object.optString("code");
        stock.name = object.optString("name");
        stock.market = object.optString("market");
        stock.groupName = object.optString("groupName");
        stock.remark = object.optString("remark");
        stock.price = object.optString("price", "--");
        stock.changePercent = object.optString("changePercent", "0.00%");
        stock.turnover = object.optString("turnover", "--");
        stock.industry = object.optString("industry", "--");
        stock.mainBusiness = object.optString("mainBusiness", "--");
        stock.marketValue = object.optString("marketValue", "--");
        stock.pe = object.optString("pe", "--");
        stock.revenue = object.optString("revenue", "--");
        stock.profit = object.optString("profit", "--");
        stock.riskTag = object.optString("riskTag", "--");
        return stock;
    }
}
