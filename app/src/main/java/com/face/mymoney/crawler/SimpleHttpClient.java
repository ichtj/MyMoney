package com.face.mymoney.crawler;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class SimpleHttpClient {
    private static final String DEFAULT_USER_AGENT = "Mozilla/5.0 MyMoneyBot/1.0";

    /**
     * 获取。
     */
    public HttpText get(String url, int timeoutMillis, int maxReadBytes,
                        String accept, String userAgent) {
        return get(url, timeoutMillis, maxReadBytes, accept, userAgent, null);
    }

    /**
     * 获取。
     */
    public HttpText get(String url, int timeoutMillis, int maxReadBytes,
                        String accept, String userAgent, String referer) {
        HashMap<String, String> headers = new HashMap<String, String>();
        putHeader(headers, "Accept", accept);
        putHeader(headers, "User-Agent", userAgent);
        putHeader(headers, "Referer", referer);
        return request("GET", url, timeoutMillis, maxReadBytes, headers, null);
    }

    /**
     * postJSON。
     */
    public HttpText postJson(String url, int timeoutMillis, int maxReadBytes,
                             String authorization, String body) {
        HashMap<String, String> headers = new HashMap<String, String>();
        putHeader(headers, "Authorization", authorization);
        putHeader(headers, "Content-Type", "application/json; charset=utf-8");
        putHeader(headers, "Accept", "application/json");
        return request("POST", url, timeoutMillis, maxReadBytes, headers, body);
    }

    /**
     * 请求。
     */
    private HttpText request(String method, String url, int timeoutMillis, int maxReadBytes,
                             HashMap<String, String> headers, String body) {
        HttpURLConnection connection = null;
        long start = System.currentTimeMillis();
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMillis);
            connection.setReadTimeout(timeoutMillis);
            connection.setInstanceFollowRedirects(true);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }
            if (body != null) {
                connection.setDoOutput(true);
                OutputStream outputStream = connection.getOutputStream();
                outputStream.write(body.getBytes(StandardCharsets.UTF_8));
                outputStream.close();
            }

            int statusCode = connection.getResponseCode();
            InputStream inputStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String responseBody = readText(inputStream, maxReadBytes);
            return new HttpText(true, statusCode, responseBody, "", System.currentTimeMillis() - start);
        } catch (Exception e) {
            return new HttpText(false, 0, "", e.getClass().getSimpleName() + ": " + safeMessage(e), System.currentTimeMillis() - start);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * putheader。
     */
    private void putHeader(HashMap<String, String> headers, String key, String value) {
        if (value != null && value.length() > 0) {
            headers.put(key, value);
        }
    }

    /**
     * 读取创建文本控件。
     */
    private String readText(InputStream inputStream, int maxReadBytes) throws Exception {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            int allow = Math.min(length, maxReadBytes - total);
            if (allow > 0) {
                outputStream.write(buffer, 0, allow);
                total += allow;
            }
            if (total >= maxReadBytes) {
                break;
            }
        }
        inputStream.close();
        return outputStream.toString("UTF-8");
    }

    /**
     * 安全message。
     */
    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.length() == 0 ? "no detail" : message;
    }

    public static class HttpText {
        public final boolean transportSuccess;
        public final int statusCode;
        public final String body;
        public final String errorMessage;
        public final long elapsedMillis;

        HttpText(boolean transportSuccess, int statusCode, String body, String errorMessage, long elapsedMillis) {
            this.transportSuccess = transportSuccess;
            this.statusCode = statusCode;
            this.body = body;
            this.errorMessage = errorMessage;
            this.elapsedMillis = elapsedMillis;
        }

        /**
         * 判断是否HTTP成功结果。
         */
        public boolean isHttpSuccess() {
            return transportSuccess && statusCode >= 200 && statusCode < 400;
        }
    }
}
