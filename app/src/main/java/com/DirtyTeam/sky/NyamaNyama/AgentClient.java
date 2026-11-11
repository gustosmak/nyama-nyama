package com.DirtyTeam.sky.NyamaNyama;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class AgentClient {

    private static final String[] HOSTS = hosts();

    private static String[] hosts() {
        java.util.LinkedHashSet<String> hosts = new java.util.LinkedHashSet<>();
        hosts.add("http://127.0.0.1:8765");
        if (BuildConfig.AGENT_HOST != null && !BuildConfig.AGENT_HOST.isEmpty()) {
            hosts.add(BuildConfig.AGENT_HOST);
        }
        hosts.add("http://10.0.2.2:8765");
        return hosts.toArray(new String[0]);
    }

    private AgentClient() {
    }

    static final class FeedAnswer {
        final List<Offer> offers;
        final boolean fromDatabase;
        final List<String> warnings;

        FeedAnswer(List<Offer> offers, boolean fromDatabase, List<String> warnings) {
            this.offers = offers;
            this.fromDatabase = fromDatabase;
            this.warnings = warnings;
        }
    }

    static final class PlaceAnswer {
        final double[] point;
        final String warning;

        PlaceAnswer(double[] point, String warning) {
            this.point = point;
            this.warning = warning;
        }
    }

    static FeedAnswer recommend(String sessionJson) throws Exception {
        JSONObject body = new JSONObject(post("/recommend", sessionJson));
        return new FeedAnswer(
                offersFrom(body),
                body.optBoolean("fromDatabase"),
                warningsFrom(body)
        );
    }

    static List<Offer> menu() throws Exception {
        return readOffers(get("/menu"));
    }

    static PlaceAnswer locate(String restaurant, double latitude, double longitude) throws Exception {
        String query = "/locate?restaurant=" + URLEncoder.encode(restaurant, "UTF-8")
                + "&latitude=" + latitude
                + "&longitude=" + longitude;
        JSONObject body = new JSONObject(get(query));
        double[] point = null;
        if (!body.isNull("lat") && !body.isNull("lon")) {
            point = new double[]{body.getDouble("lat"), body.getDouble("lon")};
        }
        String warning = body.isNull("warning") ? "" : body.optString("warning", "");
        if ("null".equals(warning) || point != null) {
            warning = "";
        }
        return new PlaceAnswer(point, warning.isEmpty() ? null : warning);
    }

    static List<String> sections(String restaurant, String dish) throws Exception {
        String query = "/sections?restaurant=" + URLEncoder.encode(restaurant, "UTF-8")
                + "&dish=" + URLEncoder.encode(dish, "UTF-8");
        JSONObject body = new JSONObject(get(query));
        JSONArray array = body.optJSONArray("sections");
        List<String> sections = new ArrayList<>();
        if (array == null) {
            return sections;
        }
        for (int i = 0; i < array.length(); i++) {
            String name = array.optString(i);
            if (!name.isEmpty()) {
                sections.add(name);
            }
        }
        return sections;
    }

    private static String post(String path, String json) throws Exception {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Exception last = null;
        for (String host : HOSTS) {
            HttpURLConnection connection = (HttpURLConnection) new URL(host + path).openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(180000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try {
                connection.getOutputStream().write(bytes);
                return read(connection);
            } catch (java.io.IOException error) {
                last = error;
                connection.disconnect();
            }
        }
        throw last == null ? new java.io.IOException("no backend") : last;
    }

    private static String get(String path) throws Exception {
        Exception last = null;
        for (String host : HOSTS) {
            HttpURLConnection connection = (HttpURLConnection) new URL(host + path).openConnection();
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("GET");
            try {
                return read(connection);
            } catch (java.io.IOException error) {
                last = error;
                connection.disconnect();
            }
        }
        throw last == null ? new java.io.IOException("no backend") : last;
    }

    private static String read(HttpURLConnection connection) throws Exception {
        try (InputStream input = connection.getResponseCode() >= 400
                ? connection.getErrorStream()
                : connection.getInputStream()) {
            if (input == null) {
                return "";
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            connection.disconnect();
        }
    }

    private static List<Offer> readOffers(String raw) throws Exception {
        return offersFrom(new JSONObject(raw));
    }

    private static List<String> warningsFrom(JSONObject body) {
        List<String> warnings = new ArrayList<>();
        JSONArray array = body.optJSONArray("warnings");
        if (array == null) {
            return warnings;
        }
        for (int i = 0; i < array.length(); i++) {
            String code = array.optString(i);
            if (!code.isEmpty()) {
                warnings.add(code);
            }
        }
        return warnings;
    }

    private static List<Offer> offersFrom(JSONObject body) {
        List<Offer> offers = new ArrayList<>();
        JSONArray array = body.optJSONArray("offers");
        if (array == null) {
            return offers;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            Integer distance = item.has("distanceMeters") && !item.isNull("distanceMeters")
                    ? item.optInt("distanceMeters")
                    : null;
            Offer offer = new Offer(item.optString("name"), item.optString("travelTime"), distance);
            JSONArray dishes = item.optJSONArray("dishes");
            if (dishes != null) {
                for (int j = 0; j < dishes.length(); j++) {
                    JSONObject dish = dishes.optJSONObject(j);
                    if (dish == null) {
                        continue;
                    }
                    offer.dishes.add(new Dish(
                            dish.optString("name"),
                            dish.optString("satiety"),
                            dish.optInt("satietyLevel"),
                            dish.optInt("price"),
                            dish.optString("calories"),
                            dish.optBoolean("generated")
                    ));
                }
            }
            if (!offer.dishes.isEmpty()) {
                offers.add(offer);
            }
        }
        return offers;
    }
}
