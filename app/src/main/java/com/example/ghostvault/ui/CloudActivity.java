package com.example.ghostvault.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.example.ghostvault.R;
import com.example.ghostvault.cloud.FirebaseBridge;
import com.example.ghostvault.data.Disk;
import com.example.ghostvault.data.GhostDatabase;
import com.example.ghostvault.data.NoteEntity;
import com.example.ghostvault.security.CryptoUtils;
import com.example.ghostvault.security.SessionManager;
import com.example.ghostvault.security.VaultSecurity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CloudActivity extends BaseSecureActivity {
    private TextView status, txtUserEmail;
    private LinearLayout layoutSignedOut, layoutSignedIn;
    private Button btnGoogleSignIn, btnSignOut, btnUpload, btnRestore;
    private FirebaseAuth auth;
    private GoogleSignInClient googleSignInClient;
    private ActivityResultLauncher<Intent> signInLauncher;

    private boolean isAuthenticating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        status = findViewById(R.id.cloudStatus);
        txtUserEmail = findViewById(R.id.txtUserEmail);
        layoutSignedOut = findViewById(R.id.layoutSignedOut);
        layoutSignedIn = findViewById(R.id.layoutSignedIn);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        btnSignOut = findViewById(R.id.btnSignOut);
        btnUpload = findViewById(R.id.btnCloudUpload);
        btnRestore = findViewById(R.id.btnCloudRestore);

        if (!FirebaseBridge.isConfigured(this)) {
            status.setText("Firebase is not configured. Check google-services.json.");
            btnGoogleSignIn.setEnabled(false);
            return;
        }

        try {
            auth = FirebaseBridge.auth(this);

            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail()
                    .build();
            googleSignInClient = GoogleSignIn.getClient(this, gso);

            signInLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        isAuthenticating = false;
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                            try {
                                GoogleSignInAccount account = task.getResult(ApiException.class);
                                if (account != null && account.getIdToken() != null) {
                                    firebaseAuthWithGoogle(account.getIdToken());
                                } else {
                                    String msg = "Google Sign-In failed: No ID token returned.";
                                    status.setText(msg);
                                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                                }
                            } catch (ApiException e) {
                                String msg = "Google Sign-In error (code " + e.getStatusCode() + ": " + e.getMessage() + ")";
                                status.setText(msg);
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                            }
                        } else {
                            String msg = "Google Sign-In canceled (resultCode " + result.getResultCode() + ")";
                            status.setText(msg);
                            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        }
                    });

            btnGoogleSignIn.setOnClickListener(v -> {
                isAuthenticating = true;
                status.setText("Opening Google account picker…");
                signInLauncher.launch(googleSignInClient.getSignInIntent());
            });

            btnSignOut.setOnClickListener(v -> signOut());
            btnUpload.setOnClickListener(v -> uploadEncryptedSnapshot());
            btnRestore.setOnClickListener(v -> restoreEncryptedSnapshot());

            refreshStatus();
        } catch (Exception e) {
            status.setText("Could not initialize Firebase: " + e.getMessage());
            btnGoogleSignIn.setEnabled(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        requireSession();
        if (!isAuthenticating && auth != null && auth.getCurrentUser() != null) {
            refreshStatus();
        }
    }

    private void refreshStatus() {
        if (auth == null) return;
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            layoutSignedOut.setVisibility(View.GONE);
            layoutSignedIn.setVisibility(View.VISIBLE);
            txtUserEmail.setText(user.getEmail() != null ? user.getEmail() : user.getUid());
            status.setText("Signed in with Google. Ready to backup or restore.");
        } else {
            layoutSignedOut.setVisibility(View.VISIBLE);
            layoutSignedIn.setVisibility(View.GONE);
            status.setText("Not signed in. Connect your Google account below.");
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        status.setText("Authenticating with Firebase…");
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        refreshStatus();
                        Toast.makeText(this, "Signed in with Google!", Toast.LENGTH_SHORT).show();
                    } else {
                        Exception e = task.getException();
                        String msg = "Authentication failed: " + (e != null ? e.getMessage() : "Unknown");
                        status.setText(msg);
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void signOut() {
        if (auth != null) auth.signOut();
        if (googleSignInClient != null) {
            googleSignInClient.signOut().addOnCompleteListener(this, t -> {
                refreshStatus();
                Toast.makeText(this, "Signed out.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void uploadEncryptedSnapshot() {
        if (auth == null || auth.getCurrentUser() == null || !SessionManager.isOpen()) return;
        final String uid = auth.getCurrentUser().getUid();
        final String vault = SessionManager.vault();
        btnUpload.setEnabled(false);
        status.setText("Preparing encrypted snapshot…");

        Disk.run(() -> {
            try {
                List<NoteEntity> notes = GhostDatabase.get(getApplicationContext()).noteDao().allInVault(vault);
                Map<String, Object> payload = new HashMap<>();
                payload.put("format", 1);
                payload.put("createdAt", System.currentTimeMillis());
                payload.put("kdfSalt", new VaultSecurity(this).activeSalt());

                Map<String, Object> records = new HashMap<>();
                for (NoteEntity note : notes) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("title", note.encryptedTitle);
                    row.put("body", note.encryptedBody);
                    if (note.encryptedLocation != null) {
                        row.put("location", note.encryptedLocation);
                    }
                    row.put("createdAt", note.createdAt);
                    row.put("updatedAt", note.updatedAt);
                    records.put(Long.toString(note.id), row);
                }
                payload.put("notes", records);

                runOnUiThread(() -> {
                    if (isFinishing() || !SessionManager.isOpen()) return;
                    DatabaseReference ref = FirebaseBridge.db(this).getReference("users")
                            .child(uid).child("snapshots")
                            .child(SessionManager.REAL.equals(vault) ? "primary" : "secondary");

                    ref.setValue(payload).addOnCompleteListener(task -> {
                        btnUpload.setEnabled(true);
                        if (task.isSuccessful()) {
                            status.setText("Encrypted backup uploaded successfully! (" + notes.size() + " notes)");
                            Toast.makeText(this, "Backup complete!", Toast.LENGTH_SHORT).show();
                        } else {
                            status.setText("Upload failed: " + (task.getException() != null ? task.getException().getMessage() : "Check rules/connection"));
                        }
                    });
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnUpload.setEnabled(true);
                    Toast.makeText(this, "Could not prepare export: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void restoreEncryptedSnapshot() {
        if (auth == null || auth.getCurrentUser() == null || !SessionManager.isOpen()) return;
        final String uid = auth.getCurrentUser().getUid();
        final String vault = SessionManager.vault();
        btnRestore.setEnabled(false);
        status.setText("Fetching backup from cloud…");

        DatabaseReference ref = FirebaseBridge.db(this).getReference("users")
                .child(uid).child("snapshots")
                .child(SessionManager.REAL.equals(vault) ? "primary" : "secondary");

        ref.get().addOnCompleteListener(task -> {
            btnRestore.setEnabled(true);
            if (!task.isSuccessful() || !task.getResult().exists()) {
                status.setText("No cloud backup found for this account.");
                return;
            }

            DataSnapshot snapshot = task.getResult();
            String kdfSalt = snapshot.child("kdfSalt").getValue(String.class);
            DataSnapshot notesNode = snapshot.child("notes");

            if (!notesNode.exists() || kdfSalt == null || !notesNode.hasChildren()) {
                status.setText("Backup is empty.");
                return;
            }

            // Test decrypting with current session key
            DataSnapshot firstChild = notesNode.getChildren().iterator().next();
            String testTitle = firstChild.child("title").getValue(String.class);

            boolean canDecryptDirectly = false;
            byte[] currentKey = SessionManager.copyKey();
            try {
                if (testTitle != null && currentKey != null) {
                    CryptoUtils.decrypt(testTitle, currentKey);
                    canDecryptDirectly = true;
                }
            } catch (Exception ignored) {
            } finally {
                if (currentKey != null) Arrays.fill(currentKey, (byte) 0);
            }

            if (canDecryptDirectly) {
                importNotesFromSnapshot(notesNode, null, vault);
            } else {
                promptPasswordAndRestore(notesNode, kdfSalt, vault);
            }
        });
    }

    private void promptPasswordAndRestore(DataSnapshot notesNode, String kdfSalt, String vault) {
        EditText input = new EditText(this);
        input.setHint("Original vault password");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Decrypt Cloud Backup")
                .setMessage("This backup was encrypted with a different key or password. Enter the original password used when creating this backup:")
                .setView(input)
                .setPositiveButton("Restore", (dialog, which) -> {
                    String pass = input.getText().toString();
                    if (pass.isEmpty()) return;
                    status.setText("Deriving key and decrypting…");
                    Disk.run(() -> {
                        try {
                            byte[] salt = CryptoUtils.fromBase64(kdfSalt);
                            byte[] backupKey = CryptoUtils.deriveKey(pass, salt);

                            // Test decrypting
                            DataSnapshot first = notesNode.getChildren().iterator().next();
                            String testCipher = first.child("title").getValue(String.class);
                            CryptoUtils.decrypt(testCipher, backupKey);

                            importNotesFromSnapshot(notesNode, backupKey, vault);
                            Arrays.fill(backupKey, (byte) 0);
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                status.setText("Decryption failed: Incorrect vault password.");
                                Toast.makeText(this, "Incorrect password for this backup", Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void importNotesFromSnapshot(DataSnapshot notesNode, byte[] backupKey, String vault) {
        Disk.run(() -> {
            byte[] activeKey = SessionManager.copyKey();
            if (activeKey == null) return;
            try {
                int imported = 0;
                for (DataSnapshot child : notesNode.getChildren()) {
                    String titleCipher = child.child("title").getValue(String.class);
                    String bodyCipher = child.child("body").getValue(String.class);
                    String locCipher = child.child("location").getValue(String.class);
                    Long createdAt = child.child("createdAt").getValue(Long.class);
                    Long updatedAt = child.child("updatedAt").getValue(Long.class);

                    if (titleCipher == null || bodyCipher == null) continue;

                    NoteEntity entity = new NoteEntity();
                    entity.vaultId = vault;
                    entity.createdAt = createdAt != null ? createdAt : System.currentTimeMillis();
                    entity.updatedAt = updatedAt != null ? updatedAt : System.currentTimeMillis();

                    if (backupKey != null) {
                        String plainTitle = CryptoUtils.decrypt(titleCipher, backupKey);
                        String plainBody = CryptoUtils.decrypt(bodyCipher, backupKey);
                        entity.encryptedTitle = CryptoUtils.encrypt(plainTitle, activeKey);
                        entity.encryptedBody = CryptoUtils.encrypt(plainBody, activeKey);
                        if (locCipher != null) {
                            String plainLoc = CryptoUtils.decrypt(locCipher, backupKey);
                            entity.encryptedLocation = CryptoUtils.encrypt(plainLoc, activeKey);
                        }
                    } else {
                        entity.encryptedTitle = titleCipher;
                        entity.encryptedBody = bodyCipher;
                        entity.encryptedLocation = locCipher;
                    }

                    GhostDatabase.get(getApplicationContext()).noteDao().insert(entity);
                    imported++;
                }

                final int total = imported;
                runOnUiThread(() -> {
                    status.setText("Restore complete! " + total + " notes restored.");
                    Toast.makeText(this, total + " notes restored!", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    status.setText("Failed to import notes: " + e.getMessage());
                });
            } finally {
                Arrays.fill(activeKey, (byte) 0);
            }
        });
    }
}
