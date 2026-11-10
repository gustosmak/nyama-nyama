package com.DirtyTeam.sky.NyamaNyama;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class NearbyRestaurants {

    static final class Place {
        final String name;
        final String address;
        final double latitude;
        final double longitude;

        Place(String name, String address, double latitude, double longitude) {
            this.name = name;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    private NearbyRestaurants() {
    }

    static List<Place> find(GeoPoint origin) {
        List<Place> places = new ArrayList<>();
        for (int page = 1; page <= 5 && places.size() < 50; page++) {
            int before = places.size();
            if (!readPage(origin, page, places)) {
                break;
            }
            if (places.size() == before) {
                break;
            }
        }
        return places;
    }

    private static boolean readPage(GeoPoint origin, int page, List<Place> places) {
        HttpURLConnection connection = null;
        try {
            String query = URLEncoder.encode("\u0440\u0435\u0441\u0442\u043e\u0440\u0430\u043d", "UTF-8");
            String url = "https://catalog.api.2gis.com/3.0/items?q=" + query
                    + "&type=branch"
                    + "&point=" + origin.getLongitude() + "," + origin.getLatitude()
                    + "&radius=2000"
                    + "&sort=distance"
                    + "&page_size=10"
                    + "&page=" + page
                    + "&fields=items.point,items.address_name"
                    + "&key=" + URLEncoder.encode(TwoGisTileSource.KEY, "UTF-8");
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setRequestProperty("User-Agent", "NyamaNyama");
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            if (stream == null) {
                return false;
            }
            JSONObject root = new JSONObject(read(stream));
            JSONObject meta = root.optJSONObject("meta");
            if (meta != null && meta.optInt("code", code) != 200) {
                return false;
            }
            JSONObject result = root.optJSONObject("result");
            if (result == null) {
                return false;
            }
            JSONArray items = result.optJSONArray("items");
            if (items == null) {
                return false;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONObject point = item.optJSONObject("point");
                if (point == null) {
                    continue;
                }
                double latitude = point.optDouble("lat", Double.NaN);
                double longitude = point.optDouble("lon", Double.NaN);
                String name = item.optString("name", "");
                if (name.isEmpty() || Double.isNaN(latitude) || Double.isNaN(longitude)) {
                    continue;
                }
                places.add(new Place(name, item.optString("address_name", ""), latitude, longitude));
            }
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String read(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        stream.close();
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
