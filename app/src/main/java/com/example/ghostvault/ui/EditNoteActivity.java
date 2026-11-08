package com.example.ghostvault.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.example.ghostvault.R;
import com.example.ghostvault.data.Disk;
import com.example.ghostvault.data.GhostDatabase;
import com.example.ghostvault.data.NoteEntity;
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
        if (noteId <= 0) loaded = true;
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
            String t = null, b = null;
            try {
                NoteEntity entity = GhostDatabase.get(getApplicationContext()).noteDao().find(noteId, vault);
                if (entity != null) {
                    t = CryptoUtils.decrypt(entity.encryptedTitle, key);
                    b = CryptoUtils.decrypt(entity.encryptedBody, key);
                }
            } catch (Exception ignored) { }
            Arrays.fill(key, (byte) 0);
            final String foundTitle = t, foundBody = b;
            runOnUiThread(() -> {
                if (isFinishing() || !SessionManager.isOpen()) return;
                if (foundTitle == null) { Toast.makeText(this, "Note unavailable", Toast.LENGTH_SHORT).show(); finish(); return; }
                title.setText(foundTitle);
                body.setText(foundBody);
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
                    if (noteId <= 0) { note.createdAt = now; note.vaultId = vault; }
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
