package com.DirtyTeam.sky.NyamaNyama;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BudgetActivity extends AppCompatActivity {

    private static final int STEP = 10;
    private static final int NAME_LIMIT = 30;
    private static final int AMOUNT_LIMIT = 7;
    private static final int MAX_AMOUNT = 9_999_999;
    private static final int COLOR_GREEN = 0xFF6FBF73;
    private static final int COLOR_RED_ORANGE = 0xFFE06A45;
    private static final int COLOR_PURPLE = 0xFFB388EB;

    private LinearLayout rows;
    private Typeface bold;
    private Typeface regular;
    private int mutedColor;
    private int activeColor;
    private int descriptionColor;
    private final Random random = new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_budget);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.budget_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bold = AppFonts.montserratBold(this);
        regular = AppFonts.montserratRegular(this);
        mutedColor = ContextCompat.getColor(this, R.color.nyama_text_secondary);
        activeColor = ContextCompat.getColor(this, R.color.white);
        descriptionColor = ContextCompat.getColor(this, R.color.nyama_text_muted);

        findViewById(R.id.budget_back).setOnClickListener(v -> finish());

        TextView title = findViewById(R.id.budget_title);
        title.setTypeface(bold);

        rows = findViewById(R.id.budget_rows);
        List<BudgetCategory> saved = UserPrefs.getBudgetCategories(this);
        if (saved.isEmpty()) {
            addRow(getString(R.string.budget_low), getString(R.string.budget_low_desc), 300, COLOR_GREEN, false);
            addRow(getString(R.string.budget_mid), getString(R.string.budget_mid_desc), 500, COLOR_RED_ORANGE, false);
            addRow(getString(R.string.budget_high), getString(R.string.budget_high_desc), 1000, COLOR_PURPLE, false);
        } else {
            for (BudgetCategory category : saved) {
                addRow(category.name, presetDescription(category), category.amount, category.color, category.custom);
            }
        }

        MaterialButton addCustom = findViewById(R.id.add_custom_budget);
        AppFonts.applyMontserratBold(addCustom);
        addCustom.setOnClickListener(v -> addRow("", null, 300, randomAccent(), true));

        TextView next = findViewById(R.id.budget_next);
        next.setTypeface(bold);
        next.setOnClickListener(v -> {
            if (!categoriesAreValid()) {
                showInvalidWarning(next);
                return;
            }
            UserPrefs.saveBudgetCategories(this, collectCategories());
            startActivity(new Intent(this, DesireActivity.class));
        });
    }

    private void addRow(String name, String description, int amount, int accent, boolean custom) {
        View row = getLayoutInflater().inflate(R.layout.item_budget_row, rows, false);
        row.setTag(new RowState(accent, custom));
        row.setBackground(bubble(accent));

        EditText nameField = row.findViewById(R.id.budget_name);
        TextView descriptionView = row.findViewById(R.id.budget_description);
        EditText amountField = row.findViewById(R.id.budget_amount);
        TextView currency = row.findViewById(R.id.budget_currency);
        ImageButton minus = row.findViewById(R.id.budget_minus);
        ImageButton plus = row.findViewById(R.id.budget_plus);
        ImageButton delete = row.findViewById(R.id.budget_delete);
        minus.setImageResource(R.drawable.ic_minus);
        plus.setImageResource(R.drawable.ic_plus);

        nameField.setTypeface(bold);
        amountField.setTypeface(bold);
        currency.setTypeface(bold);
        descriptionView.setTypeface(regular);
        nameField.setTextColor(mutedColor);
        amountField.setTextColor(mutedColor);
        currency.setTextColor(mutedColor);
        descriptionView.setTextColor(descriptionColor);
        nameField.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(NAME_LIMIT),
                (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        if (source.charAt(i) == '\n') {
                            return "";
                        }
                    }
                    return null;
                }
        });

        nameField.setText(name);
        amountField.setText(String.valueOf(amount));
        if (description == null || description.isEmpty()) {
            descriptionView.setVisibility(View.GONE);
        } else {
            descriptionView.setText(description);
            descriptionView.setVisibility(View.VISIBLE);
        }
        if (!custom) {
            nameField.setFocusable(false);
            nameField.setClickable(false);
            nameField.setCursorVisible(false);
            nameField.setInputType(InputType.TYPE_NULL);
            nameField.setHint("");
        } else {
            nameField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    nameField.setTextColor(activeColor);
                    if (s != null && s.length() > 0) {
                        nameField.setHint(null);
                    }
                }
            });
        }
        amountField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(AMOUNT_LIMIT)});
        nameField.setSingleLine(false);
        nameField.setHorizontallyScrolling(false);
        nameField.setMaxLines(4);
        amountField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                amountField.setTextColor(activeColor);
                currency.setTextColor(activeColor);
                fitAmount(amountField);
            }
        });
        fitAmount(amountField);

        minus.setOnClickListener(v -> changeAmount(amountField, -STEP));
        plus.setOnClickListener(v -> changeAmount(amountField, STEP));
        delete.setOnClickListener(v -> rows.removeView(row));
        rows.addView(row);
    }

    private String presetDescription(BudgetCategory category) {
        if (category.custom) {
            return null;
        }
        if (category.name.equals(getString(R.string.budget_low))) {
            return getString(R.string.budget_low_desc);
        }
        if (category.name.equals(getString(R.string.budget_mid))) {
            return getString(R.string.budget_mid_desc);
        }
        if (category.name.equals(getString(R.string.budget_high))) {
            return getString(R.string.budget_high_desc);
        }
        return null;
    }

    private List<BudgetCategory> collectCategories() {
        List<BudgetCategory> categories = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            EditText nameField = row.findViewById(R.id.budget_name);
            EditText amountField = row.findViewById(R.id.budget_amount);
            String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
            RowState state = row.getTag() instanceof RowState
                    ? (RowState) row.getTag()
                    : new RowState(COLOR_GREEN, true);
            categories.add(new BudgetCategory(name, amountValue(amountField), state.color, state.custom));
        }
        return categories;
    }

    private boolean categoriesAreValid() {
        if (rows.getChildCount() == 0) {
            return false;
        }
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            EditText nameField = row.findViewById(R.id.budget_name);
            EditText amountField = row.findViewById(R.id.budget_amount);
            String name = nameField.getText() == null ? "" : nameField.getText().toString().trim();
            if (name.isEmpty() || amountValue(amountField) == 0) {
                return false;
            }
        }
        return true;
    }

    private void showInvalidWarning(View anchor) {
        Snackbar snackbar = Snackbar.make(
                findViewById(R.id.budget_root),
                R.string.budget_invalid,
                Snackbar.LENGTH_LONG
        );
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.nyama_orange));
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.nyama_gray));
        snackbar.setAnchorView(anchor);
        snackbar.show();
    }

    private int amountValue(EditText amountField) {
        try {
            String raw = amountField.getText() == null ? "" : amountField.getText().toString().trim();
            if (raw.isEmpty()) {
                return 0;
            }
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void fitAmount(EditText amountField) {
        amountField.post(() -> {
            int available = amountField.getWidth() - amountField.getPaddingLeft() - amountField.getPaddingRight();
            CharSequence text = amountField.getText();
            if (available <= 0 || text == null) {
                return;
            }
            float size = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 15, getResources().getDisplayMetrics());
            float min = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 9, getResources().getDisplayMetrics());
            TextPaint paint = new TextPaint(amountField.getPaint());
            paint.setTextSize(size);
            while (size > min && paint.measureText(text, 0, text.length()) > available) {
                size -= TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, 1, getResources().getDisplayMetrics());
                paint.setTextSize(size);
            }
            if (amountField.getTextSize() != size) {
                amountField.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
            }
        });
    }

    private GradientDrawable bubble(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(20));
        drawable.setColor(Color.argb(0x40, Color.red(color), Color.green(color), Color.blue(color)));
        drawable.setStroke(Math.max(1, dp(1)), color);
        return drawable;
    }

    private int randomAccent() {
        float[] hsv = new float[]{random.nextInt(360), 0.45f + random.nextFloat() * 0.3f, 0.72f + random.nextFloat() * 0.2f};
        return Color.HSVToColor(hsv);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void changeAmount(EditText amountField, int delta) {
        int value = 0;
        try {
            String raw = amountField.getText() == null ? "" : amountField.getText().toString().trim();
            if (!raw.isEmpty()) {
                value = Integer.parseInt(raw);
            }
        } catch (NumberFormatException ignored) {
            value = 0;
        }
        int next = Math.min(MAX_AMOUNT, Math.max(0, value + delta));
        amountField.setText(String.valueOf(next));
        amountField.setSelection(amountField.getText().length());
    }

    private static final class RowState {
        final int color;
        final boolean custom;

        RowState(int color, boolean custom) {
            this.color = color;
            this.custom = custom;
        }
    }
}
