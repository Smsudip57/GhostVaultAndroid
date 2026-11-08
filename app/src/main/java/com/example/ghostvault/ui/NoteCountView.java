package com.example.ghostvault.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.Nullable;
import com.example.ghostvault.R;

/** Course topic: custom View drawing using Canvas, Paint, and onDraw(). */
public final class NoteCountView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private int count;
    private final int color;
    private final int muted;

    public NoteCountView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        color = context.getColor(R.color.gv_primary);
        muted = context.getColor(R.color.gv_ring_track);
    }

    public void setCount(int newCount) { count = Math.max(0, newCount); invalidate(); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float stroke = dp(8);
        float radius = Math.min(getWidth(), getHeight()) / 2f - stroke - dp(4);
        arc.set(cx-radius, cy-radius, cx+radius, cy+radius);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(muted);
        canvas.drawArc(arc, 0, 360, false, paint);
        paint.setColor(color);
        // Visual progress toward 20 notes; actual note total is printed in the center.
        canvas.drawArc(arc, -90, 360f * Math.min(count, 20) / 20f, false, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(color);
        paint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD));
        paint.setTextSize(dp(28));
        canvas.drawText(String.valueOf(count), cx, cy + dp(5), paint);
        paint.setTypeface(android.graphics.Typeface.DEFAULT);
        paint.setTextSize(dp(11));
        canvas.drawText("NOTES", cx, cy + dp(22), paint);
    }

    private float dp(float dp) { return dp * getResources().getDisplayMetrics().density; }
}
