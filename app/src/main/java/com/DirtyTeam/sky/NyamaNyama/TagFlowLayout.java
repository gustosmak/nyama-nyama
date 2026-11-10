package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class TagFlowLayout extends ViewGroup {

    private final int gap;

    public TagFlowLayout(Context context) {
        this(context, null);
    }

    public TagFlowLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        gap = Math.round(8 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int available = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int x = 0;
        int y = getPaddingTop();
        int lineHeight = 0;
        int childWidthSpec = MeasureSpec.makeMeasureSpec(available, MeasureSpec.AT_MOST);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(childWidthSpec, childHeightSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            if (x > 0 && x + childWidth > available) {
                y += lineHeight + gap;
                x = 0;
                lineHeight = 0;
            }
            x += childWidth + gap;
            lineHeight = Math.max(lineHeight, childHeight);
        }
        setMeasuredDimension(width, y + lineHeight + getPaddingBottom());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int available = Math.max(0, right - left - getPaddingLeft() - getPaddingRight());
        int x = getPaddingLeft();
        int y = getPaddingTop();
        int lineHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            if (x > getPaddingLeft() && x - getPaddingLeft() + childWidth > available) {
                y += lineHeight + gap;
                x = getPaddingLeft();
                lineHeight = 0;
            }
            child.layout(x, y, x + childWidth, y + childHeight);
            x += childWidth + gap;
            lineHeight = Math.max(lineHeight, childHeight);
        }
    }
}
