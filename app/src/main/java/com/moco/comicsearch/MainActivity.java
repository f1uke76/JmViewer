package com.moco.comicsearch;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int SEC_HOME = 0;
    private static final int SEC_HISTORY = 1;
    private static final int SEC_FAV = 2;
    private static final int SEC_PROFILE = 3;

    private Site site;
    private Prefs prefs;
    private Store store;
    private ImageLoader loader;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    private DrawerLayout drawer;
    private View navHome, navHistory, navFav, navProfile, navSettings;
    private TextView drawerSite;

    private EditText searchInput;
    private View btnClear;
    private View searchBar;
    private TextView barTitle;
    private SwipeRefreshLayout refresh;
    private RecyclerView list;
    private AlbumAdapter adapter;
    private View emptyView;
    private View homeMark;
    private View profileView;
    private View loading;
    private TextView emptyTitle;
    private TextView emptyMsg;
    private TextView accountName;
    private TextView accountHint;
    private View btnAccount;
    private View historyPanel;
    private LinearLayout historyList;
    private WebView webView;
    private boolean webMode = false;

    private int section = SEC_HOME;
    private String keyword = "";
    private int page = 1;
    private boolean noMore = false;
    private boolean requesting = false;
    private final List<Models.Album> searchResults = new ArrayList<>();
    private final List<Models.Album> homeAlbums = new ArrayList<>();
    private boolean homeLoading = false;
    private boolean homeLoaded = false;
    private String lastBase = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        site = Site.get(this);
        prefs = new Prefs(this);
        store = new Store(this);
        loader = ImageLoader.get(this);
        lastBase = site.base();

        bindViews();
        setupSearch();
        setupList();
        setupDrawer();

        showSection(SEC_HOME);
        IconSwitcher.ensure(this);
    }

    private void bindViews() {
        drawer = findViewById(R.id.drawer);
        navHome = findViewById(R.id.navHome);
        navHistory = findViewById(R.id.navHistory);
        navFav = findViewById(R.id.navFav);
        navProfile = findViewById(R.id.navProfile);
        navSettings = findViewById(R.id.navSettings);
        drawerSite = findViewById(R.id.drawerSite);

        searchInput = findViewById(R.id.searchInput);
        btnClear = findViewById(R.id.btnClear);
        searchBar = findViewById(R.id.searchBar);
        barTitle = findViewById(R.id.barTitle);
        refresh = findViewById(R.id.refresh);
        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.emptyView);
        homeMark = findViewById(R.id.homeMark);
        profileView = findViewById(R.id.profileView);
        loading = findViewById(R.id.loading);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyMsg = findViewById(R.id.emptyMsg);
        accountName = findViewById(R.id.accountName);
        accountHint = findViewById(R.id.accountHint);
        btnAccount = findViewById(R.id.btnAccount);
        historyPanel = findViewById(R.id.historyPanel);
        historyList = findViewById(R.id.historyList);
        webView = findViewById(R.id.webView);
        setupWebView();

        findViewById(R.id.btnMenu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        drawerSite.setText(site.base().replace("https://", "").replace("http://", ""));

        refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.primary));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface2));
        refresh.setOnRefreshListener(() -> {
            if (section != SEC_HOME) {
                refresh.setRefreshing(false);
                return;
            }
            if (keyword.isEmpty()) {
                if (!prefs.homeRecommend()) {
                    refresh.setRefreshing(false);
                    return;
                }
                homeLoaded = false;
                loadHome(true);
            } else {
                page = 1;
                noMore = false;
                doSearch(keyword, false);
            }
        });
    }

    private void setupDrawer() {
        navHome.setOnClickListener(v -> showSection(SEC_HOME));
        navHistory.setOnClickListener(v -> showSection(SEC_HISTORY));
        navFav.setOnClickListener(v -> showSection(SEC_FAV));
        navProfile.setOnClickListener(v -> showSection(SEC_PROFILE));
        navSettings.setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                if (s.length() > 0) {
                    hideSearchHistory();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        // 键盘「搜索」键 / 回车键 / 数字键盘回车 都要能触发
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean hit = actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_SEND;
            if (!hit && event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                int code = event.getKeyCode();
                hit = code == KeyEvent.KEYCODE_ENTER
                        || code == KeyEvent.KEYCODE_NUMPAD_ENTER
                        || code == KeyEvent.KEYCODE_DPAD_CENTER;
            }
            if (hit) {
                performSearch();
                return true;
            }
            return false;
        });

        findViewById(R.id.btnSearchIcon).setOnClickListener(v -> performSearch());

        btnClear.setOnClickListener(v -> {
            searchInput.setText("");
            searchResults.clear();
            keyword = "";
            page = 1;
            noMore = false;
            hideSearchHistory();
            showSection(SEC_HOME);
        });

        // 点击搜索框立即展开搜索历史
        searchInput.setOnClickListener(v -> {
            if (searchInput.getText().length() == 0) {
                showSearchHistory();
            }
        });
        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchInput.getText().length() == 0) {
                showSearchHistory();
            } else if (!hasFocus) {
                hideSearchHistory();
            }
        });

        findViewById(R.id.btnClearSearchHistory).setOnClickListener(v -> {
            prefs.clearHistory();
            hideSearchHistory();
            Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupList() {
        adapter = new AlbumAdapter(loader, new AlbumAdapter.Listener() {
            @Override
            public void onClick(Models.Album album) {
                openDetail(album);
            }

            @Override
            public void onCopy(Models.Album album) {
                copyTitle(album);
            }

            @Override
            public void onLongClick(Models.Album album) {
                showItemMenu(album);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (historyPanel != null && historyPanel.getVisibility() == View.VISIBLE) {
                    hideSearchHistory();
                }
                if (section != SEC_HOME || noMore || requesting || dy <= 0 || keyword.isEmpty()) {
                    return;
                }
                RecyclerView.LayoutManager mgr = list.getLayoutManager();
                if (!(mgr instanceof LinearLayoutManager)) {
                    return;
                }
                int last = ((LinearLayoutManager) mgr).findLastVisibleItemPosition();
                if (last >= adapter.count() - 3) {
                    page++;
                    doSearch(keyword, false);
                }
            }
        });
    }

    /** 按当前显示方式切换列表 / 封面网格。 */
    private void applyViewMode() {
        boolean grid = prefs.getViewMode() == 1;
        adapter.setGrid(grid);
        boolean isGrid = list.getLayoutManager() instanceof GridLayoutManager;
        if (grid && !isGrid) {
            list.setLayoutManager(new GridLayoutManager(this, 2));
        } else if (!grid && isGrid) {
            list.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    // ------------------------------------------------------------ 分区

    private void showSection(int which) {
        section = which;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
        boolean home = which == SEC_HOME;

        searchBar.setVisibility(home ? View.VISIBLE : View.GONE);
        barTitle.setVisibility(home ? View.GONE : View.VISIBLE);
        if (!home) {
            barTitle.setText(sectionTitle(which));
        }
        profileView.setVisibility(which == SEC_PROFILE ? View.VISIBLE : View.GONE);

        boolean showWeb = home && webMode;
        webView.setVisibility(showWeb ? View.VISIBLE : View.GONE);
        refresh.setVisibility((which == SEC_PROFILE || showWeb) ? View.GONE : View.VISIBLE);

        updateDrawerSelection(which);

        emptyView.setVisibility(View.GONE);
        homeMark.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        hideSearchHistory();

        if (which == SEC_PROFILE) {
            bindAccount();
            return;
        }
        if (showWeb) {
            String cur = webView.getUrl();
            if (cur == null || cur.isEmpty()) {
                webView.loadUrl(site.base());
            }
            return;
        }

        applyViewMode();

        if (which == SEC_HOME) {
            if (keyword.isEmpty()) {
                if (!prefs.homeRecommend()) {
                    // 关闭推荐：主页只显示 Jm 标识，保持空白
                    homeAlbums.clear();
                    adapter.submit(new ArrayList<>());
                    emptyView.setVisibility(View.GONE);
                    homeMark.setVisibility(View.VISIBLE);
                    return;
                }
                if (homeAlbums.isEmpty()) {
                    adapter.submit(new ArrayList<>());
                    loadHome(false);
                } else {
                    adapter.submit(new ArrayList<>(homeAlbums));
                    emptyView.setVisibility(View.GONE);
                }
            } else {
                adapter.submit(new ArrayList<>(searchResults));
                if (searchResults.isEmpty()) {
                    showEmpty(getString(R.string.search_empty_title),
                            getString(R.string.search_empty_msg));
                }
            }
        } else if (which == SEC_HISTORY) {
            List<Models.Album> items = historyToAlbums(store.history());
            adapter.submit(items);
            if (items.isEmpty()) {
                showEmpty(getString(R.string.empty_history), getString(R.string.empty_history_msg));
            }
        } else {
            List<Models.Album> favs = store.favorites();
            adapter.submit(favs);
            if (favs.isEmpty()) {
                showEmpty(getString(R.string.empty_fav), getString(R.string.empty_fav_msg));
            }
        }
    }

    private String sectionTitle(int which) {
        switch (which) {
            case SEC_HISTORY:
                return getString(R.string.history_chip);
            case SEC_FAV:
                return getString(R.string.tab_fav);
            case SEC_PROFILE:
                return getString(R.string.tab_profile);
            default:
                return getString(R.string.tab_search);
        }
    }

    private void updateDrawerSelection(int which) {
        setRowSelected(navHome, which == SEC_HOME);
        setRowSelected(navHistory, which == SEC_HISTORY);
        setRowSelected(navFav, which == SEC_FAV);
        setRowSelected(navProfile, which == SEC_PROFILE);
        setRowSelected(navSettings, false);
    }

    private void setRowSelected(View row, boolean selected) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(selected ? R.drawable.bg_drawer_selected : tv.resourceId);
        int primary = ContextCompat.getColor(this, R.color.primary);
        int normal = ContextCompat.getColor(this, R.color.on_surface);
        for (int i = 0; i < ((android.view.ViewGroup) row).getChildCount(); i++) {
            View c = ((android.view.ViewGroup) row).getChildAt(i);
            if (c instanceof TextView) {
                ((TextView) c).setTextColor(selected ? primary : normal);
            } else if (c instanceof ImageView) {
                if (selected) {
                    ((ImageView) c).setColorFilter(primary);
                } else {
                    ((ImageView) c).clearColorFilter();
                }
            }
        }
    }

    /** 历史条目统一成 Album，复用同一套列表 / 封面样式。 */
    private List<Models.Album> historyToAlbums(List<Models.History> hs) {
        List<Models.Album> out = new ArrayList<>();
        for (Models.History h : hs) {
            Models.Album a = new Models.Album();
            a.id = h.id;
            a.title = h.title;
            a.cover = h.cover;
            a.readId = h.readId;
            a.extra = timeAgo(h.time);
            out.add(a);
        }
        return out;
    }

    private String timeAgo(long time) {
        if (time <= 0) {
            return "";
        }
        long d = System.currentTimeMillis() - time;
        if (d < 60_000L) {
            return getString(R.string.time_just_now);
        }
        if (d < 3_600_000L) {
            return getString(R.string.time_minutes, (int) (d / 60_000L));
        }
        if (d < 86_400_000L) {
            return getString(R.string.time_hours, (int) (d / 3_600_000L));
        }
        return getString(R.string.time_days, (int) (d / 86_400_000L));
    }

    private void bindAccount() {
        String user = prefs.getLoggedUser();
        if (user == null || user.isEmpty()) {
            accountName.setText(R.string.not_logged_in);
            accountHint.setText(R.string.login_hint_short);
            ((com.google.android.material.button.MaterialButton) btnAccount).setText(R.string.login);
        } else {
            accountName.setText(user);
            accountHint.setText(R.string.logged_in_hint);
            ((com.google.android.material.button.MaterialButton) btnAccount).setText(R.string.logout);
        }
        btnAccount.setOnClickListener(v -> {
            if (isLoggedIn()) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(R.string.logout)
                        .setMessage(accountName.getText())
                        .setPositiveButton(R.string.logout, (d, w) -> {
                            site.logout();
                            prefs.setLoggedUser("");
                            bindAccount();
                            Toast.makeText(this, R.string.logout, Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
        });
    }

    private boolean isLoggedIn() {
        String u = prefs.getLoggedUser();
        return u != null && !u.isEmpty();
    }

    // ------------------------------------------------------------ 搜索

    private void performSearch() {
        String q = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            showSection(SEC_HOME);
            return;
        }
        if (section != SEC_HOME) {
            section = SEC_HOME;
        }
        showSection(SEC_HOME);
        if (webMode) {
            hideKeyboard();
            searchInput.clearFocus();
            webView.loadUrl(site.searchUrl(q, 1));
            return;
        }
        doSearch(q, true);
    }

    private void doSearch(String q, boolean reset) {
        if (q == null || q.trim().isEmpty()) {
            return;
        }
        keyword = q.trim();
        if (reset) {
            page = 1;
            noMore = false;
            prefs.addHistory(keyword);
            hideKeyboard();
            searchInput.clearFocus();
            emptyView.setVisibility(View.GONE);
            loading.setVisibility(View.VISIBLE);
            list.setVisibility(View.VISIBLE);
        }
        if (requesting) {
            return;
        }
        requesting = true;
        final int reqPage = page;
        final String id = asAlbumId(keyword);

        pool.execute(() -> {
            List<Models.Album> result = new ArrayList<>();
            String error = null;
            try {
                if (!id.isEmpty()) {
                    Models.Album a = fetchAlbum("/album/" + id, id);
                    if (a == null) {
                        String body = site.getUrl(site.searchUrl(id, 1));
                        if (Parsers.isAlbumPage(body)) {
                            a = Parsers.parseAlbum(body, site.base());
                            if (!a.title.isEmpty()) {
                                String realId = Parsers.albumIdFrom(body);
                                a.id = realId.isEmpty() ? id : realId;
                            }
                        } else {
                            result.addAll(Parsers.parseSearch(body, site.base()));
                        }
                    }
                    if (a != null && !a.title.isEmpty()) {
                        result.add(0, a);
                    } else if (result.isEmpty()) {
                        error = getString(R.string.album_not_found, id);
                    }
                } else {
                    String body = site.getUrl(site.searchUrl(keyword, reqPage));
                    if (Parsers.isAlbumPage(body)) {
                        Models.Album a = Parsers.parseAlbum(body, site.base());
                        if (!a.title.isEmpty()) {
                            String realId = Parsers.albumIdFrom(body);
                            if (!realId.isEmpty()) {
                                a.id = realId;
                            }
                            result.add(a);
                        }
                    } else {
                        result = Parsers.parseSearch(body, site.base());
                    }
                }
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final List<Models.Album> data = result;
            final String err = error;
            final boolean single = !id.isEmpty();
            runOnUiThread(() -> onSearchResult(data, reqPage, err, single));
        });
    }

    private Models.Album fetchAlbum(String path, String fallbackId) {
        try {
            String html = site.get(path);
            Models.Album a = Parsers.parseAlbum(html, site.base());
            if (a.title.isEmpty()) {
                return null;
            }
            String realId = Parsers.albumIdFrom(html);
            a.id = realId.isEmpty() ? fallbackId : realId;
            return a;
        } catch (Exception e) {
            return null;
        }
    }

    /** 纯数字（或 JM1464578 这种）当作作品编号。 */
    String asAlbumId(String q) {
        if (q == null) {
            return "";
        }
        String s = q.trim();
        if (s.length() > 2 && s.regionMatches(true, 0, "jm", 0, 2)) {
            s = s.substring(2).trim();
        }
        if (s.length() < 4 || s.length() > 9) {
            return "";
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return "";
            }
        }
        return s;
    }

    private void onSearchResult(List<Models.Album> data, int reqPage, String error, boolean single) {
        requesting = false;
        refresh.setRefreshing(false);
        loading.setVisibility(View.GONE);

        if (error != null && reqPage == 1) {
            showEmpty(getString(R.string.load_failed), error);
            return;
        }
        if (data.isEmpty()) {
            noMore = true;
            if (reqPage == 1) {
                searchResults.clear();
                adapter.submit(new ArrayList<>());
                showEmpty(getString(R.string.no_result), keyword);
            }
            return;
        }
        if (single) {
            noMore = true;
        }

        if (reqPage == 1) {
            searchResults.clear();
            searchResults.addAll(data);
            adapter.submit(new ArrayList<>(searchResults));
        } else {
            List<Models.Album> fresh = new ArrayList<>();
            for (Models.Album a : data) {
                if (!adapter.contains(a.id)) {
                    fresh.add(a);
                }
            }
            if (fresh.isEmpty()) {
                noMore = true;
            } else {
                searchResults.addAll(fresh);
                adapter.append(fresh);
            }
        }
        emptyView.setVisibility(View.GONE);
    }

    private void showEmpty(String title, String msg) {
        emptyTitle.setText(title);
        emptyMsg.setText(msg);
        emptyView.setVisibility(View.VISIBLE);
        loading.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------ 首页推荐

    private void loadHome(boolean force) {
        if (homeLoading || !prefs.homeRecommend()) {
            return;
        }
        if (!force && homeLoaded && !homeAlbums.isEmpty()) {
            return;
        }
        homeLoading = true;
        loading.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);

        pool.execute(() -> {
            List<Models.Album> data = new ArrayList<>();
            String error = null;
            try {
                data = Parsers.parseSearch(site.get("/"), site.base());
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            final List<Models.Album> result = data;
            final String err = error;
            runOnUiThread(() -> {
                homeLoading = false;
                refresh.setRefreshing(false);
                loading.setVisibility(View.GONE);
                if (!prefs.homeRecommend()) {
                    return;
                }
                if (result.isEmpty()) {
                    if (err != null) {
                        showEmpty(getString(R.string.home_recommend_failed), err);
                    } else {
                        showEmpty(getString(R.string.search_empty_title),
                                getString(R.string.search_empty_msg));
                    }
                    return;
                }
                homeAlbums.clear();
                homeAlbums.addAll(result);
                homeLoaded = true;
                if (section == SEC_HOME && keyword.isEmpty()) {
                    adapter.submit(new ArrayList<>(homeAlbums));
                    emptyView.setVisibility(View.GONE);
                }
            });
        });
    }

    // ------------------------------------------------------------ 搜索历史

    private void showSearchHistory() {
        if (historyPanel == null) {
            return;
        }
        List<String> items = prefs.getHistory();
        if (items.isEmpty()) {
            hideSearchHistory();
            return;
        }
        historyList.removeAllViews();
        int limit = Math.min(items.size(), 8);
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        for (int i = 0; i < limit; i++) {
            final String q = items.get(i);
            TextView row = new TextView(this);
            row.setText(q);
            row.setTextSize(14f);
            row.setTextColor(ContextCompat.getColor(this, R.color.on_surface));
            row.setSingleLine(true);
            row.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.setPadding(0, dp(11), 0, dp(11));
            if (tv.resourceId != 0) {
                row.setBackgroundResource(tv.resourceId);
            }
            row.setOnClickListener(v -> {
                searchInput.setText(q);
                searchInput.setSelection(q.length());
                hideSearchHistory();
                performSearch();
            });
            historyList.addView(row);
        }
        historyPanel.setVisibility(View.VISIBLE);
    }

    private void hideSearchHistory() {
        if (historyPanel != null) {
            historyPanel.setVisibility(View.GONE);
        }
    }

    // ------------------------------------------------------------ 原网页

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36");

        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(webView, true);
        } catch (Exception ignored) {
        }

        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.bg));
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    // ------------------------------------------------------------ 列表操作

    private void openDetail(Models.Album album) {
        Intent i = new Intent(this, DetailActivity.class);
        i.putExtra("id", album.id);
        i.putExtra("title", album.title);
        i.putExtra("cover", album.cover);
        startActivity(i);
    }

    private void copyTitle(Models.Album album) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("title", album.title));
            Toast.makeText(this, R.string.title_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void showItemMenu(Models.Album album) {
        List<String> actions = new ArrayList<>();
        actions.add(getString(R.string.copy_title));
        final boolean fav = store.isFavorite(album.id);
        actions.add(getString(fav ? R.string.unfavorited : R.string.favorite));
        actions.add(getString(R.string.clear_album_cache));
        actions.add(getString(R.string.detail));

        new MaterialAlertDialogBuilder(this)
                .setTitle(album.title)
                .setItems(actions.toArray(new String[0]), (d, which) -> {
                    String action = actions.get(which);
                    if (action.equals(getString(R.string.copy_title))) {
                        copyTitle(album);
                    } else if (action.equals(getString(R.string.favorite))
                            || action.equals(getString(R.string.unfavorited))) {
                        toggleFavorite(album);
                    } else if (action.equals(getString(R.string.clear_album_cache))) {
                        confirmClearAlbumCache(album);
                    } else {
                        openDetail(album);
                    }
                })
                .show();
    }

    private void confirmClearAlbumCache(Models.Album album) {
        String name = album.title.isEmpty() ? album.id : album.title;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_cache)
                .setMessage(getString(R.string.confirm_clear_album, name))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    loader.clearAlbum(album.readId.isEmpty() ? album.id : album.readId);
                    Toast.makeText(this, R.string.cache_cleared_album, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toggleFavorite(Models.Album album) {
        boolean fav = store.isFavorite(album.id);
        if (fav) {
            store.removeFavorite(album.id);
            Toast.makeText(this, R.string.unfavorited, Toast.LENGTH_SHORT).show();
        } else {
            store.addFavorite(album);
            Toast.makeText(this, R.string.favorited, Toast.LENGTH_SHORT).show();
        }
        if (isLoggedIn()) {
            final boolean add = !fav;
            pool.execute(() -> {
                try {
                    site.favorite(album.id, add);
                } catch (Exception ignored) {
                }
            });
        }
        if (section == SEC_FAV) {
            showSection(SEC_FAV);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        drawerSite.setText(site.base().replace("https://", "").replace("http://", ""));
        // 设置页可能改了站点地址，回来后清空旧数据
        if (!site.base().equals(lastBase)) {
            lastBase = site.base();
            homeLoaded = false;
            homeAlbums.clear();
            searchResults.clear();
            keyword = "";
            page = 1;
            noMore = false;
        }
        if (section == SEC_PROFILE) {
            bindAccount();
            return;
        }
        // 重新渲染，顺便应用设置里改过的显示方式 / 推荐开关
        showSection(section);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }
        if (section == SEC_HOME && webMode) {
            if (webView.canGoBack()) {
                webView.goBack();
                return;
            }
            webMode = false;
            showSection(SEC_HOME);
            return;
        }
        if (section != SEC_HOME) {
            showSection(SEC_HOME);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        pool.shutdownNow();
        super.onDestroy();
    }
}
