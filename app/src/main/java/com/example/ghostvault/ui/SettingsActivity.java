package com.example.ghostvault.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.ghostvault.R;
import com.example.ghostvault.security.VaultSecurity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;

public final class SettingsActivity extends BaseSecureActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        VaultSecurity prefs = new VaultSecurity(this);
        MaterialSwitch dark = findViewById(R.id.switchDark);
        dark.setChecked(prefs.isDarkMode());
        dark.setOnCheckedChangeListener((v, checked) -> {
            prefs.setDarkMode(checked);
            AppCompatDelegate.setDefaultNightMode(checked
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });
        Button cloud = findViewById(R.id.btnCloud);
        cloud.setOnClickListener(v -> startActivity(new Intent(this, CloudActivity.class)));
        findViewById(R.id.btnLock).setOnClickListener(v -> lockNow());
    }

    @Override protected void onResume() {
        super.onResume();
        requireSession();
    }
}
