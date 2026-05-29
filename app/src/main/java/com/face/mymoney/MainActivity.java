package com.face.mymoney;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.face.mymoney.auth.LocalAuthManager;
import com.face.mymoney.crawler.DebugCrawlerActivity;
import com.face.mymoney.crawler.StockNewsFetcher;
import com.face.mymoney.crawler.StockQuoteFetcher;
import com.face.mymoney.data.LocalStockRepository;
import com.face.mymoney.model.DecisionNote;
import com.face.mymoney.model.News;
import com.face.mymoney.model.Stock;
import com.face.mymoney.opinion.Opinion;
import com.face.mymoney.opinion.StockOpinionFetcher;
import com.face.mymoney.ui.MainUiKit;
import com.face.mymoney.ui.detail.WinLossRatioCard;
import com.face.mymoney.ui.home.HomePageBuilder;
import com.face.mymoney.ui.login.LoginPageBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MyMoneyMain";
    private static final int COLOR_TEXT = Color.rgb(23, 32, 51);
    private static final int COLOR_SUB = Color.rgb(107, 114, 128);
    private static final int COLOR_ACCENT = Color.rgb(37, 99, 235);
    private static final int COLOR_ACCENT_SOFT = Color.rgb(234, 241, 255);
    private static final String TAB_WATCHLIST = "watchlist";
    private static final String TAB_NEWS = "news";
    private static final String TAB_PROFILE = "profile";

    private LocalAuthManager authManager;
    private LocalStockRepository stockRepository;
    private FrameLayout root;
    private ArrayList<Stock> stocks = new ArrayList<Stock>();
    private ArrayList<DecisionNote> notes = new ArrayList<DecisionNote>();
    private HashMap<String, ArrayList<News>> newsCache = new HashMap<String, ArrayList<News>>();
    private HashMap<String, ArrayList<Opinion>> opinionCache = new HashMap<String, ArrayList<Opinion>>();
    private HashSet<String> loadingNewsCodes = new HashSet<String>();
    private HashSet<String> loadingOpinionCodes = new HashSet<String>();
    private HashMap<String, String> selectedNewsSources = new HashMap<String, String>();
    private HashMap<String, String> selectedOpinionSources = new HashMap<String, String>();
    private int pendingDetailScrollY = -1;
    private LinearLayout currentNewsContainer;
    private LinearLayout currentOpinionContainer;
    private String currentTab = TAB_WATCHLIST;
    private LinearLayout tabContent;
    private String selectedGroup = "";
    private Stock currentStock;
    private MainUiKit ui;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.main);
        ui = new MainUiKit(this);
        authManager = new LocalAuthManager(this);
        stockRepository = new LocalStockRepository(this);
        selectedGroup = stockRepository.getAllGroup();
        ViewCompat.setOnApplyWindowInsetsListener(root, new OnApplyWindowInsetsListener() {
            @Override
            public WindowInsetsCompat onApplyWindowInsets(View v, WindowInsetsCompat insets) {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            }
        });
        stockRepository.removeSampleStocksOnce();
        loadData();
        showMainShell();
    }

    @Override
    public void onBackPressed() {
        if (currentStock != null) {
            currentStock = null;
            showMainShell();
            return;
        }
        super.onBackPressed();
    }

    private boolean isLoggedIn() {
        return authManager.isLoggedIn();
    }

    private void showLogin() {
        currentStock = null;
        root.removeAllViews();
        LoginPageBuilder builder = new LoginPageBuilder(this, ui, new LoginPageBuilder.Listener() {
            @Override
            public void onLogin(String account, View anchorView) {
                authManager.login(account);
                seedStocksIfEmpty();
                loadData();
                hideKeyboard(anchorView);
                showHome();
            }
        });
        root.addView(builder.build(), matchMatch());
    }

    private void showMainShell() {
        currentStock = null;
        root.removeAllViews();
        LinearLayout shell = vertical();
        shell.setBackgroundColor(Color.rgb(245, 247, 251));
        tabContent = vertical();
        shell.addView(tabContent, new LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        shell.addView(bottomTabs(), matchHeight(dp(62)));
        root.addView(shell, matchMatch());
        showCurrentTab();
    }

    private void showCurrentTab() {
        if (tabContent == null) {
            return;
        }
        tabContent.removeAllViews();
        if (TAB_NEWS.equals(currentTab)) {
            tabContent.addView(buildNewsFeedPage(), matchMatch());
        } else if (TAB_PROFILE.equals(currentTab)) {
            tabContent.addView(buildProfilePage(), matchMatch());
        } else {
            tabContent.addView(buildWatchlistPage(), matchMatch());
        }
    }

    private void showHome() {
        currentStock = null;
        root.removeAllViews();
        currentTab = TAB_WATCHLIST;
        showMainShell();
    }

    private View buildWatchlistPage() {
        ArrayList<Stock> displayStocks = filterStocks();
        HomePageBuilder builder = new HomePageBuilder(this, ui, new HomePageBuilder.Listener() {
            @Override
            public void onAddStock() {
                showAddStockDialog();
            }

            @Override
            public void onAddGroup() {
                showAddGroupDialog();
            }

            @Override
            public void onRefreshQuotes() {
                refreshQuotes();
            }

            @Override
            public void onGroupSelected(String group) {
                selectedGroup = group;
                showCurrentTab();
            }

            @Override
            public void onStockSelected(Stock stock) {
                currentStock = stock;
                showStockDetail(stock);
            }

            @Override
            public void onEditStock(Stock stock) {
                showEditStockDialog(stock);
            }

            @Override
            public void onDeleteStock(Stock stock) {
                confirmDeleteStock(stock);
            }

            @Override
            public void onDebugRequested() {
                startActivity(new Intent(MainActivity.this, DebugCrawlerActivity.class));
            }
        }, authManager.getUserName(), stocks, displayStocks, notes, getGroups(), selectedGroup, countRiskStocks());
        return builder.build();
    }

    private View bottomTabs() {
        LinearLayout tabs = horizontal();
        tabs.setGravity(Gravity.CENTER);
        tabs.setPadding(dp(12), dp(7), dp(12), dp(7));
        tabs.setBackground(roundedStroke(Color.WHITE, 0, Color.rgb(226, 232, 240)));
        tabs.addView(tabItem(getString(R.string.tab_watchlist), TAB_WATCHLIST, R.drawable.ic_tab_watchlist), weightWrap(1));
        tabs.addView(spacer(8, 1));
        tabs.addView(tabItem(getString(R.string.tab_news_feed), TAB_NEWS, R.drawable.ic_tab_news), weightWrap(1));
        tabs.addView(spacer(8, 1));
        tabs.addView(tabItem(getString(R.string.tab_profile), TAB_PROFILE, R.drawable.ic_tab_profile), weightWrap(1));
        return tabs;
    }

    private View tabItem(String label, final String tab, int iconResId) {
        boolean selected = currentTab.equals(tab);
        int activeText = COLOR_ACCENT;
        int inactiveText = COLOR_SUB;

        LinearLayout item = vertical();
        item.setGravity(Gravity.CENTER);
        item.setPadding(dp(8), dp(5), dp(8), dp(5));
        item.setBackground(rounded(selected ? COLOR_ACCENT_SOFT : Color.TRANSPARENT, dp(14)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconResId);
        icon.setColorFilter(selected ? activeText : inactiveText);
        item.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView textView = text(label, 11, selected ? activeText : inactiveText, selected);
        textView.setGravity(Gravity.CENTER);
        item.addView(textView, wrapWrap());

        item.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentTab = tab;
                showMainShell();
            }
        });
        return item;
    }

    private View buildNewsFeedPage() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(16), dp(18), dp(24));
        scrollView.addView(page, pageParams(false));

        LinearLayout header = horizontal();
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(getString(R.string.news_feed_title), 28, COLOR_TEXT, true), weightWrap(1));
        Button refresh = primaryButton(getString(R.string.news_feed_refresh));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                refreshNewsFeed();
            }
        });
        header.addView(refresh, wrapHeight(dp(42)));
        page.addView(header, matchWrap());
        page.addView(spacer(14));

        ArrayList<News> feedNews = buildSubscribedNews();
        if (feedNews.size() == 0) {
            LinearLayout empty = card();
            empty.addView(text(getString(R.string.news_feed_empty_title), 18, COLOR_TEXT, true), matchWrap());
            empty.addView(spacer(8));
            empty.addView(text(getString(R.string.news_feed_empty_desc), 14, COLOR_SUB, false), matchWrap());
            page.addView(empty, matchWrap());
            return scrollView;
        }

        ArrayList<String> sources = getNewsSources(feedNews);
        String selectedSource = selectedNewsSources.get("feed");
        if (selectedSource == null || !sources.contains(selectedSource)) {
            selectedSource = sources.get(0);
            selectedNewsSources.put("feed", selectedSource);
        }
        page.addView(sourceSwitchBarForFeed(sources, selectedSource), matchWrap());
        page.addView(spacer(10));
        ArrayList<News> sourceNews = getNewsBySource(feedNews, selectedSource);
        page.addView(sourceHeader(selectedSource, sourceNews.size(), "条资讯"), matchWrap());
        page.addView(spacer(8));
        int displayCount = Math.min(sourceNews.size(), 12);
        for (int i = 0; i < displayCount; i++) {
            page.addView(feedNewsRow(sourceNews.get(i)), matchWrap());
            page.addView(spacer(10));
        }
        return scrollView;
    }

    private View sourceSwitchBarForFeed(ArrayList<String> sources, String selectedSource) {
        final android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        row.setPadding(0, 0, dp(4), 0);
        final View[] selectedChip = new View[1];
        for (int i = 0; i < sources.size(); i++) {
            final String source = sources.get(i);
            boolean selected = selectedSource.equals(source);
            TextView chip = text(source, 13, selected ? Color.WHITE : COLOR_TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(rounded(selected ? COLOR_ACCENT : Color.WHITE, dp(22)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectedNewsSources.put("feed", source);
                    showCurrentTab();
                }
            });
            if (selected) {
                selectedChip[0] = chip;
            }
            row.addView(chip, wrapHeight(dp(38)));
            row.addView(spacer(8, 1));
        }
        scroll.addView(row, wrapWrap());
        scrollSelectedSourceIntoView(scroll, selectedChip[0]);
        return scroll;
    }

    private View feedNewsRow(final News item) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.addView(text(item.title, 16, COLOR_TEXT, true), matchWrap());
        row.addView(spacer(5));
        row.addView(text(getString(R.string.news_meta_format, item.source, item.time, item.keyword), 12, COLOR_SUB, false), matchWrap());
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showFeedNewsDialog(item);
            }
        });
        return row;
    }

    private View buildProfilePage() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(16), dp(18), dp(24));
        scrollView.addView(page, pageParams(false));
        page.addView(text(getString(R.string.profile_title), 28, COLOR_TEXT, true), matchWrap());
        page.addView(spacer(14));

        if (isLoggedIn()) {
            LinearLayout card = profileCard();
            LinearLayout top = horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(avatarView(authManager.getUserName(), authManager.getAvatarStyle(), dp(72)), wrapWrap());
            top.addView(spacer(14, 1));
            LinearLayout info = vertical();
            info.addView(text(authManager.getUserName(), 22, COLOR_TEXT, true), matchWrap());
            info.addView(spacer(4));
            info.addView(tag(getString(R.string.profile_logged_in), COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
            top.addView(info, weightWrap(1));
            card.addView(top, matchWrap());
            card.addView(spacer(18));
            LinearLayout actions = horizontal();
            Button edit = primaryButton(getString(R.string.profile_edit_title));
            edit.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showEditProfileDialog();
                }
            });
            actions.addView(edit, weightWrap(1));
            actions.addView(spacer(10, 1));
            Button logout = ghostButton(getString(R.string.logout));
            logout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    authManager.logout();
                    showCurrentTab();
                }
            });
            actions.addView(logout, weightWrap(1));
            card.addView(actions, matchWrap());
            page.addView(card, matchWrap());
            return scrollView;
        }

        LinearLayout guest = profileCard();
        LinearLayout guestTop = horizontal();
        guestTop.setGravity(Gravity.CENTER_VERTICAL);
        guestTop.addView(avatarView("?", 3, dp(72)), wrapWrap());
        guestTop.addView(spacer(14, 1));
        LinearLayout guestInfo = vertical();
        guestInfo.addView(text(getString(R.string.profile_not_logged_in), 22, COLOR_TEXT, true), matchWrap());
        guestInfo.addView(spacer(4));
        guestInfo.addView(text(getString(R.string.login_intro), 13, COLOR_SUB, false), matchWrap());
        guestTop.addView(guestInfo, weightWrap(1));
        guest.addView(guestTop, matchWrap());
        page.addView(guest, matchWrap());
        page.addView(spacer(14));
        page.addView(profileLoginCard(), matchWrap());
        return scrollView;
    }

    private View profileLoginCard() {
        LinearLayout card = profileCard();
        card.addView(text(getString(R.string.login_title), 22, COLOR_TEXT, true), matchWrap());
        card.addView(spacer(12));
        final EditText accountInput = input(getString(R.string.login_account_hint));
        final EditText passwordInput = input(getString(R.string.login_password_hint));
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(accountInput, matchHeight(dp(54)));
        card.addView(spacer(12));
        card.addView(passwordInput, matchHeight(dp(54)));
        card.addView(spacer(16));
        Button loginButton = primaryButton(getString(R.string.login_button));
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String account = accountInput.getText().toString().trim();
                if (account.length() == 0) {
                    Toast.makeText(MainActivity.this, getString(R.string.login_empty_account), Toast.LENGTH_SHORT).show();
                    return;
                }
                authManager.login(account);
                hideKeyboard(accountInput);
                showCurrentTab();
            }
        });
        card.addView(loginButton, matchHeight(dp(50)));
        card.addView(spacer(10));
        card.addView(text(getString(R.string.login_tip), 12, COLOR_SUB, false), matchWrap());
        return card;
    }

    private LinearLayout profileCard() {
        LinearLayout card = card();
        card.setBackground(roundedStroke(Color.WHITE, dp(18), Color.rgb(226, 232, 240)));
        return card;
    }

    private TextView avatarView(String name, int style, int size) {
        int[] colors = new int[]{
                Color.rgb(37, 99, 235),
                Color.rgb(217, 45, 32),
                Color.rgb(7, 148, 85),
                Color.rgb(124, 58, 237)
        };
        int color = colors[Math.abs(style) % colors.length];
        TextView avatar = text(avatarText(name), 24, Color.WHITE, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(rounded(color, size / 2));
        avatar.setIncludeFontPadding(false);
        avatar.setMinWidth(size);
        avatar.setMinHeight(size);
        return avatar;
    }

    private String avatarText(String name) {
        if (name == null || name.trim().length() == 0) {
            return "U";
        }
        String value = name.trim();
        return value.substring(0, 1).toUpperCase(Locale.CHINA);
    }

    private void showEditProfileDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        final EditText name = input(getString(R.string.profile_name_hint));
        name.setText(authManager.getUserName());
        final Spinner avatarSpinner = new Spinner(this);
        String[] avatars = new String[]{"蓝色", "红色", "绿色", "紫色"};
        avatarSpinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, avatars));
        avatarSpinner.setSelection(authManager.getAvatarStyle() % avatars.length);
        form.addView(name, matchWrap());
        form.addView(spacer(10));
        form.addView(text(getString(R.string.profile_avatar_label), 12, COLOR_SUB, false), matchWrap());
        form.addView(avatarSpinner, matchHeight(dp(48)));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.profile_edit_title))
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String userName = name.getText().toString().trim();
                        if (userName.length() == 0) {
                            Toast.makeText(MainActivity.this, getString(R.string.login_empty_account), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        authManager.updateProfile(userName, avatarSpinner.getSelectedItemPosition());
                        Toast.makeText(MainActivity.this, getString(R.string.profile_save_success), Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                        showCurrentTab();
                    }
                });
            }
        });
        dialog.show();
    }

    private void showStockDetail(final Stock stock) {
        android.util.Log.d(TAG, "showStockDetail code=" + stock.code
                + ", name=" + stock.name
                + ", cachedNews=" + (newsCache.get(stock.code) == null ? "null" : newsCache.get(stock.code).size())
                + ", loading=" + loadingNewsCodes.contains(stock.code));
        root.removeAllViews();

        final ScrollView scrollView = new ScrollView(this);
        LinearLayout page = vertical();
        page.setPadding(dp(18), dp(16), dp(18), dp(28));
        scrollView.addView(page, pageParams(true));

        LinearLayout nav = horizontal();
        nav.setGravity(Gravity.CENTER_VERTICAL);
        Button back = ghostButton(getString(R.string.back));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentStock = null;
                showHome();
            }
        });
        nav.addView(back, wrapHeight(dp(42)));
        nav.addView(spaceWeight(), weightWrap(1));
        Button delete = ghostButton(getString(R.string.delete));
        delete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDeleteStock(stock);
            }
        });
        nav.addView(delete, wrapHeight(dp(42)));
        page.addView(nav, matchWrap());
        page.addView(spacer(8));

        LinearLayout hero = card();
        hero.setBackground(rounded(COLOR_TEXT, dp(18)));
        hero.addView(text(stock.name + "  " + stock.code, 25, Color.WHITE, true), matchWrap());
        hero.addView(spacer(6));
        hero.addView(text(stock.industry + " · " + stock.market + " · " + stock.groupName, 13, Color.rgb(203, 213, 225), false), matchWrap());
        hero.addView(spacer(18));
        LinearLayout quote = horizontal();
        quote.addView(quoteItem(getString(R.string.quote_latest_price), stock.price, Color.WHITE), weightWrap(1));
        quote.addView(quoteItem(getString(R.string.quote_change_percent), stock.changePercent, stock.changePercent.startsWith("-") ? Color.rgb(96, 211, 148) : Color.rgb(255, 138, 128)), weightWrap(1));
        quote.addView(quoteItem(getString(R.string.quote_turnover), stock.turnover, Color.WHITE), weightWrap(1));
        hero.addView(quote, matchWrap());
        page.addView(hero, matchWrap());
        page.addView(spacer(14));

        page.addView(WinLossRatioCard.create(this, stock, getNotes(stock.code)), matchWrap());
        page.addView(spacer(14));

        page.addView(sectionTitle(getString(R.string.company_info)), matchWrap());
        page.addView(companyCard(stock), matchWrap());
        page.addView(spacer(14));

        page.addView(sectionTitle(getString(R.string.news_section)), matchWrap());
        currentNewsContainer = vertical();
        currentNewsContainer.addView(newsList(stock), matchWrap());
        page.addView(currentNewsContainer, matchWrap());
        page.addView(spacer(14));

        page.addView(sectionTitle(getString(R.string.opinion_section)), matchWrap());
        currentOpinionContainer = vertical();
        currentOpinionContainer.addView(opinionList(stock), matchWrap());
        page.addView(currentOpinionContainer, matchWrap());
        page.addView(spacer(14));

        LinearLayout noteHeader = horizontal();
        noteHeader.setGravity(Gravity.CENTER_VERTICAL);
        noteHeader.addView(sectionTitle(getString(R.string.my_notes)), weightWrap(1));
        Button addNote = primaryButton(getString(R.string.add_note_button));
        addNote.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddNoteDialog(stock);
            }
        });
        noteHeader.addView(addNote, wrapHeight(dp(42)));
        page.addView(noteHeader, matchWrap());
        page.addView(noteList(stock), matchWrap());

        root.addView(scrollView, matchMatch());
        restoreDetailScroll(scrollView);
    }

    private View quoteItem(String label, String value, int valueColor) {
        LinearLayout box = vertical();
        box.addView(text(label, 12, Color.rgb(203, 213, 225), false), matchWrap());
        box.addView(spacer(4));
        box.addView(text(value, 18, valueColor, true), matchWrap());
        return box;
    }

    private View companyCard(Stock stock) {
        LinearLayout card = card();
        card.addView(infoRow(getString(R.string.main_business), stock.mainBusiness), matchWrap());
        card.addView(infoRow(getString(R.string.market_value), stock.marketValue), matchWrap());
        card.addView(infoRow(getString(R.string.pe_label), stock.pe), matchWrap());
        card.addView(infoRow(getString(R.string.revenue_profit), stock.revenue + " / " + stock.profit), matchWrap());
        card.addView(infoRow(getString(R.string.risk_tag), stock.riskTag), matchWrap());
        return card;
    }

    private View newsList(final Stock stock) {
        LinearLayout list = vertical();
        ArrayList<News> news = buildNews(stock);
        android.util.Log.d(TAG, "newsList code=" + stock.code
                + ", displayNewsCount=" + news.size()
                + ", hasCache=" + (newsCache.get(stock.code) != null));
        if (newsCache.get(stock.code) == null) {
            LinearLayout loading = card();
            loading.setPadding(dp(16), dp(14), dp(16), dp(14));
            loading.addView(text(getString(R.string.news_loading_title), 16, COLOR_TEXT, true), matchWrap());
            loading.addView(spacer(5));
            loading.addView(text(getString(R.string.news_loading_desc), 12, COLOR_SUB, false), matchWrap());
            list.addView(loading, matchWrap());
            list.addView(spacer(10));
            loadStockNews(stock);
        }
        ArrayList<String> sources = getNewsSources(news);
        if (sources.size() > 0) {
            String selectedSource = selectedNewsSource(stock, sources);
            list.addView(sourceSwitchBar(stock, sources, selectedSource, true), matchWrap());
            list.addView(spacer(8));
            ArrayList<News> sourceNews = getNewsBySource(news, selectedSource);
            list.addView(sourceHeader(selectedSource, sourceNews.size(), "条资讯"), matchWrap());
            list.addView(spacer(8));
            int displayCount = Math.min(sourceNews.size(), 6);
            for (int j = 0; j < displayCount; j++) {
                list.addView(newsRow(stock, sourceNews.get(j)), matchWrap());
                list.addView(spacer(10));
            }
            if (sourceNews.size() > displayCount) {
                list.addView(text("仅显示前 " + displayCount + " 条，切换来源查看其他内容", 12, COLOR_SUB, false), matchWrap());
                list.addView(spacer(10));
            }
        }
        return list;
    }

    private View newsRow(final Stock stock, final News item) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.addView(text(item.title, 16, COLOR_TEXT, true), matchWrap());
        row.addView(spacer(5));
        row.addView(text(getString(R.string.news_meta_format, item.source, item.time, item.keyword), 12, COLOR_SUB, false), matchWrap());
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showNewsDialog(stock, item);
            }
        });
        return row;
    }

    private View noteList(Stock stock) {
        LinearLayout list = vertical();
        ArrayList<DecisionNote> stockNotes = getNotes(stock.code);
        if (stockNotes.size() == 0) {
            LinearLayout empty = card();
            empty.addView(text(getString(R.string.no_note_title), 16, COLOR_TEXT, true), matchWrap());
            empty.addView(spacer(6));
            empty.addView(text(getString(R.string.no_note_desc), 13, COLOR_SUB, false), matchWrap());
            list.addView(empty, matchWrap());
            return list;
        }

        for (int i = 0; i < stockNotes.size(); i++) {
            DecisionNote note = stockNotes.get(i);
            LinearLayout card = card();
            LinearLayout top = horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            top.addView(tag(note.type, COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
            top.addView(spacer(8, 1));
            top.addView(text(note.createdTime, 12, COLOR_SUB, false), wrapWrap());
            card.addView(top, matchWrap());
            card.addView(spacer(9));
            card.addView(text(note.title, 17, COLOR_TEXT, true), matchWrap());
            card.addView(spacer(6));
            TextView content = text(note.content, 14, COLOR_TEXT, false);
            content.setLineSpacing(dp(3), 1.0f);
            card.addView(content, matchWrap());
            card.addView(spacer(10));
            card.addView(text(getString(R.string.note_meta_format, note.targetPrice, note.stopLossPrice, note.confidence), 12, COLOR_SUB, false), matchWrap());
            list.addView(card, matchWrap());
            list.addView(spacer(10));
        }
        return list;
    }

    private View opinionList(final Stock stock) {
        LinearLayout list = vertical();
        ArrayList<Opinion> opinions = buildOpinions(stock);
        if (opinionCache.get(stock.code) == null) {
            LinearLayout loading = card();
            loading.setPadding(dp(16), dp(14), dp(16), dp(14));
            loading.addView(text(getString(R.string.opinion_loading_title), 16, COLOR_TEXT, true), matchWrap());
            loading.addView(spacer(5));
            loading.addView(text(getString(R.string.opinion_loading_desc), 12, COLOR_SUB, false), matchWrap());
            list.addView(loading, matchWrap());
            list.addView(spacer(10));
            loadStockOpinions(stock);
        }
        ArrayList<String> sources = getOpinionSources(opinions);
        if (sources.size() > 0) {
            String selectedSource = selectedOpinionSource(stock, sources);
            list.addView(sourceSwitchBar(stock, sources, selectedSource, false), matchWrap());
            list.addView(spacer(8));
            ArrayList<Opinion> sourceOpinions = getOpinionsBySource(opinions, selectedSource);
            list.addView(sourceHeader(selectedSource, sourceOpinions.size(), "条观点"), matchWrap());
            list.addView(spacer(8));
            int displayCount = Math.min(sourceOpinions.size(), 6);
            for (int j = 0; j < displayCount; j++) {
                list.addView(opinionRow(stock, sourceOpinions.get(j)), matchWrap());
                list.addView(spacer(10));
            }
            if (sourceOpinions.size() > displayCount) {
                list.addView(text("仅显示前 " + displayCount + " 条，切换来源查看其他内容", 12, COLOR_SUB, false), matchWrap());
                list.addView(spacer(10));
            }
        }
        return list;
    }

    private View sourceSwitchBar(final Stock stock, ArrayList<String> sources, String selectedSource, final boolean news) {
        final android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = horizontal();
        row.setPadding(0, 0, dp(4), 0);
        final View[] selectedChip = new View[1];
        for (int i = 0; i < sources.size(); i++) {
            final String source = sources.get(i);
            boolean selected = selectedSource.equals(source);
            TextView chip = text(source, 13, selected ? Color.WHITE : COLOR_TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(14), dp(8), dp(14), dp(8));
            chip.setBackground(rounded(selected ? COLOR_ACCENT : Color.WHITE, dp(22)));
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (news) {
                        selectedNewsSources.put(stock.code, source);
                    } else {
                        selectedOpinionSources.put(stock.code, source);
                    }
                    if (currentStock != null && stock.code.equals(currentStock.code)) {
                        refreshSourceSection(stock, news);
                    }
                }
            });
            if (selected) {
                selectedChip[0] = chip;
            }
            row.addView(chip, wrapHeight(dp(38)));
            row.addView(spacer(8, 1));
        }
        scroll.addView(row, wrapWrap());
        scrollSelectedSourceIntoView(scroll, selectedChip[0]);
        return scroll;
    }

    private void scrollSelectedSourceIntoView(final android.widget.HorizontalScrollView scroll, final View selectedChip) {
        if (selectedChip == null) {
            return;
        }
        scroll.post(new Runnable() {
            @Override
            public void run() {
                int targetX = Math.max(0, selectedChip.getLeft() - dp(24));
                scroll.scrollTo(targetX, 0);
            }
        });
    }

    private void refreshSourceSection(Stock stock, boolean news) {
        if (news) {
            if (currentNewsContainer != null) {
                currentNewsContainer.removeAllViews();
                currentNewsContainer.addView(newsList(stock), matchWrap());
            }
            return;
        }
        if (currentOpinionContainer != null) {
            currentOpinionContainer.removeAllViews();
            currentOpinionContainer.addView(opinionList(stock), matchWrap());
        }
    }

    private String selectedNewsSource(Stock stock, ArrayList<String> sources) {
        String selected = selectedNewsSources.get(stock.code);
        if (selected != null && sources.contains(selected)) {
            return selected;
        }
        String first = sources.get(0);
        selectedNewsSources.put(stock.code, first);
        return first;
    }

    private String selectedOpinionSource(Stock stock, ArrayList<String> sources) {
        String selected = selectedOpinionSources.get(stock.code);
        if (selected != null && sources.contains(selected)) {
            return selected;
        }
        String first = sources.get(0);
        selectedOpinionSources.put(stock.code, first);
        return first;
    }

    private void rememberDetailScroll() {
        if (root.getChildCount() == 0) {
            pendingDetailScrollY = -1;
            return;
        }
        View child = root.getChildAt(0);
        if (child instanceof ScrollView) {
            pendingDetailScrollY = ((ScrollView) child).getScrollY();
        } else {
            pendingDetailScrollY = -1;
        }
    }

    private void restoreDetailScroll(final ScrollView scrollView) {
        if (pendingDetailScrollY < 0) {
            return;
        }
        final int scrollY = pendingDetailScrollY;
        pendingDetailScrollY = -1;
        scrollView.post(new Runnable() {
            @Override
            public void run() {
                scrollView.scrollTo(0, scrollY);
            }
        });
    }

    private View sourceHeader(String source, int count, String suffix) {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(2));
        row.addView(tag(source, COLOR_ACCENT_SOFT, COLOR_ACCENT), wrapWrap());
        row.addView(spacer(8, 1));
        row.addView(text(count + " " + suffix, 12, COLOR_SUB, false), wrapWrap());
        return row;
    }

    private View opinionRow(final Stock stock, final Opinion item) {
            LinearLayout row = card();
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            row.addView(text(item.title, 16, COLOR_TEXT, true), matchWrap());
            row.addView(spacer(5));
            row.addView(text(getString(R.string.opinion_meta_format, item.source, item.time, item.keyword), 12, COLOR_SUB, false), matchWrap());
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showOpinionDialog(stock, item);
                }
            });
            return row;
    }

    private ArrayList<String> getOpinionSources(ArrayList<Opinion> opinions) {
        ArrayList<String> sources = new ArrayList<String>();
        for (int i = 0; i < opinions.size(); i++) {
            String source = opinions.get(i).source;
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
        return sources;
    }

    private ArrayList<String> getNewsSources(ArrayList<News> news) {
        ArrayList<String> sources = new ArrayList<String>();
        for (int i = 0; i < news.size(); i++) {
            String source = news.get(i).source;
            if (!sources.contains(source)) {
                sources.add(source);
            }
        }
        return sources;
    }

    private ArrayList<News> getNewsBySource(ArrayList<News> news, String source) {
        ArrayList<News> result = new ArrayList<News>();
        for (int i = 0; i < news.size(); i++) {
            News item = news.get(i);
            if (source.equals(item.source)) {
                result.add(item);
            }
        }
        return result;
    }

    private ArrayList<Opinion> getOpinionsBySource(ArrayList<Opinion> opinions, String source) {
        ArrayList<Opinion> result = new ArrayList<Opinion>();
        for (int i = 0; i < opinions.size(); i++) {
            Opinion opinion = opinions.get(i);
            if (source.equals(opinion.source)) {
                result.add(opinion);
            }
        }
        return result;
    }

    private View infoRow(String label, String value) {
        LinearLayout row = vertical();
        row.setPadding(0, dp(7), 0, dp(7));
        row.addView(text(label, 12, COLOR_SUB, false), matchWrap());
        row.addView(spacer(2));
        TextView valueView = text(value, 15, COLOR_TEXT, false);
        valueView.setLineSpacing(dp(2), 1.0f);
        row.addView(valueView, matchWrap());
        return row;
    }

    private TextView sectionTitle(String title) {
        return text(title, 20, COLOR_TEXT, true);
    }

    private void showAddStockDialog() {
        showStockFormDialog(null);
    }

    private void showEditStockDialog(final Stock stock) {
        showStockFormDialog(stock);
    }

    private void showStockFormDialog(final Stock editingStock) {
        final boolean isEdit = editingStock != null;
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(10), dp(20), 0);
        final EditText code = input(getString(R.string.stock_code_hint));
        code.setSingleLine(true);
        code.setInputType(InputType.TYPE_CLASS_NUMBER);
        if (isEdit) {
            code.setText(editingStock.code);
            code.setEnabled(false);
        }
        final EditText name = input(getString(R.string.stock_name_hint));
        name.setSingleLine(true);
        if (isEdit) {
            name.setText(editingStock.name);
        }
        final Spinner groupSpinner = new Spinner(this);
        final ArrayList<String> stockGroups = selectableGroups();
        ArrayAdapter<String> groupAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, stockGroups);
        groupSpinner.setAdapter(groupAdapter);
        String currentGroup = isEdit ? editingStock.groupName : selectedGroup;
        int selectedIndex = stockGroups.indexOf(currentGroup);
        if (selectedIndex >= 0) {
            groupSpinner.setSelection(selectedIndex);
        }
        final EditText newGroup = input(getString(R.string.stock_new_group_hint));
        newGroup.setSingleLine(true);
        final EditText remark = input(getString(R.string.stock_remark_hint));
        remark.setMinLines(2);
        remark.setGravity(Gravity.TOP);
        if (isEdit) {
            remark.setText(editingStock.remark);
        }

        form.addView(code, matchHeight(dp(52)));
        form.addView(spacer(10));
        form.addView(name, matchHeight(dp(52)));
        form.addView(spacer(10));
        form.addView(text(getString(R.string.stock_group_select_hint), 12, COLOR_SUB, false), matchWrap());
        form.addView(groupSpinner, matchHeight(dp(48)));
        form.addView(spacer(10));
        form.addView(newGroup, matchHeight(dp(52)));
        form.addView(spacer(10));
        form.addView(remark, matchHeight(dp(82)));

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(isEdit ? getString(R.string.edit_stock_title) : getString(R.string.add_stock_title))
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String codeText = code.getText().toString().trim();
                        String nameText = name.getText().toString().trim();
                        if ((!isEdit && codeText.length() == 0) || nameText.length() == 0) {
                            Toast.makeText(MainActivity.this, getString(R.string.stock_required), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!isEdit && !isValidStockCode(codeText)) {
                            Toast.makeText(MainActivity.this, getString(R.string.stock_code_invalid), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!isEdit && containsStockCode(codeText)) {
                            Toast.makeText(MainActivity.this, getString(R.string.stock_duplicate), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String groupText = resolveStockGroup(groupSpinner, newGroup);
                        stockRepository.saveGroupIfNeeded(groupText);
                        if (isEdit) {
                            editingStock.name = nameText;
                            editingStock.groupName = groupText;
                            editingStock.remark = remark.getText().toString().trim();
                        } else {
                            Stock stock = createDefaultStock(codeText, nameText, groupText, remark.getText().toString().trim());
                            stocks.add(0, stock);
                        }
                        saveStocks();
                        selectedGroup = groupText;
                        dialog.dismiss();
                        showCurrentTab();
                    }
                });
            }
        });
        dialog.show();
    }

    private void showAddGroupDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        final EditText groupName = input(getString(R.string.group_name_hint));
        form.addView(groupName, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_group_title))
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String groupText = groupName.getText().toString().trim();
                        if (groupText.length() == 0) {
                            Toast.makeText(MainActivity.this, getString(R.string.group_required), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        stockRepository.saveGroupIfNeeded(groupText);
                        selectedGroup = groupText;
                        dialog.dismiss();
                        showHome();
                    }
                });
            }
        });
        dialog.show();
    }

    private void showAddNoteDialog(final Stock stock) {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        final Spinner typeSpinner = new Spinner(this);
        String[] types = new String[]{getString(R.string.note_type_observe), getString(R.string.note_type_buy), getString(R.string.note_type_sell), getString(R.string.note_type_risk), getString(R.string.note_type_review)};
        typeSpinner.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, types));
        final EditText title = input(getString(R.string.note_title_hint));
        final EditText content = input(getString(R.string.note_content_hint));
        content.setMinLines(3);
        content.setGravity(Gravity.TOP);
        final EditText target = input(getString(R.string.target_price_hint));
        target.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText stop = input(getString(R.string.stop_loss_price_hint));
        stop.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        final EditText confidence = input(getString(R.string.confidence_hint));
        confidence.setInputType(InputType.TYPE_CLASS_NUMBER);

        form.addView(typeSpinner, matchHeight(dp(48)));
        form.addView(spacer(10));
        form.addView(title, matchWrap());
        form.addView(spacer(10));
        form.addView(content, matchHeight(dp(110)));
        form.addView(spacer(10));
        form.addView(target, matchWrap());
        form.addView(spacer(10));
        form.addView(stop, matchWrap());
        form.addView(spacer(10));
        form.addView(confidence, matchWrap());

        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.add_note_title))
                .setView(form)
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
                Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (title.getText().toString().trim().length() == 0) {
                            Toast.makeText(MainActivity.this, getString(R.string.note_title_required), Toast.LENGTH_SHORT).show();
                            return;
                        }
                        DecisionNote note = new DecisionNote();
                        note.id = System.currentTimeMillis();
                        note.stockCode = stock.code;
                        note.type = typeSpinner.getSelectedItem().toString();
                        note.title = title.getText().toString().trim();
                        note.content = content.getText().toString().trim();
                        note.targetPrice = textOrDefault(target, "-");
                        note.stopLossPrice = textOrDefault(stop, "-");
                        note.confidence = textOrDefault(confidence, "5");
                        note.createdTime = now();
                        notes.add(0, note);
                        saveNotes();
                        dialog.dismiss();
                        showStockDetail(stock);
                    }
                });
            }
        });
        dialog.show();
    }

    private void showNewsDialog(Stock stock, News news) {
        String message = getString(R.string.news_dialog_format, news.source, news.time, news.content, stock.name, stock.code, news.keyword);
        new AlertDialog.Builder(this)
                .setTitle(news.title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void showFeedNewsDialog(News news) {
        new AlertDialog.Builder(this)
                .setTitle(news.title)
                .setMessage(news.source + " · " + news.time + "\n\n" + news.content + "\n\n关键词：" + news.keyword)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void showOpinionDialog(Stock stock, Opinion opinion) {
        String message = getString(R.string.opinion_dialog_format, opinion.source, opinion.time, opinion.content, stock.name, stock.code, opinion.keyword);
        if (opinion.url.length() > 0) {
            message = message + "\n\n来源链接：" + opinion.url;
        }
        new AlertDialog.Builder(this)
                .setTitle(opinion.title)
                .setMessage(message)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    private void confirmDeleteStock(final Stock stock) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_stock_title))
                .setMessage(getString(R.string.delete_stock_message, stock.name))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.delete), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        for (int i = stocks.size() - 1; i >= 0; i--) {
                            if (stocks.get(i).code.equals(stock.code)) {
                                stocks.remove(i);
                            }
                        }
                        saveStocks();
                        currentStock = null;
                        showHome();
                    }
                })
                .show();
    }

    private void loadData() {
        stocks = stockRepository.loadStocks();
        notes = stockRepository.loadNotes();
    }

    private void seedStocksIfEmpty() {
        stockRepository.seedStocksIfEmpty();
    }

    private Stock createDefaultStock(String code, String name, String group, String remark) {
        return stockRepository.createDefaultStock(code, name, group, remark);
    }

    private ArrayList<News> buildNews(Stock stock) {
        ArrayList<News> cachedNews = newsCache.get(stock.code);
        if (cachedNews != null && cachedNews.size() > 0) {
            android.util.Log.d(TAG, "buildNews use fetched news code=" + stock.code + ", count=" + cachedNews.size());
            return cachedNews;
        }
        android.util.Log.d(TAG, "buildNews use mock news code=" + stock.code
                + ", cacheExists=" + (cachedNews != null)
                + ", cacheCount=" + (cachedNews == null ? -1 : cachedNews.size()));
        return stockRepository.buildNews(stock);
    }

    private ArrayList<Opinion> buildOpinions(Stock stock) {
        ArrayList<Opinion> cachedOpinions = opinionCache.get(stock.code);
        if (cachedOpinions != null) {
            return cachedOpinions;
        }
        return new ArrayList<Opinion>();
    }

    private void loadStockNews(final Stock stock) {
        if (loadingNewsCodes.contains(stock.code)) {
            android.util.Log.d(TAG, "loadStockNews ignored because loading code=" + stock.code);
            return;
        }
        loadingNewsCodes.add(stock.code);
        android.util.Log.d(TAG, "loadStockNews start code=" + stock.code + ", name=" + stock.name);
        new Thread(new Runnable() {
            @Override
            public void run() {
                StockNewsFetcher fetcher = new StockNewsFetcher(MainActivity.this);
                final ArrayList<News> fetchedNews = fetcher.fetchForStock(stock);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingNewsCodes.remove(stock.code);
                        newsCache.put(stock.code, fetchedNews);
                        android.util.Log.d(TAG, "loadStockNews finish code=" + stock.code
                                + ", fetchedCount=" + fetchedNews.size()
                                + ", currentStock=" + (currentStock == null ? "null" : currentStock.code));
                        if (currentStock != null && stock.code.equals(currentStock.code)) {
                            showStockDetail(currentStock);
                        }
                    }
                });
            }
        }).start();
    }

    private void refreshNewsFeed() {
        if (stocks.size() == 0) {
            Toast.makeText(this, getString(R.string.news_feed_empty_title), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, getString(R.string.news_loading_title), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final HashMap<String, ArrayList<News>> fetched = new HashMap<String, ArrayList<News>>();
                StockNewsFetcher fetcher = new StockNewsFetcher(MainActivity.this);
                for (int i = 0; i < stocks.size(); i++) {
                    Stock stock = stocks.get(i);
                    fetched.put(stock.code, fetcher.fetchForStock(stock));
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        newsCache.putAll(fetched);
                        showCurrentTab();
                    }
                });
            }
        }).start();
    }

    private ArrayList<News> buildSubscribedNews() {
        ArrayList<News> result = new ArrayList<News>();
        for (int i = 0; i < stocks.size(); i++) {
            Stock stock = stocks.get(i);
            ArrayList<News> cached = newsCache.get(stock.code);
            if (cached != null) {
                addFeedNews(result, cached);
            }
        }
        return result;
    }

    private void addFeedNews(ArrayList<News> target, ArrayList<News> source) {
        for (int i = 0; i < source.size(); i++) {
            News item = source.get(i);
            if (!containsNewsTitle(target, item.title)) {
                target.add(item);
            }
        }
    }

    private boolean containsNewsTitle(ArrayList<News> news, String title) {
        for (int i = 0; i < news.size(); i++) {
            if (title.equals(news.get(i).title)) {
                return true;
            }
        }
        return false;
    }

    private void loadStockOpinions(final Stock stock) {
        if (loadingOpinionCodes.contains(stock.code)) {
            return;
        }
        loadingOpinionCodes.add(stock.code);
        new Thread(new Runnable() {
            @Override
            public void run() {
                StockOpinionFetcher fetcher = new StockOpinionFetcher();
                final ArrayList<Opinion> fetchedOpinions = fetcher.fetchForStock(stock);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingOpinionCodes.remove(stock.code);
                        opinionCache.put(stock.code, fetchedOpinions);
                        if (currentStock != null && stock.code.equals(currentStock.code)) {
                            showStockDetail(currentStock);
                        }
                    }
                });
            }
        }).start();
    }

    private void refreshQuotes() {
        if (stocks.size() == 0) {
            Toast.makeText(this, getString(R.string.refresh_quote_empty), Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, getString(R.string.refresh_quote_loading), Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                StockQuoteFetcher fetcher = new StockQuoteFetcher();
                final int successCount = fetcher.refreshQuotes(stocks);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        saveStocks();
                        Toast.makeText(MainActivity.this, getString(R.string.refresh_quote_result, successCount, stocks.size()), Toast.LENGTH_SHORT).show();
                        showHome();
                    }
                });
            }
        }).start();
    }

    private ArrayList<Stock> filterStocks() {
        return stockRepository.filterStocks(stocks, selectedGroup);
    }

    private ArrayList<String> getGroups() {
        return stockRepository.getGroups(stocks);
    }

    private ArrayList<String> selectableGroups() {
        ArrayList<String> result = new ArrayList<String>();
        ArrayList<String> groups = getGroups();
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);
            if (!stockRepository.getAllGroup().equals(group)) {
                result.add(group);
            }
        }
        if (result.size() == 0) {
            result.add(getString(R.string.group_candidate));
        }
        return result;
    }

    private boolean isValidStockCode(String code) {
        return code.matches("[03689][0-9]{5}");
    }

    private boolean containsStockCode(String code) {
        for (int i = 0; i < stocks.size(); i++) {
            if (code.equals(stocks.get(i).code)) {
                return true;
            }
        }
        return false;
    }

    private String resolveStockGroup(Spinner groupSpinner, EditText newGroup) {
        String groupText = newGroup.getText().toString().trim();
        if (groupText.length() > 0) {
            return groupText;
        }
        Object selected = groupSpinner.getSelectedItem();
        if (selected == null || selected.toString().trim().length() == 0) {
            return getString(R.string.group_candidate);
        }
        return selected.toString().trim();
    }

    private int countRiskStocks() {
        return stockRepository.countRiskStocks(stocks);
    }

    private ArrayList<DecisionNote> getNotes(String stockCode) {
        return stockRepository.getNotes(notes, stockCode);
    }

    private void saveStocks() {
        stockRepository.saveStocks(stocks);
    }

    private void saveNotes() {
        stockRepository.saveNotes(notes);
    }

    private LinearLayout vertical() {
        return ui.vertical();
    }

    private LinearLayout horizontal() {
        return ui.horizontal();
    }

    private LinearLayout card() {
        return ui.card();
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        return ui.text(value, sp, color, bold);
    }

    private TextView tag(String value, int bgColor, int textColor) {
        return ui.tag(value, bgColor, textColor);
    }

    private EditText input(String hint) {
        return ui.input(hint);
    }

    private Button primaryButton(String text) {
        return ui.primaryButton(text);
    }

    private Button ghostButton(String text) {
        return ui.ghostButton(text);
    }

    private GradientDrawable rounded(int color, int radius) {
        return ui.rounded(color, radius);
    }

    private GradientDrawable roundedStroke(int color, int radius, int strokeColor) {
        return ui.roundedStroke(color, radius, strokeColor);
    }

    private View spacer(int height) {
        return ui.spacer(height);
    }

    private View spacer(int width, int height) {
        return ui.spacer(width, height);
    }

    private View spaceWeight() {
        return ui.spaceWeight();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return ui.matchWrap();
    }

    private LinearLayout.LayoutParams matchHeight(int height) {
        return ui.matchHeight(height);
    }

    private LinearLayout.LayoutParams wrapHeight(int height) {
        return ui.wrapHeight(height);
    }

    private LinearLayout.LayoutParams wrapWrap() {
        return ui.wrapWrap();
    }

    private LinearLayout.LayoutParams weightWrap(float weight) {
        return ui.weightWrap(weight);
    }

    private FrameLayout.LayoutParams matchMatch() {
        return ui.matchMatch();
    }

    private FrameLayout.LayoutParams pageParams(boolean detailPage) {
        return ui.pageParams(detailPage);
    }

    private int dp(int value) {
        return ui.dp(value);
    }

    private String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date());
    }

    private String textOrDefault(EditText editText, String defaultValue) {
        String value = editText.getText().toString().trim();
        return value.length() == 0 ? defaultValue : value;
    }

    private void hideKeyboard(View view) {
        ui.hideKeyboard(view);
    }
}
