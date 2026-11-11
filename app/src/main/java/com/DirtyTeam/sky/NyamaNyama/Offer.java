package com.DirtyTeam.sky.NyamaNyama;

import java.util.ArrayList;
import java.util.List;

public final class Offer {

    public final String name;
    public final String travelTime;
    public final Integer distanceMeters;
    public final List<Dish> dishes = new ArrayList<>();

    public Offer(String name, String travelTime) {
        this(name, travelTime, null);
    }

    public Offer(String name, String travelTime, Integer distanceMeters) {
        this.name = name;
        this.travelTime = travelTime;
        this.distanceMeters = distanceMeters;
    }
}
