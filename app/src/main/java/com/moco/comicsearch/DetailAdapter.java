package com.moco.comicsearch;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.List;

/** 详情页：头部信息 + 三列图片墙。 */
public class DetailAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PAGE = 1;

    public interface Listener {
        void onCopy();

        void onFavorite();

        void onRead();

        void onDownload();

        void onPageClick(int index);
    }

    private final ImageLoader loader;
    private final Listener listener;

    private Models.Album album;
    private List<Models.PageImage> pages = new ArrayList<>();
    private String aid = "";
    private int scrambleId = 0;
    private boolean fav = false;
    private boolean downloaded = false;

    public DetailAdapter(ImageLoader loader, Listener listener) {
        this.loader = loader;
        this.listener = listener;
    }

    public void setAlbum(Models.Album album, boolean fav) {
        this.album = album;
        this.fav = fav;
        notifyDataSetChanged();
    }

    public void setDownloaded(boolean downloaded) {
        if (this.downloaded != downloaded) {
            this.downloaded = downloaded;
            notifyItemChanged(0);
        }
    }

    public void setPages(Models.ChapterContent content) {
        pages = content == null ? new ArrayList<>() : content.pages;
        aid = content == null ? "" : content.aid;
        scrambleId = content == null ? 0 : content.scrambleId;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return (album == null ? 0 : 1) + pages.size();
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? TYPE_HEADER : TYPE_PAGE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderVH(inf.inflate(R.layout.item_detail_header, parent, false));
        }
        return new PageVH(inf.inflate(R.layout.item_detail_page, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        if (h instanceof HeaderVH) {
            bindHeader((HeaderVH) h);
        } else {
            bindPage((PageVH) h, position - 1);
        }
    }

    private void bindHeader(HeaderVH h) {
        Models.Album a = album;
        if (a == null) {
            return;
        }
        h.title.setText(a.title);
        h.author.setText(a.author);
        h.author.setVisibility(a.author.isEmpty() ? View.GONE : View.VISIBLE);

        StringBuilder meta = new StringBuilder();
        if (!a.pages.isEmpty()) {
            meta.append(a.pages).append('p');
        }
        if (!a.id.isEmpty()) {
            if (meta.length() > 0) {
                meta.append(" · ");
            }
            meta.append(a.id);
        }
        h.meta.setText(meta);

        h.tags.removeAllViews();
        for (String tag : a.tags) {
            h.tags.addView(chip(h.tags, tag));
        }

        h.cover.setImageBitmap(null);
        final String url = a.cover;
        h.cover.setTag(url);
        if (!url.isEmpty()) {
            loader.loadCover(url, new ImageLoader.Callback() {
                @Override
                public void onLoaded(Bitmap bitmap) {
                    if (url.equals(h.cover.getTag())) {
                        h.cover.setImageBitmap(bitmap);
                    }
                }

                @Override
                public void onError(Exception e) {
                }
            });
        }

        h.btnFav.setColorFilter(fav
                ? ThemeManager.primary(h.itemView.getContext())
                : androidx.core.content.ContextCompat.getColor(
                        h.itemView.getContext(), R.color.muted));
        h.btnCopy.setOnClickListener(v -> listener.onCopy());
        h.btnFav.setOnClickListener(v -> listener.onFavorite());
        h.btnRead.setOnClickListener(v -> listener.onRead());
        h.btnDownload.setImageResource(downloaded ? R.drawable.ic_check : R.drawable.ic_download);
        h.btnDownload.setContentDescription(h.itemView.getContext().getString(
                downloaded ? R.string.downloaded : R.string.download));
        h.btnDownload.setOnClickListener(v -> listener.onDownload());
    }

    private void bindPage(PageVH h, int index) {
        if (index < 0 || index >= pages.size()) {
            return;
        }
        Models.PageImage page = pages.get(index);

        int screenW = h.itemView.getResources().getDisplayMetrics().widthPixels;
        int cellW = screenW / 3 - dp(h.itemView, 6);
        ViewGroup.LayoutParams lp = h.itemView.getLayoutParams();
        if (lp != null) {
            lp.height = (int) (cellW * 4f / 3f);
            h.itemView.setLayoutParams(lp);
        }

        final String key = aid + "/" + page.name;
        h.thumb.setTag(key);
        h.thumb.setImageBitmap(null);
        h.progress.setVisibility(View.VISIBLE);

        final int pos = index;
        loader.loadThumb(page, aid, scrambleId, cellW, new ImageLoader.Callback() {
            @Override
            public void onLoaded(Bitmap bitmap) {
                if (!key.equals(h.thumb.getTag())) {
                    return;
                }
                h.progress.setVisibility(View.GONE);
                h.thumb.setImageBitmap(bitmap);
            }

            @Override
            public void onError(Exception e) {
                if (key.equals(h.thumb.getTag())) {
                    h.progress.setVisibility(View.GONE);
                }
            }
        });

        h.itemView.setOnClickListener(v -> listener.onPageClick(pos));
    }

    private TextView chip(ViewGroup parent, String text) {
        TextView tv = new TextView(parent.getContext());
        tv.setText(text);
        tv.setTextSize(10);
        tv.setTextColor(parent.getContext().getColor(R.color.muted));
        tv.setBackgroundResource(R.drawable.bg_chip);
        int px = (int) (6 * parent.getResources().getDisplayMetrics().density);
        int py = (int) (2 * parent.getResources().getDisplayMetrics().density);
        tv.setPadding(px, py, px, py);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (6 * parent.getResources().getDisplayMetrics().density));
        tv.setLayoutParams(lp);
        tv.setMaxLines(1);
        return tv;
    }

    private static int dp(View v, int value) {
        return Math.round(v.getResources().getDisplayMetrics().density * value);
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;
        TextView author;
        TextView meta;
        LinearLayout tags;
        ImageView btnCopy;
        ImageView btnFav;
        ImageView btnRead;
        ImageView btnDownload;

        HeaderVH(View v) {
            super(v);
            cover = v.findViewById(R.id.cover);
            title = v.findViewById(R.id.title);
            author = v.findViewById(R.id.author);
            meta = v.findViewById(R.id.meta);
            tags = v.findViewById(R.id.tags);
            btnCopy = v.findViewById(R.id.btnCopy);
            btnFav = v.findViewById(R.id.btnFav);
            btnRead = v.findViewById(R.id.btnRead);
            btnDownload = v.findViewById(R.id.btnDownload);
        }
    }

    static class PageVH extends RecyclerView.ViewHolder {
        ImageView thumb;
        CircularProgressIndicator progress;

        PageVH(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            progress = v.findViewById(R.id.thumbProgress);
        }
    }
}
