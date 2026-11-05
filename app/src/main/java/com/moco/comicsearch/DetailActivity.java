package com.moco.comicsearch;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
