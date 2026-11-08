package com.example.ghostvault.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import com.example.ghostvault.R;
import com.example.ghostvault.data.Disk;
import com.example.ghostvault.security.SessionManager;
import com.example.ghostvault.security.VaultSecurity;
import com.google.android.material.textfield.TextInputEditText;
import java.util.Arrays;

public final class SetupActivity extends BaseSecureActivity {
    private TextInputEditText first, second;
    private Button create;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        VaultSecurity security = new VaultSecurity(this);
        if (security.initialized()) { startActivity(new Intent(this, UnlockActivity.class)); finish(); return; }
        setContentView(R.layout.activity_setup);
        first = findViewById(R.id.editPassword);
        second = findViewById(R.id.editConfirm);
        create = findViewById(R.id.btnCreate);
        create.setOnClickListener(v -> {
            String p = String.valueOf(first.getText());
            String confirm = String.valueOf(second.getText());
            if (p.length() < 6) { first.setError("Use 6 or more characters"); return; }
            if (!p.equals(confirm)) { second.setError("Passwords do not match"); return; }
            create.setEnabled(false);
            Disk.run(() -> {
                try {
                    security.createRealVault(p);
                    VaultSecurity.Result result = security.authenticate(p);
                    security.finishRealUnlock();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || !activityIsVisible()) {
                            if (result.key != null) Arrays.fill(result.key, (byte) 0);
                            return;
                        }
                        SessionManager.open(SessionManager.REAL, result.key);
                        Arrays.fill(result.key, (byte) 0);
                        startActivity(new Intent(this, NotesActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        create.setEnabled(true);
                        Toast.makeText(this, "Could not create notebook", Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (new VaultSecurity(this).initialized() && !SessionManager.isOpen()) {
            startActivity(new Intent(this, UnlockActivity.class));
            finish();
        }
    }
}
