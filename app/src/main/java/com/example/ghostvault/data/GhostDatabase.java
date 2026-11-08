package com.example.ghostvault.data;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {NoteEntity.class}, version = 1, exportSchema = true)
public abstract class GhostDatabase extends RoomDatabase {
    private static volatile GhostDatabase instance;
    public abstract NoteDao noteDao();

    public static GhostDatabase get(Context context) {
        if (instance == null) {
            synchronized (GhostDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    GhostDatabase.class, "ghostvault_notes.db")
                            .build();
                }
            }
        }
        return instance;
    }
}
