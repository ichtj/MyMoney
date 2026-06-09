package com.face.mymoney.ui.widget;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ScrollView;
import android.widget.TextView;

public class PullRefreshScrollView extends ScrollView {
    public interface Listener {
        /**
         * 当刷新时的回调处理。
         */
        void onRefresh();
    }

    private TextView indicator;
    private Listener listener;
    private int triggerHeight;
    private int maxHeight;
    private float downY;
    private boolean dragging;
    private boolean refreshing;

    public PullRefreshScrollView(Context context) {
        super(context);
    }

    public void setPullRefresh(TextView indicator, int triggerHeight, int maxHeight, Listener listener) {
        this.indicator = indicator;
        this.triggerHeight = triggerHeight;
        this.maxHeight = maxHeight;
        this.listener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            downY = event.getY();
            dragging = false;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE && getScrollY() == 0) {
            float distance = event.getY() - downY;
            if (distance > 16) {
                dragging = true;
                return true;
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (indicator == null || listener == null) {
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (refreshing) {
                return true;
            }
            downY = event.getY();
            dragging = getScrollY() == 0;
            return super.onTouchEvent(event);
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE && (dragging || getScrollY() == 0)) {
            float distance = event.getY() - downY;
            if (distance > 0) {
                dragging = true;
                int height = Math.min(maxHeight, (int) (distance * 0.55f));
                setIndicatorHeight(height);
                indicator.setText(height >= triggerHeight ? "松开刷新" : "下拉刷新");
                return true;
            }
        }
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (dragging) {
                int height = indicator.getLayoutParams().height;
                dragging = false;
                if (height >= triggerHeight) {
                    refreshing = true;
                    setIndicatorHeight(triggerHeight);
                    indicator.setText("加载中...");
                    listener.onRefresh();
                } else {
                    setIndicatorHeight(0);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    public void finishRefresh() {
        if (!refreshing || indicator == null) {
            return;
        }
        refreshing = false;
        indicator.setText("加载完成");
        setIndicatorHeight(triggerHeight);
        postDelayed(new Runnable() {
            @Override
            public void run() {
                setIndicatorHeight(0);
                indicator.setText("下拉刷新");
            }
        }, 650L);
    }

    public boolean isRefreshing() {
        return refreshing;
    }

    private void setIndicatorHeight(int height) {
        android.view.ViewGroup.LayoutParams params = indicator.getLayoutParams();
        params.height = height;
        indicator.setLayoutParams(params);
    }
}
