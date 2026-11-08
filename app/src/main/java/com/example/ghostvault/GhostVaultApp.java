package com.example.ghostvault;

import android.app.Application;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import com.example.ghostvault.security.SessionManager;
import com.example.ghostvault.security.VaultSecurity;

/** App-wide settings and an automatic in-memory vault lock when the app goes to background. */
public final class GhostVaultApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        boolean dark = new VaultSecurity(this).isDarkMode();
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override public void onStop(LifecycleOwner owner) {
                SessionManager.clear();
            }
        });
    }
}
