package com.example.ghostvault.data;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(entities = {NoteEntity.class}, version = 2, exportSchema = true)
public abstract class GhostDatabase extends RoomDatabase {
    private static volatile GhostDatabase instance;
    public abstract NoteDao noteDao();

    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN encrypted_location TEXT");
        }
    };

    public static GhostDatabase get(Context context) {
        if (instance == null) {
            synchronized (GhostDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    GhostDatabase.class, "ghostvault_notes.db")
                            .addMigrations(MIGRATION_1_2)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}
