package com.example.ghostvault.security;

import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/** Password-derived AES-256-GCM encryption; no plaintext notes or passwords on disk. */
public final class CryptoUtils {
    public static final int SALT_SIZE = 16;
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final byte[] CHECK_LABEL =
            "GhostVault password verifier v1".getBytes(StandardCharsets.UTF_8);

    private CryptoUtils() {}

    public static byte[] freshSalt() {
        byte[] value = new byte[SALT_SIZE];
        RANDOM.nextBytes(value);
        return value;
    }

    public static byte[] deriveKey(String password, byte[] salt) throws GeneralSecurityException {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    public static byte[] verifier(byte[] key) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(CHECK_LABEL);
    }

    public static boolean matches(byte[] derivedKey, String encodedVerifier)
            throws GeneralSecurityException {
        if (encodedVerifier == null || encodedVerifier.isEmpty()) return false;
        try {
            return MessageDigest.isEqual(verifier(derivedKey), fromBase64(encodedVerifier));
        } catch (IllegalArgumentException invalidData) {
            return false;
        }
    }

    public static String encrypt(String plaintext, byte[] key) throws GeneralSecurityException {
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] payload = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, payload, 0, iv.length);
        System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
        return toBase64(payload);
    }

    public static String decrypt(String payload64, byte[] key) throws GeneralSecurityException {
        byte[] payload;
        try { payload = fromBase64(payload64); }
        catch (IllegalArgumentException e) { throw new GeneralSecurityException("Bad ciphertext", e); }
        if (payload.length < IV_BYTES + 16) throw new GeneralSecurityException("Bad ciphertext");
        byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    public static String toBase64(byte[] bytes) { return Base64.encodeToString(bytes, Base64.NO_WRAP); }
    public static byte[] fromBase64(String text) { return Base64.decode(text, Base64.NO_WRAP); }
}
