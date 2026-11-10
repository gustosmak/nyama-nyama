package com.DirtyTeam.sky.NyamaNyama;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextPaint;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.TextView;

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
        TextView title = findViewById(R.id.main_title_text);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            title.post(() -> stretchTitleToScreenWidth(title));
            return insets;
        });
        title.post(() -> stretchTitleToScreenWidth(title));

        Button loginButton = findViewById(R.id.login_button);
        Button registerButton = findViewById(R.id.register_button);
        AppFonts.applyMontserratBold(loginButton, registerButton);

        loginButton.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class)));
        registerButton.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void stretchTitleToScreenWidth(TextView title) {
        CharSequence text = title.getText();
        int available = title.getWidth() - title.getPaddingLeft() - title.getPaddingRight();
        if (text == null || text.length() == 0 || available <= 0) {
            return;
        }
        TextPaint paint = new TextPaint(title.getPaint());
        String value = text.toString();
        float advance = paint.measureText(value);
        Rect bounds = new Rect();
        paint.getTextBounds(value, 0, value.length(), bounds);
        float leftOverflow = Math.max(0, -bounds.left);
        float rightOverflow = Math.max(0, bounds.right - advance);
        float needed = advance + leftOverflow + rightOverflow + paint.getTextSize() * 0.08f;
        if (needed <= 0f) {
            return;
        }
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, paint.getTextSize() * available / needed);
    }
}
