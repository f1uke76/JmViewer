package com.moco.comicsearch;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** 阅读历史列表。支持「列表方式」与「封面方式」。 */
public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.VH> {

    private static final int TYPE_LIST = 0;
    private static final int TYPE_GRID = 1;

    public interface Listener {
        void onClick(Models.History item);

        void onLongClick(Models.History item);
    }

    private final List<Models.History> items = new ArrayList<>();
    private final ImageLoader loader;
    private final Listener listener;
    private boolean grid = false;

    public HistoryAdapter(ImageLoader loader, Listener listener) {
        this.loader = loader;
        this.listener = listener;
    }

    public void setGrid(boolean grid) {
        if (this.grid != grid) {
            this.grid = grid;
            notifyDataSetChanged();
        }
    }

    public void submit(List<Models.History> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    public static String timeAgo(Context ctx, long time) {
        if (time <= 0) {
            return "";
        }
        long diff = System.currentTimeMillis() - time;
        if (diff < 60_000L) {
            return ctx.getString(R.string.time_just_now);
        }
        if (diff < 3_600_000L) {
            return ctx.getString(R.string.time_minutes, (int) (diff / 60_000L));
        }
        if (diff < 86_400_000L) {
            return ctx.getString(R.string.time_hours, (int) (diff / 3_600_000L));
        }
        return ctx.getString(R.string.time_days, (int) (diff / 86_400_000L));
    }

    @Override
    public int getItemViewType(int position) {
        return grid ? TYPE_GRID : TYPE_LIST;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_GRID
                ? R.layout.item_history_grid : R.layout.item_history;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Models.History item = items.get(position);
        h.title.setText(item.title.isEmpty() ? item.id : item.title);

        String chapter = item.chapterName == null ? "" : item.chapterName;
        String time = timeAgo(h.itemView.getContext(), item.time);
        h.sub.setText(chapter.isEmpty() ? time : chapter + " · " + time);

        if (h.cover != null) {
            int w = h.itemView.getResources().getDisplayMetrics().widthPixels / 2 - dp(h.itemView, 12);
            ViewGroup.LayoutParams lp = h.cover.getLayoutParams();
            lp.height = (int) (w * 4f / 3f);
            h.cover.setLayoutParams(lp);

            h.cover.setImageBitmap(null);
            final String url = item.cover;
            h.cover.setTag(url);
            if (url != null && !url.isEmpty()) {
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
        }

        h.itemView.setOnClickListener(v -> listener.onClick(item));
        h.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(item);
            return true;
        });
    }

    private static int dp(View v, int value) {
        return Math.round(v.getResources().getDisplayMetrics().density * value);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;
        TextView sub;

        VH(@NonNull View v) {
            super(v);
            cover = v.findViewById(R.id.historyCover);
            title = v.findViewById(R.id.historyTitle);
            sub = v.findViewById(R.id.historySub);
        }
    }
}
