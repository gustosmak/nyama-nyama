package com.DirtyTeam.sky.NyamaNyama;

import java.util.ArrayList;
import java.util.List;

final class MenuDb {

    private MenuDb() {
    }

    static long[] ids(String restaurant, String dish) {
        return new long[]{0L, 0L};
    }

    static List<Offer> offers() {
        try {
            return AgentClient.menu();
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }

    static List<String> sections(String restaurant, String dish) {
        try {
            return AgentClient.sections(restaurant, dish);
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
    }
}
