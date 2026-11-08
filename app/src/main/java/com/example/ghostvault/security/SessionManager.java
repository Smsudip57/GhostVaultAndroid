package com.example.ghostvault.security;

import java.util.Arrays;

/** Only an in-memory derived key. Never pass this key or the password via Intent extras. */
public final class SessionManager {
    public static final String REAL = "REAL";
    public static final String DECOY = "DECOY";
    private static byte[] key;
    private static String vault;
    private SessionManager() {}

    public static synchronized void open(String vaultId, byte[] derivedKey) {
        clear();
        vault = vaultId;
        key = derivedKey.clone();
    }

    public static synchronized boolean isOpen() { return key != null && vault != null; }
    public static synchronized String vault() { return vault; }
    public static synchronized byte[] copyKey() { return key == null ? null : key.clone(); }
    public static synchronized void clear() {
        if (key != null) Arrays.fill(key, (byte) 0);
        key = null;
        vault = null;
    }
}
