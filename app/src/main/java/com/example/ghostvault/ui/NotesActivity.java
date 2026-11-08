package com.example.ghostvault.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.ghostvault.R;
import com.example.ghostvault.data.Disk;
import com.example.ghostvault.data.GhostDatabase;
import com.example.ghostvault.data.NoteEntity;
import com.example.ghostvault.security.CryptoUtils;
import com.example.ghostvault.security.SessionManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class NotesActivity extends BaseSecureActivity {
    private NotesAdapter adapter;
    private View empty;
    private TextView countSubtitle;
    private NoteCountView countView;
    private int generation = 0;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notes);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.notes_menu);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) {
                startActivity(new Intent(this, SettingsActivity.class)); return true;
            }
            if (item.getItemId() == R.id.action_lock) { lockNow(); return true; }
            return false;
        });
        countSubtitle = findViewById(R.id.countSubtitle);
        countView = findViewById(R.id.noteCountRing);
        empty = findViewById(R.id.emptyNotes);
        adapter = new NotesAdapter(new NotesAdapter.Clicks() {
            @Override public void onTap(long id) {
                if (!SessionManager.isOpen()) return;
                startActivity(new Intent(NotesActivity.this, EditNoteActivity.class)
                        .putExtra(EditNoteActivity.EXTRA_NOTE_ID, id));
            }
            @Override public void onLongPress(long id) { confirmDelete(id); }
        });
        RecyclerView list = findViewById(R.id.recyclerNotes);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        TextInputEditText search = findViewById(R.id.searchNotes);
        search.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.applyFilter(s.toString());
                showEmpty();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
        ExtendedFloatingActionButton add = findViewById(R.id.fabAddNote);
        add.setOnClickListener(v -> {
            if (SessionManager.isOpen()) startActivity(new Intent(this, EditNoteActivity.class));
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (!requireSession()) return;
        loadNotes();
    }

    private void showEmpty() { empty.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE); }

    private void loadNotes() {
        int mine = ++generation;
        byte[] key = SessionManager.copyKey();
        String vault = SessionManager.vault();
        if (key == null || vault == null) return;
        Disk.run(() -> {
            List<NotesAdapter.Entry> items = new ArrayList<>();
            boolean good = true;
            try {
                for (NoteEntity note : GhostDatabase.get(getApplicationContext()).noteDao().allInVault(vault)) {
                    String title = CryptoUtils.decrypt(note.encryptedTitle, key);
                    String body = CryptoUtils.decrypt(note.encryptedBody, key);
                    String preview = body.replace('\n', ' ').trim();
                    if (preview.length() > 110) preview = preview.substring(0, 110) + "…";
                    items.add(new NotesAdapter.Entry(note.id, title, preview, note.updatedAt));
                }
            } catch (Exception e) { good = false; }
            Arrays.fill(key, (byte) 0);
            final boolean ok = good;
            runOnUiThread(() -> {
                if (mine != generation || isFinishing() || !SessionManager.isOpen()) return;
                if (!ok) {
                    countSubtitle.setText("Could not decrypt notes");
                    return;
                }
                adapter.submit(items);
                countView.setCount(adapter.totalCount());
                countSubtitle.setText(adapter.totalCount() == 1 ? "1 saved note" : adapter.totalCount() + " saved notes");
                showEmpty();
            });
        });
    }

    private void confirmDelete(long id) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete note?")
                .setMessage("This note will be permanently deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    String vault = SessionManager.vault();
                    if (vault == null) return;
                    Disk.run(() -> {
                        GhostDatabase.get(getApplicationContext()).noteDao().delete(id, vault);
                        runOnUiThread(() -> { if (SessionManager.isOpen()) loadNotes(); });
                    });
                }).show();
    }
}
