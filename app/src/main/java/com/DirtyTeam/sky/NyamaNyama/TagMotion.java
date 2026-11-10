package com.DirtyTeam.sky.NyamaNyama;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class TagMotion {

    private static final Map<View, Boolean> BUSY = new IdentityHashMap<>();

    private TagMotion() {
    }

    public static void moveCheckedToFront(ViewGroup tags, View rising) {
        reorder(tags, rising, true, false);
    }

    public static void slideCheckedToEnd(ViewGroup tags, View justChecked) {
        reorder(tags, justChecked, false, true);
    }

    public static void applyOrder(ViewGroup tags, List<View> order) {
        if (Boolean.TRUE.equals(BUSY.get(tags)) || order.isEmpty()) {
            return;
        }
        boolean laidOut = false;
        for (int i = 0; i < tags.getChildCount(); i++) {
            if (tags.getChildAt(i).getWidth() > 0) {
                laidOut = true;
                break;
            }
        }
        if (!laidOut) {
            tags.removeAllViews();
            for (View child : order) {
                tags.addView(child);
            }
            return;
        }
        BUSY.put(tags, true);
        Map<View, int[]> starts = new IdentityHashMap<>();
        for (int i = 0; i < tags.getChildCount(); i++) {
            View child = tags.getChildAt(i);
            starts.put(child, new int[]{child.getLeft(), child.getTop()});
        }
        tags.removeAllViews();
        for (View child : order) {
            tags.addView(child);
        }
        tags.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                tags.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                for (int i = 0; i < tags.getChildCount(); i++) {
                    View child = tags.getChildAt(i);
                    int[] start = starts.get(child);
                    if (start == null) {
                        continue;
                    }
                    float dx = start[0] - child.getLeft();
                    float dy = start[1] - child.getTop();
                    if (dx == 0f && dy == 0f) {
                        continue;
                    }
                    child.setTranslationX(dx);
                    child.setTranslationY(dy);
                    child.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .setDuration(280)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
                BUSY.put(tags, false);
            }
        });
    }

    private static void reorder(ViewGroup tags, View special, boolean lift, boolean appendSpecial) {
        if (Boolean.TRUE.equals(BUSY.get(tags))) {
            return;
        }
        BUSY.put(tags, true);
        Map<View, int[]> starts = new IdentityHashMap<>();
        List<View> selected = new ArrayList<>();
        List<View> rest = new ArrayList<>();
        for (int i = 0; i < tags.getChildCount(); i++) {
            View child = tags.getChildAt(i);
            starts.put(child, new int[]{child.getLeft(), child.getTop()});
            RoundCheck check = child.findViewById(R.id.tag_check);
            if (appendSpecial && child == special) {
                continue;
            }
            if (check != null && check.isChecked()) {
                selected.add(child);
            } else {
                rest.add(child);
            }
        }
        tags.removeAllViews();
        for (View child : selected) {
            tags.addView(child);
        }
        if (appendSpecial && special != null) {
            tags.addView(special);
        }
        for (View child : rest) {
            tags.addView(child);
        }
        tags.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                tags.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                float extra = 28 * tags.getResources().getDisplayMetrics().density;
                for (int i = 0; i < tags.getChildCount(); i++) {
                    View child = tags.getChildAt(i);
                    int[] start = starts.get(child);
                    if (start == null) {
                        continue;
                    }
                    float dx = start[0] - child.getLeft();
                    float dy = start[1] - child.getTop();
                    boolean raised = lift && child == special;
                    if (raised) {
                        dy += extra;
                        child.setTranslationZ(extra);
                    }
                    if (dx == 0f && dy == 0f && !raised) {
                        continue;
                    }
                    child.setTranslationX(dx);
                    child.setTranslationY(dy);
                    child.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .translationZ(0f)
                            .setDuration(raised ? 460 : 280)
                            .setInterpolator(raised ? new OvershootInterpolator(0.8f) : new DecelerateInterpolator())
                            .start();
                }
                BUSY.put(tags, false);
            }
        });
    }
}
