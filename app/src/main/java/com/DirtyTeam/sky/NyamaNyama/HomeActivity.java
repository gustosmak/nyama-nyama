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
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

public class HomeActivity extends AppCompatActivity {

    private MapView mapView;
    private LocationManager locationManager;
    private Marker userMarker;
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
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(3.0);
        mapView.getController().setCenter(new GeoPoint(20.0, 0.0));

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
            mapView.getOverlays().add(userMarker);
        }
        userMarker.setPosition(point);
        mapView.getController().setZoom(16.0);
        mapView.getController().animateTo(point);
        mapView.invalidate();
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
