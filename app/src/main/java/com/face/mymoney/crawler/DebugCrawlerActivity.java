package com.face.mymoney.crawler;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.face.mymoney.ui.MainUiKit;

import java.util.ArrayList;

public class DebugCrawlerActivity extends AppCompatActivity {
    private static final int COLOR_BG = Color.rgb(245, 247, 251);
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_OK = Color.rgb(7, 148, 85);
    private static final int COLOR_ERROR = Color.rgb(217, 45, 32);

    private MainUiKit ui;
    private LinearLayout resultList;
    private Button startButton;
    private TextView statusView;
    private volatile boolean running;

    /**
     * 当create时的回调处理。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ui = new MainUiKit(this);
        setContentView(buildContent());
    }

    /**
     * 构建content。
     */
    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(COLOR_BG);

        LinearLayout page = ui.vertical();
        page.setPadding(ui.dp(18), ui.dp(18), ui.dp(18), ui.dp(28));
        scrollView.addView(page, ui.pageParams(true));

        LinearLayout nav = ui.horizontal();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Button back = ui.ghostButton("返回");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        nav.addView(back, ui.wrapHeight(ui.dp(42)));
        page.addView(nav, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(12)));

        page.addView(ui.text("网页源调试", 26, COLOR_TEXT, true), ui.matchWrap());
        page.addView(ui.spacer(ui.dp(6)));
        page.addView(ui.text("长按首页标题进入。这里按默认配置请求网页，只展示标题和摘要。", 13, COLOR_SUB, false), ui.matchWrap());
        page.addView(ui.spacer(ui.dp(16)));

        startButton = ui.primaryButton("开始加载默认网页");
        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFetch();
            }
        });
        page.addView(startButton, ui.matchHeight(ui.dp(48)));
        page.addView(ui.spacer(ui.dp(12)));

        statusView = ui.text("未开始", 13, COLOR_SUB, false);
        page.addView(statusView, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(12)));

        resultList = ui.vertical();
        page.addView(resultList, ui.matchWrap());
        return scrollView;
    }

    /**
     * 启动获取。
     */
    private void startFetch() {
        if (running) {
            return;
        }
        running = true;
        resultList.removeAllViews();
        startButton.setEnabled(false);
        statusView.setText("正在加载默认网页源...");

        new Thread(new Runnable() {
            @Override
            public void run() {
                DefaultWebPageSourceLoader loader = new DefaultWebPageSourceLoader(DebugCrawlerActivity.this);
                final ArrayList<WebPageSource> sources = loader.loadEnabledSources();
                SimpleWebPageFetcher fetcher = new SimpleWebPageFetcher();
                updateStatus("已加载 " + sources.size() + " 个网页源，开始请求...");

                for (int i = 0; i < sources.size(); i++) {
                    WebPageSource source = sources.get(i);
                    updateStatus("正在请求 " + (i + 1) + "/" + sources.size() + ": " + source.name);
                    WebPageFetchResult result = fetcher.fetch(source);
                    addResult(result);
                    if (i < sources.size() - 1) {
                        sleepQuietly(source.delayMillis);
                    }
                }
                finishFetch("完成，共请求 " + sources.size() + " 个网页源");
            }
        }).start();
    }

    /**
     * 添加result。
     */
    private void addResult(final WebPageFetchResult result) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                resultList.addView(resultCard(result), ui.matchWrap());
                resultList.addView(ui.spacer(ui.dp(10)));
            }
        });
    }

    /**
     * result创建卡片布局。
     */
    private View resultCard(WebPageFetchResult result) {
        LinearLayout card = ui.card();
        card.addView(ui.text(result.source.name, 17, COLOR_TEXT, true), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(4)));
        card.addView(ui.text(result.source.url, 12, COLOR_SUB, false), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(8)));

        int statusColor = result.success ? COLOR_OK : COLOR_ERROR;
        String status = result.success ? "成功" : "失败";
        card.addView(ui.text(status + " / HTTP " + result.statusCode + " / " + result.elapsedMillis + "ms", 13, statusColor, true), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(8)));

        String title = result.title.length() == 0 ? "未解析到标题" : result.title;
        card.addView(ui.text(title, 15, COLOR_TEXT, true), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(6)));

        String body = result.success ? result.snippet : result.errorMessage;
        TextView snippet = ui.text(body, 13, COLOR_SUB, false);
        snippet.setLineSpacing(ui.dp(3), 1.0f);
        card.addView(snippet, ui.matchWrap());
        return card;
    }

    /**
     * 更新status。
     */
    private void updateStatus(final String value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                statusView.setText(value);
            }
        });
    }

    /**
     * 结束获取。
     */
    private void finishFetch(final String value) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                running = false;
                startButton.setEnabled(true);
                statusView.setText(value);
            }
        });
    }

    /**
     * sleepquietly。
     */
    private void sleepQuietly(int delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
