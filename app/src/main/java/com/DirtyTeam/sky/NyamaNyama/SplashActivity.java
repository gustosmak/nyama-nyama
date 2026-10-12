package com.DirtyTeam.sky.NyamaNyama;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 1500;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable openMain = () -> {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_splash);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.postDelayed(openMain, SPLASH_DURATION_MS);
    }

    @Override
    protected void onStop() {
        super.onStop();
        handler.removeCallbacks(openMain);
    }
}
