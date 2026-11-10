package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class UserPrefs {

    private static final String PREFS_NAME = "nyama_prefs";
    private static final String KEY_IS_REGISTERED = "is_registered";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_BUDGET_CATEGORIES = "budget_categories";
    private static final String KEY_DESIRE = "desire_selection";
    private static final String KEY_DISH_MARKS = "dish_marks";

    private UserPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isRegistered(Context context) {
        return prefs(context).getBoolean(KEY_IS_REGISTERED, false);
    }

    public static void setRegistered(Context context, boolean registered) {
        prefs(context).edit().putBoolean(KEY_IS_REGISTERED, registered).apply();
    }

    public static void saveAccount(Context context, String email, String password) {
        prefs(context).edit()
                .putString(KEY_EMAIL, email)
                .putString(KEY_PASSWORD, password)
                .apply();
    }

    public static String getEmail(Context context) {
        String email = prefs(context).getString(KEY_EMAIL, "");
        return email == null ? "" : email;
    }

    public static String getPassword(Context context) {
        String password = prefs(context).getString(KEY_PASSWORD, "");
        return password == null ? "" : password;
    }

    public static void saveBudgetCategories(Context context, List<BudgetCategory> categories) {
        JSONArray array = new JSONArray();
        for (BudgetCategory category : categories) {
            JSONObject object = new JSONObject();
            try {
                object.put("name", category.name);
                object.put("amount", category.amount);
                object.put("color", category.color);
                object.put("custom", category.custom);
            } catch (JSONException ignored) {
                continue;
            }
            array.put(object);
        }
        prefs(context).edit().putString(KEY_BUDGET_CATEGORIES, array.toString()).apply();
    }

    public static List<BudgetCategory> getBudgetCategories(Context context) {
        List<BudgetCategory> categories = new ArrayList<>();
        String raw = prefs(context).getString(KEY_BUDGET_CATEGORIES, "[]");
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.getJSONObject(i);
                categories.add(new BudgetCategory(
                        object.optString("name"),
                        object.optInt("amount"),
                        object.optInt("color", 0xFFF1B467),
                        !object.has("custom") || object.optBoolean("custom")
                ));
            }
        } catch (JSONException ignored) {
            return categories;
        }
        return categories;
    }

    public static void saveDesireSelection(Context context, DesireSelection selection) {
        JSONObject object = new JSONObject();
        JSONArray frames = new JSONArray();
        try {
            object.put("hunger", selection.hungerLevel);
            object.put("phrase", selection.hungerPhrase);
            object.put("comment", selection.comment);
            for (BudgetCategory category : selection.frames) {
                JSONObject frame = new JSONObject();
                frame.put("name", category.name);
                frame.put("amount", category.amount);
                frame.put("color", category.color);
                frame.put("custom", category.custom);
                frames.put(frame);
            }
            object.put("frames", frames);
        } catch (JSONException ignored) {
            return;
        }
        prefs(context).edit().putString(KEY_DESIRE, object.toString()).apply();
    }

    public static DesireSelection getDesireSelection(Context context) {
        List<BudgetCategory> frames = new ArrayList<>();
        String raw = prefs(context).getString(KEY_DESIRE, "");
        if (raw == null || raw.isEmpty()) {
            return new DesireSelection(0, "", frames, "");
        }
        try {
            JSONObject object = new JSONObject(raw);
            JSONArray array = object.optJSONArray("frames");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject frame = array.getJSONObject(i);
                    frames.add(new BudgetCategory(
                            frame.optString("name"),
                            frame.optInt("amount"),
                            frame.optInt("color", 0xFFF1B467),
                            frame.optBoolean("custom")
                    ));
                }
            }
            return new DesireSelection(
                    object.optInt("hunger"),
                    object.optString("phrase"),
                    frames,
                    object.optString("comment")
            );
        } catch (JSONException ignored) {
            return new DesireSelection(0, "", frames, "");
        }
    }

    public static boolean isLiked(Context context, String restaurant, String dish) {
        JSONObject item = mark(context, restaurant, dish);
        return item.optBoolean("liked") && !item.optBoolean("hidden");
    }

    public static boolean isInHistory(Context context, String restaurant, String dish) {
        return mark(context, restaurant, dish).optBoolean("history");
    }

    public static void setLiked(Context context, String restaurant, String dish, boolean liked) {
        setMark(context, restaurant, dish, "liked", liked);
    }

    public static void setInHistory(Context context, String restaurant, String dish, boolean history) {
        setMark(context, restaurant, dish, "history", history);
    }

    public static boolean isHidden(Context context, String restaurant, String dish) {
        return mark(context, restaurant, dish).optBoolean("hidden");
    }

    public static List<MarkedDish> markedDishes(Context context) {
        JSONObject all = marks(context);
        List<MarkedDish> result = new ArrayList<>();
        Iterator<String> keys = all.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            JSONObject item = all.optJSONObject(key);
            if (item == null) {
                continue;
            }
            int split = key.indexOf('\n');
            String restaurant = split < 0 ? "" : key.substring(0, split);
            String dish = split < 0 ? key : key.substring(split + 1);
            boolean hidden = item.optBoolean("hidden");
            boolean liked = item.optBoolean("liked") && !hidden;
            boolean history = item.optBoolean("history");
            if (!liked && !history && !hidden) {
                continue;
            }
            result.add(new MarkedDish(restaurant, dish, liked, history, hidden));
        }
        return result;
    }

    public static final class MarkedDish {
        public final String restaurant;
        public final String dish;
        public final boolean liked;
        public final boolean history;
        public final boolean hidden;

        MarkedDish(String restaurant, String dish, boolean liked, boolean history, boolean hidden) {
            this.restaurant = restaurant;
            this.dish = dish;
            this.liked = liked;
            this.history = history;
            this.hidden = hidden;
        }
    }

    public static void setHidden(Context context, String restaurant, String dish, boolean hidden) {
        setMark(context, restaurant, dish, "hidden", hidden);
    }

    static boolean[] exclusive(boolean liked, boolean hidden, String field, boolean value) {
        if ("liked".equals(field)) {
            liked = value;
        }
        if ("hidden".equals(field)) {
            hidden = value;
        }
        if (value && "liked".equals(field)) {
            hidden = false;
        }
        if (value && "hidden".equals(field)) {
            liked = false;
        }
        if (liked && hidden) {
            if ("hidden".equals(field)) {
                liked = false;
            } else {
                hidden = false;
            }
        }
        return new boolean[]{liked, hidden};
    }

    static void applyMark(JSONObject item, String field, boolean value) throws JSONException {
        boolean[] pair = exclusive(item.optBoolean("liked"), item.optBoolean("hidden"), field, value);
        item.put("liked", pair[0]);
        item.put("hidden", pair[1]);
        if (!"liked".equals(field) && !"hidden".equals(field)) {
            item.put(field, value);
        }
    }

    private static JSONObject mark(Context context, String restaurant, String dish) {
        JSONObject item = marks(context).optJSONObject(dishKey(restaurant, dish));
        return item == null ? new JSONObject() : item;
    }

    private static void setMark(
            Context context,
            String restaurant,
            String dish,
            String field,
            boolean value
    ) {
        JSONObject all = marks(context);
        String key = dishKey(restaurant, dish);
        JSONObject item = all.optJSONObject(key);
        if (item == null) {
            item = new JSONObject();
        }
        try {
            applyMark(item, field, value);
            if (!item.optBoolean("liked") && !item.optBoolean("history") && !item.optBoolean("hidden")) {
                all.remove(key);
            } else {
                all.put(key, item);
            }
        } catch (JSONException ignored) {
            return;
        }
        prefs(context).edit().putString(KEY_DISH_MARKS, all.toString()).apply();
    }

    private static JSONObject marks(Context context) {
        String raw = prefs(context).getString(KEY_DISH_MARKS, "{}");
        try {
            return new JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private static String dishKey(String restaurant, String dish) {
        return restaurant + "\n" + dish;
    }
}
