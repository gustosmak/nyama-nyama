package com.DirtyTeam.sky.NyamaNyama;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ScrollView;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class DesireActivity extends AppCompatActivity {

    private TagFlowLayout tags;
    private ScrollView scroll;
    private TextInputLayout commentLayout;
    private TextInputEditText comment;
    private SeekBar hungerSlider;
    private TextView hungerPopup;
    private Typeface bold;
    private String[] hungerPhrases;
    private static final int HUNGER_LOW = 0xFF6FBF73;
    private static final int HUNGER_MID = 0xFFF1B467;
    private static final int HUNGER_HIGH = 0xFFE23B3B;
    private static final int HUNGER_IDLE = 0xFFD8D5D0;

    private GradientDrawable popupBackground;
    private boolean reordering;
    private boolean hungerChosen;
    private int shownPhrase = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_desire);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.desire_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            int bottom = Math.max(systemBars.bottom, ime.bottom);
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, bottom);
            if (ime.bottom > 0 && comment != null && comment.hasFocus()) {
                scrollCommentIntoView();
            }
            return insets;
        });

        bold = AppFonts.montserratBold(this);
        Typeface regular = AppFonts.montserratRegular(this);
        hungerPhrases = getResources().getStringArray(R.array.hunger_phrases);

        TextView title = findViewById(R.id.desire_title);
        TextView hungerLabel = findViewById(R.id.desire_hunger_label);
        TextView frames = findViewById(R.id.budget_frames_title2);
        TextView commentTitle = findViewById(R.id.budget_frames_title);
        TextView hungerLow = findViewById(R.id.hunger_end_low);
        TextView hungerHigh = findViewById(R.id.hunger_end_high);
        TextView next = findViewById(R.id.desire_next);
        hungerPopup = findViewById(R.id.hunger_popup);
        title.setTypeface(bold);
        hungerLabel.setTypeface(regular);
        frames.setTypeface(bold);
        commentTitle.setTypeface(bold);
        hungerLow.setTypeface(regular);
        hungerHigh.setTypeface(regular);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            frames.setFontVariationSettings("'wght' 700");
            commentTitle.setFontVariationSettings("'wght' 700");
        }
        hungerPopup.setTypeface(bold);
        popupBackground = new GradientDrawable();
        popupBackground.setCornerRadius(16f * getResources().getDisplayMetrics().density);
        popupBackground.setColor(HUNGER_IDLE);
        hungerPopup.setBackground(popupBackground);
        next.setTypeface(bold);

        scroll = findViewById(R.id.desire_scroll);
        findViewById(R.id.desire_back).setOnClickListener(v -> openHome());
        next.setOnClickListener(v -> {
            if (!hungerChosen || !hasSelectedTag()) {
                showInvalidWarning(next);
                return;
            }
            int phrase = Math.max(0, Math.min(hungerPhrases.length - 1, shownPhrase));
            UserPrefs.saveDesireSelection(this, new DesireSelection(
                    hungerSlider.getProgress(),
                    hungerPhrases[phrase],
                    selectedFrames(),
                    comment.getText() == null ? "" : comment.getText().toString().trim()
            ));
            startActivity(new Intent(this, OffersActivity.class));
        });

        commentLayout = findViewById(R.id.desire_comment_input);
        comment = findViewById(R.id.desire_comment);
        commentLayout.setTypeface(regular);
        comment.setTypeface(regular);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            comment.setFontVariationSettings("'wght' 400");
        }
        commentLayout.setBoxStrokeColorStateList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.nyama_orange)
        ));
        comment.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(100),
                (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        if (source.charAt(i) == '\n') {
                            return "";
                        }
                    }
                    return null;
                }
        });
        comment.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideCommentKeyboard();
                return true;
            }
            return false;
        });
        View commentDone = findViewById(R.id.comment_done);
        commentDone.setFocusable(false);
        commentDone.setOnClickListener(v -> hideCommentKeyboard());
        comment.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateCommentDone(commentDone);
            }
        });
        comment.setOnFocusChangeListener((v, hasFocus) -> {
            next.setVisibility(hasFocus ? View.GONE : View.VISIBLE);
            updateCommentDone(commentDone);
            if (hasFocus) {
                scrollCommentIntoView();
            }
        });

        hungerSlider = findViewById(R.id.hunger_slider);
        hungerSlider.setThumbOffset(0);
        hungerSlider.setProgressTintList(null);
        hungerSlider.setProgressBackgroundTintList(null);
        hungerSlider.setThumbTintList(null);
        hungerSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    hungerChosen = true;
                    showHungerPhrase(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                hungerChosen = true;
                showHungerPhrase(seekBar.getProgress());
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        tags = findViewById(R.id.budget_tags);
        for (BudgetCategory category : UserPrefs.getBudgetCategories(this)) {
            addTag(category);
        }
    }

    private void openHome() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private void updateCommentDone(View commentDone) {
        boolean editing = comment.hasFocus();
        boolean hasText = comment.getText() != null && comment.getText().length() > 0;
        commentDone.setVisibility(editing && hasText ? View.VISIBLE : View.GONE);
    }

    private void hideCommentKeyboard() {
        InputMethodManager manager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(comment.getWindowToken(), 0);
        }
        comment.clearFocus();
    }

    private void scrollCommentIntoView() {
        comment.post(() -> {
            int[] fieldOnScreen = new int[2];
            int[] scrollOnScreen = new int[2];
            comment.getLocationOnScreen(fieldOnScreen);
            scroll.getLocationOnScreen(scrollOnScreen);
            int gap = Math.round(12 * getResources().getDisplayMetrics().density);
            int visibleTop = scrollOnScreen[1];
            int visibleBottom = visibleTop + scroll.getHeight();
            int desiredTop = visibleBottom - comment.getHeight() - gap;
            if (desiredTop < visibleTop + gap) {
                desiredTop = visibleTop + gap;
            }
            scroll.smoothScrollBy(0, fieldOnScreen[1] - desiredTop);
        });
    }

    private List<BudgetCategory> selectedFrames() {
        List<BudgetCategory> selected = new ArrayList<>();
        for (int i = 0; i < tags.getChildCount(); i++) {
            View child = tags.getChildAt(i);
            CheckBox check = child.findViewById(R.id.tag_check);
            if (check.isChecked() && child.getTag() instanceof BudgetCategory) {
                selected.add((BudgetCategory) child.getTag());
            }
        }
        return selected;
    }

    private void showHungerPhrase(int progress) {
        popupBackground.setColor(hungerColor(progress));
        int index = Math.round(progress * (hungerPhrases.length - 1) / 100f);
        index = Math.max(0, Math.min(hungerPhrases.length - 1, index));
        if (index == shownPhrase) {
            return;
        }
        shownPhrase = index;
        hungerPopup.setText(hungerPhrases[index]);
        hungerPopup.setScaleX(0.86f);
        hungerPopup.setScaleY(0.86f);
        hungerPopup.setAlpha(0f);
        hungerPopup.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(180)
                .setInterpolator(new OvershootInterpolator())
                .start();
    }

    private int hungerColor(int progress) {
        float fraction = progress / 100f;
        if (fraction < 0.5f) {
            return blend(HUNGER_LOW, HUNGER_MID, fraction / 0.5f);
        }
        return blend(HUNGER_MID, HUNGER_HIGH, (fraction - 0.5f) / 0.5f);
    }

    private int blend(int start, int end, float fraction) {
        int red = (int) (Color.red(start) + (Color.red(end) - Color.red(start)) * fraction);
        int green = (int) (Color.green(start) + (Color.green(end) - Color.green(start)) * fraction);
        int blue = (int) (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * fraction);
        return Color.rgb(red, green, blue);
    }

    private void addTag(BudgetCategory category) {
        View tag = getLayoutInflater().inflate(R.layout.item_budget_tag, tags, false);
        tag.setTag(category);
        tag.setBackground(bubble(category.color));
        CheckBox check = tag.findViewById(R.id.tag_check);
        TextView label = tag.findViewById(R.id.tag_label);
        label.setTypeface(bold);
        label.setText(category.name + "  " + category.amount + " " + getString(R.string.ruble_sign));
        check.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        check.setOnCheckedChangeListener((button, isChecked) -> moveCheckedToFront(isChecked ? tag : null));
        tag.setOnClickListener(v -> check.toggle());
        tags.addView(tag);
    }

    private void moveCheckedToFront(View rising) {
        if (reordering) {
            return;
        }
        reordering = true;
        Map<View, int[]> starts = new IdentityHashMap<>();
        List<View> selected = new ArrayList<>();
        List<View> rest = new ArrayList<>();
        for (int i = 0; i < tags.getChildCount(); i++) {
            View child = tags.getChildAt(i);
            starts.put(child, new int[]{child.getLeft(), child.getTop()});
            CheckBox check = child.findViewById(R.id.tag_check);
            if (check.isChecked()) {
                selected.add(child);
            } else {
                rest.add(child);
            }
        }
        tags.removeAllViews();
        for (View child : selected) {
            tags.addView(child);
        }
        for (View child : rest) {
            tags.addView(child);
        }
        tags.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                tags.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                float extra = 28 * getResources().getDisplayMetrics().density;
                for (int i = 0; i < tags.getChildCount(); i++) {
                    View child = tags.getChildAt(i);
                    int[] start = starts.get(child);
                    if (start == null) {
                        continue;
                    }
                    float dx = start[0] - child.getLeft();
                    float dy = start[1] - child.getTop();
                    boolean lift = child == rising;
                    if (lift) {
                        dy += extra;
                        child.setTranslationZ(extra);
                    }
                    if (dx == 0f && dy == 0f) {
                        continue;
                    }
                    child.setTranslationX(dx);
                    child.setTranslationY(dy);
                    child.animate()
                            .translationX(0f)
                            .translationY(0f)
                            .translationZ(0f)
                            .setDuration(lift ? 460 : 280)
                            .setInterpolator(lift ? new OvershootInterpolator(0.8f) : new DecelerateInterpolator())
                            .start();
                }
                reordering = false;
            }
        });
    }

    private boolean hasSelectedTag() {
        for (int i = 0; i < tags.getChildCount(); i++) {
            CheckBox check = tags.getChildAt(i).findViewById(R.id.tag_check);
            if (check.isChecked()) {
                return true;
            }
        }
        return false;
    }

    private void showInvalidWarning(View anchor) {
        Snackbar snackbar = Snackbar.make(
                findViewById(R.id.desire_root),
                R.string.desire_invalid,
                Snackbar.LENGTH_LONG
        );
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.nyama_orange));
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.nyama_gray));
        snackbar.setAnchorView(anchor);
        snackbar.show();
    }

    private GradientDrawable bubble(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        float radius = 20 * getResources().getDisplayMetrics().density;
        drawable.setCornerRadius(radius);
        drawable.setColor(Color.argb(0x40, Color.red(color), Color.green(color), Color.blue(color)));
        drawable.setStroke(Math.max(1, Math.round(getResources().getDisplayMetrics().density)), color);
        return drawable;
    }
}
