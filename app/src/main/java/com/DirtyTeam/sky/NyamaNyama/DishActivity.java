package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class DishActivity extends AppCompatActivity {

    public static final String EXTRA_RESTAURANT = "restaurant";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_SATIETY = "satiety";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_PRICE = "price";
    public static final String EXTRA_CALORIES = "calories";

    private String restaurant;
    private String dishName;
    private boolean liked;
    private boolean history;
    private boolean hidden;

    public static void open(Context context, String restaurant, Dish dish) {
        Intent intent = new Intent(context, DishActivity.class);
        intent.putExtra(EXTRA_RESTAURANT, restaurant);
        intent.putExtra(EXTRA_NAME, dish.name);
        intent.putExtra(EXTRA_SATIETY, dish.satiety);
        intent.putExtra(EXTRA_LEVEL, dish.satietyLevel);
        intent.putExtra(EXTRA_PRICE, dish.price);
        intent.putExtra(EXTRA_CALORIES, dish.calories);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_dish);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dish_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        findViewById(R.id.dish_back).setOnClickListener(v -> finish());

        restaurant = textExtra(EXTRA_RESTAURANT);
        dishName = textExtra(EXTRA_NAME);
        int price = getIntent().getIntExtra(EXTRA_PRICE, 0);
        liked = UserPrefs.isLiked(this, restaurant, dishName);
        history = UserPrefs.isInHistory(this, restaurant, dishName);
        hidden = UserPrefs.isHidden(this, restaurant, dishName);

        Typeface bold = AppFonts.montserratBold(this);
        Typeface regular = AppFonts.montserratRegular(this);
        TextView restaurantView = findViewById(R.id.dish_restaurant);
        TextView title = findViewById(R.id.dish_title);
        TextView satiety = findViewById(R.id.dish_satiety);
        TextView level = findViewById(R.id.dish_level);
        TextView priceView = findViewById(R.id.dish_price);
        TextView calories = findViewById(R.id.dish_calories);
        TextView segmentLabel = findViewById(R.id.dish_segment_label);
        TextView segment = findViewById(R.id.dish_segment);
        restaurantView.setTypeface(regular);
        title.setTypeface(bold);
        satiety.setTypeface(regular);
        level.setTypeface(bold);
        priceView.setTypeface(bold);
        calories.setTypeface(bold);
        segmentLabel.setTypeface(regular);
        segment.setTypeface(bold);

        restaurantView.setText(restaurant);
        title.setText(dishName);
        satiety.setText(textExtra(EXTRA_SATIETY));
        level.setText(getString(R.string.dish_satiety_level, getIntent().getIntExtra(EXTRA_LEVEL, 0)));
        priceView.setText(price + " \u20BD");
        calories.setText(textExtra(EXTRA_CALORIES));

        List<BudgetCategory> categories = DishStyle.categories(this);
        BudgetCategory category = DishStyle.segment(price, categories);
        String segmentName = category.name == null || category.name.isEmpty()
                ? String.valueOf(category.amount)
                : category.name;
        segment.setText(segmentName + "  " + category.amount + " \u20BD");
        segment.setBackground(DishStyle.bubble(segment, category.color));

        findViewById(R.id.dish_like_button).setOnClickListener(v -> {
            liked = !liked;
            if (liked) {
                hidden = false;
            }
            UserPrefs.setLiked(this, restaurant, dishName, liked);
            paintMarks();
        });
        findViewById(R.id.dish_history_button).setOnClickListener(v -> {
            history = !history;
            UserPrefs.setInHistory(this, restaurant, dishName, history);
            paintMarks();
            if (history) {
                showHint(R.string.history_added);
            }
        });
        findViewById(R.id.dish_hide_button).setOnClickListener(v -> {
            hidden = !hidden;
            if (hidden) {
                liked = false;
            }
            UserPrefs.setHidden(this, restaurant, dishName, hidden);
            paintMarks();
            if (hidden) {
                showHint(R.string.hidden_added);
            }
        });
        paintMarks();
    }

    private void paintMarks() {
        paintMark(
                findViewById(R.id.dish_like_button),
                findViewById(R.id.dish_like_icon),
                liked,
                DishStyle.COLOR_LIKE
        );
        paintMark(
                findViewById(R.id.dish_history_button),
                findViewById(R.id.dish_history_icon),
                history,
                DishStyle.COLOR_HISTORY
        );
        paintMark(
                findViewById(R.id.dish_hide_button),
                findViewById(R.id.dish_hide_icon),
                hidden,
                DishStyle.COLOR_HIDE
        );
    }

    private void paintMark(View button, ImageView icon, boolean on, int color) {
        int shown = on ? color : DishStyle.COLOR_MUTED;
        button.setBackground(DishStyle.bubble(button, shown, 24f));
        icon.setImageTintList(ColorStateList.valueOf(shown));
    }

    private void showHint(int message) {
        Snackbar snackbar = Snackbar.make(
                findViewById(R.id.dish_root),
                message,
                Snackbar.LENGTH_SHORT
        );
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.nyama_orange));
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.black));
        TextView text = snackbar.getView().findViewById(com.google.android.material.R.id.snackbar_text);
        text.setTextSize(13);
        text.setTypeface(AppFonts.montserratRegular(this));
        snackbar.show();
    }

    private String textExtra(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value;
    }
}
