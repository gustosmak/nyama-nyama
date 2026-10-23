package com.DirtyTeam.sky.NyamaNyama;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Typeface openSansBold = createOpenSansBold();
        applyButtonTypeface(R.id.login_button, openSansBold);
        applyButtonTypeface(R.id.register_button, openSansBold);
        applyButtonTypeface(R.id.leave_button, openSansBold);

        findViewById(R.id.login_button).setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        findViewById(R.id.register_button).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
        findViewById(R.id.leave_button).setOnClickListener(v -> finishAffinity());
    }

    private Typeface createOpenSansBold() {
        Typeface openSans = ResourcesCompat.getFont(this, R.font.open_sans);
        if (openSans == null) {
            return Typeface.DEFAULT_BOLD;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(openSans, 700, false);
        }
        return Typeface.create(openSans, Typeface.BOLD);
    }

    private void applyButtonTypeface(int buttonId, Typeface typeface) {
        Button button = findViewById(buttonId);
        button.setTypeface(typeface);
    }
}
