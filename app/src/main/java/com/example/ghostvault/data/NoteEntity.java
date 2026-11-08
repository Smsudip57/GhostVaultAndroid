package com.example.ghostvault.data;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "notes")
public class NoteEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @ColumnInfo(name = "vault_id")
    public String vaultId;
    @ColumnInfo(name = "encrypted_title")
    public String encryptedTitle;
    @ColumnInfo(name = "encrypted_body")
    public String encryptedBody;
    @ColumnInfo(name = "created_at")
    public long createdAt;
    @ColumnInfo(name = "updated_at")
    public long updatedAt;
}
