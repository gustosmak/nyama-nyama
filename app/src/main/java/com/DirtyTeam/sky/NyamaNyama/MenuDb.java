package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

final class MenuDb {

    private static SQLiteDatabase database;

    private MenuDb() {
    }

    static long[] ids(Context context, String restaurant, String dish) {
        SQLiteDatabase db = open(context);
        if (db == null) {
            return new long[]{0L, 0L};
        }
        Cursor cursor = db.rawQuery(
                "SELECT restaurants.id, dishes.id "
                        + "FROM dishes "
                        + "JOIN restaurants ON restaurants.id = dishes.restaurant_id "
                        + "WHERE restaurants.name = ? AND dishes.name = ? "
                        + "ORDER BY dishes.id LIMIT 1",
                new String[]{restaurant, dish}
        );
        try {
            if (!cursor.moveToFirst()) {
                return new long[]{0L, 0L};
            }
            return new long[]{cursor.getLong(0), cursor.getLong(1)};
        } finally {
            cursor.close();
        }
    }

    static List<Offer> offers(Context context) {
        List<Offer> offers = new ArrayList<>();
        SQLiteDatabase db = open(context);
        if (db == null) {
            return offers;
        }
        Cursor cursor = db.rawQuery(
                "SELECT restaurants.name, dishes.name, dishes.description, dishes.satiety, "
                        + "dishes.price, dishes.calories, sections.name "
                        + "FROM dishes "
                        + "JOIN restaurants ON restaurants.id = dishes.restaurant_id "
                        + "LEFT JOIN sections ON sections.id = dishes.section_id "
                        + "ORDER BY restaurants.name, dishes.id",
                null
        );
        try {
            Offer current = null;
            while (cursor.moveToNext()) {
                String restaurant = cursor.getString(0);
                if (current == null || !current.name.equals(restaurant)) {
                    current = new Offer(restaurant, "");
                    offers.add(current);
                }
                int calories = cursor.getInt(5);
                current.dishes.add(new Dish(
                        cursor.getString(1),
                        cursor.isNull(2) ? "" : cursor.getString(2),
                        cursor.getInt(3),
                        (int) Math.round(cursor.getDouble(4)),
                        calories + " \u043a\u043a\u0430\u043b"
                ));
            }
        } finally {
            cursor.close();
        }
        return offers;
    }

    static List<String> sections(Context context, String restaurant, String dish) {
        List<String> sections = new ArrayList<>();
        SQLiteDatabase db = open(context);
        if (db == null) {
            return sections;
        }
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT sections.name "
                        + "FROM dishes "
                        + "JOIN restaurants ON restaurants.id = dishes.restaurant_id "
                        + "JOIN sections ON sections.id = dishes.section_id "
                        + "WHERE restaurants.name = ? AND dishes.name = ? "
                        + "ORDER BY sections.name",
                new String[]{restaurant, dish}
        );
        try {
            while (cursor.moveToNext()) {
                String name = cursor.getString(0);
                if (name != null && !name.isEmpty()) {
                    sections.add(name);
                }
            }
        } finally {
            cursor.close();
        }
        return sections;
    }

    private static SQLiteDatabase open(Context context) {
        if (database != null && database.isOpen()) {
            return database;
        }
        File file = new File(context.getFilesDir(), "foodai_menu.db");
        if (!file.exists() || !hasSections(file)) {
            if (database != null) {
                database.close();
                database = null;
            }
            if (file.exists() && !file.delete()) {
                return null;
            }
            try (InputStream input = context.getAssets().open("foodai_menu.db");
                 OutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } catch (IOException ignored) {
                return null;
            }
        }
        database = SQLiteDatabase.openDatabase(
                file.getAbsolutePath(),
                null,
                SQLiteDatabase.OPEN_READONLY
        );
        return database;
    }

    private static boolean hasSections(File file) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        try {
            db = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null, SQLiteDatabase.OPEN_READONLY);
            cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sections'",
                    null
            );
            return cursor.moveToFirst();
        } catch (RuntimeException ignored) {
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (db != null) {
                db.close();
            }
        }
    }
}
