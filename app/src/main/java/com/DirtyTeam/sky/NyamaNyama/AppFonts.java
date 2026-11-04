package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.widget.TextView;

import androidx.core.content.res.ResourcesCompat;

final class AppFonts {

    private AppFonts() {
    }

    static Typeface openSansBold(Context context) {
        Typeface openSans = ResourcesCompat.getFont(context, R.font.open_sans);
        if (openSans == null) {
            return Typeface.DEFAULT_BOLD;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(openSans, 700, false);
        }
        return Typeface.create(openSans, Typeface.BOLD);
    }

    static void applyOpenSansBold(TextView... views) {
        if (views.length == 0) {
            return;
        }
        Typeface bold = openSansBold(views[0].getContext());
        for (TextView view : views) {
            view.setTypeface(bold);
            view.setPaintFlags(view.getPaintFlags() | android.graphics.Paint.SUBPIXEL_TEXT_FLAG);
        }
    }
}
