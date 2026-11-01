package com.moco.comicsearch;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        setContentView(R.layout.activity_detail);

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

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        barTitle.setText(fallbackTitle == null ? getString(R.string.detail) : fallbackTitle);
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
                    a.cover = fallbackCover == null ? "" : fallbackCover;
                }
                runOnUiThread(() -> {
                    album = a;
                    barTitle.setText(a.title.isEmpty() ? getString(R.string.detail) : a.title);
                    adapter.setAlbum(a, store.isFavorite(a.id));
                    loadPages();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.load_failed,
                        Toast.LENGTH_SHORT).show());
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
        pool.execute(() -> {
            Models.ChapterContent c = null;
            try {
                String html = site.get("/photo/" + chapterId);
                c = Parsers.parseChapter(html, site.base());
                if (c.aid.isEmpty()) {
                    c.aid = chapterId;
                }
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
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.copy_title)
                .setItems(new String[]{getString(R.string.copy_name), getString(R.string.copy_code)},
                        (d, which) -> {
                            if (which == 0) {
                                copyToClip(name, getString(R.string.title_copied));
                            } else {
                                copyToClip(code, getString(R.string.code_copied));
                            }
                        })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void copyToClip(String text, String toast) {
        if (text == null || text.isEmpty()) {
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("text", text));
            Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFavorite() {
        if (album == null) {
            return;
        }
        boolean fav = store.isFavorite(album.id);
        if (fav) {
            store.removeFavorite(album.id);
            Toast.makeText(this, R.string.unfavorited, Toast.LENGTH_SHORT).show();
        } else {
            store.addFavorite(album);
            Toast.makeText(this, R.string.favorited, Toast.LENGTH_SHORT).show();
        }
        adapter.setAlbum(album, !fav);

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

    private void downloadChapter() {
        if (album == null) {
            return;
        }
        final String aid = currentChapterId();
        if (aid.isEmpty()) {
            Toast.makeText(this, R.string.load_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (store.isDownloaded(album.id)) {
            Toast.makeText(this, R.string.downloaded, Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(DetailActivity.this, R.string.download_done,
                                        Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(DetailActivity.this,
                                        getString(R.string.download_failed) + " " + msg,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(DetailActivity.this, R.string.download_failed,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        prefetchCancel.set(true);
        pool.shutdownNow();
        super.onDestroy();
    }
}
