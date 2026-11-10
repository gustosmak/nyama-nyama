package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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

    private static SQLiteDatabase open(Context context) {
        if (database != null && database.isOpen()) {
            return database;
        }
        File file = new File(context.getFilesDir(), "foodai_menu.db");
        if (!file.exists()) {
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
}
