package com.example.ghostvault.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface NoteDao {
    @Query("SELECT * FROM notes WHERE vault_id = :vaultId ORDER BY updated_at DESC")
    List<NoteEntity> allInVault(String vaultId);

    @Query("SELECT * FROM notes WHERE id = :noteId AND vault_id = :vaultId LIMIT 1")
    NoteEntity find(long noteId, String vaultId);

    @Insert long insert(NoteEntity note);
    @Update void update(NoteEntity note);

    @Query("DELETE FROM notes WHERE id = :noteId AND vault_id = :vaultId")
    int delete(long noteId, String vaultId);

    @Query("DELETE FROM notes WHERE vault_id = :vaultId")
    int clearVault(String vaultId);
}
