package com.moco.comicsearch;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 纯净阅读器：只显示漫画图片，可切换上下话、下载整话。 */
public class ReaderActivity extends SwipeBackActivity {

    private Site site;
    private Store store;
    private ImageLoader loader;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private RecyclerView pagesView;
    private PageAdapter adapter;
    private View topBar;
    private View bottomBar;
    private View loading;
    private TextView barTitle;
    private TextView counter;
    private android.widget.SeekBar progressBar;
    private MaterialButton btnPrev;
    private MaterialButton btnNext;

    private String aid = "";
    private String albumId = "";
    private String title = "";
    private String cover = "";
    private ImageView btnFav;
    private boolean fav;
    private ArrayList<String> chapterIds = new ArrayList<>();
    private ArrayList<String> chapterNames = new ArrayList<>();
    private int chapterIndex = 0;
    private int localPages = 0;
    private int startPage = 0;
    private Models.ChapterContent content;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        EdgeToEdge.setupDark(this);
        setContentView(R.layout.activity_reader);
        EdgeToEdge.padTop(findViewById(R.id.topBar));
        EdgeToEdge.padBottom(findViewById(R.id.bottomBar));

        site = Site.get(this);
        store = new Store(this);
        loader = ImageLoader.get(this);

        aid = getIntent().getStringExtra("aid");
        albumId = getIntent().getStringExtra("albumId");
        title = getIntent().getStringExtra("title");
        cover = getIntent().getStringExtra("cover");
        ArrayList<String> ids = getIntent().getStringArrayListExtra("chapterIds");
        ArrayList<String> names = getIntent().getStringArrayListExtra("chapterNames");
        chapterIndex = getIntent().getIntExtra("index", 0);
        localPages = getIntent().getIntExtra("localPages", 0);
        startPage = getIntent().getIntExtra("page", 0);
        if (ids != null) {
            chapterIds = ids;
        }
        if (names != null) {
            chapterNames = names;
        }
        if ((aid == null || aid.isEmpty()) && (albumId == null || albumId.isEmpty())) {
            finish();
            return;
        }
        if (aid == null) {
            aid = "";
        }
        if (albumId == null) {
            albumId = "";
        }

        pagesView = findViewById(R.id.pages);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        loading = findViewById(R.id.loading);
        barTitle = findViewById(R.id.barTitle);
        counter = findViewById(R.id.counter);
        btnPrev = findViewById(R.id.btnPrevChapter);
        btnNext = findViewById(R.id.btnNextChapter);
        btnFav = findViewById(R.id.btnFav);
        progressBar = findViewById(R.id.readerProgress);
        progressBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    pagesView.scrollToPosition(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar sb) {
            }

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar sb) {
            }
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        btnPrev.setOnClickListener(v -> switchChapter(chapterIndex - 1));
        btnNext.setOnClickListener(v -> switchChapter(chapterIndex + 1));
        btnFav.setOnClickListener(v -> toggleFavorite());
        refreshFavIcon();

        adapter = new PageAdapter();
        LinearLayoutManager lm = new LinearLayoutManager(this);
        pagesView.setLayoutManager(lm);
        pagesView.setAdapter(adapter);
        pagesView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateCounter();
            }
        });

        barTitle.setText(displayTitle());
        updateChapterButtons();

        if (aid.isEmpty()) {
            resolveAlbum();
        } else {
            load(aid);
        }
    }

    /** 只给了作品 id 时，先取详情拿到章节，再进阅读。 */
    private void resolveAlbum() {
        loading.setVisibility(View.VISIBLE);
        pool.execute(() -> {
            Models.Album album = null;
            String error = null;
            try {
                album = Parsers.parseAlbum(site.get("/album/" + albumId), site.base());
            } catch (Exception e) {
                error = e.getMessage();
            }
            final Models.Album result = album;
            final String err = error;
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                if (result == null || result.chapters.isEmpty()) {
                    Toast.makeText(this, err == null ? getString(R.string.load_failed) : err,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (result.title != null && !result.title.isEmpty()) {
                    title = result.title;
                }
                chapterIds.clear();
                chapterNames.clear();
                for (Models.Chapter c : result.chapters) {
                    chapterIds.add(c.id);
                    chapterNames.add(c.name);
                }
                chapterIndex = 0;
                aid = chapterIds.get(0);
                barTitle.setText(displayTitle());
                updateChapterButtons();
                load(aid);
            });
        });
    }

    private String favKey() {
        return albumId.isEmpty() ? aid : albumId;
    }

    private void refreshFavIcon() {
        fav = store.isFavorite(favKey());
        if (btnFav != null) {
            btnFav.setImageResource(R.drawable.ic_favorite);
            btnFav.setColorFilter(fav ? 0xFFFFFFFF : 0x66FFFFFF);
        }
    }

    private void toggleFavorite() {
        String key = favKey();
        if (store.isFavorite(key)) {
            store.removeFavorite(key);
            Toast.makeText(this, R.string.unfavorited, Toast.LENGTH_SHORT).show();
        } else {
            Models.Album a = new Models.Album();
            a.id = key;
            a.title = title == null ? "" : title;
            a.readId = aid;
            store.addFavorite(a);
            Toast.makeText(this, R.string.favorited, Toast.LENGTH_SHORT).show();
        }
        refreshFavIcon();
        if (!site.prefs().getLoggedUser().isEmpty()) {
            final boolean add = !fav;
            pool.execute(() -> {
                try {
                    site.favorite(key, add);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private String displayTitle() {
        if (chapterNames.size() > chapterIndex && chapterIndex >= 0) {
            String n = chapterNames.get(chapterIndex);
            if (n != null && !n.isEmpty() && !n.equals("开始阅读")) {
                return n;
            }
        }
        return title == null ? "" : title;
    }

    private void updateChapterButtons() {
        boolean multi = chapterIds.size() > 1;
        btnPrev.setVisibility(multi ? View.VISIBLE : View.GONE);
        btnNext.setVisibility(multi ? View.VISIBLE : View.GONE);
        btnPrev.setEnabled(chapterIndex > 0);
        btnNext.setEnabled(chapterIndex < chapterIds.size() - 1);
        btnPrev.setAlpha(chapterIndex > 0 ? 1f : 0.4f);
        btnNext.setAlpha(chapterIndex < chapterIds.size() - 1 ? 1f : 0.4f);
    }

    private void switchChapter(int index) {
        if (index < 0 || index >= chapterIds.size() || index == chapterIndex) {
            return;
        }
        chapterIndex = index;
        aid = chapterIds.get(index);
        barTitle.setText(displayTitle());
        updateChapterButtons();
        pagesView.scrollToPosition(0);
        load(aid);
    }

    private void load(String chapterId) {
        loading.setVisibility(View.VISIBLE);
        adapter.clear();

        // 已下载：直接用本地图片，不需要联网
        if (localPages > 0 && Downloader.isComplete(this, chapterId, localPages)) {
            Models.ChapterContent local = new Models.ChapterContent();
            local.aid = chapterId;
            for (int i = 0; i < localPages; i++) {
                local.pages.add(new Models.PageImage("",
                        String.format(java.util.Locale.ROOT, "%05d.jpg", i + 1)));
            }
            content = local;
            adapter.setData(local);
            loading.setVisibility(View.GONE);
            applyStartPage();
            updateCounter();
            recordHistory(chapterId);
            return;
        }

        // 有章节缓存先用缓存离线阅读，再后台刷新
        Models.ChapterContent cached = store.getCachedChapter(chapterId);
        if (cached != null) {
            content = cached;
            adapter.setData(cached);
            loading.setVisibility(View.GONE);
            applyStartPage();
            updateCounter();
        }

        pool.execute(() -> {
            Models.ChapterContent c = null;
            String error = null;
            try {
                String html = site.get("/photo/" + chapterId);
                c = Parsers.parseChapter(html, site.base());
                if (c.aid.isEmpty()) {
                    c.aid = chapterId;
                }
            } catch (Exception e) {
                error = e.getMessage();
            }
            final Models.ChapterContent result = c;
            final String err = error;
            runOnUiThread(() -> {
                loading.setVisibility(View.GONE);
                if (result == null || result.pages.isEmpty()) {
                    Toast.makeText(this, err == null ? getString(R.string.load_failed) : err,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                store.saveChapter(chapterId, result);
                content = result;
                adapter.setData(result);
                applyStartPage();
                updateCounter();
                recordHistory(result.aid);
            });
        });
    }

    /** 记录阅读历史：只存元信息，图片仍走 ImageLoader 的磁盘缓存。 */
    private void recordHistory(String chapterId) {
        if (chapterId == null || chapterId.isEmpty()) {
            return;
        }
        Models.History h = new Models.History();
        h.id = (albumId == null || albumId.isEmpty()) ? chapterId : albumId;
        h.title = title == null ? "" : title;
        h.cover = cover == null ? "" : cover;
        h.readId = chapterId;
        h.chapterName = displayTitle();
        h.index = chapterIndex;
        h.time = System.currentTimeMillis();
        for (int i = 0; i < chapterIds.size(); i++) {
            Models.Chapter c = new Models.Chapter();
            c.id = chapterIds.get(i);
            c.name = i < chapterNames.size() ? chapterNames.get(i) : "";
            h.chapters.add(c);
        }
        store.addHistory(h);
    }

    /** 从详情页预览图进入时，直接跳到对应页。 */
    private void applyStartPage() {
        if (startPage <= 0 || adapter.getItemCount() == 0) {
            return;
        }
        int pos = Math.min(startPage, adapter.getItemCount() - 1);
        RecyclerView.LayoutManager lm = pagesView.getLayoutManager();
        if (lm instanceof LinearLayoutManager) {
            ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, 0);
        } else {
            pagesView.scrollToPosition(pos);
        }
        startPage = 0;
    }

    private void updateCounter() {
        if (adapter.getItemCount() == 0) {
            counter.setText("");
            return;
        }
        int first = ((LinearLayoutManager) pagesView.getLayoutManager())
                .findFirstVisibleItemPosition();
        counter.setText(getString(R.string.page_of, Math.max(first + 1, 1), adapter.getItemCount()));
        if (progressBar != null) {
            progressBar.setMax(Math.max(adapter.getItemCount() - 1, 0));
            if (!progressBar.isPressed()) {
                progressBar.setProgress(Math.max(first, 0));
            }
        }
    }

    private void toggleBars() {
        boolean show = topBar.getVisibility() != View.VISIBLE;
        topBar.setVisibility(show ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onBackPressed() {
        if (topBar.getVisibility() == View.VISIBLE) {
            toggleBars();
            return;
        }
        super.onBackPressed();
    }

    /** 底部进度条 / 章节栏区域不触发整页返回手势，避免拖动进度条时误退出。 */
    @Override
    protected boolean shouldHandleSwipeBack(MotionEvent ev) {
        if (bottomBar != null && bottomBar.getVisibility() == View.VISIBLE) {
            int[] loc = new int[2];
            bottomBar.getLocationOnScreen(loc);
            float y = ev.getRawY();
            if (y >= loc[1] && y <= loc[1] + bottomBar.getHeight()) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------ 列表

    private class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {

        private final List<Models.PageImage> pages = new ArrayList<>();
        private String aid = "";
        private int scrambleId = 0;

        void setData(Models.ChapterContent c) {
            pages.clear();
            pages.addAll(c.pages);
            aid = c.aid;
            scrambleId = c.scrambleId;
            notifyDataSetChanged();
        }

        void clear() {
            pages.clear();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_page, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Models.PageImage page = pages.get(position);
            int screenW = getResources().getDisplayMetrics().widthPixels;
            ViewGroup.LayoutParams lp = h.image.getLayoutParams();
            lp.height = (int) (screenW * 1.4f);
            h.image.setLayoutParams(lp);
            h.image.setImageBitmap(null);

            final String key = aid + "/" + page.name;
            h.image.setTag(key);

            // 先显示不确定进度环，拿到 content-length 后切换为确定进度
            h.progress.setIndeterminate(true);
            h.progress.setVisibility(View.VISIBLE);

            File local = Downloader.pageFile(ReaderActivity.this, aid, position);
            loader.load(page, aid, scrambleId, local, new ImageLoader.Callback() {
                @Override
                public void onLoaded(Bitmap bitmap) {
                    if (!key.equals(h.image.getTag())) {
                        return;
                    }
                    h.progress.setVisibility(View.GONE);
                    h.image.setImageBitmap(bitmap);
                    int w = bitmap.getWidth();
                    int ht = bitmap.getHeight();
                    if (w > 0) {
                        ViewGroup.LayoutParams p = h.image.getLayoutParams();
                        p.height = (int) (screenW * (float) ht / w);
                        h.image.setLayoutParams(p);
                    }
                }

                @Override
                public void onError(Exception e) {
                    if (key.equals(h.image.getTag())) {
                        h.progress.setVisibility(View.GONE);
                    }
                }

                @Override
                public void onProgress(int percent) {
                    if (!key.equals(h.image.getTag())) {
                        return;
                    }
                    if (h.progress.isIndeterminate()) {
                        h.progress.setIndeterminate(false);
                    }
                    h.progress.setProgressCompat(percent, true);
                    h.progress.setVisibility(View.VISIBLE);
                }
            });

            h.itemView.setOnClickListener(v -> toggleBars());
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        class VH extends RecyclerView.ViewHolder {
            ImageView image;
            CircularProgressIndicator progress;

            VH(@NonNull View v) {
                super(v);
                image = v.findViewById(R.id.page);
                progress = v.findViewById(R.id.pageProgress);
            }
        }
    }
}
