package com.DirtyTeam.sky.NyamaNyama;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class OffersRepository {

    private OffersRepository() {
    }

    public static List<Offer> load(Context context) {
        List<Offer> offers = new ArrayList<>();
        Offer current = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("offers.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    current = null;
                    continue;
                }
                String[] parts = line.split("\\|");
                for (int i = 0; i < parts.length; i++) {
                    parts[i] = parts[i].trim();
                }
                if (parts.length >= 5 && current != null) {
                    current.dishes.add(new Dish(
                            parts[0],
                            parts[1],
                            digits(parts[2]),
                            digits(parts[3]),
                            parts[4]
                    ));
                } else if (parts.length >= 2 && parts.length < 5) {
                    current = new Offer(parts[0], parts[1]);
                    offers.add(current);
                }
            }
        } catch (IOException ignored) {
            return offers;
        }
        List<Offer> filled = new ArrayList<>();
        for (Offer offer : offers) {
            if (!offer.dishes.isEmpty()) {
                filled.add(offer);
            }
        }
        return filled;
    }

    private static int digits(String value) {
        StringBuilder numbers = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char symbol = value.charAt(i);
            if (symbol >= '0' && symbol <= '9') {
                numbers.append(symbol);
            }
        }
        if (numbers.length() == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(numbers.toString());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
