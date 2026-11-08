package com.example.ghostvault.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.example.ghostvault.R;
import com.example.ghostvault.data.Disk;
import com.example.ghostvault.data.GhostDatabase;
import com.example.ghostvault.security.SessionManager;
import com.example.ghostvault.security.VaultRules;
import com.example.ghostvault.security.VaultSecurity;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Arrays;

public final class UnlockActivity extends BaseSecureActivity {
    private TextInputEditText passwordField;
    private Button unlockButton;
    private TextView feedback;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager.clear(); // Launches must always be locked, even after a process restart.
        VaultSecurity security = new VaultSecurity(this);
        if (!security.initialized()) {
            startActivity(new Intent(this, SetupActivity.class)); finish(); return;
        }
        setContentView(R.layout.activity_unlock);
        passwordField = findViewById(R.id.editPassword);
        unlockButton = findViewById(R.id.btnUnlock);
        feedback = findViewById(R.id.txtFeedback);
        unlockButton.setOnClickListener(v -> unlock());
    }

    private void unlock() {
        String attempt = String.valueOf(passwordField.getText());
        if (attempt.isEmpty()) { passwordField.setError("Enter a password"); return; }
        unlockButton.setEnabled(false);
        feedback.setText("");
        VaultSecurity security = new VaultSecurity(this);
        Disk.run(() -> {
            try {
                VaultSecurity.Result result = security.authenticate(attempt);
                if (result.outcome == VaultRules.Outcome.REAL) {
                    // Physical deletion FIRST, then credential invalidation.
                    GhostDatabase.get(getApplicationContext()).noteDao().clearVault(SessionManager.DECOY);
                    security.finishRealUnlock();
                }
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || !activityIsVisible()) {
                        if (result.key != null) Arrays.fill(result.key, (byte) 0);
                        return;
                    }
                    unlockButton.setEnabled(true);
                    passwordField.setText("");
                    if (result.outcome == VaultRules.Outcome.WRONG) {
                        // Deliberately identical feedback for failures 1 through 5 and later bad inputs.
                        feedback.setText("Incorrect password. Try again.");
                        return;
                    }
                    SessionManager.open(result.outcome == VaultRules.Outcome.REAL
                            ? SessionManager.REAL : SessionManager.DECOY, result.key);
                    Arrays.fill(result.key, (byte) 0);
                    Intent intent = new Intent(this, NotesActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    unlockButton.setEnabled(true);
                    Toast.makeText(this, "Unable to open notebook", Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
