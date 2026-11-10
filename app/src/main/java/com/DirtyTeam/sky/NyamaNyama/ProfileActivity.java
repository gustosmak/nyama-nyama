package com.DirtyTeam.sky.NyamaNyama;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private static final int TAB_HISTORY = 0;
    private static final int TAB_LIKES = 1;
    private static final int TAB_HIDDEN = 2;

    private boolean personalOpen;
    private int tab = TAB_HISTORY;
    private Typeface bold;
    private Typeface regular;
    private View personalBody;
    private ImageView personalArrow;
    private LinearLayout dishes;
    private TextView empty;
    private View historyTab;
    private View likesTab;
    private View hiddenTab;
    private ImageView historyIcon;
    private ImageView likesIcon;
    private ImageView hiddenIcon;
    private TextView historyLabel;
    private TextView likesLabel;
    private TextView hiddenLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.profile_back).setOnClickListener(v -> finish());

        bold = AppFonts.montserratBold(this);
        regular = AppFonts.montserratRegular(this);
        TextView title = findViewById(R.id.profile_title);
        title.setTypeface(bold);
        TextView personalTitle = findViewById(R.id.profile_personal_title);
        personalTitle.setTypeface(bold);

        TextInputLayout emailLayout = findViewById(R.id.profile_email_input);
        TextInputLayout passwordLayout = findViewById(R.id.profile_password_input);
        TextInputEditText email = findViewById(R.id.profile_email);
        TextInputEditText password = findViewById(R.id.profile_password);
        emailLayout.setTypeface(regular);
        passwordLayout.setTypeface(regular);
        email.setTypeface(bold);
        password.setTypeface(bold);
        email.setText(UserPrefs.getEmail(this));
        password.setText(masked(UserPrefs.getPassword(this)));

        personalBody = findViewById(R.id.profile_personal_body);
        personalArrow = findViewById(R.id.profile_personal_arrow);
        findViewById(R.id.profile_personal_header).setOnClickListener(v -> togglePersonal());

        historyTab = findViewById(R.id.profile_tab_history);
        likesTab = findViewById(R.id.profile_tab_likes);
        hiddenTab = findViewById(R.id.profile_tab_hidden);
        historyIcon = findViewById(R.id.profile_tab_history_icon);
        likesIcon = findViewById(R.id.profile_tab_likes_icon);
        hiddenIcon = findViewById(R.id.profile_tab_hidden_icon);
        historyLabel = findViewById(R.id.profile_tab_history_label);
        likesLabel = findViewById(R.id.profile_tab_likes_label);
        hiddenLabel = findViewById(R.id.profile_tab_hidden_label);
        historyLabel.setTypeface(regular);
        likesLabel.setTypeface(regular);
        hiddenLabel.setTypeface(regular);
        empty = findViewById(R.id.profile_empty);
        empty.setTypeface(regular);
        dishes = findViewById(R.id.profile_dishes);

        historyTab.setOnClickListener(v -> selectTab(TAB_HISTORY));
        likesTab.setOnClickListener(v -> selectTab(TAB_LIKES));
        hiddenTab.setOnClickListener(v -> selectTab(TAB_HIDDEN));
        selectTab(TAB_HISTORY);
    }

    @Override
    protected void onResume() {
        super.onResume();
        showDishes();
    }

    private void togglePersonal() {
        personalOpen = !personalOpen;
        personalBody.setVisibility(personalOpen ? View.VISIBLE : View.GONE);
        personalArrow.animate().rotation(personalOpen ? -90f : 90f).setDuration(180).start();
    }

    private void selectTab(int next) {
        tab = next;
        paintTab(historyTab, historyIcon, historyLabel, next == TAB_HISTORY, DishStyle.COLOR_HISTORY);
        paintTab(likesTab, likesIcon, likesLabel, next == TAB_LIKES, DishStyle.COLOR_LIKE);
        paintTab(hiddenTab, hiddenIcon, hiddenLabel, next == TAB_HIDDEN, DishStyle.COLOR_HIDE);
        showDishes();
    }

    private void paintTab(View tabView, ImageView icon, TextView label, boolean selected, int color) {
        tabView.setBackground(DishStyle.bubble(tabView, selected ? color : 0xFF2A2724, 18f));
        icon.setImageTintList(ColorStateList.valueOf(selected ? color : DishStyle.COLOR_MUTED));
        label.setTextColor(selected ? 0xFFFFFFFF : DishStyle.COLOR_MUTED);
    }

    private void showDishes() {
        if (dishes == null) {
            return;
        }
        List<UserPrefs.MarkedDish> shown = new ArrayList<>();
        for (UserPrefs.MarkedDish marked : UserPrefs.markedDishes(this)) {
            if (tab == TAB_HISTORY && marked.history) {
                shown.add(marked);
            } else if (tab == TAB_LIKES && marked.liked) {
                shown.add(marked);
            } else if (tab == TAB_HIDDEN && marked.hidden) {
                shown.add(marked);
            }
        }
        Collections.sort(shown, new Comparator<UserPrefs.MarkedDish>() {
            @Override
            public int compare(UserPrefs.MarkedDish left, UserPrefs.MarkedDish right) {
                int byRestaurant = left.restaurant.compareToIgnoreCase(right.restaurant);
                if (byRestaurant != 0) {
                    return byRestaurant;
                }
                return left.dish.compareToIgnoreCase(right.dish);
            }
        });
        dishes.removeAllViews();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        List<Offer> offers = OffersRepository.load(this);
        List<BudgetCategory> categories = DishStyle.categories(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (UserPrefs.MarkedDish marked : shown) {
            Dish dish = findDish(offers, marked.restaurant, marked.dish);
            View item = inflater.inflate(R.layout.item_profile_dish, dishes, false);
            TextView restaurant = item.findViewById(R.id.profile_dish_restaurant);
            restaurant.setTypeface(regular);
            restaurant.setText(marked.restaurant);
            bindCard(item.findViewById(R.id.profile_dish_card), marked, dish, categories);
            dishes.addView(item);
        }
    }

    private void bindCard(
            View card,
            UserPrefs.MarkedDish marked,
            Dish dish,
            List<BudgetCategory> categories
    ) {
        boolean found = dish != null;
        int color = found ? DishStyle.segment(dish.price, categories).color : DishStyle.COLOR_MUTED;
        card.setBackground(DishStyle.bubble(card, color));
        TextView dishName = card.findViewById(R.id.dish_name);
        TextView satiety = card.findViewById(R.id.dish_satiety);
        TextView price = card.findViewById(R.id.dish_price);
        TextView calories = card.findViewById(R.id.dish_calories);
        dishName.setTypeface(bold);
        satiety.setTypeface(regular);
        price.setTypeface(bold);
        calories.setTypeface(bold);
        dishName.setText(marked.dish);
        satiety.setText(found ? dish.satiety : "");
        price.setText(found ? dish.price + " \u20BD" : "");
        calories.setText(found ? dish.calories : "");

        ImageView likedIcon = card.findViewById(R.id.dish_liked);
        ImageView historyIconView = card.findViewById(R.id.dish_history);
        ImageView hiddenIconView = card.findViewById(R.id.dish_hidden);
        likedIcon.setImageTintList(ColorStateList.valueOf(DishStyle.COLOR_LIKE));
        historyIconView.setImageTintList(ColorStateList.valueOf(DishStyle.COLOR_HISTORY));
        hiddenIconView.setImageTintList(ColorStateList.valueOf(DishStyle.COLOR_HIDE));
        likedIcon.setVisibility(marked.liked ? View.VISIBLE : View.GONE);
        historyIconView.setVisibility(marked.history ? View.VISIBLE : View.GONE);
        hiddenIconView.setVisibility(marked.hidden ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.dish_marks).setVisibility(
                marked.liked || marked.history || marked.hidden ? View.VISIBLE : View.GONE
        );
        card.findViewById(R.id.dish_like_burst).setVisibility(View.GONE);
        if (found) {
            card.setOnClickListener(v -> DishActivity.open(this, marked.restaurant, dish));
        }
    }

    private static Dish findDish(List<Offer> offers, String restaurant, String name) {
        for (Offer offer : offers) {
            if (!offer.name.equals(restaurant)) {
                continue;
            }
            for (Dish dish : offer.dishes) {
                if (dish.name.equals(name)) {
                    return dish;
                }
            }
        }
        return null;
    }

    private static String masked(String password) {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < password.length(); i++) {
            dots.append('\u2022');
        }
        return dots.toString();
    }
}
