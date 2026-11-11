package com.DirtyTeam.sky.NyamaNyama;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public final class DishRow {

    private DishRow() {
    }

    public static void bind(
            View row,
            String restaurant,
            Dish dish,
            List<BudgetCategory> categories,
            Typeface bold,
            Typeface regular
    ) {
        row.setBackground(DishStyle.bubble(row, DishStyle.segment(dish.price, categories).color));
        TextView dishName = row.findViewById(R.id.dish_name);
        TextView satiety = row.findViewById(R.id.dish_satiety);
        TextView price = row.findViewById(R.id.dish_price);
        TextView calories = row.findViewById(R.id.dish_calories);
        dishName.setTypeface(bold);
        satiety.setTypeface(regular);
        price.setTypeface(bold);
        calories.setTypeface(bold);
        dishName.setText(dish.name);
        TextView generated = row.findViewById(R.id.dish_generated);
        if (generated != null) {
            generated.setVisibility(dish.generated ? View.VISIBLE : View.GONE);
            generated.setTypeface(regular);
        }
        satiety.setText(dish.satiety);
        price.setText(dish.price + " \u20BD");
        calories.setText(dish.calories);

        ImageView likedIcon = row.findViewById(R.id.dish_liked);
        ImageView historyIcon = row.findViewById(R.id.dish_history);
        likedIcon.setImageTintList(ColorStateList.valueOf(DishStyle.COLOR_LIKE));
        historyIcon.setImageTintList(ColorStateList.valueOf(DishStyle.COLOR_HISTORY));
        ImageView hiddenIcon = row.findViewById(R.id.dish_hidden);
        hiddenIcon.setImageTintList(ColorStateList.valueOf(DishStyle.COLOR_HIDE));
        ImageView burst = row.findViewById(R.id.dish_like_burst);
        burst.setImageTintList(ColorStateList.valueOf(DishStyle.COLOR_LIKE));
        burst.animate().cancel();
        likedIcon.animate().cancel();
        resetHeart(burst);
        resetHeart(likedIcon);
        showMarks(row, restaurant, dish.name);
        GestureDetector gestures = new GestureDetector(row.getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                DishActivity.open(row.getContext(), restaurant, dish);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent event) {
                boolean liked = !UserPrefs.isLiked(row.getContext(), restaurant, dish.name);
                UserPrefs.setLiked(row.getContext(), restaurant, dish.name, liked);
                showMarks(row, restaurant, dish.name);
                if (liked) {
                    playLike(row);
                }
                return true;
            }
        });
        row.setOnTouchListener((view, event) -> {
            gestures.onTouchEvent(event);
            return true;
        });
    }

    private static void showMarks(View row, String restaurant, String dish) {
        boolean liked = UserPrefs.isLiked(row.getContext(), restaurant, dish);
        boolean history = UserPrefs.isInHistory(row.getContext(), restaurant, dish);
        boolean hidden = UserPrefs.isHidden(row.getContext(), restaurant, dish);
        row.findViewById(R.id.dish_liked).setVisibility(liked ? View.VISIBLE : View.GONE);
        row.findViewById(R.id.dish_history).setVisibility(history ? View.VISIBLE : View.GONE);
        row.findViewById(R.id.dish_hidden).setVisibility(hidden ? View.VISIBLE : View.GONE);
        row.findViewById(R.id.dish_marks).setVisibility(liked || history || hidden ? View.VISIBLE : View.GONE);
    }

    private static void playLike(View row) {
        ImageView burst = row.findViewById(R.id.dish_like_burst);
        ImageView corner = row.findViewById(R.id.dish_liked);
        corner.setVisibility(View.INVISIBLE);
        burst.setVisibility(View.VISIBLE);
        burst.setAlpha(0f);
        burst.setScaleX(0.2f);
        burst.setScaleY(0.2f);
        burst.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .withEndAction(() -> burst.animate()
                        .alpha(0f)
                        .scaleX(1.15f)
                        .scaleY(1.15f)
                        .setStartDelay(90)
                        .setDuration(180)
                        .withEndAction(() -> {
                            burst.setVisibility(View.GONE);
                            resetHeart(burst);
                            corner.setVisibility(View.VISIBLE);
                            corner.setAlpha(0f);
                            corner.setScaleX(0.2f);
                            corner.setScaleY(0.2f);
                            corner.animate()
                                    .alpha(1f)
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(220)
                                    .start();
                        })
                        .start())
                .start();
    }

    private static void resetHeart(ImageView heart) {
        heart.setAlpha(1f);
        heart.setScaleX(1f);
        heart.setScaleY(1f);
    }
}
