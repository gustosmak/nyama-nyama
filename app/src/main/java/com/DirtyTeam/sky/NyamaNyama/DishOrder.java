package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class DishOrder {

    private DishOrder() {
    }

    public static List<Dish> sorted(
            Context context,
            String restaurant,
            List<Dish> dishes,
            List<BudgetCategory> catalog,
            List<SortTag> priority,
            boolean hiddenFirst
    ) {
        int[] tiers = new int[dishes.size()];
        for (int i = 0; i < dishes.size(); i++) {
            Dish dish = dishes.get(i);
            tiers[i] = tier(
                    dish,
                    catalog,
                    priority,
                    hiddenFirst,
                    UserPrefs.isLiked(context, restaurant, dish.name),
                    UserPrefs.isInHistory(context, restaurant, dish.name),
                    UserPrefs.isHidden(context, restaurant, dish.name)
            );
        }
        return byTier(dishes, tiers);
    }

    public static List<Dish> withoutHidden(List<Dish> dishes, boolean[] hidden) {
        List<Dish> visible = new ArrayList<>();
        for (int i = 0; i < dishes.size(); i++) {
            if (i >= hidden.length || !hidden[i]) {
                visible.add(dishes.get(i));
            }
        }
        return visible;
    }

    static int tier(
            Dish dish,
            List<BudgetCategory> catalog,
            List<SortTag> priority,
            boolean wantHidden,
            boolean liked,
            boolean history,
            boolean hidden
    ) {
        int rank = tagTier(dish, catalog, priority, liked, history);
        if (wantHidden && !hidden) {
            rank += 100000;
        }
        return rank;
    }

    private static int tagTier(
            Dish dish,
            List<BudgetCategory> catalog,
            List<SortTag> priority,
            boolean liked,
            boolean history
    ) {
        int count = priority.size();
        if (count == 0) {
            return 0;
        }
        BudgetCategory segment = catalog.isEmpty() ? null : DishStyle.segment(dish.price, catalog);
        int earliest = -1;
        int second = count;
        int matched = 0;
        for (int i = 0; i < count; i++) {
            if (!priority.get(i).matches(segment, liked, history)) {
                continue;
            }
            if (earliest < 0) {
                earliest = i;
            } else if (matched == 1) {
                second = i;
            }
            matched++;
        }
        if (earliest < 0) {
            return count * count * 4 + 1;
        }
        int span = count + 1;
        int band = matched == count ? 0 : 1;
        return band * span * span + earliest * span + second;
    }

    static <T> List<T> byTier(List<T> items, int[] tiers) {
        List<T> result = new ArrayList<>();
        boolean[] taken = new boolean[items.size()];
        int remaining = items.size();
        while (remaining > 0) {
            int best = Integer.MAX_VALUE;
            for (int i = 0; i < items.size(); i++) {
                if (!taken[i] && tiers[i] < best) {
                    best = tiers[i];
                }
            }
            for (int i = 0; i < items.size(); i++) {
                if (!taken[i] && tiers[i] == best) {
                    result.add(items.get(i));
                    taken[i] = true;
                    remaining--;
                }
            }
        }
        return result;
    }
}
