package com.moco.comicsearch;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** 下载页任务列表：封面 + 标题 + 进度条 + 开始/暂停。 */
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.VH> {

    private final ImageLoader loader;
    private final DownloadManager manager;
    private List<DownloadManager.Task> items = new ArrayList<>();

    public DownloadAdapter(ImageLoader loader, DownloadManager manager) {
        this.loader = loader;
        this.manager = manager;
    }

    public void submit(List<DownloadManager.Task> list) {
        items = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        DownloadManager.Task t = items.get(position);
        h.title.setText(t.title == null || t.title.isEmpty() ? t.albumId : t.title);
        int p = t.progress();
        h.progress.setProgress(p);
        String total = t.total > 0 ? String.valueOf(t.total) : "?";
        h.progressText.setText(t.done + " / " + total + "   " + p + "%");
        h.btnToggle.setImageResource(t.state == 1 ? R.drawable.ic_pause : R.drawable.ic_play);
        h.btnToggle.setOnClickListener(v -> {
            if (t.state == 1) {
                manager.pause(t.albumId);
            } else {
                manager.start(t.albumId);
            }
        });

        h.cover.setImageBitmap(null);
        final String url = t.cover;
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

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title;
        ProgressBar progress;
        TextView progressText;
        ImageButton btnToggle;

        VH(@NonNull View v) {
            super(v);
            cover = v.findViewById(R.id.cover);
            title = v.findViewById(R.id.title);
            progress = v.findViewById(R.id.progress);
            progressText = v.findViewById(R.id.progressText);
            btnToggle = v.findViewById(R.id.btnToggle);
        }
    }
}
