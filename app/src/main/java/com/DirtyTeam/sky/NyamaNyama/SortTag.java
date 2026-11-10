package com.DirtyTeam.sky.NyamaNyama;

public final class SortTag {

    public static final int CATEGORY = 0;
    public static final int LIKED = 1;
    public static final int HISTORY = 2;

    public final int kind;
    public final BudgetCategory category;

    private SortTag(int kind, BudgetCategory category) {
        this.kind = kind;
        this.category = category;
    }

    public static SortTag category(BudgetCategory category) {
        return new SortTag(CATEGORY, category);
    }

    public static SortTag liked() {
        return new SortTag(LIKED, null);
    }

    public static SortTag history() {
        return new SortTag(HISTORY, null);
    }

    public boolean same(SortTag other) {
        if (other == null || kind != other.kind) {
            return false;
        }
        if (kind != CATEGORY) {
            return true;
        }
        return DishStyle.same(category, other.category);
    }

    public boolean matches(BudgetCategory segment, boolean liked, boolean history) {
        if (kind == LIKED) {
            return liked;
        }
        if (kind == HISTORY) {
            return history;
        }
        return segment != null && DishStyle.same(segment, category);
    }
}
