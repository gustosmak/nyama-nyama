package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RestaurantActivity extends AppCompatActivity {

    public static final String EXTRA_NAME = "restaurant";
    public static final String EXTRA_TIME = "travel";
    public static final String EXTRA_DISTANCE = "distance";

    private final List<BudgetCategory> frames = new ArrayList<>();
    private final List<SortTag> selectedOrder = new ArrayList<>();
    private final List<View> categoryChips = new ArrayList<>();
    private boolean[] enabled = new boolean[0];
    private LinearLayout chipRow;
    private View historyChip;
    private View likeChip;
    private View hideChip;
    private boolean onlyLiked;
    private boolean onlyHistory;
    private boolean onlyHidden;
    private String restaurantName = "";
    private Offer restaurant;
    private List<BudgetCategory> categories;
    private Typeface bold;
    private Typeface regular;

    public static void open(Context context, String restaurant, String travelTime, Integer distanceMeters) {
        Intent intent = new Intent(context, RestaurantActivity.class);
        intent.putExtra(EXTRA_NAME, restaurant);
        intent.putExtra(EXTRA_TIME, travelTime == null || travelTime.isEmpty() ? "0 min" : travelTime);
        if (distanceMeters != null) {
            intent.putExtra(EXTRA_DISTANCE, distanceMeters);
        }
        context.startActivity(intent);
    }

    static String formatDistance(Integer meters) {
        if (meters == null || meters < 0) {
            return "";
        }
        if (meters < 2000) {
            return meters + " \u043c";
        }
        int whole = meters / 1000;
        int tenth = (meters % 1000) / 100;
        if (tenth == 0) {
            return whole + " \u043a\u043c";
        }
        return whole + "." + tenth + " \u043a\u043c";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_restaurant);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.restaurant_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        findViewById(R.id.restaurant_back).setOnClickListener(v -> finish());

        restaurantName = getIntent().getStringExtra(EXTRA_NAME);
        if (restaurantName == null) {
            restaurantName = "";
        }
        bold = AppFonts.montserratBold(this);
        regular = AppFonts.montserratRegular(this);
        TextView title = findViewById(R.id.restaurant_title);
        title.setTypeface(bold);
        TextView name = findViewById(R.id.restaurant_name);
        TextView time = findViewById(R.id.restaurant_time);
        TextView distance = findViewById(R.id.restaurant_distance);
        name.setTypeface(bold);
        time.setTypeface(bold);
        distance.setTypeface(bold);
        TextView menu = findViewById(R.id.restaurant_menu);
        menu.setTypeface(regular);
        name.setText(restaurantName);

        String shownTime = getIntent().getStringExtra(EXTRA_TIME);
        if (shownTime == null || shownTime.isEmpty()) {
            shownTime = "0 min";
        }
        Integer distanceMeters = getIntent().hasExtra(EXTRA_DISTANCE)
                ? getIntent().getIntExtra(EXTRA_DISTANCE, -1)
                : null;
        restaurant = new Offer(restaurantName, shownTime, distanceMeters);
        time.setText(shownTime);
        distance.setText(formatDistance(distanceMeters));
        new Thread(() -> {
            Offer found = restaurant;
            String wanted = restaurantName.trim();
            for (Offer offer : OffersRepository.load()) {
                if (offer.name != null && offer.name.trim().equalsIgnoreCase(wanted)) {
                    found = new Offer(offer.name, restaurant.travelTime, restaurant.distanceMeters);
                    found.dishes.addAll(offer.dishes);
                    break;
                }
            }
            Offer ready = found;
            runOnUiThread(() -> {
                restaurant = ready;
                if (ready.travelTime != null && !ready.travelTime.isEmpty()) {
                    time.setText(ready.travelTime);
                }
                showDishes();
            });
        }).start();
        findViewById(R.id.restaurant_pin).setOnClickListener(
                v -> OffersActivity.openRestaurantMap(v.getContext(), restaurantName)
        );
        categories = DishStyle.categories(this);
        List<BudgetCategory> budget = UserPrefs.getBudgetCategories(this);
        if (budget.isEmpty()) {
            frames.addAll(categories);
        } else {
            frames.addAll(budget);
        }
        enabled = new boolean[frames.size()];
        for (int i = 0; i < enabled.length; i++) {
            enabled[i] = true;
        }
        showCategories();
        showDishes();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (categories != null) {
            showDishes();
        }
    }

    private void showCategories() {
        chipRow = findViewById(R.id.restaurant_categories);
        LayoutInflater inflater = LayoutInflater.from(this);
        chipRow.removeAllViews();
        categoryChips.clear();
        selectedOrder.clear();
        for (int i = 0; i < frames.size(); i++) {
            BudgetCategory frame = frames.get(i);
            int index = i;
            categoryChips.add(addChip(inflater, frame.name + "  " + frame.amount + " \u20BD", 0, frame.color, enabled[index], (button, isChecked) -> {
                setCategoryChecked(index, isChecked);
                showDishes();
            }));
            if (enabled[index]) {
                selectedOrder.add(SortTag.category(frame));
            }
        }
        historyChip = addChip(inflater, "", R.drawable.ic_history, DishStyle.COLOR_HISTORY, onlyHistory, (button, isChecked) -> {
            onlyHistory = isChecked;
            setMarkChecked(SortTag.history(), isChecked);
            showDishes();
        });
        likeChip = addChip(inflater, "", R.drawable.ic_heart, DishStyle.COLOR_LIKE, onlyLiked, (button, isChecked) -> {
            onlyLiked = isChecked;
            setMarkChecked(SortTag.liked(), isChecked);
            showDishes();
        });
        hideChip = addChip(inflater, "", R.drawable.ic_hide, DishStyle.COLOR_HIDE, onlyHidden, (button, isChecked) -> {
            onlyHidden = isChecked;
            showDishes();
        });
        arrangeTags();
    }

    private void setCategoryChecked(int index, boolean checked) {
        enabled[index] = checked;
        BudgetCategory frame = frames.get(index);
        if (checked) {
            pushTag(SortTag.category(frame));
        } else {
            removeTag(SortTag.category(frame));
        }
        arrangeTags();
    }

    private void setMarkChecked(SortTag tag, boolean checked) {
        if (checked) {
            pushTag(tag);
        } else {
            removeTag(tag);
        }
        arrangeTags();
    }

    private void arrangeTags() {
        List<View> order = new ArrayList<>();
        for (SortTag tag : selectedOrder) {
            order.add(chipFor(tag));
        }
        for (int i = 0; i < frames.size(); i++) {
            if (!selected(SortTag.category(frames.get(i)))) {
                order.add(categoryChips.get(i));
            }
        }
        if (!selected(SortTag.history())) {
            order.add(historyChip);
        }
        if (!selected(SortTag.liked())) {
            order.add(likeChip);
        }
        order.add(hideChip);
        TagMotion.applyOrder(chipRow, order);
    }

    private View chipFor(SortTag tag) {
        if (tag.kind == SortTag.HISTORY) {
            return historyChip;
        }
        if (tag.kind == SortTag.LIKED) {
            return likeChip;
        }
        return categoryChips.get(indexOfFrame(tag.category));
    }

    private void pushTag(SortTag tag) {
        removeTag(tag);
        selectedOrder.add(0, tag);
    }

    private void removeTag(SortTag tag) {
        for (int i = 0; i < selectedOrder.size(); i++) {
            if (selectedOrder.get(i).same(tag)) {
                selectedOrder.remove(i);
                return;
            }
        }
    }

    private boolean selected(SortTag tag) {
        for (SortTag item : selectedOrder) {
            if (item.same(tag)) {
                return true;
            }
        }
        return false;
    }

    private int indexOfFrame(BudgetCategory frame) {
        for (int i = 0; i < frames.size(); i++) {
            if (DishStyle.same(frames.get(i), frame)) {
                return i;
            }
        }
        return 0;
    }

    private View addChip(
            LayoutInflater inflater,
            String text,
            int icon,
            int color,
            boolean checked,
            RoundCheck.OnCheckedChangeListener listener
    ) {
        View chip = inflater.inflate(R.layout.item_budget_tag, chipRow, false);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(Math.round(8 * getResources().getDisplayMetrics().density));
        chip.setLayoutParams(params);
        chip.setBackground(DishStyle.bubble(chip, color));
        TextView label = chip.findViewById(R.id.tag_label);
        label.setTypeface(bold);
        label.setText(text);
        if (icon != 0) {
            label.setVisibility(View.GONE);
            ImageView image = chip.findViewById(R.id.tag_icon);
            int iconSize = Math.round(22 * getResources().getDisplayMetrics().density);
            image.getLayoutParams().width = iconSize;
            image.getLayoutParams().height = iconSize;
            image.setVisibility(View.VISIBLE);
            image.setImageResource(icon);
            image.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        }
        RoundCheck check = chip.findViewById(R.id.tag_check);
        check.setChecked(checked, false);
        check.setOnCheckedChangeListener(listener);
        chip.setOnClickListener(v -> check.toggle());
        return chip;
    }

    private void showDishes() {
        LinearLayout list = findViewById(R.id.restaurant_dishes);
        Map<Object, Integer> previous = DishMotion.capture(list);
        list.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        List<Dish> dishes = DishOrder.sorted(
                this,
                restaurantName,
                restaurant.dishes,
                categories,
                selectedOrder,
                onlyHidden
        );
        for (Dish dish : dishes) {
            View row = inflater.inflate(R.layout.item_offer_dish, list, false);
            row.setTag(dish.name + "\n" + dish.price);
            DishRow.bind(row, restaurantName, dish, categories, bold, regular);
            list.addView(row);
        }
        DishMotion.slide(list, previous);
    }
}
