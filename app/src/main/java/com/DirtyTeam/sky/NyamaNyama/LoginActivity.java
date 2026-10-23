package com.DirtyTeam.sky.NyamaNyama;

import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.login_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    systemBars.left + pad,
                    systemBars.top + pad,
                    systemBars.right + pad,
                    systemBars.bottom + pad
            );
            return insets;
        });

        // Fields are UI stubs only — values are not read yet.
        findViewById(R.id.action_login).setOnClickListener(v -> finish());
    }
}
