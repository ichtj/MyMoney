package com.face.mymoney.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;

import com.face.mymoney.model.KLineItem;
import com.face.mymoney.ui.ResponsiveMetrics;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class KLineChartView extends android.view.View {
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_GRID = Color.rgb(226, 232, 240);
    private static final int COLOR_UP = Color.rgb(217, 45, 32);
    private static final int COLOR_DOWN = Color.rgb(7, 148, 85);
    private static final int COLOR_MA5 = Color.rgb(245, 158, 11);

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint candlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DecimalFormat priceFormat = new DecimalFormat("0.00");
    private final RectF bodyRect = new RectF();

    private ArrayList<KLineItem> kLines = new ArrayList<KLineItem>();
    private boolean loading;
    private String errorMessage = "";
    private int rightOffset;
    private float lastTouchX;
    private float touchStartX;
    private float touchStartY;
    private float lastSlotWidth;
    private boolean draggingHorizontal;

    public KLineChartView(Context context) {
        super(context);
        init();
    }

    public KLineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public KLineChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        gridPaint.setColor(COLOR_GRID);
        gridPaint.setStrokeWidth(dp(1));

        textPaint.setColor(COLOR_SUB);
        textPaint.setTextSize(textPx(11));

        candlePaint.setStrokeWidth(dp(1));

        maPaint.setColor(COLOR_MA5);
        maPaint.setStrokeWidth(dp(1));
        maPaint.setStyle(Paint.Style.STROKE);

        setMinimumHeight(dp(220));
    }

    public void setKLines(ArrayList<KLineItem> values) {
        kLines = values == null ? new ArrayList<KLineItem>() : new ArrayList<KLineItem>(values);
        rightOffset = 0;
        invalidate();
    }

    public void setLoading(boolean value) {
        loading = value;
        invalidate();
    }

    public void setErrorMessage(String value) {
        errorMessage = value == null ? "" : value.trim();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (kLines == null || kLines.size() == 0) {
            drawStatus(canvas);
            return;
        }

        float left = dp(48);
        float top = dp(12);
        float right = getWidth() - dp(10);
        float bottom = getHeight() - dp(30);
        if (right <= left || bottom <= top) {
            return;
        }

        int visibleCount = visibleCount(left, right);
        int maxOffset = Math.max(0, kLines.size() - visibleCount);
        if (rightOffset > maxOffset) {
            rightOffset = maxOffset;
        }
        int end = kLines.size() - 1 - rightOffset;
        int start = Math.max(0, end - visibleCount + 1);
        int count = end - start + 1;
        lastSlotWidth = (right - left) / Math.max(1, count);

        double minPrice = Double.MAX_VALUE;
        double maxPrice = -Double.MAX_VALUE;
        for (int i = start; i <= end; i++) {
            KLineItem item = kLines.get(i);
            minPrice = Math.min(minPrice, item.low);
            maxPrice = Math.max(maxPrice, item.high);
        }
        if (maxPrice <= minPrice) {
            maxPrice = maxPrice + 1d;
            minPrice = minPrice - 1d;
        }
        double padding = (maxPrice - minPrice) * 0.06d;
        maxPrice = maxPrice + padding;
        minPrice = minPrice - padding;

        drawGrid(canvas, left, top, right, bottom, minPrice, maxPrice);
        drawCandles(canvas, left, top, bottom, start, end, minPrice, maxPrice);
        drawMa5(canvas, left, top, bottom, start, end, minPrice, maxPrice);
        drawDates(canvas, left, right, bottom, start, end);
        drawLegend(canvas, left, top);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (kLines == null || kLines.size() == 0) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                touchStartX = event.getX();
                touchStartY = event.getY();
                draggingHorizontal = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float moveX = event.getX();
                float dx = moveX - lastTouchX;
                float totalX = Math.abs(moveX - touchStartX);
                float totalY = Math.abs(event.getY() - touchStartY);
                if (totalX > totalY && totalX > dp(6)) {
                    draggingHorizontal = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (draggingHorizontal) {
                    int step = (int) (Math.abs(dx) / Math.max(dp(6), lastSlotWidth));
                    if (step > 0) {
                        int visibleCount = visibleCount(dp(48), getWidth() - dp(10));
                        int maxOffset = Math.max(0, kLines.size() - visibleCount);
                        if (dx > 0) {
                            rightOffset = Math.min(maxOffset, rightOffset + step);
                        } else {
                            rightOffset = Math.max(0, rightOffset - step);
                        }
                        lastTouchX = moveX;
                        invalidate();
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                draggingHorizontal = false;
                return true;
            default:
                break;
        }
        return true;
    }

    private void drawStatus(Canvas canvas) {
        String status;
        if (loading) {
            status = "\u6b63\u5728\u52a0\u8f7d K \u7ebf...";
        } else if (errorMessage.length() > 0) {
            status = "\u83b7\u53d6 K \u7ebf\u5931\u8d25: " + errorMessage;
        } else {
            status = "\u6682\u65e0 K \u7ebf\u6570\u636e";
        }
        textPaint.setColor(COLOR_SUB);
        textPaint.setTextSize(textPx(13));
        textPaint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics metrics = textPaint.getFontMetrics();
        float y = getHeight() / 2f - (metrics.ascent + metrics.descent) / 2f;
        canvas.drawText(status, getWidth() / 2f, y, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom,
                          double minPrice, double maxPrice) {
        textPaint.setColor(COLOR_SUB);
        textPaint.setTextSize(textPx(10));
        textPaint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i <= 4; i++) {
            float y = top + (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, gridPaint);
            double price = maxPrice - (maxPrice - minPrice) * i / 4d;
            canvas.drawText(priceFormat.format(price), dp(2), y + dp(4), textPaint);
        }
        canvas.drawLine(left, top, left, bottom, gridPaint);
    }

    private void drawCandles(Canvas canvas, float left, float top, float bottom,
                             int start, int end, double minPrice, double maxPrice) {
        float halfWidth = Math.max(dp(1), Math.min(lastSlotWidth * 0.32f, dp(5)));
        for (int i = start; i <= end; i++) {
            KLineItem item = kLines.get(i);
            float x = left + lastSlotWidth * (i - start) + lastSlotWidth / 2f;
            float highY = priceY(item.high, top, bottom, minPrice, maxPrice);
            float lowY = priceY(item.low, top, bottom, minPrice, maxPrice);
            float openY = priceY(item.open, top, bottom, minPrice, maxPrice);
            float closeY = priceY(item.close, top, bottom, minPrice, maxPrice);
            int color = item.isUp() ? COLOR_UP : COLOR_DOWN;
            candlePaint.setColor(color);
            candlePaint.setStyle(Paint.Style.STROKE);
            canvas.drawLine(x, highY, x, lowY, candlePaint);

            float bodyTop = Math.min(openY, closeY);
            float bodyBottom = Math.max(openY, closeY);
            if (bodyBottom - bodyTop < dp(2)) {
                bodyTop = bodyTop - dp(1);
                bodyBottom = bodyBottom + dp(1);
            }
            bodyRect.set(x - halfWidth, bodyTop, x + halfWidth, bodyBottom);
            candlePaint.setStyle(Paint.Style.FILL);
            canvas.drawRect(bodyRect, candlePaint);
        }
    }

    private void drawMa5(Canvas canvas, float left, float top, float bottom,
                         int start, int end, double minPrice, double maxPrice) {
        Path path = new Path();
        boolean hasPoint = false;
        for (int i = start; i <= end; i++) {
            if (i < 4) {
                continue;
            }
            double sum = 0d;
            for (int j = i - 4; j <= i; j++) {
                sum += kLines.get(j).close;
            }
            double ma = sum / 5d;
            float x = left + lastSlotWidth * (i - start) + lastSlotWidth / 2f;
            float y = priceY(ma, top, bottom, minPrice, maxPrice);
            if (!hasPoint) {
                path.moveTo(x, y);
                hasPoint = true;
            } else {
                path.lineTo(x, y);
            }
        }
        if (hasPoint) {
            canvas.drawPath(path, maPaint);
        }
    }

    private void drawDates(Canvas canvas, float left, float right, float bottom, int start, int end) {
        textPaint.setColor(COLOR_SUB);
        textPaint.setTextSize(textPx(10));
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(shortDate(kLines.get(start).date), left, bottom + dp(20), textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(shortDate(kLines.get(end).date), right, bottom + dp(20), textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawLegend(Canvas canvas, float left, float top) {
        textPaint.setTextSize(textPx(10));
        textPaint.setColor(COLOR_MA5);
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("MA5", left + dp(6), top + dp(12), textPaint);
    }

    private int visibleCount(float left, float right) {
        int count = (int) ((right - left) / Math.max(1, dp(7)));
        if (count < 30) {
            return 30;
        }
        return Math.min(60, count);
    }

    private float priceY(double price, float top, float bottom, double minPrice, double maxPrice) {
        return (float) (top + (maxPrice - price) * (bottom - top) / (maxPrice - minPrice));
    }

    private String shortDate(String date) {
        if (date == null) {
            return "";
        }
        if (date.length() >= 10) {
            return date.substring(5);
        }
        return date;
    }

    private int dp(int value) {
        return ResponsiveMetrics.dp(getContext(), value);
    }

    private float textPx(int value) {
        return ResponsiveMetrics.sp(getContext(), value) * getResources().getDisplayMetrics().scaledDensity;
    }
}
