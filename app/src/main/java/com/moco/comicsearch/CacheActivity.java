package com.moco.comicsearch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 缓存管理：按作品列出已缓存的图片，支持逐个清除或全部清空。 */
public class CacheActivity extends AppCompatActivity {

    private ImageLoader loader;
    private Store store;
    private AlbumAdapter adapter;
    private View emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cache);

        loader = ImageLoader.get(this);
        store = new Store(this);

        emptyView = findViewById(R.id.emptyView);
        ((TextView) findViewById(R.id.emptyTitle)).setText(R.string.no_cache);
        ((TextView) findViewById(R.id.emptyMsg)).setText(R.string.no_cache_msg);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlbumAdapter(loader, new AlbumAdapter.Listener() {
            @Override
            public void onClick(Models.Album album) {
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
                confirmClear(album);
            }

            @Override
            public void onTrash(Models.Album album) {
                // 垃圾桶：点击直接删除该作品缓存
                String aid = album.readId == null || album.readId.isEmpty() ? album.id : album.readId;
                loader.clearAlbum(aid);
                Toast.makeText(CacheActivity.this, R.string.cache_cleared_album,
                        Toast.LENGTH_SHORT).show();
                load();
            }
        });
        adapter.setShowTrash(true);
        list.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearAll).setOnClickListener(v -> confirmClearAll());

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

        List<Models.Album> out = new ArrayList<>();
        final Map<String, Long> sizes = new HashMap<>();
        for (Map.Entry<String, Long> e : cached.entrySet()) {
            String aid = e.getKey();
            Models.Album a = known.get(aid);
            if (a == null) {
                a = new Models.Album();
                a.id = aid;
                a.title = aid;
                a.readId = aid;
            }
            a.extra = String.format(Locale.ROOT, "%.1f MB", e.getValue() / 1024f / 1024f);
            sizes.put(aid, e.getValue());
            out.add(a);
        }
        Collections.sort(out, new Comparator<Models.Album>() {
            @Override
            public int compare(Models.Album x, Models.Album y) {
                long a = sizes.get(x.readId.isEmpty() ? x.id : x.readId) == null ? 0
                        : sizes.get(x.readId.isEmpty() ? x.id : x.readId);
                long b = sizes.get(y.readId.isEmpty() ? y.id : y.readId) == null ? 0
                        : sizes.get(y.readId.isEmpty() ? y.id : y.readId);
                return Long.compare(b, a);
            }
        });

        adapter.submit(out);
        emptyView.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String key(Models.Album a) {
        return a.readId == null || a.readId.isEmpty() ? a.id : a.readId;
    }

    private void confirmClear(Models.Album album) {
        String aid = album.readId == null || album.readId.isEmpty() ? album.id : album.readId;
        String name = album.title == null || album.title.isEmpty() ? aid : album.title;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_cache)
                .setMessage(getString(R.string.confirm_clear_album, name))
                .setPositiveButton(R.string.remove, (d, w) -> {
                    loader.clearAlbum(aid);
                    Toast.makeText(this, R.string.cache_cleared_album, Toast.LENGTH_SHORT).show();
                    load();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmClearAll() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_all_cache)
                .setMessage(R.string.clear_cache)
                .setPositiveButton(R.string.remove, (d, w) -> {
                    loader.clearAll();
                    Toast.makeText(this, R.string.cache_cleared_all, Toast.LENGTH_SHORT).show();
                    load();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
