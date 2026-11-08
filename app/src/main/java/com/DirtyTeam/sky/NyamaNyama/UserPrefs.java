package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class UserPrefs {

    private static final String PREFS_NAME = "nyama_prefs";
    private static final String KEY_IS_REGISTERED = "is_registered";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_BUDGET_CATEGORIES = "budget_categories";
    private static final String KEY_DESIRE = "desire_selection";

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
}
