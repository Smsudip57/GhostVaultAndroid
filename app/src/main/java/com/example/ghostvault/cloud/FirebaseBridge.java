package com.example.ghostvault.cloud;

import android.content.Context;
import com.example.ghostvault.R;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/** Manual, optional Firebase setup: no google-services.json required for offline mode. */
public final class FirebaseBridge {
    private static final String NAME = "GhostVaultOptionalCloud";
    private static FirebaseApp customApp;
    private FirebaseBridge() {}

    public static boolean isConfigured(Context context) {
        if (!FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) return true;
        String apiKey = context.getString(R.string.firebase_api_key);
        String appId = context.getString(R.string.firebase_app_id);
        String projectId = context.getString(R.string.firebase_project_id);
        String databaseUrl = context.getString(R.string.firebase_database_url);
        return !apiKey.startsWith("YOUR_") && !appId.startsWith("YOUR_")
                && !projectId.startsWith("YOUR_") && !databaseUrl.contains("YOUR_")
                && databaseUrl.startsWith("https://");
    }

    private static synchronized FirebaseApp app(Context context) {
        if (!FirebaseApp.getApps(context.getApplicationContext()).isEmpty()) {
            return FirebaseApp.getInstance();
        }
        if (customApp != null) return customApp;
        for (FirebaseApp existing : FirebaseApp.getApps(context.getApplicationContext())) {
            if (NAME.equals(existing.getName())) { customApp = existing; return customApp; }
        }
        FirebaseOptions options = new FirebaseOptions.Builder()
                .setApiKey(context.getString(R.string.firebase_api_key))
                .setApplicationId(context.getString(R.string.firebase_app_id))
                .setProjectId(context.getString(R.string.firebase_project_id))
                .setDatabaseUrl(context.getString(R.string.firebase_database_url))
                .build();
        customApp = FirebaseApp.initializeApp(context.getApplicationContext(), options, NAME);
        return customApp;
    }

    public static FirebaseAuth auth(Context context) { return FirebaseAuth.getInstance(app(context)); }
    public static FirebaseDatabase db(Context context) {
        String dbUrl = context.getString(R.string.firebase_database_url);
        if (!dbUrl.isEmpty() && !dbUrl.contains("YOUR_")) {
            return FirebaseDatabase.getInstance(app(context), dbUrl);
        }
        return FirebaseDatabase.getInstance(app(context));
    }
}
