package com.moco.comicsearch;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** 漫画评论：从详情页抓取评论并展示。 */
public class CommentsActivity extends SwipeBackActivity {

    private Site site;
    private LinearLayout list;
    private TextView loading;
    private String albumId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeManager.apply(this);
        EdgeToEdge.setup(this);
        setContentView(R.layout.activity_comments);
        EdgeToEdge.padTop(findViewById(R.id.header));
        EdgeToEdge.padBottom(findViewById(R.id.root));

        site = Site.get(this);
        list = findViewById(R.id.commentList);
        loading = findViewById(R.id.commentLoading);
        albumId = getIntent().getStringExtra("id");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        if (albumId == null || albumId.isEmpty()) {
            showEmpty();
            return;
        }
        new Thread(() -> {
            List<String> comments = null;
            try {
                String html = site.get("/album/" + albumId);
                comments = Parsers.parseComments(html);
            } catch (Exception ignored) {
            }
            final List<String> result = comments;
            runOnUiThread(() -> show(result));
        }).start();
    }

    private void show(List<String> comments) {
        loading.setVisibility(View.GONE);
        list.removeAllViews();
        if (comments == null || comments.isEmpty()) {
            showEmpty();
            return;
        }
        for (String text : comments) {
            TextView tv = new TextView(this);
            tv.setText(text);
            tv.setTextSize(13f);
            tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.on_surface));
            int pad = dp(12);
            tv.setPadding(pad, pad, pad, pad);
            tv.setBackgroundResource(R.drawable.bg_block);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            tv.setLayoutParams(lp);
            list.addView(tv);
        }
    }

    private void showEmpty() {
        loading.setVisibility(View.GONE);
        if (list.getChildCount() > 0) {
            return;
        }
        TextView tv = new TextView(this);
        tv.setText(R.string.no_comments);
        tv.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.muted));
        tv.setTextSize(13f);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, dp(24), 0, 0);
        list.addView(tv);
    }
}
