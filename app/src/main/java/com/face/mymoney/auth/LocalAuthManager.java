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

    /**
     * 构造方法：创建 LocalAuthManager 实例。
     */
    public LocalAuthManager(Context context) {
        this.context = context.getApplicationContext();
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 判断是否loggedin。
     */
    public boolean isLoggedIn() {
        return preferences.getString(KEY_TOKEN, "").length() > 0;
    }

    /**
     * 登录。
     */
    public void login(String account) {
        preferences.edit()
                .putString(KEY_USER, account)
                .putString(KEY_TOKEN, "local-token-" + System.currentTimeMillis())
                .apply();
    }

    /**
     * logout。
     */
    public void logout() {
        preferences.edit().remove(KEY_TOKEN).apply();
    }

    /**
     * 获取用户name。
     */
    public String getUserName() {
        return preferences.getString(KEY_USER, context.getString(R.string.local_user));
    }

    /**
     * 更新个人中心。
     */
    public void updateProfile(String userName, int avatarStyle) {
        preferences.edit()
                .putString(KEY_USER, userName)
                .putInt(KEY_AVATAR_STYLE, avatarStyle)
                .apply();
    }

    /**
     * 获取头像样式。
     */
    public int getAvatarStyle() {
        return preferences.getInt(KEY_AVATAR_STYLE, 0);
    }
}
