package com.face.mymoney.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.face.mymoney.R;

public class LocalAuthManager {
    private static final String PREF_NAME = "mymoney_mvp";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER = "user";
    private static final String KEY_AVATAR_STYLE = "avatar_style";

    private final SharedPreferences preferences;
    private final Context context;

    public LocalAuthManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public boolean isLoggedIn() {
        return preferences.getString(KEY_TOKEN, "").length() > 0;
    }

    public void login(String account) {
        preferences.edit()
                .putString(KEY_USER, account)
                .putString(KEY_TOKEN, "local-token-" + System.currentTimeMillis())
                .apply();
    }

    public void logout() {
        preferences.edit().remove(KEY_TOKEN).apply();
    }

    public String getUserName() {
        return preferences.getString(KEY_USER, context.getString(R.string.local_user));
    }

    public void updateProfile(String userName, int avatarStyle) {
        preferences.edit()
                .putString(KEY_USER, userName)
                .putInt(KEY_AVATAR_STYLE, avatarStyle)
                .apply();
    }

    public int getAvatarStyle() {
        return preferences.getInt(KEY_AVATAR_STYLE, 0);
    }
}
