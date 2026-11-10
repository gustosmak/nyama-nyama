package com.DirtyTeam.sky.NyamaNyama;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationRequest;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.navigation.NavigationView;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.List;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;

public class HomeActivity extends AppCompatActivity {

    private static final int[] RESTAURANT_COLORS = {
            0xFFE23B3B,
            0xFFF1B467,
            0xFF3D8BFF,
            0xFF6FBF73,
            0xFFB388EB,
            0xFF2EC4B6,
            0xFFFF6B9A,
            0xFFFFD23F,
            0xFFE06A45,
            0xFF4CC9F0,
            0xFF9AB281,
            0xFF7C5C3B
    };
    private static final double MARKER_GAP_METERS = 36;

    private MapView mapView;
    private LocationManager locationManager;
    private Marker userMarker;
    private Drawable userIcon;
    private final List<Marker> restaurantMarkers = new ArrayList<>();
    private RestaurantCallout restaurantCallout;
    private GeoPoint searchedAt;
    private boolean searchRunning;
    private boolean updatesRequested;

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(@NonNull Location location) {
            showMarker(location);
        }

        @Override
        public void onProviderEnabled(@NonNull String provider) {
        }

        @Override
        public void onProviderDisabled(@NonNull String provider) {
        }
    };

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasLocationPermission()) {
                    showUserLocation();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Configuration.getInstance().setUserAgentValue(getPackageName());
        SharedPreferences osmdroidPreferences = getApplicationContext().getSharedPreferences(
                getPackageName() + "_preferences",
                Context.MODE_PRIVATE
        );
        Configuration.getInstance().load(getApplicationContext(), osmdroidPreferences);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_content), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.agents_drawer), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        findViewById(R.id.menu_button).setOnClickListener(v ->
                drawerLayout.openDrawer(GravityCompat.START));

        NavigationView navigationView = findViewById(R.id.nav_agents);
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });
        findViewById(R.id.about_button).setOnClickListener(v -> showAboutDialog());

        findViewById(R.id.profile_button).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, ProfileActivity.class)));
        Button wantButton = findViewById(R.id.want_button);
        wantButton.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, BudgetActivity.class)));
        Button recommendationsButton = findViewById(R.id.recommendations_button);
        AppFonts.applyMontserratBold(wantButton, recommendationsButton);

        mapView = findViewById(R.id.map);
        mapView.setTileSource(new TwoGisTileSource());
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(3.0);
        mapView.getController().setCenter(new GeoPoint(20.0, 0.0));
        restaurantCallout = new RestaurantCallout(mapView);
        mapView.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                restaurantCallout.close();
                return false;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        }));

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (hasLocationPermission()) {
            showUserLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void showAboutDialog() {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setContentView(R.layout.dialog_about);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        AppCompatButton okButton = dialog.findViewById(R.id.about_ok);
        AppFonts.applyMontserratBold(okButton);
        okButton.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    private void showUserLocation() {
        if (!hasLocationPermission() || locationManager == null) {
            return;
        }
        Location best = latestLocation();
        if (best != null) {
            showMarker(best);
        }
        if (!updatesRequested) {
            requestUpdates(LocationManager.GPS_PROVIDER);
            requestUpdates(LocationManager.NETWORK_PROVIDER);
            updatesRequested = true;
        }
    }

    @SuppressLint("MissingPermission")
    private Location latestLocation() {
        Location gps = safeLastKnown(LocationManager.GPS_PROVIDER);
        Location network = safeLastKnown(LocationManager.NETWORK_PROVIDER);
        if (gps == null) {
            return network;
        }
        if (network == null) {
            return gps;
        }
        return gps.getTime() >= network.getTime() ? gps : network;
    }

    @SuppressLint("MissingPermission")
    private Location safeLastKnown(String provider) {
        if (!providerEnabled(provider)) {
            return null;
        }
        return locationManager.getLastKnownLocation(provider);
    }

    @SuppressLint("MissingPermission")
    private void requestUpdates(String provider) {
        if (!providerEnabled(provider)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationRequest request = new LocationRequest.Builder(2000L)
                    .setMinUpdateDistanceMeters(5f)
                    .build();
            locationManager.requestLocationUpdates(provider, request, getMainExecutor(), locationListener);
            return;
        }
        requestLegacyUpdates(provider);
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission")
    private void requestLegacyUpdates(String provider) {
        locationManager.requestLocationUpdates(provider, 2000L, 5f, locationListener);
    }

    private boolean providerEnabled(String provider) {
        try {
            return locationManager.isProviderEnabled(provider);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void showMarker(Location location) {
        GeoPoint point = new GeoPoint(location.getLatitude(), location.getLongitude());
        if (userMarker == null) {
            userMarker = new Marker(mapView);
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            userMarker.setTitle(getString(R.string.you_are_here));
            userMarker.setIcon(userGlyph());
            userMarker.setInfoWindow(null);
            bindCallout(userMarker);
            mapView.getOverlays().add(userMarker);
        }
        userMarker.setPosition(point);
        mapView.getController().setZoom(16.0);
        mapView.getController().animateTo(point);
        mapView.invalidate();
        searchNearby(point);
    }

    private void searchNearby(GeoPoint point) {
        if (searchRunning) {
            return;
        }
        if (searchedAt != null && searchedAt.distanceToAsDouble(point) < 500) {
            return;
        }
        searchRunning = true;
        GeoPoint origin = new GeoPoint(point);
        new Thread(() -> {
            List<NearbyRestaurants.Place> places = NearbyRestaurants.find(origin);
            runOnUiThread(() -> {
                searchRunning = false;
                if (isFinishing()) {
                    return;
                }
                searchedAt = origin;
                showRestaurants(places);
            });
        }).start();
    }

    private void showRestaurants(List<NearbyRestaurants.Place> places) {
        restaurantCallout.close();
        for (Marker marker : restaurantMarkers) {
            mapView.getOverlays().remove(marker);
        }
        restaurantMarkers.clear();
        List<GeoPoint> placed = new ArrayList<>();
        List<Integer> usedColors = new ArrayList<>();
        for (NearbyRestaurants.Place place : places) {
            GeoPoint point = spread(new GeoPoint(place.latitude, place.longitude), placed);
            int color = pickColor(point, placed, usedColors);
            placed.add(point);
            usedColors.add(color);
            Marker marker = new Marker(mapView);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setPosition(point);
            marker.setTitle(place.name);
            marker.setSnippet(place.address);
            marker.setIcon(restaurantGlyph(color));
            marker.setInfoWindow(null);
            bindCallout(marker);
            mapView.getOverlays().add(marker);
            restaurantMarkers.add(marker);
        }
        if (userMarker != null) {
            mapView.getOverlays().remove(userMarker);
            mapView.getOverlays().add(userMarker);
        }
        mapView.invalidate();
    }

    private GeoPoint spread(GeoPoint point, List<GeoPoint> placed) {
        GeoPoint result = point;
        for (int attempt = 0; attempt < 10; attempt++) {
            if (!tooClose(result, placed)) {
                return result;
            }
            double angle = Math.toRadians(36.0 * attempt + 18.0);
            double latitude = point.getLatitude()
                    + (MARKER_GAP_METERS * Math.cos(angle)) / 111320.0;
            double longitude = point.getLongitude()
                    + (MARKER_GAP_METERS * Math.sin(angle))
                    / (111320.0 * Math.cos(Math.toRadians(point.getLatitude())));
            result = new GeoPoint(latitude, longitude);
        }
        return result;
    }

    private boolean tooClose(GeoPoint point, List<GeoPoint> placed) {
        for (GeoPoint other : placed) {
            if (point.distanceToAsDouble(other) < MARKER_GAP_METERS) {
                return true;
            }
        }
        return false;
    }

    private int pickColor(GeoPoint point, List<GeoPoint> placed, List<Integer> usedColors) {
        boolean[] taken = new boolean[RESTAURANT_COLORS.length];
        for (int i = 0; i < placed.size(); i++) {
            if (placed.get(i).distanceToAsDouble(point) < 180) {
                taken[usedColors.get(i)] = true;
            }
        }
        for (int i = 0; i < taken.length; i++) {
            if (!taken[i]) {
                return i;
            }
        }
        return placed.size() % RESTAURANT_COLORS.length;
    }

    private void bindCallout(Marker marker) {
        marker.setOnMarkerClickListener((clicked, map) -> {
            Drawable icon = clicked.getIcon();
            int lift = icon == null ? 0 : icon.getIntrinsicHeight();
            restaurantCallout.open(clicked, clicked.getPosition(), 0, -lift);
            return true;
        });
    }

    private Drawable userGlyph() {
        if (userIcon == null) {
            userIcon = glyph(0xFFE23B3B, false);
        }
        return userIcon;
    }

    private Drawable restaurantGlyph(int colorIndex) {
        return glyph(RESTAURANT_COLORS[colorIndex], true);
    }

    private Drawable glyph(int color, boolean cutlery) {
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(36 * density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(size / 48f, size / 48f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setShadowLayer(1.6f, 0f, 0.8f, 0x66000000);
        drawPin(canvas, paint);
        if (cutlery) {
            Paint utensils = new Paint(Paint.ANTI_ALIAS_FLAG);
            utensils.setColor(0xFF000000);
            drawForkAndSpoon(canvas, utensils);
        } else {
            Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
            dot.setColor(0xFFFFFFFF);
            canvas.drawCircle(24f, 16f, 4.4f, dot);
        }
        return new BitmapDrawable(getResources(), bitmap);
    }

    private void drawPin(Canvas canvas, Paint paint) {
        Path tail = new Path();
        tail.moveTo(15.2f, 17.5f);
        tail.lineTo(24f, 44.6f);
        tail.lineTo(32.8f, 17.5f);
        tail.close();
        canvas.drawPath(tail, paint);
        canvas.drawCircle(24f, 16f, 10.2f, paint);
    }

    private void drawForkAndSpoon(Canvas canvas, Paint paint) {
        canvas.save();
        canvas.rotate(32f, 24f, 16f);
        canvas.drawRoundRect(21.35f, 8.5f, 22.35f, 12.6f, 0.55f, 0.55f, paint);
        canvas.drawRoundRect(23.45f, 8.05f, 24.55f, 12.9f, 0.55f, 0.55f, paint);
        canvas.drawRoundRect(25.65f, 8.5f, 26.65f, 12.6f, 0.55f, 0.55f, paint);
        canvas.drawRoundRect(21.45f, 12.15f, 26.55f, 13.85f, 0.9f, 0.9f, paint);
        canvas.drawRoundRect(23.4f, 13.45f, 24.6f, 22.4f, 0.6f, 0.6f, paint);
        canvas.restore();

        canvas.save();
        canvas.rotate(-32f, 24f, 16f);
        canvas.drawOval(21.55f, 8.05f, 26.45f, 13.35f, paint);
        canvas.drawRoundRect(23.4f, 12.85f, 24.6f, 22.4f, 0.6f, 0.6f, paint);
        canvas.restore();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (hasLocationPermission()) {
            showUserLocation();
        }
    }

    @Override
    protected void onPause() {
        if (locationManager != null) {
            locationManager.removeUpdates(locationListener);
            updatesRequested = false;
        }
        mapView.onPause();
        super.onPause();
    }
}
