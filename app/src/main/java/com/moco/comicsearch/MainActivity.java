package com.moco.comicsearch;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
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

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int SEC_HOME = 0;
    private static final int SEC_HISTORY = 1;
    private static final int SEC_FAV = 2;
    private static final int SEC_PROFILE = 3;
    private static final int SEC_SETTINGS = 4;
    private static final int SEC_DOWNLOAD = 5;

    private Site site;
    private Prefs prefs;
    private Store store;
    private ImageLoader loader;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);

    private SwipeDrawerLayout drawer;
    private View navHome, navHistory, navFav, navProfile, navSettings, navDownload;
    private TextView drawerSite;
    private TextView drawerVersion;

    private ImageView btnHeader;
    private TextView headerTitle;
    private HamburgerDrawable headerMorph;
    private ImageView searchFab;
    private View selectionBar;
    private ImageView btnSelAll;
    private View btnSelExport, btnSelPause, btnSelDelete;
    private ImageView btnSelStart;
    private final List<DownloadManager.Task> pendingExport = new ArrayList<>();
    private ActivityResultLauncher<Uri> exportTreeLauncher;
    private boolean selectionMode = false;
    private boolean selectionUiShown = false;
    private final java.util.Set<String> selectedIds = new java.util.HashSet<>();
    private View searchBarExpanded;
    private EditText searchInput;
    private SwipeRefreshLayout refresh;
    private RecyclerView list;
    private AlbumAdapter adapter;
    private View emptyView;
    private View homeMark;
    private RecyclerView profileList;
    private AlbumAdapter favAdapter;
    private AlbumAdapter histAdapter;
    private SectionHeaderAdapter headerFav;
    private SectionHeaderAdapter headerHist;
    private View settingsView;
    private SettingsPanel settingsPanel;
    private DownloadManager downloadManager;
    private final DownloadManager.Listener downloadListener = this::onDownloadsChanged;
    private View loading;
    private TextView emptyTitle;
    private TextView emptyMsg;
    private View historyPanel;
    private LinearLayout historyList;
    private WebView webView;
    private boolean webMode = false;
    private boolean searchExpanded = false;
    private boolean imeWasVisible = false;
    private boolean suppressImeCollapse = false;

    private int section = SEC_HOME;
    private String keyword = "";
    private int page = 1;
    private boolean noMore = false;
    private boolean requesting = false;
    private final List<Models.Album> searchResults = new ArrayList<>();
    private final java.util.Map<String, Models.Album> dlMeta = new java.util.HashMap<>();
    private final List<Models.Album> homeAlbums = new ArrayList<>();
    private boolean homeLoading = false;
    private boolean homeLoaded = false;
    private String lastBase = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        EdgeToEdge.setup(this);
        IconSwitcher.apply(this);
        setContentView(R.layout.activity_main);
        EdgeToEdge.padTop(findViewById(R.id.header));
        // 底部同时避开导航栏与输入法：键盘弹出时搜索框跟着上移
        final View contentFrame = findViewById(R.id.contentFrame);
        ViewCompat.setOnApplyWindowInsetsListener(contentFrame, (v, insets) -> {
            Insets sb = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(),
                    Math.max(sb.bottom, ime.bottom));
            return insets;
        });
        ViewCompat.requestApplyInsets(contentFrame);
        EdgeToEdge.padVertical(findViewById(R.id.drawerContent));

        site = Site.get(this);
        prefs = new Prefs(this);
        store = new Store(this);
        loader = ImageLoader.get(this);
        lastBase = site.base();

        bindViews();
        setupSearch();
        setupList();
        setupProfile();
        setupSettings();
        setupDownloads();
        setupDrawer();

        int start = savedInstanceState == null
                ? getIntent().getIntExtra("section", SEC_HOME)
                : savedInstanceState.getInt("section", SEC_HOME);
        showSection(start);
        maybeCheckUpdate();
    }

    /** 启动时静默检查更新：开关打开且有新版本时弹窗，关闭时只在关于页留红点。 */
    private void maybeCheckUpdate() {
        UpdateChecker.check(this, r -> {
            if (settingsPanel != null) {
                settingsPanel.refresh();
            }
            if (isFinishing() || r == null || !r.ok || !r.newer) {
                return;
            }
            if (!prefs.autoCheckUpdate()) {
                return;
            }
            String v = "v" + r.version;
            if (v.equals(UpdateChecker.promptedVersion)) {
                return;
            }
            UpdateChecker.promptedVersion = v;
            new MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.update_available, v))
                    .setMessage(getString(R.string.update_available_msg,
                            BuildConfig.VERSION_NAME, r.version))
                    .setPositiveButton(R.string.go_update, (d, w) -> openUrl(r.url))
                    .setNegativeButton(R.string.later, null)
                    .show();
        });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Ui.toast(this, getString(R.string.browser_open_failed));
        }
    }

    private void bindViews() {
        drawer = findViewById(R.id.drawer);
        navHome = findViewById(R.id.navHome);
        navHistory = findViewById(R.id.navHistory);
        navFav = findViewById(R.id.navFav);
        navProfile = findViewById(R.id.navProfile);
        navSettings = findViewById(R.id.navSettings);
        navDownload = findViewById(R.id.navDownload);
        drawerSite = findViewById(R.id.drawerSite);
        drawerVersion = findViewById(R.id.drawerVersion);

        btnHeader = findViewById(R.id.btnHeader);
        headerTitle = findViewById(R.id.headerTitle);
        headerMorph = new HamburgerDrawable(
                ContextCompat.getColor(this, R.color.on_surface), 2f,
                getResources().getDisplayMetrics().density);
        searchFab = findViewById(R.id.searchFab);
        selectionBar = findViewById(R.id.selectionBar);
        btnSelAll = findViewById(R.id.btnSelAll);
        btnSelExport = findViewById(R.id.btnSelExport);
        btnSelStart = findViewById(R.id.btnSelStart);
        btnSelPause = findViewById(R.id.btnSelPause);
        btnSelDelete = findViewById(R.id.btnSelDelete);
        searchBarExpanded = findViewById(R.id.searchBarExpanded);
        searchInput = findViewById(R.id.searchInput);
        refresh = findViewById(R.id.refresh);
        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.emptyView);
        homeMark = findViewById(R.id.homeMark);
        profileList = findViewById(R.id.profileView);
        settingsView = findViewById(R.id.settingsView);
        loading = findViewById(R.id.loading);
        emptyTitle = findViewById(R.id.emptyTitle);
        emptyMsg = findViewById(R.id.emptyMsg);
        historyPanel = findViewById(R.id.historyPanel);
        historyList = findViewById(R.id.historyList);
        webView = findViewById(R.id.webView);
        setupWebView();

        btnHeader.setOnClickListener(v -> {
            if (selectionMode) {
                exitSelection();
            } else if (isSearchMode()) {
                // 搜索结果态：左侧是叉，点击退出搜索、回到主页
                exitSearch();
            } else {
                drawer.openDrawer(GravityCompat.START);
            }
        });
        searchFab.setOnClickListener(v -> expandSearch());
        btnSelAll.setOnClickListener(v -> toggleSelectAll());
        btnSelExport.setOnClickListener(v -> startExportSelected());
        btnSelStart.setOnClickListener(v -> toggleSelectedDownload());
        btnSelPause.setOnClickListener(v -> pauseSelected());
        btnSelDelete.setOnClickListener(v -> deleteSelected());
        drawerSite.setText(site.base().replace("https://", "").replace("http://", ""));
        drawerVersion.setText("v" + BuildConfig.VERSION_NAME);
        drawerSite.setOnClickListener(v -> MirrorPicker.show(this, m -> {
            prefs.setBaseUrl("https://" + m.host);
            drawerSite.setText(m.host);
            homeLoaded = false;
            homeAlbums.clear();
            searchResults.clear();
            keyword = "";
            showSection(SEC_HOME);
        }));

        refresh.setColorSchemeColors(ThemeManager.primary(this));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.surface2));
        refresh.setOnRefreshListener(() -> {
            if (section == SEC_DOWNLOAD) {
                adapter.submit(downloadAlbums());
                refresh.setRefreshing(false);
                return;
            }
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
        navHome.setOnClickListener(v -> switchSection(SEC_HOME));
        navHistory.setOnClickListener(v -> switchSection(SEC_HISTORY));
        navFav.setOnClickListener(v -> switchSection(SEC_FAV));
        navProfile.setOnClickListener(v -> switchSection(SEC_PROFILE));
        navDownload.setOnClickListener(v -> switchSection(SEC_DOWNLOAD));
        navSettings.setOnClickListener(v -> switchSection(SEC_SETTINGS));

        // 主页 / 历史 / 收藏 / 设置 同级：整页右滑始终呼出侧边栏
        drawer.setOnSwipeRightListener(() -> {
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
                return true;
            }
            return false;
        });
    }

    private void switchSection(int which) {
        // 侧边栏切页：搜索框直接收起（不播放收回动画），键盘一并隐藏
        if (searchExpanded) {
            resetSearchImmediate();
        }
        if (which == SEC_HOME) {
            // 从侧边栏回到主页：退出搜索态，恢复推荐流
            keyword = "";
            searchResults.clear();
        }
        showSection(which);
    }

    /** 立即收起搜索框（无动画），用于侧边栏切页。 */
    private void resetSearchImmediate() {
        searchExpanded = false;
        hideKeyboard();
        searchInput.clearFocus();
        searchInput.setText("");
        hideSearchHistory();
        searchBarExpanded.setVisibility(View.GONE);
        searchBarExpanded.setScaleX(1f);
        searchBarExpanded.setScaleY(1f);
        searchBarExpanded.setAlpha(1f);
        searchBarExpanded.setTranslationX(0f);
        searchBarExpanded.setTranslationY(0f);
        searchFab.setVisibility(View.GONE);
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (s.length() > 0) {
                    hideSearchHistory();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

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

        searchInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && searchInput.getText().length() == 0) {
                showSearchHistory();
            } else if (!hasFocus) {
                hideSearchHistory();
            }
        });

        findViewById(R.id.btnSearchAction).setOnClickListener(v -> collapseSearch());
        findViewById(R.id.btnClearSearchHistory).setOnClickListener(v -> {
            prefs.clearHistory();
            hideSearchHistory();
            Ui.toast(this, this.getString(R.string.history_cleared));
        });

        // 系统返回手势收起键盘时，搜索框跟着收回成圆形按钮
        final View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (!searchExpanded) {
                imeWasVisible = false;
                return;
            }
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            boolean visible = keypadHeight > screenHeight * 0.15;
            if (visible) {
                imeWasVisible = true;
            } else if (imeWasVisible) {
                imeWasVisible = false;
                if (searchExpanded && !suppressImeCollapse) {
                    collapseSearch();
                }
            }
        });
    }

    /** 圆形按钮展开成底部横条搜索框：从按钮位置横向生长，避免圆形硬切成矩形。 */
    private void expandSearch() {
        if (searchExpanded) {
            return;
        }
        searchExpanded = true;
        searchFab.animate().scaleX(0f).scaleY(0f).alpha(0f).setDuration(120)
                .withEndAction(() -> searchFab.setVisibility(View.GONE)).start();
        searchBarExpanded.setVisibility(View.VISIBLE);
        searchBarExpanded.post(() -> {
            float pivotX = searchBarExpanded.getWidth() - dp(34);
            float pivotY = searchBarExpanded.getHeight() / 2f;
            searchBarExpanded.setPivotX(pivotX);
            searchBarExpanded.setPivotY(pivotY);
            searchBarExpanded.setScaleX(0.16f);
            searchBarExpanded.setScaleY(1.15f);
            searchBarExpanded.setAlpha(0f);
            searchBarExpanded.animate()
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(240)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        });
        searchInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        }
        if (searchInput.getText().length() == 0) {
            showSearchHistory();
        }
    }

    /** 横条收回成圆形按钮。 */
    private void collapseSearch() {
        if (!searchExpanded) {
            return;
        }
        searchExpanded = false;
        hideKeyboard();
        searchInput.clearFocus();
        hideSearchHistory();
        searchInput.setText("");
        int w = searchBarExpanded.getWidth();
        int h = searchBarExpanded.getHeight();
        if (w > 0 && h > 0) {
            searchBarExpanded.setPivotX(w - dp(34));
            searchBarExpanded.setPivotY(h / 2f);
            searchBarExpanded.animate()
                    .scaleX(0.16f).scaleY(1.15f).alpha(0f)
                    .setDuration(180)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        searchBarExpanded.setVisibility(View.GONE);
                        searchBarExpanded.setScaleX(1f);
                        searchBarExpanded.setScaleY(1f);
                        searchBarExpanded.setAlpha(1f);
                        searchFab.setVisibility(View.VISIBLE);
                        searchFab.setScaleX(0f);
                        searchFab.setScaleY(0f);
                        searchFab.setAlpha(0f);
                        searchFab.animate().scaleX(1f).scaleY(1f).alpha(1f)
                                .setDuration(160).start();
                    })
                    .start();
        } else {
            searchBarExpanded.setVisibility(View.GONE);
            searchBarExpanded.setScaleX(1f);
            searchBarExpanded.setScaleY(1f);
            searchBarExpanded.setAlpha(1f);
            searchFab.setVisibility(View.VISIBLE);
            searchFab.setScaleX(1f);
            searchFab.setScaleY(1f);
            searchFab.setAlpha(1f);
        }
    }

    private void setupList() {
        adapter = new AlbumAdapter(loader, newAlbumListener());
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

    private void setupProfile() {
        headerFav = new SectionHeaderAdapter();
        headerHist = new SectionHeaderAdapter();
        favAdapter = new AlbumAdapter(loader, newAlbumListener());
        histAdapter = new AlbumAdapter(loader, newAlbumListener());
        favAdapter.setGrid(true);
        histAdapter.setGrid(true);
        final ConcatAdapter concat =
                new ConcatAdapter(headerFav, favAdapter, headerHist, histAdapter);
        GridLayoutManager glm = new GridLayoutManager(this, 2);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                android.util.Pair<RecyclerView.Adapter<? extends RecyclerView.ViewHolder>, Integer>
                        pair = concat.getWrappedAdapterAndPosition(position);
                return pair.first instanceof SectionHeaderAdapter ? 2 : 1;
            }
        });
        profileList.setLayoutManager(glm);
        profileList.setAdapter(concat);
    }

    private void setupSettings() {
        settingsPanel = new SettingsPanel(this, settingsView, () -> showSection(section));
    }

    private void setupDownloads() {
        downloadManager = DownloadManager.get(this);
        exportTreeLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(), uri -> {
                    if (uri != null) {
                        copyExport(uri);
                    }
                });
    }

    private AlbumAdapter.Listener newAlbumListener() {
        return new AlbumAdapter.Listener() {
            @Override
            public void onClick(Models.Album album) {
                if (selectionMode) {
                    toggleSelection(album);
                } else {
                    openDetail(album);
                }
            }

            @Override
            public void onCopy(Models.Album album) {
                copyTitle(album);
            }

            @Override
            public void onLongClick(Models.Album album) {
                if (selectionMode) {
                    toggleSelection(album);
                } else {
                    enterSelection(album);
                }
            }

            @Override
            public void onTrash(Models.Album album) {
                confirmRemoveDownload(album);
            }

            @Override
            public void onDownload(Models.Album album) {
                if (downloadManager == null) {
                    return;
                }
                DownloadManager.Task t = downloadManager.get(album.id);
                if (t == null) {
                    return;
                }
                if (t.state == 1) {
                    downloadManager.pause(album.id);
                } else {
                    downloadManager.start(album.id);
                }
            }
        };
    }

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
        if (selectionMode) {
            selectionMode = false;
            selectedIds.clear();
        }
        selectionUiShown = false;
        if (selectionBar != null) {
            selectionBar.setVisibility(View.GONE);
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
        }
        if (adapter != null) {
            adapter.setSelectionMode(false);
            adapter.setSelectedIds(selectedIds);
        }
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        }
        applyHeader(which);

        boolean listSection = which == SEC_HOME || which == SEC_HISTORY || which == SEC_FAV
                || which == SEC_DOWNLOAD;
        list.setVisibility(listSection ? View.VISIBLE : View.GONE);
        refresh.setVisibility((which == SEC_PROFILE || which == SEC_SETTINGS)
                ? View.GONE : View.VISIBLE);
        profileList.setVisibility(which == SEC_PROFILE ? View.VISIBLE : View.GONE);
        settingsView.setVisibility(which == SEC_SETTINGS ? View.VISIBLE : View.GONE);

        boolean showFab = which == SEC_HOME;
        searchFab.setVisibility(showFab && !searchExpanded ? View.VISIBLE : View.GONE);
        if (showFab && !searchExpanded) {
            searchFab.setScaleX(1f);
            searchFab.setScaleY(1f);
            searchFab.setAlpha(1f);
        }
        if (!listSection) {
            searchExpanded = false;
            searchBarExpanded.setVisibility(View.GONE);
            hideSearchHistory();
        }

        webView.setVisibility((which == SEC_HOME && webMode) ? View.VISIBLE : View.GONE);
        updateDrawerSelection(which);

        emptyView.setVisibility(View.GONE);
        homeMark.setVisibility(View.GONE);
        loading.setVisibility(View.GONE);
        hideSearchHistory();

        if (which == SEC_PROFILE) {
            showProfile();
            return;
        }
        if (which == SEC_SETTINGS) {
            settingsPanel.refresh();
            return;
        }
        if (which == SEC_DOWNLOAD) {
            adapter.setShowProgress(true);
            adapter.setShowTrash(false);
            adapter.setShowDownload(true);
            applyViewMode();
            adapter.submit(downloadAlbums());
            return;
        }
        adapter.setShowProgress(false);
        adapter.setShowTrash(false);
        adapter.setShowDownload(false);
        if (which == SEC_HOME && webMode) {
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

    private void applyHeader(int which) {
        int icon;
        CharSequence title;
        switch (which) {
            case SEC_HISTORY:
                icon = R.drawable.ic_history_solid;
                title = getString(R.string.history_chip);
                break;
            case SEC_FAV:
                icon = R.drawable.ic_favorite;
                title = getString(R.string.tab_fav);
                break;
            case SEC_DOWNLOAD:
                icon = R.drawable.ic_download;
                title = getString(R.string.tab_download);
                break;
            case SEC_PROFILE:
                icon = R.drawable.ic_person;
                title = getString(R.string.tab_profile);
                break;
            case SEC_SETTINGS:
                icon = R.drawable.ic_settings;
                title = getString(R.string.settings_title);
                break;
            default:
                icon = 0;
                title = keyword.isEmpty()
                        ? getString(R.string.home_title)
                        : getString(R.string.search_title, keyword);
                break;
        }
        int color = ContextCompat.getColor(this, R.color.on_surface);
        if (icon == 0) {
            // 主页：三横 ↔ 叉 的可动画图标
            boolean searching = !keyword.isEmpty();
            btnHeader.setImageDrawable(headerMorph);
            btnHeader.setColorFilter(color);
            btnHeader.setContentDescription(getString(
                    searching ? R.string.cancel : R.string.menu));
            animateHeaderMorph(searching);
        } else {
            btnHeader.setImageResource(icon);
            btnHeader.setColorFilter(color);
        }
        headerTitle.setText(title);
    }

    private boolean isSearchMode() {
        return section == SEC_HOME && !keyword.isEmpty();
    }

    /** 退出搜索结果，回到主页推荐 / 空白主页。 */
    private void exitSearch() {
        keyword = "";
        searchResults.clear();
        page = 1;
        noMore = false;
        showSection(SEC_HOME);
    }

    private void animateHeaderMorph(boolean searching) {
        float to = searching ? 1f : 0f;
        float from = headerMorph.getProgress();
        if (Math.abs(from - to) < 0.01f) {
            headerMorph.setProgress(to);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> headerMorph.setProgress((Float) a.getAnimatedValue()));
        animator.start();
    }

    private void showProfile() {
        headerFav.setTitle(getString(R.string.tab_fav));
        headerHist.setTitle(getString(R.string.history_chip));
        List<Models.Album> favs = store.favorites();
        List<Models.Album> history = historyToAlbums(store.history());
        favAdapter.submit(favs);
        histAdapter.submit(history);
        if (favs.isEmpty() && history.isEmpty()) {
            showEmpty(getString(R.string.empty_fav), getString(R.string.empty_fav_msg));
        }
    }

    private void updateDrawerSelection(int which) {
        setRowSelected(navHome, which == SEC_HOME);
        setRowSelected(navHistory, which == SEC_HISTORY);
        setRowSelected(navFav, which == SEC_FAV);
        setRowSelected(navDownload, which == SEC_DOWNLOAD);
        setRowSelected(navProfile, which == SEC_PROFILE);
        setRowSelected(navSettings, which == SEC_SETTINGS);
    }

    private void setRowSelected(View row, boolean selected) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(selected ? R.drawable.bg_drawer_selected : tv.resourceId);
        int primary = ThemeManager.primary(this);
        int normal = ContextCompat.getColor(this, R.color.on_surface);
        for (int i = 0; i < ((android.view.ViewGroup) row).getChildCount(); i++) {
            View c = ((android.view.ViewGroup) row).getChildAt(i);
            if (c instanceof TextView) {
                ((TextView) c).setTextColor(selected ? primary : normal);
            } else if (c instanceof ImageView) {
                ((ImageView) c).setColorFilter(primary);
                c.setAlpha(selected ? 1f : 0.72f);
            }
        }
    }

    private List<Models.Album> historyToAlbums(List<Models.History> hs) {
        List<Models.Album> out = new ArrayList<>();
        for (Models.History h : hs) {
            Models.Album a = new Models.Album();
            a.id = h.id;
            a.title = h.title;
            a.cover = h.cover;
            a.readId = h.readId;
            a.extra = timeAgo(h.time);
            Models.Album cached = store.getCachedAlbum(h.id);
            if (cached != null) {
                a.pages = cached.pages;
                a.tags.addAll(cached.tags);
            }
            out.add(a);
        }
        return out;
    }

    private void onDownloadsChanged() {
        if (section == SEC_DOWNLOAD && adapter != null && downloadManager != null) {
            adapter.submit(downloadAlbums());
        }
    }

    private List<Models.Album> downloadAlbums() {
        List<Models.Album> out = new ArrayList<>();
        if (downloadManager == null) {
            return out;
        }
        for (DownloadManager.Task t : downloadManager.tasks()) {
            Models.Album a = new Models.Album();
            a.id = t.albumId;
            a.title = t.title;
            a.cover = t.cover;
            a.readId = t.chapterId;
            a.progress = t.state == 3 ? -1 : t.progress();
            a.downloadState = t.state;
            a.extra = t.state == 3 ? "" : (t.done + "/" + (t.total > 0 ? t.total : "?"));
            if (t.state == 3) {
                // 下载完成后恢复成和收藏页一致的标签展示（元信息缓存到内存，避免每帧读盘）
                Models.Album cached = dlMeta.get(t.albumId);
                if (cached == null) {
                    cached = store.getCachedAlbum(t.albumId);
                    if (cached == null) {
                        cached = new Models.Album();
                    }
                    dlMeta.put(t.albumId, cached);
                }
                a.pages = cached.pages;
                a.tags.addAll(cached.tags);
            }
            out.add(a);
        }
        return out;
    }

    private void confirmRemoveDownload(Models.Album album) {
        String name = album.title == null || album.title.isEmpty() ? album.id : album.title;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.remove)
                .setMessage(getString(R.string.confirm_remove_download, name))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    downloadManager.remove(album.id);
                    adapter.submit(downloadAlbums());
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ------------------------------------------------------------ 多选

    private void enterSelection(Models.Album album) {
        if (album == null || album.id == null || album.id.isEmpty()) {
            return;
        }
        if (section == SEC_PROFILE) {
            return;
        }
        selectionMode = true;
        selectedIds.clear();
        selectedIds.add(album.id);
        applySelectionUi();
    }

    private void selectAll() {
        if (adapter == null || !selectionMode) {
            return;
        }
        selectedIds.clear();
        for (Models.Album a : adapter.items()) {
            if (a.id != null && !a.id.isEmpty()) {
                selectedIds.add(a.id);
            }
        }
        if (selectedIds.isEmpty()) {
            return;
        }
        applySelectionUi();
    }

    private boolean allSelected() {
        if (adapter == null || adapter.items().isEmpty()) {
            return false;
        }
        for (Models.Album a : adapter.items()) {
            if (!selectedIds.contains(a.id)) {
                return false;
            }
        }
        return true;
    }

    /** 未全选时全选，已全选时全不选（退出多选）。 */
    private void toggleSelectAll() {
        if (allSelected()) {
            // 全不选：只清空勾选，保留多选状态，方便继续手动选择或再次全选
            selectedIds.clear();
            applySelectionUi();
        } else {
            selectAll();
        }
    }

    private boolean allSelectedDownloading() {
        if (downloadManager == null || selectedIds.isEmpty()) {
            return false;
        }
        for (String id : selectedIds) {
            DownloadManager.Task t = downloadManager.get(id);
            if (t == null || t.state != 1) {
                return false;
            }
        }
        return true;
    }

    /** 全部在下载时暂停，否则开始（把未开始的补上）。 */
    private void toggleSelectedDownload() {
        if (allSelectedDownloading()) {
            pauseSelected();
        } else {
            startSelected();
        }
    }

    private void startExportSelected() {
        if (downloadManager == null) {
            return;
        }
        pendingExport.clear();
        for (String id : selectedIds) {
            DownloadManager.Task t = downloadManager.get(id);
            if (t != null && t.state == 3 && t.zipPath != null && !t.zipPath.isEmpty()
                    && new File(t.zipPath).exists()) {
                pendingExport.add(t);
            }
        }
        if (pendingExport.isEmpty()) {
            Ui.toast(this, getString(R.string.export_none));
            return;
        }
        Ui.toast(this, getString(R.string.exporting));
        exportTreeLauncher.launch(null);
    }

    private void copyExport(Uri treeUri) {
        final List<DownloadManager.Task> tasks = new ArrayList<>(pendingExport);
        if (tasks.isEmpty()) {
            return;
        }
        new Thread(() -> {
            int ok = 0;
            for (DownloadManager.Task t : tasks) {
                try {
                    File src = new File(t.zipPath);
                    String treeId = DocumentsContract.getTreeDocumentId(treeUri);
                    Uri parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeId);
                    Uri doc = DocumentsContract.createDocument(
                            getContentResolver(), parent, "application/zip", src.getName());
                    if (doc == null) {
                        continue;
                    }
                    try (InputStream in = new java.io.FileInputStream(src);
                         OutputStream out = getContentResolver().openOutputStream(doc)) {
                        if (out == null) {
                            continue;
                        }
                        byte[] buf = new byte[16384];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                    ok++;
                } catch (Exception ignored) {
                }
            }
            final int count = ok;
            runOnUiThread(() -> Ui.toast(this, getString(R.string.export_done, count)));
        }).start();
    }

    private void toggleSelection(Models.Album album) {
        if (album == null) {
            return;
        }
        if (selectedIds.contains(album.id)) {
            selectedIds.remove(album.id);
        } else {
            selectedIds.add(album.id);
        }
        applySelectionUi();
    }

    private void exitSelection() {
        selectionMode = false;
        selectedIds.clear();
        applySelectionUi();
    }

    private void applySelectionUi() {
        if (adapter != null) {
            adapter.setSelectionMode(selectionMode);
            adapter.setSelectedIds(selectedIds);
        }
        boolean download = section == SEC_DOWNLOAD;
        boolean allDownloading = download && allSelectedDownloading();
        btnSelAll.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        if (selectionMode) {
            boolean all = allSelected();
            btnSelAll.setImageResource(all
                    ? R.drawable.ic_deselect_all : R.drawable.ic_select_all);
            btnSelAll.setContentDescription(getString(
                    all ? R.string.deselect_all : R.string.select_all));
            // 全选：主题色底 + 白勾；全不选：白底 + 主题色勾
            btnSelAll.setBackgroundResource(all
                    ? R.drawable.bg_circle_surface : R.drawable.bg_circle_primary);
            btnSelAll.setColorFilter(all
                    ? ThemeManager.primary(this)
                    : ContextCompat.getColor(this, R.color.white));
        }
        btnSelExport.setVisibility(selectionMode && download ? View.VISIBLE : View.GONE);
        btnSelStart.setVisibility(selectionMode && download ? View.VISIBLE : View.GONE);
        btnSelPause.setVisibility(View.GONE);
        if (selectionMode && download) {
            btnSelStart.setImageResource(allDownloading ? R.drawable.ic_pause : R.drawable.ic_play);
            btnSelStart.setContentDescription(getString(
                    allDownloading ? R.string.download_pause : R.string.download_start));
        }
        if (selectionMode != selectionUiShown) {
            selectionUiShown = selectionMode;
            animateSelectionBar(selectionMode);
        }
        if (selectionMode) {
            searchFab.setVisibility(View.GONE);
            searchBarExpanded.setVisibility(View.GONE);
            hideSearchHistory();
            btnHeader.setImageResource(R.drawable.ic_close);
            btnHeader.setColorFilter(ContextCompat.getColor(this, R.color.on_surface));
            headerTitle.setText(getString(R.string.selected_count, selectedIds.size()));
        } else {
            applyHeader(section);
            boolean showFab = section == SEC_HOME && !searchExpanded;
            searchFab.setVisibility(showFab ? View.VISIBLE : View.GONE);
            if (showFab) {
                searchFab.setScaleX(1f);
                searchFab.setScaleY(1f);
                searchFab.setAlpha(1f);
            }
        }
    }

    private void animateSelectionBar(boolean show) {
        if (show) {
            selectionBar.setVisibility(View.VISIBLE);
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
            animateSelButton(btnSelAll, 0);
            animateSelButton(btnSelExport, 50);
            animateSelButton(btnSelStart, 100);
            animateSelButton(btnSelPause, 150);
            animateSelButton(btnSelDelete, 200);
        } else {
            selectionBar.animate().alpha(0f).translationY(dp(70)).setDuration(160)
                    .setInterpolator(new AccelerateInterpolator())
                    .withEndAction(() -> {
                        selectionBar.setVisibility(View.GONE);
                        selectionBar.setAlpha(1f);
                        selectionBar.setTranslationY(0f);
                    }).start();
        }
    }

    private void animateSelButton(View v, long delay) {
        if (v.getVisibility() != View.VISIBLE) {
            return;
        }
        v.setTranslationY(dp(70));
        v.setAlpha(0f);
        v.setScaleX(0.7f);
        v.setScaleY(0.7f);
        v.animate().translationY(0f).alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay).setDuration(240)
                .setInterpolator(new OvershootInterpolator(1.1f)).start();
    }

    private void deleteSelected() {
        if (selectedIds.isEmpty()) {
            return;
        }
        final int count = selectedIds.size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.remove)
                .setMessage(getString(R.string.confirm_delete_selected, count))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    for (String id : new ArrayList<>(selectedIds)) {
                        if (section == SEC_FAV) {
                            store.removeFavorite(id);
                        } else if (section == SEC_HISTORY) {
                            store.removeHistory(id);
                        } else if (section == SEC_DOWNLOAD && downloadManager != null) {
                            downloadManager.remove(id);
                        }
                    }
                    exitSelection();
                    showSection(section);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void startSelected() {
        if (downloadManager == null) {
            return;
        }
        for (String id : new ArrayList<>(selectedIds)) {
            downloadManager.start(id);
        }
        // 保持多选状态，仅刷新列表
        if (section == SEC_DOWNLOAD) {
            adapter.submit(downloadAlbums());
        }
        applySelectionUi();
    }

    private void pauseSelected() {
        if (downloadManager == null) {
            return;
        }
        for (String id : new ArrayList<>(selectedIds)) {
            downloadManager.pause(id);
        }
        // 保持多选状态，仅刷新列表
        if (section == SEC_DOWNLOAD) {
            adapter.submit(downloadAlbums());
        }
        applySelectionUi();
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

    private boolean isLoggedIn() {
        String u = prefs.getLoggedUser();
        return u != null && !u.isEmpty();
    }

    // ------------------------------------------------------------ 搜索

    private void performSearch() {
        String q = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (q.isEmpty()) {
            return;
        }
        suppressImeCollapse = true;
        hideKeyboard();
        // 先写入关键词，让标题立即变成「搜索：xxx」
        keyword = q;
        if (section != SEC_HOME) {
            section = SEC_HOME;
        }
        showSection(SEC_HOME);
        doSearch(q, true);
        // 搜索开始后自动收起搜索框，回到圆形按钮状态
        collapseSearch();
        searchInput.postDelayed(() -> suppressImeCollapse = false, 700);
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
            homeMark.setVisibility(View.GONE);
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

        if (section != SEC_HOME) {
            return;
        }

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
        if (section != SEC_HOME && section != SEC_HISTORY && section != SEC_FAV) {
            return;
        }
        emptyTitle.setText(title);
        emptyMsg.setText(msg);
        emptyView.setVisibility(View.VISIBLE);
        loading.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------ 主页推荐

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
                    if (section == SEC_HOME) {
                        if (err != null) {
                            showEmpty(getString(R.string.home_recommend_failed), err);
                        } else {
                            showEmpty(getString(R.string.search_empty_title),
                                    getString(R.string.search_empty_msg));
                        }
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
            Ui.toast(this, this.getString(R.string.title_copied));
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
                    Ui.toast(this, this.getString(R.string.cache_cleared_album));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void toggleFavorite(Models.Album album) {
        boolean fav = store.isFavorite(album.id);
        if (fav) {
            store.removeFavorite(album.id);
            Ui.toast(this, this.getString(R.string.unfavorited));
        } else {
            store.addFavorite(album);
            Ui.toast(this, this.getString(R.string.favorited));
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
        if (section == SEC_FAV || section == SEC_PROFILE) {
            showSection(section);
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
        if (!site.base().equals(lastBase)) {
            lastBase = site.base();
            homeLoaded = false;
            homeAlbums.clear();
            searchResults.clear();
            keyword = "";
            page = 1;
            noMore = false;
        }
        showSection(section);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (downloadManager != null) {
            downloadManager.addListener(downloadListener);
        }
    }

    @Override
    protected void onStop() {
        if (downloadManager != null) {
            downloadManager.removeListener(downloadListener);
        }
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("section", section);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }
        if (searchExpanded) {
            collapseSearch();
            return;
        }
        if (selectionMode) {
            exitSelection();
            return;
        }
        if (isSearchMode()) {
            exitSearch();
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

    // ------------------------------------------------------------ 分区标题

    private class SectionHeaderAdapter extends RecyclerView.Adapter<SectionHeaderAdapter.VH> {

        private String title = "";

        void setTitle(String t) {
            title = t == null ? "" : t;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_section_header, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.text.setText(title);
        }

        @Override
        public int getItemCount() {
            return 1;
        }

        class VH extends RecyclerView.ViewHolder {
            TextView text;

            VH(@NonNull View itemView) {
                super(itemView);
                text = itemView.findViewById(R.id.sectionTitle);
            }
        }
    }
}
