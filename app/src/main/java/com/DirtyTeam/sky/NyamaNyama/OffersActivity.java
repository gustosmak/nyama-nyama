package com.DirtyTeam.sky.NyamaNyama;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
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
import java.util.List;
import java.util.Map;

public class OffersActivity extends AppCompatActivity {

    private final List<BudgetCategory> frames = new ArrayList<>();
    private final List<SortTag> selectedOrder = new ArrayList<>();
    private final List<View> categoryChips = new ArrayList<>();
    private boolean[] enabled = new boolean[0];
    private boolean onlyLiked;
    private boolean onlyHistory;
    private LinearLayout chipRow;
    private View historyChip;
    private View likeChip;
    private List<Offer> source = new ArrayList<>();
    private OffersAdapter adapter;

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
        List<BudgetCategory> budget = UserPrefs.getBudgetCategories(this);
        if (budget.isEmpty()) {
            budget = DishStyle.categories(this);
        }
        for (BudgetCategory category : budget) {
            if (DishStyle.inFrames(category, desire.frames)) {
                frames.add(category);
            }
        }
        for (BudgetCategory frame : desire.frames) {
            if (!DishStyle.inFrames(frame, frames)) {
                frames.add(frame);
            }
        }
        enabled = new boolean[frames.size()];
        for (int i = 0; i < enabled.length; i++) {
            enabled[i] = true;
        }
        showFilters(bold);
        source = matchingOffers(OffersRepository.load(this), desire, DishStyle.categories(this));

        RecyclerView list = findViewById(R.id.offers_list);
        LinearLayoutManager manager = new LinearLayoutManager(this);
        adapter = new OffersAdapter(
                DishStyle.categories(this),
                bold,
                AppFonts.montserratRegular(this)
        );
        adapter.show(arranged());
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

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.show(arranged());
        }
    }

    static void openRestaurantMap(Context context, String name) {
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

    private void showFilters(Typeface bold) {
        chipRow = findViewById(R.id.selected_categories);
        LayoutInflater inflater = LayoutInflater.from(this);
        chipRow.removeAllViews();
        categoryChips.clear();
        selectedOrder.clear();
        for (int i = 0; i < frames.size(); i++) {
            BudgetCategory frame = frames.get(i);
            int index = i;
            categoryChips.add(addChip(
                    inflater,
                    frame.name + "  " + frame.amount + " \u20BD",
                    0,
                    frame.color,
                    bold,
                    enabled[index],
                    (check, isChecked) -> {
                        setCategoryChecked(index, isChecked);
                        if (adapter != null) {
                            adapter.show(arranged());
                        }
                    }
            ));
            if (enabled[index]) {
                selectedOrder.add(SortTag.category(frame));
            }
        }
        historyChip = addChip(inflater, "", R.drawable.ic_history, DishStyle.COLOR_HISTORY, bold, onlyHistory, (check, isChecked) -> {
            onlyHistory = isChecked;
            setMarkChecked(SortTag.history(), isChecked);
            if (adapter != null) {
                adapter.show(arranged());
            }
        });
        likeChip = addChip(inflater, "", R.drawable.ic_heart, DishStyle.COLOR_LIKE, bold, onlyLiked, (check, isChecked) -> {
            onlyLiked = isChecked;
            setMarkChecked(SortTag.liked(), isChecked);
            if (adapter != null) {
                adapter.show(arranged());
            }
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
            Typeface bold,
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

    private List<Offer> arranged() {
        List<BudgetCategory> catalog = DishStyle.categories(this);
        List<Offer> result = new ArrayList<>();
        int[] ranks = new int[source.size()];
        int count = 0;
        for (Offer offer : source) {
            Offer copy = new Offer(offer.name, offer.travelTime);
            List<Dish> visible = new ArrayList<>();
            boolean[] hidden = new boolean[offer.dishes.size()];
            for (int i = 0; i < offer.dishes.size(); i++) {
                hidden[i] = UserPrefs.isHidden(this, offer.name, offer.dishes.get(i).name);
            }
            visible.addAll(DishOrder.withoutHidden(offer.dishes, hidden));
            copy.dishes.addAll(DishOrder.sorted(
                    this,
                    offer.name,
                    visible,
                    catalog,
                    selectedOrder,
                    false
            ));
            if (!copy.dishes.isEmpty()) {
                Dish first = copy.dishes.get(0);
                ranks[count] = DishOrder.tier(
                        first,
                        catalog,
                        selectedOrder,
                        false,
                        UserPrefs.isLiked(this, offer.name, first.name),
                        UserPrefs.isInHistory(this, offer.name, first.name),
                        false
                );
                result.add(copy);
                count++;
            }
        }
        int[] used = new int[result.size()];
        System.arraycopy(ranks, 0, used, 0, result.size());
        return DishOrder.byTier(result, used);
    }

    private List<Offer> matchingOffers(List<Offer> source, DesireSelection desire, List<BudgetCategory> catalog) {
        List<Offer> matching = new ArrayList<>();
        for (Offer offer : source) {
            Offer copy = new Offer(offer.name, offer.travelTime);
            for (Dish dish : offer.dishes) {
                BudgetCategory segment = DishStyle.segment(dish.price, catalog);
                if (fitsHunger(dish, desire.hungerLevel) && DishStyle.inFrames(segment, desire.frames)) {
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

    private static final class OffersAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private static final int TYPE_OFFER = 0;
        private static final int TYPE_SKELETON = 1;
        private static final int INITIAL = 8;
        private static final int PREFETCH = 8;
        private static final int SKELETONS = 2;

        private List<Offer> offers = new ArrayList<>();
        private final List<BudgetCategory> categories;
        private final Typeface bold;
        private final Typeface regular;
        private int loaded;
        private boolean skeletonsShown;

        private OffersAdapter(
                List<BudgetCategory> categories,
                Typeface bold,
                Typeface regular
        ) {
            this.categories = categories;
            this.bold = bold;
            this.regular = regular;
        }

        private void show(List<Offer> next) {
            offers = next;
            skeletonsShown = false;
            if (offers.isEmpty()) {
                loaded = 0;
            } else if (loaded < INITIAL) {
                loaded = INITIAL;
            }
            notifyDataSetChanged();
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
            offerHolder.name.setOnClickListener(v -> RestaurantActivity.open(v.getContext(), offer.name));
            offerHolder.pin.setOnClickListener(v -> openRestaurantMap(v.getContext(), offer.name));
            offerHolder.time.setText(offer.travelTime);
            offerHolder.time.setTypeface(bold);
            Map<Object, Integer> previous = DishMotion.capture(offerHolder.dishes);
            offerHolder.dishes.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(offerHolder.itemView.getContext());
            for (Dish dish : offer.dishes) {
                View row = inflater.inflate(R.layout.item_offer_dish, offerHolder.dishes, false);
                row.setTag(dish.name + "\n" + dish.price);
                DishRow.bind(row, offer.name, dish, categories, bold, regular);
                offerHolder.dishes.addView(row);
            }
            DishMotion.slide(offerHolder.dishes, previous);
        }

        @Override
        public int getItemCount() {
            if (offers.isEmpty()) {
                return 0;
            }
            return loaded + (skeletonsShown ? SKELETONS : 0);
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
