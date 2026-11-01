package com.moco.comicsearch;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 统一提示：用当前主题色的衬线 Jm + 文案，避免系统提示框使用蓝色应用图标。 */
public final class Ui {

    private Ui() {
    }

    public static void toast(Context ctx, CharSequence msg) {
        try {
            float density = ctx.getResources().getDisplayMetrics().density;
            int pad = (int) (density * 14);
            int radius = (int) (density * 10);

            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xF0222222);
            bg.setCornerRadius(radius);

            LinearLayout box = new LinearLayout(ctx);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setBackground(bg);
            box.setPadding(pad, pad, pad, pad);

            TextView mark = new TextView(ctx);
            mark.setText("Jm");
            mark.setTypeface(Typeface.SERIF, Typeface.BOLD);
            mark.setTextColor(ThemeManager.primary(ctx));
            mark.setTextSize(16f);
            mark.setIncludeFontPadding(false);
            box.addView(mark);

            TextView text = new TextView(ctx);
            text.setText(msg);
            text.setTextColor(Color.WHITE);
            text.setTextSize(14f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = pad;
            box.addView(text, lp);

            Toast toast = new Toast(ctx);
            toast.setDuration(Toast.LENGTH_SHORT);
            toast.setView(box);
            toast.show();
        } catch (Exception e) {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
        }
    }
}
