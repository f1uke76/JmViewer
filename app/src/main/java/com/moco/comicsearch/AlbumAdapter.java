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

import java.util.ArrayList;
import java.util.List;

/** 漫画列表项。支持「列表方式」与「封面方式」两种排版。 */
public class AlbumAdapter extends RecyclerView.Adapter<AlbumAdapter.VH> {

    private static final int TYPE_LIST = 0;
    private static final int TYPE_GRID = 1;

    public interface Listener {
        void onClick(Models.Album album);

        void onCopy(Models.Album album);

        void onLongClick(Models.Album album);

        /** 缓存管理等场景下的垃圾桶删除，默认不处理。 */
        default void onTrash(Models.Album album) {
        }
    }

    private final List<Models.Album> items = new ArrayList<>();
    private final ImageLoader loader;
    private final Listener listener;
    private boolean grid = false;
    private boolean showTrash = false;

    public AlbumAdapter(ImageLoader loader, Listener listener) {
        this.loader = loader;
        this.listener = listener;
    }

    /** 是否在每行右侧显示垃圾桶。 */
    public void setShowTrash(boolean show) {
        this.showTrash = show;
    }

    public void setGrid(boolean grid) {
        if (this.grid != grid) {
            this.grid = grid;
            notifyDataSetChanged();
        }
    }

    public void submit(List<Models.Album> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    public void append(List<Models.Album> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        int start = items.size();
        items.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public List<Models.Album> items() {
        return items;
    }

    public boolean contains(String id) {
        for (Models.Album a : items) {
            if (a.id.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public int count() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return grid ? TYPE_GRID : TYPE_LIST;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_GRID ? R.layout.item_album_grid : R.layout.item_album;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Models.Album a = items.get(position);
        h.title.setText(a.title);

        if (h.meta != null) {
            StringBuilder meta = new StringBuilder();
            if (!a.author.isEmpty()) {
                meta.append(a.author);
            }
            if (!a.category.isEmpty()) {
                if (meta.length() > 0) {
                    meta.append(" · ");
                }
                meta.append(a.category);
                if (!a.sub.isEmpty()) {
                    meta.append(' ').append(a.sub);
                }
            }
            if (!a.pages.isEmpty()) {
                if (meta.length() > 0) {
                    meta.append(" · ");
                }
                meta.append(a.pages).append("P");
            }
            if (!a.likes.isEmpty()) {
                if (meta.length() > 0) {
                    meta.append(" · ");
                }
                meta.append("♥ ").append(a.likes);
            }
            if (!a.extra.isEmpty()) {
                if (meta.length() > 0) {
                    meta.append(" · ");
                }
                meta.append(a.extra);
            }
            h.meta.setText(meta);
        }

        if (h.tags != null) {
            h.tags.removeAllViews();
            int shown = 0;
            for (String tag : a.tags) {
                if (shown >= 3) {
                    break;
                }
                h.tags.addView(chip(h.tags, tag));
                shown++;
            }
        }

        if (grid) {
            // 封面方式：按两列宽度算高度，保持 3:4（扣除外边距 12dp 与内边距 16dp）
            int w = h.itemView.getResources().getDisplayMetrics().widthPixels / 2 - dp(h.itemView, 28);
            ViewGroup.LayoutParams lp = h.cover.getLayoutParams();
            lp.height = (int) (w * 4f / 3f);
            h.cover.setLayoutParams(lp);
        }

        h.cover.setImageBitmap(null);
        final String coverUrl = a.cover;
        h.cover.setTag(coverUrl);
        if (!coverUrl.isEmpty()) {
            loader.loadCover(coverUrl, new ImageLoader.Callback() {
                @Override
                public void onLoaded(Bitmap bitmap) {
                    if (coverUrl.equals(h.cover.getTag())) {
                        h.cover.setImageBitmap(bitmap);
                    }
                }

                @Override
                public void onError(Exception e) {
                }
            });
        }

        h.itemView.setOnClickListener(v -> listener.onClick(a));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(a);
            return true;
        });
        if (h.btnCopy != null) {
            h.btnCopy.setOnClickListener(v -> listener.onCopy(a));
        }
        if (h.btnTrash != null) {
            if (showTrash) {
                h.btnTrash.setVisibility(View.VISIBLE);
                h.btnTrash.setOnClickListener(v -> listener.onTrash(a));
            } else {
                h.btnTrash.setVisibility(View.GONE);
            }
        }
    }

    private static int dp(View v, int value) {
        return Math.round(v.getResources().getDisplayMetrics().density * value);
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

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;
        TextView meta;
        LinearLayout tags;
        ImageView btnCopy;
        ImageView btnTrash;

        VH(@NonNull View v) {
            super(v);
            cover = v.findViewById(R.id.cover);
            title = v.findViewById(R.id.title);
            meta = v.findViewById(R.id.meta);
            tags = v.findViewById(R.id.tags);
            btnCopy = v.findViewById(R.id.btnCopy);
            btnTrash = v.findViewById(R.id.btnTrash);
        }
    }
}
