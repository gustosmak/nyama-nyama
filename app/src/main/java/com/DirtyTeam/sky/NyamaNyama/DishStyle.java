package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class DishStyle {

    public static final int COLOR_GREEN = 0xFF6FBF73;
    public static final int COLOR_RED_ORANGE = 0xFFE06A45;
    public static final int COLOR_PURPLE = 0xFFB388EB;
    public static final int COLOR_LIKE = 0xFFE23B3B;
    public static final int COLOR_HISTORY = 0xFFF1B467;
    public static final int COLOR_HIDE = 0xFF1B3A6B;
    public static final int COLOR_MUTED = 0xFF9C9894;

    private DishStyle() {
    }

    public static List<BudgetCategory> categories(Context context) {
        List<BudgetCategory> saved = new ArrayList<>(UserPrefs.getBudgetCategories(context));
        if (saved.isEmpty()) {
            saved.add(new BudgetCategory("", 300, COLOR_GREEN, false));
            saved.add(new BudgetCategory("", 500, COLOR_RED_ORANGE, false));
            saved.add(new BudgetCategory("", 1000, COLOR_PURPLE, false));
        }
        Collections.sort(saved, new Comparator<BudgetCategory>() {
            @Override
            public int compare(BudgetCategory left, BudgetCategory right) {
                return Integer.compare(left.amount, right.amount);
            }
        });
        return saved;
    }

    public static BudgetCategory segment(int price, List<BudgetCategory> categories) {
        for (BudgetCategory category : categories) {
            if (price <= category.amount + 100) {
                return category;
            }
        }
        return categories.get(categories.size() - 1);
    }

    public static boolean same(BudgetCategory left, BudgetCategory right) {
        return left.amount == right.amount && left.name.equals(right.name);
    }

    public static boolean inFrames(BudgetCategory segment, List<BudgetCategory> frames) {
        for (BudgetCategory frame : frames) {
            if (same(segment, frame)) {
                return true;
            }
        }
        return false;
    }

    public static boolean fitsPrice(int price, List<BudgetCategory> frames) {
        for (BudgetCategory frame : frames) {
            if (price <= frame.amount + 100) {
                return true;
            }
        }
        return false;
    }

    public static GradientDrawable bubble(View view, int color) {
        return bubble(view, color, 20f);
    }

    public static GradientDrawable bubble(View view, int color, float radiusDp) {
        return bubble(view.getContext(), color, radiusDp);
    }

    public static GradientDrawable bubble(Context context, int color, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        float density = context.getResources().getDisplayMetrics().density;
        drawable.setCornerRadius(radiusDp * density);
        drawable.setStroke(Math.max(1, Math.round(density)), color);
        drawable.setColor(Color.argb(0x40, Color.red(color), Color.green(color), Color.blue(color)));
        return drawable;
    }
}
