package com.DirtyTeam.sky.NyamaNyama;

import java.util.List;

public final class OffersRepository {

    private OffersRepository() {
    }

    public static List<Offer> load() {
        return MenuDb.offers();
    }
}
