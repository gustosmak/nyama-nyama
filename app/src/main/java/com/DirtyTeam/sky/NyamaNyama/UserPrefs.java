package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.content.SharedPreferences;

public final class UserPrefs {

    private static final String PREFS_NAME = "nyama_prefs";
    private static final String KEY_IS_REGISTERED = "is_registered";

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
}
