package com.moco.comicsearch;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 详情页：信息头 + 三列图片墙。
 * 进入即开始加载图片并预取到磁盘缓存，退出即停止。
 */
public class DetailActivity extends SwipeBackActivity {

    private Site site;
    private Store store;
    private ImageLoader loader;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private String albumId;
    private String fallbackTitle;
    private String fallbackCover;
    private Models.Album album;
    private Models.ChapterContent content;

    private TextView barTitle;
    private DetailAdapter adapter;

    // 评论面板
    private View commentsPanel;
    private LinearLayout commentItems;
    private TextView commentsLoading;
    private TextView commentsEmpty;
    private TextView btnMoreComments;
    private final List<String> comments = new ArrayList<>();
    private boolean commentsOpen;
    private boolean commentsLoaded;
    private boolean commentsLoadingFlag;
    private int commentShown;
    private float touchDownX;
    private float touchDownY;
    private boolean touchTracking;
    private static final int COMMENT_PAGE = 10;
    private final AtomicBoolean prefetchCancel = new AtomicBoolean(false);
    private boolean downloadNotified;
    private final DownloadManager.Listener dlListener = () -> {
        if (album == null || adapter == null) {
            return;
        }
        DownloadManager.Task t = DownloadManager.get(this).get(album.id);
        if (t == null) {
            refreshDownloadState();
            return;
        }
        if (t.state == 3) {
            if (!downloadNotified) {
                downloadNotified = true;
                adapter.animateDownloadDone(() ->
                        Ui.toast(DetailActivity.this, getString(R.string.download_done)));
            }
        } else {
            downloadNotified = false;
            adapter.setDownloadProgress(t.progress());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        EdgeToEdge.setup(this);
        setContentView(R.layout.activity_detail);
        EdgeToEdge.padTop(findViewById(R.id.header));
        EdgeToEdge.padBottom(findViewById(R.id.content));

        site = Site.get(this);
        store = new Store(this);
        loader = ImageLoader.get(this);

        albumId = getIntent().getStringExtra("id");
        fallbackTitle = getIntent().getStringExtra("title");
        fallbackCover = getIntent().getStringExtra("cover");
        if (albumId == null || albumId.isEmpty()) {
            finish();
            return;
        }

        barTitle = findViewById(R.id.barTitle);
        RecyclerView contentView = findViewById(R.id.content);

        adapter = new DetailAdapter(loader, new DetailAdapter.Listener() {
            @Override
            public void onCopy() {
                copyTitle();
            }

            @Override
            public void onFavorite() {
                toggleFavorite();
            }

            @Override
            public void onRead() {
                openReader(0);
            }

            @Override
            public void onDownload() {
                if (album == null) {
                    return;
                }
                if (isDownloaded(album.id)) {
                    Ui.toast(DetailActivity.this, DetailActivity.this.getString(R.string.downloaded));
                    return;
                }
                downloadNotified = false;
                DownloadManager dm = DownloadManager.get(DetailActivity.this);
                dm.enqueue(album, currentChapterId());
                dm.start(album.id);
                Ui.toast(DetailActivity.this, DetailActivity.this.getString(R.string.download_start));
            }

            @Override
            public void onPageClick(int index) {
                openReader(index);
            }
        });

        GridLayoutManager glm = new GridLayoutManager(this, 3);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return position == 0 ? 3 : 1;
            }
        });
        contentView.setLayoutManager(glm);
        contentView.setAdapter(adapter);
        DownloadManager.get(this).addListener(dlListener);
        setupComments();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        barTitle.setText(fallbackTitle == null ? getString(R.string.detail) : fallbackTitle);

        // 有缓存先用缓存秒开，再后台刷新；无缓存时从本地收藏/历史兜底
        Models.Album cached = store.getCachedAlbum(albumId);
        if (cached == null) {
            cached = store.findLocalAlbum(albumId);
        }
        if (cached != null) {
            if ((cached.cover == null || cached.cover.isEmpty()) && fallbackCover != null) {
                cached.cover = fallbackCover;
            }
            album = cached;
            if (!cached.title.isEmpty()) {
                barTitle.setText(cached.title);
            }
            adapter.setAlbum(cached, store.isFavorite(cached.id));
            refreshDownloadState();
            Models.ChapterContent cachedContent = store.getCachedChapter(currentChapterId());
            if (cachedContent != null) {
                content = cachedContent;
                adapter.setPages(cachedContent);
                loadPages();
            }
        }

        loadDetail();
    }

    private void loadDetail() {
        pool.execute(() -> {
            try {
                String html = site.get("/album/" + albumId);
                Models.Album a = Parsers.parseAlbum(html, site.base());
                a.id = albumId;
                if (a.title.isEmpty()) {
                    a.title = fallbackTitle == null ? "" : fallbackTitle;
                }
                if (a.cover.isEmpty()) {
                    Models.Album ca = store.getCachedAlbum(albumId);
                    if (ca != null && ca.cover != null && !ca.cover.isEmpty()) {
                        a.cover = ca.cover;
                    } else {
                        a.cover = fallbackCover == null ? "" : fallbackCover;
                    }
                }
                store.saveAlbum(a);
                runOnUiThread(() -> {
                    album = a;
                    barTitle.setText(a.title.isEmpty() ? getString(R.string.detail) : a.title);
                    adapter.setAlbum(a, store.isFavorite(a.id));
                    refreshDownloadState();
                    if (content == null) {
                        loadPages();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (album == null) {
                        Ui.toast(this, this.getString(R.string.load_failed));
                    }
                });
            }
        });
    }

    /** 取本章图片，填进三列图片墙，并开始预取缓存。 */
    private void loadPages() {
        if (album == null) {
            return;
        }
        final String chapterId = currentChapterId();
        if (chapterId.isEmpty()) {
            return;
        }
        // 先展示缓存的章节图片列表，再后台刷新
        Models.ChapterContent cached = store.getCachedChapter(chapterId);
        if (cached != null) {
            content = cached;
            adapter.setPages(cached);
        }
        pool.execute(() -> {
            Models.ChapterContent c = null;
            try {
                String html = site.get("/photo/" + chapterId);
                c = Parsers.parseChapter(html, site.base());
                if (c.aid.isEmpty()) {
                    c.aid = chapterId;
                }
                store.saveChapter(chapterId, c);
            } catch (Exception ignored) {
            }
            final Models.ChapterContent result = c;
            runOnUiThread(() -> {
                if (result == null || result.pages.isEmpty()) {
                    return;
                }
                content = result;
                adapter.setPages(result);
                startPrefetch();
            });
        });
    }

    private void startPrefetch() {
        if (content == null || content.pages.isEmpty()) {
            return;
        }
        prefetchCancel.set(false);
        loader.prefetch(content.pages, content.aid, prefetchCancel);
    }

    @Override
    protected void onStart() {
        super.onStart();
        startPrefetch();
    }

    @Override
    protected void onStop() {
        // 离开详情页就停止继续加载
        prefetchCancel.set(true);
        super.onStop();
    }

    private String currentChapterId() {
        if (album == null) {
            return "";
        }
        if (!album.readId.isEmpty()) {
            return album.readId;
        }
        return album.chapters.isEmpty() ? "" : album.chapters.get(0).id;
    }

    private Prefs prefs() {
        return site.prefs();
    }

    private void copyTitle() {
        if (album == null) {
            return;
        }
        final String name = album.title == null ? "" : album.title;
        final String code = album.id == null ? "" : album.id;
        final String link = site.base() + "/album/" + code + "/";
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.share)
                .setItems(new String[]{
                        getString(R.string.copy_name),
                        getString(R.string.copy_code),
                        getString(R.string.copy_link),
                        getString(R.string.open_in_browser)
                }, (d, which) -> {
                    if (which == 0) {
                        copyToClip(name, getString(R.string.title_copied));
                    } else if (which == 1) {
                        copyToClip(code, getString(R.string.code_copied));
                    } else if (which == 2) {
                        copyToClip(link, getString(R.string.link_copied));
                    } else {
                        openInBrowser(link);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openInBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception e) {
            Ui.toast(this, this.getString(R.string.browser_open_failed));
        }
    }

    private void copyToClip(String text, String toast) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("text", text));
            Ui.toast(this, toast);
        }
    }

    private void toggleFavorite() {
        if (album == null) {
            return;
        }
        boolean fav = store.isFavorite(album.id);
        if (fav) {
            store.removeFavorite(album.id);
            Ui.toast(this, this.getString(R.string.unfavorited));
        } else {
            store.addFavorite(album);
            Ui.toast(this, this.getString(R.string.favorited));
        }
        adapter.setAlbum(album, !fav);
        adapter.setDownloaded(isDownloaded(album.id));

        if (prefs().getLoggedUser().isEmpty()) {
            return;
        }
        final boolean add = !fav;
        final String id = album.id;
        pool.execute(() -> {
            try {
                site.favorite(id, add);
            } catch (Exception ignored) {
            }
        });
    }

    private void openReader(int startPage) {
        if (album == null) {
            return;
        }
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (Models.Chapter c : album.chapters) {
            ids.add(c.id);
            names.add(c.name);
        }
        if (ids.isEmpty()) {
            String cid = currentChapterId();
            if (cid.isEmpty()) {
                return;
            }
            ids.add(cid);
            names.add(getString(R.string.start_reading));
        }
        Intent i = new Intent(this, ReaderActivity.class);
        i.putExtra("aid", ids.get(0));
        i.putExtra("albumId", album.id);
        i.putExtra("title", album.title);
        i.putExtra("cover", album.cover);
        i.putStringArrayListExtra("chapterIds", ids);
        i.putStringArrayListExtra("chapterNames", names);
        i.putExtra("index", 0);
        i.putExtra("page", startPage);
        startActivity(i);
    }

    // ------------------------------------------------------------ 评论

    private void setupComments() {
        commentsPanel = findViewById(R.id.commentsPanel);
        commentItems = findViewById(R.id.commentItems);
        commentsLoading = findViewById(R.id.commentsLoading);
        commentsEmpty = findViewById(R.id.commentsEmpty);
        btnMoreComments = findViewById(R.id.btnMoreComments);
        btnMoreComments.setPaintFlags(btnMoreComments.getPaintFlags()
                | Paint.UNDERLINE_TEXT_FLAG);
        btnMoreComments.setOnClickListener(v -> renderComments());
        findViewById(R.id.btnCommentsClose).setOnClickListener(v -> closeComments());
    }

    /** 预览图区域右滑进入评论，左滑返回预览。 */
    private void openComments() {
        if (commentsOpen) {
            return;
        }
        commentsOpen = true;
        commentsPanel.setVisibility(View.VISIBLE);
        int w = commentsPanel.getWidth() > 0
                ? commentsPanel.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        commentsPanel.setTranslationX(w);
        commentsPanel.animate().translationX(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        if (!commentsLoaded && !commentsLoadingFlag) {
            loadComments();
        }
    }

    private void closeComments() {
        if (!commentsOpen) {
            return;
        }
        commentsOpen = false;
        int w = commentsPanel.getWidth() > 0
                ? commentsPanel.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        commentsPanel.animate().translationX(w)
                .setDuration(200)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    if (!commentsOpen) {
                        commentsPanel.setVisibility(View.GONE);
                    }
                })
                .start();
    }

    /** 从作品详情页的评论区读取评论。 */
    private void loadComments() {
        commentsLoadingFlag = true;
        commentsLoading.setVisibility(View.VISIBLE);
        final String id = albumId;
        pool.execute(() -> {
            List<String> result = new ArrayList<>();
            try {
                String html = site.get("/album/" + id);
                result = Parsers.parseComments(html);
            } catch (Exception ignored) {
            }
            final List<String> parsed = result;
            runOnUiThread(() -> {
                commentsLoadingFlag = false;
                commentsLoaded = true;
                commentsLoading.setVisibility(View.GONE);
                comments.clear();
                comments.addAll(parsed);
                commentShown = 0;
                commentItems.removeAllViews();
                renderComments();
            });
        });
    }

    /** 每次追加 10 条，未显示完时保留「显示更多评论」。 */
    private void renderComments() {
        if (comments.isEmpty()) {
            commentsEmpty.setVisibility(View.VISIBLE);
            btnMoreComments.setVisibility(View.GONE);
            return;
        }
        commentsEmpty.setVisibility(View.GONE);
        int target = Math.min(commentShown + COMMENT_PAGE, comments.size());
        for (int i = commentShown; i < target; i++) {
            commentItems.addView(commentView(comments.get(i)));
        }
        commentShown = target;
        btnMoreComments.setVisibility(commentShown < comments.size()
                ? View.VISIBLE : View.GONE);
    }

    private TextView commentView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(14f);
        tv.setTextColor(getColor(R.color.on_surface));
        tv.setLineSpacing(dp(4), 1f);
        int pad = dp(12);
        tv.setPadding(pad, pad, pad, pad);
        tv.setBackgroundResource(R.drawable.bg_block);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = ev.getX();
                touchDownY = ev.getY();
                touchTracking = true;
                break;
            case MotionEvent.ACTION_MOVE:
                if (touchTracking) {
                    float dx = ev.getX() - touchDownX;
                    float dy = ev.getY() - touchDownY;
                    if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) * 1.6f) {
                        touchTracking = false;
                        if (dx < 0 && commentsOpen) {
                            closeComments();
                            return true;
                        }
                        if (dx > 0 && !commentsOpen) {
                            openComments();
                            return true;
                        }
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touchTracking = false;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 详情页的左右滑用于切换预览 / 评论，关闭全局右滑返回，避免手势冲突。 */
    @Override
    protected boolean shouldHandleSwipeBack(MotionEvent ev) {
        return false;
    }

    @Override
    public void onBackPressed() {
        if (commentsOpen) {
            closeComments();
            return;
        }
        super.onBackPressed();
    }

    // ------------------------------------------------------------ 下载

    private void refreshDownloadState() {
        if (album == null || adapter == null) {
            return;
        }
        DownloadManager.Task t = DownloadManager.get(this).get(album.id);
        if (t != null && t.state != 3) {
            adapter.setDownloadProgress(t.progress());
        } else {
            adapter.setDownloaded(isDownloaded(album.id));
        }
    }

    private boolean isDownloaded(String id) {
        DownloadManager.Task t = DownloadManager.get(this).get(id);
        return t != null && t.state == 3;
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDownloadState();
    }

    private void downloadChapter() {
        if (album == null) {
            return;
        }
        final String aid = currentChapterId();
        if (aid.isEmpty()) {
            Ui.toast(this, this.getString(R.string.load_failed));
            return;
        }
        if (store.isDownloaded(album.id)) {
            Ui.toast(this, this.getString(R.string.downloaded));
            return;
        }

        TextView message = new TextView(this);
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        message.setPadding(pad, pad, pad, pad);
        message.setText(R.string.download_start);
        Dialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(album.title)
                .setView(message)
                .setNegativeButton(R.string.cancel, null)
                .setCancelable(false)
                .show();

        final Models.Album target = album;
        pool.execute(() -> {
            try {
                String html = site.get("/photo/" + aid);
                Models.ChapterContent cc = Parsers.parseChapter(html, site.base());
                if (cc.aid.isEmpty()) {
                    cc.aid = aid;
                }
                Downloader.download(this, cc, new Downloader.Progress() {
                    @Override
                    public void onProgress(int done, int total) {
                        runOnUiThread(() -> message.setText(
                                getString(R.string.download_progress, done, total)));
                    }

                    @Override
                    public void onDone(boolean ok, String msg) {
                        runOnUiThread(() -> {
                            dialog.dismiss();
                            if (ok) {
                                store.addDownload(target);
                                Ui.toast(DetailActivity.this, DetailActivity.this.getString(R.string.download_done));
                            } else {
                                Ui.toast(DetailActivity.this,
                                        getString(R.string.download_failed) + " " + msg);
                            }
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Ui.toast(DetailActivity.this, DetailActivity.this.getString(R.string.download_failed));
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        DownloadManager.get(this).removeListener(dlListener);
        prefetchCancel.set(true);
        pool.shutdownNow();
        super.onDestroy();
    }
}
