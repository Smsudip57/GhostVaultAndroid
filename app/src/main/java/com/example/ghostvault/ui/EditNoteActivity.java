package com.example.ghostvault.ui;

import android.Manifest;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.example.ghostvault.R;
import com.example.ghostvault.data.Disk;
import com.example.ghostvault.data.GhostDatabase;
import com.example.ghostvault.data.NoteEntity;
import com.example.ghostvault.location.LocationHelper;
import com.example.ghostvault.security.CryptoUtils;
import com.example.ghostvault.security.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.Arrays;

public final class EditNoteActivity extends BaseSecureActivity {
    public static final String EXTRA_NOTE_ID = "note_id";
    private long noteId;
    private EditText title, body;
    private Button save, delete;
    private View cardLocation, btnRemoveLocation;
    private TextView txtNoteLocation;
    private String noteLocation = null;
    private boolean locationDismissed = false;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private boolean loaded = false;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_note);
        noteId = getIntent().getLongExtra(EXTRA_NOTE_ID, -1);
        title = findViewById(R.id.noteEditTitle);
        body = findViewById(R.id.noteEditBody);
        save = findViewById(R.id.btnSaveNote);
        delete = findViewById(R.id.btnDeleteNote);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(noteId > 0 ? "Edit note" : "New note");
        toolbar.setNavigationOnClickListener(v -> finish());
        save.setOnClickListener(v -> saveNote());
        delete.setVisibility(noteId > 0 ? View.VISIBLE : View.GONE);
        delete.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("Delete note?")
                .setMessage("This action cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteNote()).show());

        cardLocation = findViewById(R.id.cardLocation);
        txtNoteLocation = findViewById(R.id.txtNoteLocation);
        btnRemoveLocation = findViewById(R.id.btnRemoveLocation);

        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean fineGranted = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarseGranted = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if ((fineGranted != null && fineGranted) || (coarseGranted != null && coarseGranted)) {
                        acquireLocation();
                    } else {
                        txtNoteLocation.setText("Location permission denied (tap to retry)");
                        btnRemoveLocation.setVisibility(View.GONE);
                    }
                });

        cardLocation.setOnClickListener(v -> {
            if (noteId <= 0 && (locationDismissed || noteLocation == null || noteLocation.isEmpty())) {
                locationDismissed = false;
                requestLocation();
            }
        });

        btnRemoveLocation.setOnClickListener(v -> {
            locationDismissed = true;
            noteLocation = null;
            txtNoteLocation.setText("No location attached (tap to add)");
            btnRemoveLocation.setVisibility(View.GONE);
        });

        if (noteId <= 0) {
            loaded = true;
            requestLocation();
        }
    }

    private void requestLocation() {
        if (LocationHelper.hasLocationPermission(this)) {
            acquireLocation();
        } else {
            cardLocation.setVisibility(View.VISIBLE);
            txtNoteLocation.setText("Requesting location permission…");
            btnRemoveLocation.setVisibility(View.GONE);
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void acquireLocation() {
        if (locationDismissed) return;
        cardLocation.setVisibility(View.VISIBLE);
        txtNoteLocation.setText("Acquiring location…");
        btnRemoveLocation.setVisibility(View.VISIBLE);
        LocationHelper.getCurrentLocation(this, new LocationHelper.Callback() {
            @Override
            public void onLocationResolved(String locationInfo) {
                if (isFinishing() || locationDismissed) return;
                noteLocation = locationInfo;
                cardLocation.setVisibility(View.VISIBLE);
                txtNoteLocation.setText(locationInfo);
                btnRemoveLocation.setVisibility(View.VISIBLE);
            }

            @Override
            public void onLocationFailed(String reason) {
                if (isFinishing() || locationDismissed) return;
                if (noteLocation == null) {
                    txtNoteLocation.setText("Location unavailable (" + reason + ")");
                    btnRemoveLocation.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (!requireSession()) return;
        if (noteId > 0 && !loaded) loadNote();
    }

    private void loadNote() {
        byte[] key = SessionManager.copyKey();
        String vault = SessionManager.vault();
        if (key == null || vault == null) return;
        save.setEnabled(false);
        Disk.run(() -> {
            String t = null, b = null, loc = null;
            try {
                NoteEntity entity = GhostDatabase.get(getApplicationContext()).noteDao().find(noteId, vault);
                if (entity != null) {
                    t = CryptoUtils.decrypt(entity.encryptedTitle, key);
                    b = CryptoUtils.decrypt(entity.encryptedBody, key);
                    if (entity.encryptedLocation != null && !entity.encryptedLocation.isEmpty()) {
                        loc = CryptoUtils.decrypt(entity.encryptedLocation, key);
                    }
                }
            } catch (Exception ignored) { }
            Arrays.fill(key, (byte) 0);
            final String foundTitle = t, foundBody = b, foundLoc = loc;
            runOnUiThread(() -> {
                if (isFinishing() || !SessionManager.isOpen()) return;
                if (foundTitle == null) { Toast.makeText(this, "Note unavailable", Toast.LENGTH_SHORT).show(); finish(); return; }
                title.setText(foundTitle);
                body.setText(foundBody);
                if (foundLoc != null && !foundLoc.isEmpty()) {
                    noteLocation = foundLoc;
                    cardLocation.setVisibility(View.VISIBLE);
                    txtNoteLocation.setText("Created at: " + foundLoc);
                    btnRemoveLocation.setVisibility(View.GONE);
                } else {
                    cardLocation.setVisibility(View.GONE);
                }
                loaded = true;
                save.setEnabled(true);
            });
        });
    }

    private void saveNote() {
        if (!loaded) return;
        String titleValue = title.getText().toString();
        String bodyValue = body.getText().toString();
        if (titleValue.trim().isEmpty() && bodyValue.trim().isEmpty()) {
            Toast.makeText(this, "Write something first", Toast.LENGTH_SHORT).show(); return;
        }
        byte[] key = SessionManager.copyKey();
        String vault = SessionManager.vault();
        if (key == null || vault == null) { lockNow(); return; }
        save.setEnabled(false);
        Disk.run(() -> {
            boolean success = false;
            try {
                NoteEntity note = noteId > 0
                        ? GhostDatabase.get(getApplicationContext()).noteDao().find(noteId, vault)
                        : new NoteEntity();
                if (note != null) {
                    long now = System.currentTimeMillis();
                    if (noteId <= 0) {
                        note.createdAt = now;
                        note.vaultId = vault;
                        if (!locationDismissed && noteLocation != null && !noteLocation.trim().isEmpty()) {
                            note.encryptedLocation = CryptoUtils.encrypt(noteLocation.trim(), key);
                        }
                    }
                    note.encryptedTitle = CryptoUtils.encrypt(titleValue, key);
                    note.encryptedBody = CryptoUtils.encrypt(bodyValue, key);
                    note.updatedAt = now;
                    if (noteId > 0) GhostDatabase.get(getApplicationContext()).noteDao().update(note);
                    else GhostDatabase.get(getApplicationContext()).noteDao().insert(note);
                    success = true;
                }
            } catch (Exception ignored) { }
            finally { Arrays.fill(key, (byte) 0); }
            final boolean ok = success;
            runOnUiThread(() -> {
                if (isFinishing()) return;
                save.setEnabled(true);
                if (ok) finish();
                else Toast.makeText(this, "Unable to save note", Toast.LENGTH_LONG).show();
            });
        });
    }

    private void deleteNote() {
        String vault = SessionManager.vault();
        if (vault == null) { lockNow(); return; }
        Disk.run(() -> {
            GhostDatabase.get(getApplicationContext()).noteDao().delete(noteId, vault);
            runOnUiThread(() -> { if (!isFinishing()) finish(); });
        });
    }
}
