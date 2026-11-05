package com.moco.comicsearch;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

/** 布局里用的「Jm」标识（灰色单色），和封面占位图一致。 */
public class JmMarkView extends View {

    private final JmMarkDrawable mark;

    public JmMarkView(Context context) {
        this(context, null);
    }

    public JmMarkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mark = new JmMarkDrawable(ContextCompat.getColor(context, R.color.muted));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mark.setBounds(0, 0, getWidth(), getHeight());
        mark.draw(canvas);
    }
}
