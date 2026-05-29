package com.face.mymoney.ui.login;

import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.face.mymoney.R;
import com.face.mymoney.ui.MainUiKit;

public class LoginPageBuilder {
    public interface Listener {
        void onLogin(String account, View anchorView);
    }

    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);

    private final Context context;
    private final MainUiKit ui;
    private final Listener listener;

    public LoginPageBuilder(Context context, MainUiKit ui, Listener listener) {
        this.context = context;
        this.ui = ui;
        this.listener = listener;
    }

    public ScrollView build() {
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        LinearLayout page = ui.vertical();
        page.setGravity(Gravity.CENTER_VERTICAL);
        page.setPadding(ui.dp(24), ui.dp(24), ui.dp(24), ui.dp(24));
        scrollView.addView(page, ui.pageParams(false));

        TextView appName = ui.text(context.getString(R.string.app_name), 34, COLOR_TEXT, true);
        TextView subtitle = ui.text(context.getString(R.string.login_subtitle), 17, COLOR_SUB, false);
        TextView intro = ui.text(context.getString(R.string.login_intro), 14, COLOR_SUB, false);
        intro.setLineSpacing(ui.dp(3), 1.0f);

        final EditText accountInput = ui.input(context.getString(R.string.login_account_hint));
        final EditText passwordInput = ui.input(context.getString(R.string.login_password_hint));
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button loginButton = ui.primaryButton(context.getString(R.string.login_button));
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String account = accountInput.getText().toString().trim();
                if (account.length() == 0) {
                    Toast.makeText(context, context.getString(R.string.login_empty_account), Toast.LENGTH_SHORT).show();
                    return;
                }
                listener.onLogin(account, accountInput);
            }
        });

        LinearLayout card = ui.card();
        card.addView(ui.text(context.getString(R.string.login_title), 22, COLOR_TEXT, true), ui.matchWrap());
        card.addView(ui.spacer(ui.dp(10)));
        card.addView(accountInput, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(10)));
        card.addView(passwordInput, ui.matchWrap());
        card.addView(ui.spacer(ui.dp(16)));
        card.addView(loginButton, ui.matchHeight(ui.dp(50)));
        card.addView(ui.spacer(ui.dp(10)));
        TextView tip = ui.text(context.getString(R.string.login_tip), 12, COLOR_SUB, false);
        tip.setLineSpacing(ui.dp(2), 1.0f);
        card.addView(tip, ui.matchWrap());

        page.addView(appName, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(8)));
        page.addView(subtitle, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(12)));
        page.addView(intro, ui.matchWrap());
        page.addView(ui.spacer(ui.dp(24)));
        page.addView(card, ui.matchWrap());
        return scrollView;
    }
}
