package com.moco.comicsearch;

import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 缓存管理：统一列表 / 封面展示，支持多选与删除。 */
public class CacheActivity extends SwipeBackActivity {

    private ImageLoader loader;
    private Store store;
    private AlbumAdapter adapter;
    private View emptyView;
    private TextView titleView;
    private View selectionBar;
    private ImageView btnSelAll;
    private View btnDelete;

    private final List<Models.Album> current = new ArrayList<>();
    private final Map<String, String> idToAid = new HashMap<>();
    private final Set<String> selectedIds = new HashSet<>();
    private boolean selectionMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        EdgeToEdge.setup(this);
        setContentView(R.layout.activity_cache);
        EdgeToEdge.padTop(findViewById(R.id.header));
        EdgeToEdge.padBottom(findViewById(R.id.root));

        loader = ImageLoader.get(this);
        store = new Store(this);

        emptyView = findViewById(R.id.emptyView);
        ((TextView) findViewById(R.id.emptyTitle)).setText(R.string.no_cache);
        ((TextView) findViewById(R.id.emptyMsg)).setText(R.string.no_cache_msg);
        titleView = findViewById(R.id.cacheTitle);
        selectionBar = findViewById(R.id.cacheSelectionBar);
        btnSelAll = findViewById(R.id.btnCacheSelAll);
        btnDelete = findViewById(R.id.btnCacheDelete);

        RecyclerView list = findViewById(R.id.list);
        boolean grid = new Prefs(this).getViewMode() == 1;
        list.setLayoutManager(grid
                ? new GridLayoutManager(this, 2) : new LinearLayoutManager(this));

        adapter = new AlbumAdapter(loader, new AlbumAdapter.Listener() {
            @Override
            public void onClick(Models.Album album) {
                if (selectionMode) {
                    toggleSelection(album);
                    return;
                }
                Intent i = new Intent(CacheActivity.this, DetailActivity.class);
                i.putExtra("id", album.id);
                i.putExtra("title", album.title);
                i.putExtra("cover", album.cover);
                startActivity(i);
            }

            @Override
            public void onCopy(Models.Album album) {
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
                String aid = album.readId == null || album.readId.isEmpty() ? album.id : album.readId;
                loader.clearAlbum(aid);
                Ui.toast(CacheActivity.this, CacheActivity.this.getString(R.string.cache_cleared_album));
                load();
            }

            @Override
            public void onDownload(Models.Album album) {
                DownloadManager dm = DownloadManager.get(CacheActivity.this);
                String chapterId = album.readId == null || album.readId.isEmpty()
                        ? album.id : album.readId;
                dm.enqueue(album, chapterId);
                dm.start(album.id);
                Ui.toast(CacheActivity.this, CacheActivity.this.getString(R.string.download_start));
            }
        });
        adapter.setShowTrash(true);
        adapter.setShowDownload(true);
        adapter.setGrid(grid);
        list.setAdapter(adapter);

        list.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                if (selectionMode && e.getAction() == MotionEvent.ACTION_DOWN
                        && rv.findChildViewUnder(e.getX(), e.getY()) == null) {
                    exitSelection();
                    return true;
                }
                return false;
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (selectionMode) {
                exitSelection();
            } else {
                finish();
            }
        });
        btnSelAll.setOnClickListener(v -> toggleSelectAll());
        btnDelete.setOnClickListener(v -> confirmDeleteSelected());

        load();
    }

    private void load() {
        Map<String, Long> cached = loader.cachedAlbums();
        Map<String, Models.Album> known = new HashMap<>();
        for (Models.Album a : store.favorites()) {
            known.put(key(a), a);
        }
        for (Models.History h : store.history()) {
            Models.Album a = new Models.Album();
            a.id = h.id;
            a.title = h.title;
            a.cover = h.cover;
            a.readId = h.readId;
            known.put(h.readId.isEmpty() ? h.id : h.readId, a);
        }
        // 缓存里保存过完整元信息的作品（含封面、标题、标签）
        for (Models.Album a : store.allCachedAlbums()) {
            if (!a.readId.isEmpty()) {
                known.put(a.readId, a);
            }
            known.put(a.id, a);
        }

        List<Models.Album> out = new ArrayList<>();
        final Map<String, Long> sizes = new HashMap<>();
        idToAid.clear();
        DownloadManager dm = DownloadManager.get(this);
        for (Map.Entry<String, Long> e : cached.entrySet()) {
            String aid = e.getKey();
            if (dm.isDownloadedChapter(aid)) {
                continue;
            }
            Models.Album a = known.get(aid);
            if (a == null) {
                a = new Models.Album();
                a.id = aid;
                a.title = aid;
            }
            if (a.id == null || a.id.isEmpty()) {
                a.id = aid;
            }
            if (a.title == null || a.title.isEmpty()) {
                a.title = aid;
            }
            a.readId = aid;
            a.extra = String.format(Locale.ROOT, "%.1f MB", e.getValue() / 1024f / 1024f);
            sizes.put(aid, e.getValue());
            idToAid.put(a.id, aid);
            out.add(a);
        }
        Collections.sort(out, new Comparator<Models.Album>() {
            @Override
            public int compare(Models.Album x, Models.Album y) {
                long a = sizes.get(x.readId) == null ? 0 : sizes.get(x.readId);
                long b = sizes.get(y.readId) == null ? 0 : sizes.get(y.readId);
                return Long.compare(b, a);
            }
        });

        current.clear();
        current.addAll(out);
        adapter.submit(out);
        emptyView.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
        if (selectionMode) {
            selectedIds.retainAll(idSet());
            if (selectedIds.isEmpty()) {
                selectionMode = false;
            }
            applySelectionUi();
        }
    }

    private Set<String> idSet() {
        Set<String> ids = new HashSet<>();
        for (Models.Album a : current) {
            ids.add(a.id);
        }
        return ids;
    }

    private String key(Models.Album a) {
        return a.readId == null || a.readId.isEmpty() ? a.id : a.readId;
    }

    private void enterSelection(Models.Album album) {
        selectionMode = true;
        selectedIds.clear();
        selectedIds.add(album.id);
        applySelectionUi();
    }

    private void toggleSelection(Models.Album album) {
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

    private boolean allSelected() {
        return !current.isEmpty() && selectedIds.size() == current.size();
    }

    private void toggleSelectAll() {
        if (allSelected()) {
            selectedIds.clear();
        } else {
            selectedIds.clear();
            for (Models.Album a : current) {
                selectedIds.add(a.id);
            }
        }
        applySelectionUi();
    }

    private void applySelectionUi() {
        adapter.setSelectionMode(selectionMode);
        adapter.setSelectedIds(selectedIds);
        selectionBar.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        if (selectionMode) {
            titleView.setText(getString(R.string.selected_count, selectedIds.size()));
            boolean all = allSelected();
            btnSelAll.setImageResource(all
                    ? R.drawable.ic_deselect_all : R.drawable.ic_select_all);
            btnSelAll.setBackgroundResource(all
                    ? R.drawable.bg_circle_surface : R.drawable.bg_circle_primary);
            btnSelAll.setColorFilter(all
                    ? ThemeManager.primary(this)
                    : ContextCompat.getColor(this, R.color.white));
            btnSelAll.setContentDescription(getString(
                    all ? R.string.deselect_all : R.string.select_all));
        } else {
            titleView.setText(R.string.cache_manage);
        }
    }

    private void confirmDeleteSelected() {
        if (selectedIds.isEmpty()) {
            return;
        }
        final int count = selectedIds.size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_cache)
                .setMessage(getString(R.string.confirm_delete_selected, count))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    for (String id : new ArrayList<>(selectedIds)) {
                        String aid = idToAid.get(id);
                        if (aid != null) {
                            loader.clearAlbum(aid);
                        }
                    }
                    Ui.toast(this, this.getString(R.string.cache_cleared_album));
                    exitSelection();
                    load();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (selectionMode) {
            exitSelection();
            return;
        }
        super.onBackPressed();
    }
}
