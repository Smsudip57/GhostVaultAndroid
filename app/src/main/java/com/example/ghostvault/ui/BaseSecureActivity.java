package com.example.ghostvault.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import androidx.annotation.Nullable;
import androidx.lifecycle.Lifecycle;
import androidx.appcompat.app.AppCompatActivity;
import com.example.ghostvault.security.SessionManager;

/** Hides screen snapshots and sends background-locked activities to the login screen. */
public abstract class BaseSecureActivity extends AppCompatActivity {
    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
    }

    protected boolean activityIsVisible() {
        return getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
    }

    protected boolean requireSession() {
        if (SessionManager.isOpen()) return true;
        Intent intent = new Intent(this, UnlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        return false;
    }

    protected void lockNow() {
        SessionManager.clear();
        Intent intent = new Intent(this, UnlockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
