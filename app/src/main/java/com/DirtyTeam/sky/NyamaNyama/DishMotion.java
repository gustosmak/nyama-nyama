package com.DirtyTeam.sky.NyamaNyama;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;

import java.util.HashMap;
import java.util.Map;

public final class DishMotion {

    private DishMotion() {
    }

    public static Map<Object, Integer> capture(ViewGroup group) {
        Map<Object, Integer> tops = new HashMap<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getTag() != null) {
                tops.put(child.getTag(), child.getTop());
            }
        }
        return tops;
    }

    public static void slide(ViewGroup group, Map<Object, Integer> oldTops) {
        if (oldTops.isEmpty()) {
            return;
        }
        group.setClipChildren(false);
        if (group.getParent() instanceof ViewGroup) {
            ((ViewGroup) group.getParent()).setClipChildren(false);
        }
        group.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                group.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                for (int i = 0; i < group.getChildCount(); i++) {
                    View child = group.getChildAt(i);
                    if (child.getTag() == null) {
                        continue;
                    }
                    Integer old = oldTops.get(child.getTag());
                    if (old == null) {
                        continue;
                    }
                    float dy = old - child.getTop();
                    if (dy == 0f) {
                        continue;
                    }
                    child.setTranslationY(dy);
                    child.animate()
                            .translationY(0f)
                            .setDuration(320)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                }
            }
        });
    }
}
