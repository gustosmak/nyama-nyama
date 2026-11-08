package com.DirtyTeam.sky.NyamaNyama;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OffersActivity extends AppCompatActivity {

    private static final int COLOR_GREEN = 0xFF6FBF73;
    private static final int COLOR_RED_ORANGE = 0xFFE06A45;
    private static final int COLOR_PURPLE = 0xFFB388EB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_offers);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.offers_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.offers_back).setOnClickListener(v -> finish());

        DesireSelection desire = UserPrefs.getDesireSelection(this);
        Typeface bold = AppFonts.montserratBold(this);
        TextView title = findViewById(R.id.offers_title);
        title.setTypeface(bold);
        showSelectedFrames(desire.frames, bold);

        RecyclerView list = findViewById(R.id.offers_list);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        OffersAdapter adapter = new OffersAdapter(
                matchingOffers(OffersRepository.load(this), desire),
                categories(),
                bold,
                AppFonts.montserratRegular(this)
        );
        list.setLayoutManager(manager);
        list.setAdapter(adapter);
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            private boolean fastDown;
            private boolean revealing;

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    fastDown = false;
                    if (adapter.hasUnloaded()) {
                        adapter.reveal();
                    } else {
                        adapter.ensureAhead(manager.findLastVisibleItemPosition());
                    }
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy >= 120) {
                    fastDown = true;
                }
                int last = manager.findLastVisibleItemPosition();
                if (fastDown && dy > 0 && last >= adapter.loadedCount() - 1) {
                    if (adapter.showUnloaded() && !revealing) {
                        revealing = true;
                        recyclerView.stopScroll();
                        recyclerView.postDelayed(() -> {
                            adapter.reveal();
                            revealing = false;
                            fastDown = false;
                        }, 120);
                    }
                    return;
                }
                if (!fastDown) {
                    adapter.ensureAhead(last);
                }
            }
        });
    }

    private static void openRestaurantMap(Context context, String name) {
        Uri geo = Uri.parse("geo:0,0?q=" + Uri.encode(name));
        Intent map = new Intent(Intent.ACTION_VIEW, geo);
        try {
            context.startActivity(map);
        } catch (ActivityNotFoundException first) {
            Uri web = Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(name));
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, web));
            } catch (ActivityNotFoundException ignored) {
            }
        }
    }

    private void showSelectedFrames(List<BudgetCategory> frames, Typeface bold) {
        LinearLayout row = findViewById(R.id.selected_categories);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (BudgetCategory frame : frames) {
            TextView chip = (TextView) inflater.inflate(R.layout.item_selected_frame, row, false);
            chip.setTypeface(bold);
            chip.setText(frame.name + "  " + frame.amount + " \u20BD");
            chip.setBackground(frameBubble(frame.color));
            row.addView(chip);
        }
    }

    private List<Offer> matchingOffers(List<Offer> source, DesireSelection desire) {
        List<Offer> matching = new ArrayList<>();
        for (Offer offer : source) {
            Offer copy = new Offer(offer.name, offer.travelTime);
            for (Dish dish : offer.dishes) {
                if (fitsHunger(dish, desire.hungerLevel) && fitsPrice(dish, desire.frames)) {
                    copy.dishes.add(dish);
                }
            }
            if (!copy.dishes.isEmpty()) {
                matching.add(copy);
            }
        }
        return matching;
    }

    private boolean fitsHunger(Dish dish, int hunger) {
        return Math.abs(dish.satietyLevel - hunger) <= 20;
    }

    private boolean fitsPrice(Dish dish, List<BudgetCategory> frames) {
        for (BudgetCategory frame : frames) {
            if (dish.price <= frame.amount + 100) {
                return true;
            }
        }
        return false;
    }

    private GradientDrawable frameBubble(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        float density = getResources().getDisplayMetrics().density;
        drawable.setCornerRadius(18f * density);
        drawable.setStroke(Math.max(1, Math.round(density)), color);
        drawable.setColor(Color.argb(0x40, Color.red(color), Color.green(color), Color.blue(color)));
        return drawable;
    }

    private List<BudgetCategory> categories() {
        List<BudgetCategory> saved = new ArrayList<>(UserPrefs.getBudgetCategories(this));
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

    private static final class OffersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_OFFER = 0;
        private static final int TYPE_SKELETON = 1;
        private static final int INITIAL = 8;
        private static final int PREFETCH = 8;
        private static final int SKELETONS = 2;

        private final List<Offer> offers;
        private final List<BudgetCategory> categories;
        private final Typeface bold;
        private final Typeface regular;
        private int loaded;
        private boolean skeletonsShown;

        private OffersAdapter(
                List<Offer> offers,
                List<BudgetCategory> categories,
                Typeface bold,
                Typeface regular
        ) {
            this.offers = offers;
            this.categories = categories;
            this.bold = bold;
            this.regular = regular;
            this.loaded = offers.isEmpty() ? 0 : INITIAL;
        }

        private int loadedCount() {
            return loaded;
        }

        private boolean hasUnloaded() {
            return skeletonsShown;
        }

        private void ensureAhead(int lastVisible) {
            if (offers.isEmpty() || skeletonsShown || lastVisible < 0) {
                return;
            }
            if (loaded - lastVisible > 5) {
                return;
            }
            int insertAt = loaded;
            loaded += PREFETCH;
            notifyItemRangeInserted(insertAt, PREFETCH);
        }

        private boolean showUnloaded() {
            if (offers.isEmpty() || skeletonsShown) {
                return false;
            }
            skeletonsShown = true;
            notifyItemRangeInserted(loaded, SKELETONS);
            return true;
        }

        private void reveal() {
            if (!skeletonsShown) {
                return;
            }
            skeletonsShown = false;
            notifyItemRangeRemoved(loaded, SKELETONS);
            int insertAt = loaded;
            loaded += PREFETCH;
            notifyItemRangeInserted(insertAt, PREFETCH);
        }

        @Override
        public int getItemViewType(int position) {
            return position < loaded ? TYPE_OFFER : TYPE_SKELETON;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_SKELETON) {
                return new RecyclerView.ViewHolder(
                        inflater.inflate(R.layout.item_offer_skeleton, parent, false)
                ) {
                };
            }
            return new Holder(inflater.inflate(R.layout.item_offer, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (!(holder instanceof Holder) || offers.isEmpty()) {
                return;
            }
            Holder offerHolder = (Holder) holder;
            Offer offer = offers.get(position % offers.size());
            offerHolder.name.setText(offer.name);
            offerHolder.name.setTypeface(bold);
            offerHolder.pin.setOnClickListener(v -> openRestaurantMap(v.getContext(), offer.name));
            offerHolder.time.setText(offer.travelTime);
            offerHolder.time.setTypeface(bold);
            offerHolder.dishes.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(offerHolder.itemView.getContext());
            for (Dish dish : offer.dishes) {
                View row = inflater.inflate(R.layout.item_offer_dish, offerHolder.dishes, false);
                row.setBackground(bubble(offerHolder.itemView, colorFor(dish.price)));
                TextView dishName = row.findViewById(R.id.dish_name);
                TextView satiety = row.findViewById(R.id.dish_satiety);
                TextView price = row.findViewById(R.id.dish_price);
                TextView calories = row.findViewById(R.id.dish_calories);
                dishName.setTypeface(bold);
                satiety.setTypeface(regular);
                price.setTypeface(bold);
                calories.setTypeface(bold);
                dishName.setText(dish.name);
                satiety.setText(dish.satiety);
                price.setText(dish.price + " \u20BD");
                calories.setText(dish.calories);
                offerHolder.dishes.addView(row);
            }
        }

        @Override
        public int getItemCount() {
            if (offers.isEmpty()) {
                return 0;
            }
            return loaded + (skeletonsShown ? SKELETONS : 0);
        }

        private int colorFor(int price) {
            for (BudgetCategory category : categories) {
                if (price <= category.amount) {
                    return category.color;
                }
            }
            return categories.get(categories.size() - 1).color;
        }

        private GradientDrawable bubble(View view, int color) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.RECTANGLE);
            float density = view.getResources().getDisplayMetrics().density;
            drawable.setCornerRadius(20f * density);
            drawable.setStroke(Math.max(1, Math.round(density)), color);
            drawable.setColor(Color.argb(0x40, Color.red(color), Color.green(color), Color.blue(color)));
            return drawable;
        }

        private static final class Holder extends RecyclerView.ViewHolder {
            private final TextView name;
            private final TextView time;
            private final ImageButton pin;
            private final LinearLayout dishes;

            private Holder(View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.offer_name);
                time = itemView.findViewById(R.id.offer_time);
                pin = itemView.findViewById(R.id.offer_pin);
                dishes = itemView.findViewById(R.id.offer_dishes);
            }
        }
    }
}
