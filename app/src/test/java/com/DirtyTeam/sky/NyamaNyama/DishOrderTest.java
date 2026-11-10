package com.DirtyTeam.sky.NyamaNyama;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DishOrderTest {

    private final BudgetCategory budget = new BudgetCategory("Бюджет", 300, 1, false);
    private final BudgetCategory optimal = new BudgetCategory("Оптимальный", 500, 2, false);
    private final BudgetCategory maximum = new BudgetCategory("Максимум", 1000, 3, false);
    private final List<BudgetCategory> catalog = Arrays.asList(budget, optimal, maximum);

    @Test
    public void dishesFollowSelectedTagOrderThenTheRest() {
        List<Dish> dishes = Arrays.asList(
                dish("дорогое", 900),
                dish("бюджетное", 250),
                dish("среднее", 450),
                dish("ещё бюджет", 280)
        );
        List<SortTag> priority = Arrays.asList(SortTag.category(optimal), SortTag.category(budget));

        List<Dish> sorted = DishOrder.byTier(dishes, tiers(dishes, priority, new boolean[4], new boolean[4]));

        assertEquals("среднее", sorted.get(0).name);
        assertEquals("бюджетное", sorted.get(1).name);
        assertEquals("ещё бюджет", sorted.get(2).name);
        assertEquals("дорогое", sorted.get(3).name);
    }

    @Test
    public void likeTagAtFrontSortsLikeACategory() {
        List<Dish> dishes = Arrays.asList(
                dish("средний лайк", 450),
                dish("бюджет", 250),
                dish("бюджет лайк", 280),
                dish("чужой", 900)
        );
        boolean[] liked = {true, false, true, false};
        List<SortTag> priority = Arrays.asList(SortTag.liked(), SortTag.category(budget), SortTag.category(optimal));
        int[] tiers = tiers(dishes, priority, liked, new boolean[4]);

        List<Dish> sorted = DishOrder.byTier(dishes, tiers);

        assertEquals("бюджет лайк", sorted.get(0).name);
        assertEquals("средний лайк", sorted.get(1).name);
        assertEquals("бюджет", sorted.get(2).name);
        assertEquals("чужой", sorted.get(3).name);
        assertTrue(tiers[2] < tiers[0]);
        assertTrue(tiers[0] < tiers[1]);
    }

    @Test
    public void historyThenLikeRequiresBothBeforeEitherAlone() {
        List<Dish> dishes = Arrays.asList(
                dish("только лайк", 250),
                dish("оба", 250),
                dish("только история", 250)
        );
        boolean[] liked = {true, true, false};
        boolean[] history = {false, true, true};
        List<SortTag> priority = Arrays.asList(SortTag.history(), SortTag.liked());
        List<Dish> sorted = DishOrder.byTier(dishes, tiers(dishes, priority, liked, history));

        assertEquals("оба", sorted.get(0).name);
        assertEquals("только история", sorted.get(1).name);
        assertEquals("только лайк", sorted.get(2).name);
    }

    @Test
    public void restaurantsUseTheBestDishTier() {
        List<SortTag> priority = Arrays.asList(SortTag.category(budget), SortTag.category(optimal));
        Dish onlyOptimal = dish("только средний", 450);
        Dish budgetDish = dish("бюджет", 250);
        int optimalTier = DishOrder.tier(onlyOptimal, catalog, priority, false, false, false, false);
        int budgetTier = DishOrder.tier(budgetDish, catalog, priority, false, false, false, false);
        List<String> names = Arrays.asList("второй", "первый", "мимо");
        int[] ranks = {optimalTier, budgetTier, optimalTier + 5};

        List<String> sorted = DishOrder.byTier(names, ranks);

        assertEquals("первый", sorted.get(0));
        assertEquals("второй", sorted.get(1));
        assertEquals("мимо", sorted.get(2));
        assertTrue(budgetTier < optimalTier);
    }

    @Test
    public void hiddenDishesStayOutOfTheFeed() {
        List<Dish> dishes = Arrays.asList(dish("скрыто", 250), dish("видно", 450), dish("тоже скрыто", 900));
        List<Dish> visible = DishOrder.withoutHidden(dishes, new boolean[]{true, false, true});

        assertEquals(1, visible.size());
        assertEquals("видно", visible.get(0).name);
    }

    @Test
    public void likeAndHideCannotStayTogether() {
        boolean[] hiddenWins = UserPrefs.exclusive(true, false, "hidden", true);
        assertFalse(hiddenWins[0]);
        assertTrue(hiddenWins[1]);

        boolean[] likeWins = UserPrefs.exclusive(false, true, "liked", true);
        assertTrue(likeWins[0]);
        assertFalse(likeWins[1]);

        boolean[] likeOff = UserPrefs.exclusive(true, false, "liked", false);
        assertFalse(likeOff[0]);
        assertFalse(likeOff[1]);
    }

    private int[] tiers(List<Dish> dishes, List<SortTag> priority, boolean[] liked, boolean[] history) {
        int[] tiers = new int[dishes.size()];
        for (int i = 0; i < dishes.size(); i++) {
            tiers[i] = DishOrder.tier(
                    dishes.get(i), catalog, priority,
                    false,
                    liked[i], history[i], false
            );
        }
        return tiers;
    }

    private Dish dish(String name, int price) {
        return new Dish(name, "", 0, price, "");
    }
}
