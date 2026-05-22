package com.example.palpito1;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;

public class ResetPasswordActivity extends AppCompatActivity {

    private EditText resetEmailEditText;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password);

        mAuth = FirebaseAuth.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reset_password), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        resetEmailEditText = findViewById(R.id.resetEmailEditText);
        Button sendResetButton = findViewById(R.id.sendResetButton);
        TextView backToLoginTextView = findViewById(R.id.backToLoginTextView);

        sendResetButton.setOnClickListener(v -> {
            String email = resetEmailEditText.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                resetEmailEditText.setError("Email is required");
                return;
            }

            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(ResetPasswordActivity.this, "Reset link sent to: " + email, Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            String errorMsg = task.getException() != null ? task.getException().getMessage() : "Error sending reset email";
                            Toast.makeText(ResetPasswordActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        backToLoginTextView.setOnClickListener(v -> {
            finish(); // Go back to login
        });
    }
}
