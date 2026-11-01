package com.moco.comicsearch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SmokeTest {

    @Test
    public void main_activity_inflates_and_switches_tabs() {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        Activity a = c.get();

        assertNotNull(a.findViewById(R.id.list));
        assertNotNull(a.findViewById(R.id.searchInput));
        assertNotNull(a.findViewById(R.id.profileView));
        assertNotNull(a.findViewById(R.id.settingsView));
        assertEquals(View.GONE, a.findViewById(R.id.profileView).getVisibility());

        a.findViewById(R.id.navProfile).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.profileView).getVisibility());

        a.findViewById(R.id.navFav).performClick();
        assertEquals(View.GONE, a.findViewById(R.id.profileView).getVisibility());

        TextView emptyTitle = a.findViewById(R.id.emptyTitle);
        assertEquals(a.getString(R.string.empty_fav), emptyTitle.getText().toString());

        a.findViewById(R.id.navSettings).performClick();
        assertEquals(View.VISIBLE, a.findViewById(R.id.settingsView).getVisibility());

        c.pause().stop().destroy();
    }

    @Test
    public void mirror_row_has_radio_on_left() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View row = a.getLayoutInflater().inflate(R.layout.item_mirror, null, false);
        assertNotNull("缺少单选圆框", row.findViewById(R.id.mirrorRadio));
        assertNotNull("缺少线路文字", row.findViewById(R.id.mirrorLabel));
    }

    @Test
    @Config(sdk = 34, qualifiers = "night")
    public void dark_mode_inflates() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertNotNull(a.findViewById(R.id.list));
        assertNotNull(a.findViewById(R.id.webView));
    }

    @Test
    public void read_history_roundtrip() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        Store s = new Store(a);
        s.clearReadHistory();

        Models.History h = new Models.History();
        h.id = "1480638";
        h.title = "测试漫画";
        h.readId = "1480638";
        h.chapterName = "第 1 话";
        h.index = 0;
        h.time = System.currentTimeMillis();
        Models.Chapter c = new Models.Chapter();
        c.id = "1480638";
        c.name = "第 1 话";
        h.chapters.add(c);

        s.addHistory(h);
        assertEquals(1, s.history().size());
        assertEquals("1480638", s.history().get(0).readId);
        assertEquals("第 1 话", s.history().get(0).chapterName);
        assertEquals(1, s.history().get(0).chapters.size());

        // 同一作品重复记录只保留一条
        s.addHistory(h);
        assertEquals(1, s.history().size());

        s.removeHistory("1480638");
        assertEquals(0, s.history().size());
    }

    @Test
    public void settings_activity_inflates() {
        ActivityController<SettingsActivity> c =
                Robolectric.buildActivity(SettingsActivity.class).setup();
        Activity a = c.get();
        assertNotNull(a.findViewById(R.id.rowTheme));
        assertNotNull(a.findViewById(R.id.rowViewMode));
        assertNotNull(a.findViewById(R.id.rowSite));
        assertNotNull(a.findViewById(R.id.rowMirror));
        assertNotNull(a.findViewById(R.id.rowHomeRecommend));
        assertNotNull(a.findViewById(R.id.rowCacheManage));
        assertNotNull(a.findViewById(R.id.rowClearRecords));
        assertNotNull(a.findViewById(R.id.rowExport));
        assertNotNull(a.findViewById(R.id.rowImport));
        c.pause().stop().destroy();
    }

    @Test
    public void night_mode_mapping() {
        assertEquals(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                App.toNightMode(0));
        assertEquals(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO, App.toNightMode(1));
        assertEquals(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES, App.toNightMode(2));
    }

    @Test
    public void album_grid_adapter_binds() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Models.Album album = new Models.Album();
        album.id = "1";
        album.title = "封面测试";

        AlbumAdapter adapter = new AlbumAdapter(ImageLoader.get(activity),
                new AlbumAdapter.Listener() {
                    @Override
                    public void onClick(Models.Album a) {
                    }

                    @Override
                    public void onCopy(Models.Album a) {
                    }

                    @Override
                    public void onLongClick(Models.Album a) {
                    }
                });
        adapter.setGrid(true);
        adapter.submit(java.util.Collections.singletonList(album));

        androidx.recyclerview.widget.RecyclerView parent =
                new androidx.recyclerview.widget.RecyclerView(activity);
        parent.setLayoutManager(
                new androidx.recyclerview.widget.GridLayoutManager(activity, 2));
        int type = adapter.getItemViewType(0);
        AlbumAdapter.VH holder = adapter.onCreateViewHolder(parent, type);
        adapter.onBindViewHolder(holder, 0);

        TextView title = holder.itemView.findViewById(R.id.title);
        assertEquals("封面测试", title.getText().toString());
        assertNotNull(holder.itemView.findViewById(R.id.cover));
    }

    @Test
    public void settings_dialog_inflates() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        View content = a.getLayoutInflater().inflate(R.layout.dialog_settings, null, false);
        assertNotNull("缺少地址输入框", content.findViewById(R.id.inputBase));
        assertNotNull("缺少分流下拉按钮", content.findViewById(R.id.btnMirrorDrop));
        assertNotNull("缺少原网页开关", content.findViewById(R.id.swWebMode));
        // 抽屉里的「设置」入口存在
        assertNotNull("缺少侧边栏设置入口", a.findViewById(R.id.navSettings));
    }

    @Test
    public void detail_activity_inflates() {
        Intent i = new Intent(org.robolectric.RuntimeEnvironment.getApplication(), DetailActivity.class);
        i.putExtra("id", "1480638");
        i.putExtra("title", "TITLE");
        i.putExtra("cover", "");
        ActivityController<DetailActivity> c =
                Robolectric.buildActivity(DetailActivity.class, i).setup();
        Activity a = c.get();
        assertNotNull(a.findViewById(R.id.content));
        assertNotNull(a.findViewById(R.id.barTitle));
        assertEquals("TITLE", ((TextView) a.findViewById(R.id.barTitle)).getText().toString());
        c.pause().stop().destroy();
    }

    @Test
    public void detail_header_has_fav_read_and_copy() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        DetailAdapter adapter = new DetailAdapter(ImageLoader.get(a),
                new DetailAdapter.Listener() {
                    @Override
                    public void onCopy() {
                    }

                    @Override
                    public void onFavorite() {
                    }

                    @Override
                    public void onRead() {
                    }

                    @Override
                    public void onDownload() {
                    }

                    @Override
                    public void onPageClick(int index) {
                    }
                });
        Models.Album album = new Models.Album();
        album.id = "1";
        album.title = "详情标题";
        adapter.setAlbum(album, false);

        androidx.recyclerview.widget.RecyclerView parent =
                new androidx.recyclerview.widget.RecyclerView(a);
        parent.setLayoutManager(
                new androidx.recyclerview.widget.GridLayoutManager(a, 3));
        androidx.recyclerview.widget.RecyclerView.ViewHolder h = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(h, 0);

        assertNotNull(h.itemView.findViewById(R.id.btnFav));
        assertNotNull(h.itemView.findViewById(R.id.btnRead));
        assertNotNull(h.itemView.findViewById(R.id.btnDownload));
        assertNotNull("缺少复制按钮", h.itemView.findViewById(R.id.btnCopy));
        assertEquals("详情标题",
                ((TextView) h.itemView.findViewById(R.id.title)).getText().toString());
    }

    @Test
    public void reader_activity_inflates() {
        Intent i = new Intent(org.robolectric.RuntimeEnvironment.getApplication(), ReaderActivity.class);
        i.putExtra("aid", "1480638");
        i.putExtra("title", "TITLE");
        ActivityController<ReaderActivity> c =
                Robolectric.buildActivity(ReaderActivity.class, i).setup();
        Activity a = c.get();
        assertNotNull(a.findViewById(R.id.pages));
        assertEquals(View.GONE, a.findViewById(R.id.topBar).getVisibility());
        c.pause().stop().destroy();
    }

    @Test
    public void login_activity_inflates() {
        ActivityController<LoginActivity> c = Robolectric.buildActivity(LoginActivity.class).setup();
        Activity a = c.get();
        assertNotNull(a.findViewById(R.id.inputUser));
        assertNotNull(a.findViewById(R.id.inputPass));
        assertTrue(a.findViewById(R.id.searchInput) == null);
        c.pause().stop().destroy();
    }

    @Test
    public void numeric_keyword_is_treated_as_album_id() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        assertEquals("1464578", a.asAlbumId("1464578"));
        assertEquals("1464578", a.asAlbumId(" 1464578 "));
        assertEquals("1464578", a.asAlbumId("JM1464578"));
        assertEquals("1464578", a.asAlbumId("jm1464578"));
        assertEquals("", a.asAlbumId("海贼王"));
        assertEquals("", a.asAlbumId("123"));
        assertEquals("", a.asAlbumId("14645a78"));
    }

    @Test
    public void adapter_renders_album_item() {
        MainActivity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Models.Album album = new Models.Album();
        album.id = "1";
        album.title = "测试漫画";
        album.author = "作者";
        album.tags.add("标签A");
        album.likes = "12";

        AlbumAdapter adapter = new AlbumAdapter(ImageLoader.get(activity),
                new AlbumAdapter.Listener() {
                    @Override
                    public void onClick(Models.Album a) {
                    }

                    @Override
                    public void onCopy(Models.Album a) {
                    }

                    @Override
                    public void onLongClick(Models.Album a) {
                    }
                });
        adapter.submit(java.util.Collections.singletonList(album));
        androidx.recyclerview.widget.RecyclerView parent =
                new androidx.recyclerview.widget.RecyclerView(activity);
        parent.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(activity));
        AlbumAdapter.VH holder = adapter.onCreateViewHolder(parent, 0);
        adapter.onBindViewHolder(holder, 0);

        TextView title = holder.itemView.findViewById(R.id.title);
        assertEquals("测试漫画", title.getText().toString());
        TextView meta = holder.itemView.findViewById(R.id.meta);
        assertTrue(meta.getText().toString().contains("1"));
        assertNotNull(holder.itemView.findViewById(R.id.cover));
    }
}
