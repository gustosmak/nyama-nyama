package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.widget.TextViewCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

final class AppFonts {

    private AppFonts() {
    }

    static Typeface montserratBold(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Typeface bold = buildVariation(
                    context,
                    R.font.montserrat_variable,
                    "montserrat_variable.ttf",
                    "'wght' 700"
            );
            if (bold != null) {
                return bold;
            }
        }
        Typeface montserrat = ResourcesCompat.getFont(context, R.font.montserrat_bold);
        return montserrat != null ? montserrat : Typeface.DEFAULT_BOLD;
    }

    static void applyMontserratBold(TextView... views) {
        applyVariation(
                views,
                views.length == 0 ? null : montserratBold(views[0].getContext()),
                "'wght' 700"
        );
    }

    private static void applyVariation(TextView[] views, Typeface typeface, String settings) {
        if (views.length == 0 || typeface == null) {
            return;
        }
        for (TextView view : views) {
            view.setTypeface(typeface);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                view.setFontVariationSettings(settings);
            }
            fitSingleLine(view);
        }
    }

    private static Typeface buildVariation(Context context, int fontRes, String cacheName, String settings) {
        File file = new File(context.getCacheDir(), cacheName);
        if (!file.exists()) {
            try (InputStream input = context.getResources().openRawResource(fontRes);
                 OutputStream output = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
            } catch (IOException exception) {
                return null;
            }
        }
        return new Typeface.Builder(file).setFontVariationSettings(settings).build();
    }

    private static void fitSingleLine(TextView view) {
        view.setMaxLines(1);
        float currentSp = view.getTextSize() / view.getResources().getDisplayMetrics().scaledDensity;
        int maxSp = Math.max(1, Math.round(currentSp));
        int minSp = Math.max(11, maxSp - 4);
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                view,
                minSp,
                maxSp,
                1,
                TypedValue.COMPLEX_UNIT_SP
        );
    }
}
