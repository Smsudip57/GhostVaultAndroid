package com.example.ghostvault.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.example.ghostvault.data.Disk;
import java.util.List;
import java.util.Locale;

public final class LocationHelper {
    public interface Callback {
        void onLocationResolved(String locationInfo);
        void onLocationFailed(String reason);
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("MissingPermission")
    public static void getCurrentLocation(Context context, @NonNull Callback callback) {
        if (!hasLocationPermission(context)) {
            callback.onLocationFailed("Location permission not granted");
            return;
        }

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            callback.onLocationFailed("Location service unavailable");
            return;
        }

        // Search across all providers for the freshest / best last known location
        Location bestLastKnown = null;
        List<String> allProviders = null;
        try {
            allProviders = lm.getAllProviders();
        } catch (Exception ignored) { }

        if (allProviders != null) {
            for (String provider : allProviders) {
                try {
                    Location loc = lm.getLastKnownLocation(provider);
                    if (loc == null) continue;
                    if (bestLastKnown == null || loc.getTime() > bestLastKnown.getTime()) {
                        bestLastKnown = loc;
                    }
                } catch (Exception ignored) { }
            }
        }

        boolean networkEnabled = false;
        boolean gpsEnabled = false;
        try { networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER); } catch (Exception ignored) { }
        try { gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER); } catch (Exception ignored) { }

        // If providers are completely disabled and we have no fallback, fail fast
        if (!networkEnabled && !gpsEnabled) {
            if (bestLastKnown != null) {
                resolveAddress(context, bestLastKnown, callback);
            } else {
                callback.onLocationFailed("Location is disabled on device");
            }
            return;
        }

        long now = System.currentTimeMillis();
        // If last known location is fresh (less than 3 minutes old), resolve immediately
        if (bestLastKnown != null && (now - bestLastKnown.getTime()) < 3 * 60 * 1000) {
            resolveAddress(context, bestLastKnown, callback);
            return;
        }

        final Location fallbackLoc = bestLastKnown;
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final boolean[] completed = new boolean[]{false};

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            CancellationSignal signal = new CancellationSignal();
            Runnable cancelRunnable = () -> {
                if (!completed[0]) {
                    completed[0] = true;
                    try { signal.cancel(); } catch (Exception ignored) { }
                    if (fallbackLoc != null) {
                        resolveAddress(context, fallbackLoc, callback);
                    } else {
                        callback.onLocationFailed("Location request timed out");
                    }
                }
            };
            mainHandler.postDelayed(cancelRunnable, 5000);

            try {
                String provider;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && allProviders != null && allProviders.contains(LocationManager.FUSED_PROVIDER)) {
                    provider = LocationManager.FUSED_PROVIDER;
                } else {
                    provider = networkEnabled ? LocationManager.NETWORK_PROVIDER : LocationManager.GPS_PROVIDER;
                }
                lm.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context), location -> {
                    if (completed[0]) return;
                    completed[0] = true;
                    mainHandler.removeCallbacks(cancelRunnable);
                    if (location != null) {
                        resolveAddress(context, location, callback);
                    } else if (fallbackLoc != null) {
                        resolveAddress(context, fallbackLoc, callback);
                    } else {
                        callback.onLocationFailed("Could not acquire location fix");
                    }
                });
                return;
            } catch (Exception ignored) {
                mainHandler.removeCallbacks(cancelRunnable);
            }
        }

        // Legacy / fallback path for Android < 11
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (completed[0]) return;
                completed[0] = true;
                try {
                    lm.removeUpdates(this);
                } catch (Exception ignored) { }
                resolveAddress(context, location, callback);
            }

            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        };

        Runnable timeout = () -> {
            if (completed[0]) return;
            completed[0] = true;
            try {
                lm.removeUpdates(listener);
            } catch (Exception ignored) { }
            if (fallbackLoc != null) {
                resolveAddress(context, fallbackLoc, callback);
            } else {
                callback.onLocationFailed("Location request timed out");
            }
        };
        mainHandler.postDelayed(timeout, 5000);

        boolean registered = false;
        try {
            if (networkEnabled) {
                lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper());
                registered = true;
            }
        } catch (Exception ignored) { }

        try {
            if (gpsEnabled) {
                lm.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper());
                registered = true;
            }
        } catch (Exception ignored) { }

        if (!registered) {
            mainHandler.removeCallbacks(timeout);
            if (fallbackLoc != null) {
                resolveAddress(context, fallbackLoc, callback);
            } else {
                callback.onLocationFailed("Location providers disabled");
            }
        }
    }

    private static void resolveAddress(Context context, Location loc, Callback callback) {
        Disk.run(() -> {
            double lat = loc.getLatitude();
            double lon = loc.getLongitude();
            String coords = String.format(Locale.US, "%.4f, %.4f", lat, lon);
            String formatted = coords;

            if (Geocoder.isPresent()) {
                try {
                    Geocoder geocoder = new Geocoder(context.getApplicationContext(), Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address a = addresses.get(0);
                        StringBuilder sb = new StringBuilder();
                        String locality = a.getLocality();
                        String subAdmin = a.getSubAdminArea();
                        String admin = a.getAdminArea();
                        String country = a.getCountryName();

                        if (locality != null && !locality.isEmpty()) {
                            sb.append(locality);
                        } else if (subAdmin != null && !subAdmin.isEmpty()) {
                            sb.append(subAdmin);
                        } else if (a.getFeatureName() != null && !a.getFeatureName().isEmpty()) {
                            sb.append(a.getFeatureName());
                        }

                        if (admin != null && !admin.isEmpty()) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(admin);
                        } else if (country != null && !country.isEmpty()) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(country);
                        }

                        if (sb.length() > 0) {
                            formatted = sb.toString() + " (" + coords + ")";
                        }
                    }
                } catch (Exception ignored) { }
            }

            final String result = formatted;
            new Handler(Looper.getMainLooper()).post(() -> callback.onLocationResolved(result));
        });
    }
}
