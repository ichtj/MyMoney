package com.face.mymoney.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

public class PieRatioView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float opportunityRatio = 0.5f;
    private int opportunityColor;
    private int riskColor;
    private int holeColor;

    public PieRatioView(Context context) {
        super(context);
        opportunityColor = 0xFFD92D20;
        riskColor = 0xFF079455;
        holeColor = 0xFFFFFFFF;
    }

    public void setRatio(float opportunityRatio, int opportunityColor, int riskColor, int holeColor) {
        if (opportunityRatio < 0f) {
            opportunityRatio = 0f;
        }
        if (opportunityRatio > 1f) {
            opportunityRatio = 1f;
        }
        this.opportunityRatio = opportunityRatio;
        this.opportunityColor = opportunityColor;
        this.riskColor = riskColor;
        this.holeColor = holeColor;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = Math.min(getWidth(), getHeight());
        float left = (getWidth() - size) / 2f;
        float top = (getHeight() - size) / 2f;
        RectF rect = new RectF(left, top, left + size, top + size);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(riskColor);
        canvas.drawArc(rect, -90f, 360f, true, paint);

        paint.setColor(opportunityColor);
        canvas.drawArc(rect, -90f, 360f * opportunityRatio, true, paint);

        float holePadding = size * 0.28f;
        paint.setColor(holeColor);
        canvas.drawOval(left + holePadding, top + holePadding, left + size - holePadding, top + size - holePadding, paint);
    }
}
