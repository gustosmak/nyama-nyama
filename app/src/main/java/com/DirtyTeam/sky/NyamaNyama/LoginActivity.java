package com.DirtyTeam.sky.NyamaNyama;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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

        TextInputLayout loginInput = findViewById(R.id.login_input);
        TextInputLayout passwordInput = findViewById(R.id.password_input);
        TextInputEditText loginEdit = findViewById(R.id.login_edit);
        TextInputEditText passwordEdit = findViewById(R.id.password_edit);
        Button actionLogin = findViewById(R.id.action_login);
        AppFonts.applyMontserratBold(actionLogin);

        actionLogin.setOnClickListener(v -> {
            String login = textOf(loginEdit);
            String password = textOf(passwordEdit);
            boolean loginEmpty = login.isEmpty();
            boolean passwordEmpty = password.isEmpty();
            loginInput.setError(loginEmpty ? getString(R.string.field_required) : null);
            passwordInput.setError(passwordEmpty ? getString(R.string.field_required) : null);
            if (loginEmpty || passwordEmpty) {
                showEmptyWarning(actionLogin);
                return;
            }
            long userId = UserDb.find(this, login, password);
            if (userId < 0L) {
                showWarning(actionLogin, R.string.login_failed);
                return;
            }
            UserPrefs.setCurrentUser(this, userId, login, password);
            openHome();
        });
    }

    private static String textOf(TextInputEditText editText) {
        if (editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }

    private void openHome() {
        Intent home = new Intent(this, HomeActivity.class);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(home);
    }

    private void showEmptyWarning(Button anchor) {
        showWarning(anchor, R.string.login_empty_warning);
    }

    private void showWarning(Button anchor, int message) {
        Snackbar snackbar = Snackbar.make(
                findViewById(R.id.login_root),
                message,
                Snackbar.LENGTH_LONG
        );
        snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.nyama_orange));
        snackbar.setTextColor(ContextCompat.getColor(this, R.color.nyama_gray));
        snackbar.setAnchorView(anchor);
        snackbar.show();
    }
}
