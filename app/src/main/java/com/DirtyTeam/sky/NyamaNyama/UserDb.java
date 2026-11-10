package com.DirtyTeam.sky.NyamaNyama;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public final class UserDb extends SQLiteOpenHelper {

    public static final String TABLE_LIKES = "user_likes";
    public static final String TABLE_HISTORY = "user_history";
    public static final String TABLE_DISLIKES = "user_dislikes";

    private static final String NAME = "users.db";
    private static final int VERSION = 1;

    private static UserDb instance;

    private UserDb(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    static UserDb get(Context context) {
        if (instance == null) {
            instance = new UserDb(context);
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE user_info ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "login TEXT UNIQUE NOT NULL,"
                        + "password TEXT NOT NULL)"
        );
        db.execSQL(markTable(TABLE_LIKES));
        db.execSQL(markTable(TABLE_HISTORY));
        db.execSQL(markTable(TABLE_DISLIKES));
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    static long register(Context context, String login, String password) {
        ContentValues values = new ContentValues();
        values.put("login", login);
        values.put("password", password);
        return get(context).getWritableDatabase().insert("user_info", null, values);
    }

    static long find(Context context, String login, String password) {
        Cursor cursor = get(context).getReadableDatabase().rawQuery(
                "SELECT id FROM user_info WHERE login = ? AND password = ?",
                new String[]{login, password}
        );
        try {
            if (!cursor.moveToFirst()) {
                return -1L;
            }
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    static void setMark(
            Context context,
            long userId,
            long restaurantId,
            long dishId,
            String table,
            boolean on
    ) {
        if (!TABLE_LIKES.equals(table) && !TABLE_HISTORY.equals(table) && !TABLE_DISLIKES.equals(table)) {
            return;
        }
        SQLiteDatabase db = get(context).getWritableDatabase();
        String[] keys = new String[]{
                String.valueOf(userId),
                String.valueOf(restaurantId),
                String.valueOf(dishId)
        };
        if (!on) {
            db.delete(table, "user_id = ? AND restaurant_id = ? AND dish_id = ?", keys);
            return;
        }
        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("restaurant_id", restaurantId);
        values.put("dish_id", dishId);
        db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static String markTable(String name) {
        return "CREATE TABLE " + name + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "user_id INTEGER NOT NULL,"
                + "restaurant_id INTEGER NOT NULL,"
                + "dish_id INTEGER NOT NULL,"
                + "UNIQUE (user_id, restaurant_id, dish_id),"
                + "FOREIGN KEY (user_id) REFERENCES user_info(id))";
    }
}
