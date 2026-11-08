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

public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.register_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    systemBars.left + pad,
                    systemBars.top + pad,
                    systemBars.right + pad,
                    systemBars.bottom + pad
            );
            return insets;
        });

        TextInputLayout emailInput = findViewById(R.id.login_input);
        TextInputLayout passwordInput = findViewById(R.id.password_input);
        TextInputLayout passwordConfirmInput = findViewById(R.id.password_confirm_input);
        TextInputEditText emailEdit = findViewById(R.id.login_edit);
        TextInputEditText passwordEdit = findViewById(R.id.password_edit);
        TextInputEditText passwordConfirmEdit = findViewById(R.id.password_confirm_edit);
        Button actionRegister = findViewById(R.id.action_register);
        AppFonts.applyMontserratBold(actionRegister);

        actionRegister.setOnClickListener(v -> {
            boolean emailEmpty = textOf(emailEdit).isEmpty();
            boolean passwordEmpty = textOf(passwordEdit).isEmpty();
            boolean confirmEmpty = textOf(passwordConfirmEdit).isEmpty();
            emailInput.setError(emailEmpty ? getString(R.string.field_required) : null);
            passwordInput.setError(passwordEmpty ? getString(R.string.field_required) : null);
            passwordConfirmInput.setError(confirmEmpty ? getString(R.string.field_required) : null);
            if (emailEmpty || passwordEmpty || confirmEmpty) {
                Snackbar snackbar = Snackbar.make(
                        findViewById(R.id.register_root),
                        R.string.register_empty_warning,
                        Snackbar.LENGTH_LONG
                );
                snackbar.setBackgroundTint(ContextCompat.getColor(this, R.color.nyama_orange));
                snackbar.setTextColor(ContextCompat.getColor(this, R.color.nyama_gray));
                snackbar.setAnchorView(actionRegister);
                snackbar.show();
                return;
            }
            UserPrefs.setRegistered(this, true);
            UserPrefs.saveAccount(this, textOf(emailEdit), textOf(passwordEdit));
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }

    private static String textOf(TextInputEditText editText) {
        if (editText.getText() == null) {
            return "";
        }
        return editText.getText().toString().trim();
    }
}
