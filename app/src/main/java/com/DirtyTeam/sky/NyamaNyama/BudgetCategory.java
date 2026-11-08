package com.DirtyTeam.sky.NyamaNyama;

public final class BudgetCategory {

    public final String name;
    public final int amount;
    public final int color;
    public final boolean custom;

    public BudgetCategory(String name, int amount, int color, boolean custom) {
        this.name = name;
        this.amount = amount;
        this.color = color;
        this.custom = custom;
    }
}
