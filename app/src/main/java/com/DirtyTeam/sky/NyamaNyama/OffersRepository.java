package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;

import java.util.List;

public final class OffersRepository {

    private OffersRepository() {
    }

    public static List<Offer> load(Context context) {
        return MenuDb.offers(context);
    }
}
