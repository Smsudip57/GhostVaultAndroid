# GhostVault · Android Encrypted Notebook

A local-first, zero-knowledge encrypted notebook for Android featuring a **plausibly deniable, persistent decoy vault** and **Google Cloud Backup & Restore**.

- **Platform:** Native Android (Java + XML)
- **Minimum SDK:** Android 8.0 (API 26) · **Target SDK:** Android 14/15 (API 34/35)
- **Local Storage:** Room / SQLite with AES-256-GCM field-level encryption
- **Key Derivation:** PBKDF2-HMAC-SHA256 (210,000 iterations) with cryptographic 16-byte salt
- **Cloud Backend:** Firebase Realtime Database & Firebase Authentication with Google Sign-In

---

## Key Highlights

- **Zero-Knowledge Encryption:** Note titles, bodies, and creation location metadata are encrypted locally using AES-256-GCM. Plaintext notes and vault passwords are never stored on disk or sent over the network.
- **Location-Aware Creation Metadata:** When a new note is created, the device's location (address locality and coordinates) is automatically captured, encrypted with the vault key, and displayed alongside the note in the note list and editor. Users can search notes by location, retry capture, or remove the location tag before saving.
- **Plausible Deniability (Decoy Vault):**
  - Entering **six unique and completely distinct wrong passwords** creates a persistent, empty decoy notebook.
  - Repeating the same wrong password will **never** trigger the decoy, preventing intruders from discovering the spoofing behavior.
  - The decoy password remains valid across app restarts and force-stops.
  - Entering the **real** password immediately wipes all decoy notes, invalidates the decoy password, and seamlessly opens the genuine notebook.
- **Google Cloud Backup & Restore:**
  - One-tap **Sign in with Google** via Firebase Authentication.
  - Exports encrypted snapshots (AES-256-GCM ciphertext only) to Firebase Realtime Database.
  - Full cross-device **Restore** capability: download and decrypt your notes on a new device using your original vault password.
- **Anti-Snooping Protections:**
  - Auto-locks and clears in-memory encryption keys when the app is backgrounded.
  - Prevents screenshots and screen recording using Android's `FLAG_SECURE`.

---

## Credential State Machine

| User Input / State | Expected Behavior |
|---|---|
| **Real password** (any attempt) | Unlocks genuine vault; permanently wipes all decoy notes; removes decoy verifier; resets failure counters. |
| **Duplicate wrong password** (attempts 1–5) | Rejected with standard `Incorrect password` error. Does **not** advance attempt counter. |
| **Unique wrong passwords 1–5** | Recorded as distinct attempts; failure counter increments in secure storage. |
| **6th unique wrong password** | Saves salted verifier for this exact password; silently unlocks a new, empty decoy notebook. |
| **Saved decoy password** | Unlocks the persistent decoy vault across restarts until real password is entered. |
| **Other wrong password** (while decoy exists) | Rejected; cannot overwrite the active decoy password. |

---

## Tech Stack & Architecture

- **UI & Navigation:** Android Material 3 Components, Activities, Explicit Intents, Custom Canvas circular note counter (`NoteCountView`).
- **Persistence:** Android Jetpack Room (SQLite ORM) partitioned by `vault_id` (`REAL` vs. `DECOY`).
- **Security:** Standard Java Cryptography Architecture (`javax.crypto.Cipher`, `SecretKeyFactory`, `MessageDigest`, `SecureRandom`).
- **Cloud Sync:** Google Play Services Auth (`play-services-auth`), Firebase Authentication, and Firebase Realtime Database.

---

## Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/example/ghostvault/
│   ├── GhostVaultApp.java               // Application class with auto-lock lifecycle
│   ├── security/
│   │   ├── CryptoUtils.java             // PBKDF2 key derivation & AES-256-GCM encryption
│   │   ├── SessionManager.java          // In-memory zeroed-out session key management
│   │   ├── VaultRules.java              // Deterministic 6-unique-attempt state machine
│   │   └── VaultSecurity.java           // SharedPreferences verifiers & failure tracking
│   ├── data/
│   │   ├── NoteEntity.java              // Room entity with encrypted fields
│   │   ├── NoteDao.java                 // Room database queries and CRUD
│   │   ├── GhostDatabase.java           // Room database instance
│   │   └── Disk.java                    // Background worker thread executor
│   ├── location/
│   │   └── LocationHelper.java          // Location capture & geocoding resolution
│   ├── ui/
│   │   ├── BaseSecureActivity.java      // Base activity with FLAG_SECURE
│   │   ├── SetupActivity.java           // First-launch password creation
│   │   ├── UnlockActivity.java          // Lock screen & failure counter
│   │   ├── NotesActivity.java           // Main note list, live search & count view
│   │   ├── EditNoteActivity.java        // Note creation and editor
│   │   ├── SettingsActivity.java        // Dark theme & manual lock
│   │   ├── CloudActivity.java           // Google Sign-In & backup/restore
│   │   ├── NotesAdapter.java            // RecyclerView adapter & view holder
│   │   └── NoteCountView.java           // Custom 2D Canvas note count graphic
│   └── cloud/
│       └── FirebaseBridge.java          // Dynamic Firebase App initialization
└── res/
    ├── layout/                          // Material 3 XML layouts
    └── values/                          // Color schemes, styles, strings & config
```

---

## Building & Running

1. Open the project in **Android Studio**.
2. Ensure you have **Android SDK 35** and **JDK 17+** configured.
3. Sync Gradle and run on an Android device or emulator running **Android 8.0 (API 26) or higher**.
4. To build debug APK from command line:
   ```bash
   ./gradlew assembleDebug
   ```
   Output APK: `app/build/outputs/apk/debug/app-debug.apk`

---

## License

Created for educational and privacy engineering demonstration.
