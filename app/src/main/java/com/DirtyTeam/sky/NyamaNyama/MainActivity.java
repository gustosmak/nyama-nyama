package com.DirtyTeam.sky.NyamaNyama;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button loginButton = findViewById(R.id.login_button);
        Button registerButton = findViewById(R.id.register_button);
        Button leaveButton = findViewById(R.id.leave_button);
        AppFonts.applyOpenSansBold(loginButton, registerButton, leaveButton);

        loginButton.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        registerButton.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        leaveButton.setOnClickListener(v -> finishAffinity());
    }
}
