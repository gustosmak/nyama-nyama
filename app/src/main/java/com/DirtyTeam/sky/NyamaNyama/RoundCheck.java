package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class RoundCheck extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(RoundCheck check, boolean isChecked);
    }

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean checked;
    private OnCheckedChangeListener listener;

    public RoundCheck(Context context) {
        this(context, null);
    }

    public RoundCheck(Context context, AttributeSet attrs) {
        super(context, attrs);
        setClickable(false);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.WHITE);
        dot.setStyle(Paint.Style.FILL);
        dot.setColor(Color.WHITE);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        setChecked(value, true);
    }

    public void setChecked(boolean value, boolean notify) {
        if (checked == value) {
            return;
        }
        checked = value;
        invalidate();
        if (notify && listener != null) {
            listener.onCheckedChanged(this, checked);
        }
    }

    public void toggle() {
        setChecked(!checked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float stroke = 2f * density;
        ring.setStrokeWidth(stroke);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) - stroke;
        canvas.drawCircle(cx, cy, radius, ring);
        if (checked) {
            canvas.drawCircle(cx, cy, radius * 0.48f, dot);
        }
    }
}
