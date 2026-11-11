package com.DirtyTeam.sky.NyamaNyama;

public final class Dish {

    public final String name;
    public final String satiety;
    public final int satietyLevel;
    public final int price;
    public final String calories;
    public final boolean generated;

    public Dish(String name, String satiety, int satietyLevel, int price, String calories) {
        this(name, satiety, satietyLevel, price, calories, false);
    }

    public Dish(
            String name,
            String satiety,
            int satietyLevel,
            int price,
            String calories,
            boolean generated
    ) {
        this.name = name;
        this.satiety = satiety;
        this.satietyLevel = satietyLevel;
        this.price = price;
        this.calories = calories;
        this.generated = generated;
    }
}
