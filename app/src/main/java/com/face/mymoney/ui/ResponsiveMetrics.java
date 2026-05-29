package com.face.mymoney.ui;

import android.content.Context;
import android.content.res.Resources;
import android.util.DisplayMetrics;

public final class ResponsiveMetrics {
    private static final float BASE_WIDTH_DP = 393f;
    private static final float MIN_SCALE = 0.90f;
    private static final float MAX_SCALE = 1.18f;

    private ResponsiveMetrics() {
    }

    public static int dp(Context context, int value) {
        return Math.round(value * density(context) * scale(context));
    }

    public static float sp(Context context, int value) {
        return value * scale(context);
    }

    public static int maxPageWidth(Context context) {
        return dp(context, 560);
    }

    public static int maxDetailPageWidth(Context context) {
        return dp(context, 680);
    }

    private static float scale(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float widthDp = metrics.widthPixels / metrics.density;
        float heightDp = metrics.heightPixels / metrics.density;
        float shortSideDp = Math.min(widthDp, heightDp);
        float scale = shortSideDp / BASE_WIDTH_DP;
        if (scale < MIN_SCALE) {
            return MIN_SCALE;
        }
        if (scale > MAX_SCALE) {
            return MAX_SCALE;
        }
        return scale;
    }

    private static float density(Context context) {
        Resources resources = context.getResources();
        return resources.getDisplayMetrics().density;
    }
}
