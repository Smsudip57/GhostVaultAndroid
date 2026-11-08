package com.example.ghostvault.location;

import com.example.ghostvault.data.NoteEntity;
import com.example.ghostvault.security.CryptoUtils;
import com.example.ghostvault.ui.NotesAdapter;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NoteLocationTest {

    @Test
    public void testLocationEncryptionAndDecryption() throws Exception {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        String sampleLocation = "Austin, TX (30.2672, -97.7431)";
        String cipher = CryptoUtils.encrypt(sampleLocation, key);
        assertNotNull(cipher);

        String decrypted = CryptoUtils.decrypt(cipher, key);
        assertEquals(sampleLocation, decrypted);
    }

    @Test
    public void testNoteEntityWithLocation() {
        NoteEntity note = new NoteEntity();
        note.vaultId = "REAL";
        note.encryptedTitle = "cipherTitle";
        note.encryptedBody = "cipherBody";
        note.encryptedLocation = "cipherLocation";
        note.createdAt = 1000L;
        note.updatedAt = 1000L;

        assertEquals("cipherLocation", note.encryptedLocation);
    }

    @Test
    public void testNotesAdapterFilteringByLocation() {
        List<NotesAdapter.Entry> entries = new ArrayList<>();
        entries.add(new NotesAdapter.Entry(1, "Groceries", "Milk, Eggs", "Austin, TX (30.2672, -97.7431)", 1000L));
        entries.add(new NotesAdapter.Entry(2, "Secret Ideas", "Quantum vault", "Tokyo, Japan (35.6762, 139.6503)", 2000L));
        entries.add(new NotesAdapter.Entry(3, "Work Note", "Meeting agenda", null, 3000L));

        NotesAdapter adapter = new NotesAdapter(new NotesAdapter.Clicks() {
            @Override public void onTap(long id) {}
            @Override public void onLongPress(long id) {}
        });
        adapter.submit(entries);
        assertEquals(3, adapter.getItemCount());

        // Filter by location name
        adapter.applyFilter("Austin");
        assertEquals(1, adapter.getItemCount());

        // Filter by country in location
        adapter.applyFilter("Japan");
        assertEquals(1, adapter.getItemCount());

        // Filter by coordinate in location
        adapter.applyFilter("35.6762");
        assertEquals(1, adapter.getItemCount());

        // Empty filter shows all
        adapter.applyFilter("");
        assertEquals(3, adapter.getItemCount());
    }
}
