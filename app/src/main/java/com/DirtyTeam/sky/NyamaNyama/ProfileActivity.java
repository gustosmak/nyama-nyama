package com.DirtyTeam.sky.NyamaNyama;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ProfileActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_profile);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        findViewById(R.id.profile_back).setOnClickListener(v -> finish());

        Typeface bold = AppFonts.montserratBold(this);
        Typeface regular = AppFonts.montserratRegular(this);
        TextView title = findViewById(R.id.profile_title);
        title.setTypeface(bold);
        TextInputLayout emailLayout = findViewById(R.id.profile_email_input);
        TextInputLayout passwordLayout = findViewById(R.id.profile_password_input);
        TextInputEditText email = findViewById(R.id.profile_email);
        TextInputEditText password = findViewById(R.id.profile_password);
        emailLayout.setTypeface(regular);
        passwordLayout.setTypeface(regular);
        email.setTypeface(bold);
        password.setTypeface(bold);
        email.setText(UserPrefs.getEmail(this));
        password.setText(masked(UserPrefs.getPassword(this)));
    }

    private static String masked(String password) {
        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < password.length(); i++) {
            dots.append('\u2022');
        }
        return dots.toString();
    }
}
