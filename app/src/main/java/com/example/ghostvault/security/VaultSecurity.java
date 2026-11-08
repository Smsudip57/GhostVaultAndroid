package com.example.ghostvault.security;

import android.content.Context;
import android.content.SharedPreferences;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;

/** Credential metadata only. Note content lives encrypted in Room. */
public final class VaultSecurity {
    private static final String PREFS = "ghostvault_security_v1";
    private static final String REAL_SALT = "real_salt";
    private static final String REAL_CHECK = "real_check";
    private static final String DECOY_SALT = "decoy_salt";
    private static final String DECOY_CHECK = "decoy_check";
    private static final String FAILURES = "failures";
    private static final String FAILED_HASHES = "failed_hashes";
    private static final String DARK = "dark_theme";
    private final SharedPreferences prefs;

    public VaultSecurity(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean initialized() { return prefs.contains(REAL_SALT) && prefs.contains(REAL_CHECK); }
    public boolean decoyExists() { return prefs.contains(DECOY_CHECK); }
    public boolean isDarkMode() { return prefs.getBoolean(DARK, false); }
    public void setDarkMode(boolean enabled) { prefs.edit().putBoolean(DARK, enabled).apply(); }

    public static final class Result {
        public final VaultRules.Outcome outcome;
        public final byte[] key;
        private Result(VaultRules.Outcome outcome, byte[] key) {
            this.outcome = outcome;
            this.key = key;
        }
    }

    public void createRealVault(String password) throws GeneralSecurityException {
        if (initialized()) throw new IllegalStateException("Vault is already initialized");
        if (password.length() < 6) throw new IllegalArgumentException("At least 6 characters required");
        byte[] salt = CryptoUtils.freshSalt();
        byte[] key = CryptoUtils.deriveKey(password, salt);
        try {
            prefs.edit()
                    .putString(REAL_SALT, CryptoUtils.toBase64(salt))
                    .putString(REAL_CHECK, CryptoUtils.toBase64(CryptoUtils.verifier(key)))
                    .putInt(FAILURES, 0)
                    .remove(FAILED_HASHES)
                    .commit();
        } finally { Arrays.fill(key, (byte) 0); }
    }

    public Result authenticate(String input) throws GeneralSecurityException {
        if (!initialized()) throw new IllegalStateException("Setup required");
        if (input.isEmpty()) return new Result(VaultRules.Outcome.WRONG, null);
        byte[] realKey = CryptoUtils.deriveKey(input,
                CryptoUtils.fromBase64(prefs.getString(REAL_SALT, "")));
        boolean real = CryptoUtils.matches(realKey, prefs.getString(REAL_CHECK, ""));
        if (real) return new Result(VaultRules.Outcome.REAL, realKey);
        Arrays.fill(realKey, (byte) 0);

        boolean hasDecoy = decoyExists();
        byte[] decoyKey = null;
        boolean decoy = false;
        if (hasDecoy) {
            decoyKey = CryptoUtils.deriveKey(input,
                    CryptoUtils.fromBase64(prefs.getString(DECOY_SALT, "")));
            decoy = CryptoUtils.matches(decoyKey, prefs.getString(DECOY_CHECK, ""));
        }
        String attemptHash = hashAttempt(input);
        String storedHashes = prefs.getString(FAILED_HASHES, "");
        boolean isDuplicate = !hasDecoy && isHashPresent(storedHashes, attemptHash);

        VaultRules.Decision decision = VaultRules.resolve(false, hasDecoy, decoy,
                prefs.getInt(FAILURES, 0), isDuplicate);
        if (decision.outcome == VaultRules.Outcome.DECOY) return new Result(decision.outcome, decoyKey);
        if (decoyKey != null) Arrays.fill(decoyKey, (byte) 0);

        if (decision.outcome == VaultRules.Outcome.DECOY_CREATED) {
            // Sixth UNIQUE invalid input becomes the immutable decoy password until real unlock.
            byte[] salt = CryptoUtils.freshSalt();
            byte[] key = CryptoUtils.deriveKey(input, salt);
            prefs.edit()
                    .putString(DECOY_SALT, CryptoUtils.toBase64(salt))
                    .putString(DECOY_CHECK, CryptoUtils.toBase64(CryptoUtils.verifier(key)))
                    .remove(FAILED_HASHES)
                    .putInt(FAILURES, 5)
                    .commit();
            return new Result(decision.outcome, key);
        }
        if (!hasDecoy) {
            SharedPreferences.Editor editor = prefs.edit().putInt(FAILURES, decision.failedAttempts);
            if (!isDuplicate) {
                editor.putString(FAILED_HASHES, storedHashes.isEmpty() ? attemptHash : storedHashes + "," + attemptHash);
            }
            editor.commit();
        }
        return new Result(VaultRules.Outcome.WRONG, null);
    }

    /** Call ONLY after a real password is verified and decoy notes are deleted. */
    public void finishRealUnlock() {
        prefs.edit()
                .remove(DECOY_SALT)
                .remove(DECOY_CHECK)
                .remove(FAILED_HASHES)
                .putInt(FAILURES, 0)
                .commit();
    }

    private String hashAttempt(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] salt = CryptoUtils.fromBase64(prefs.getString(REAL_SALT, ""));
            md.update(salt);
            md.update(input.getBytes(StandardCharsets.UTF_8));
            return CryptoUtils.toBase64(md.digest());
        } catch (Exception e) {
            return input;
        }
    }

    private static boolean isHashPresent(String storedHashes, String targetHash) {
        if (storedHashes == null || storedHashes.isEmpty()) return false;
        String[] parts = storedHashes.split(",");
        for (String part : parts) {
            if (part.equals(targetHash)) return true;
        }
        return false;
    }

    public String activeSalt() {
        return prefs.getString(SessionManager.REAL.equals(SessionManager.vault())
                ? REAL_SALT : DECOY_SALT, "");
    }
}
