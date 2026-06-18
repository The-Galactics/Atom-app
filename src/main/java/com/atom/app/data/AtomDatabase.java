package com.atom.app.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

/**
 * Room database holding the conversation transcript. Exposed as a process-wide
 * singleton so the chat screen, the floating bubble, and the History screen all
 * read and write the same store. All access goes through a background executor:
 * inserts and clears run on {@link #databaseWriteExecutor}; reads are exposed as
 * observable LiveData (Room handles their threading), so nothing touches the DB
 * on the main thread.
 */
@Database(entities = {ChatMessage.class}, version = 1, exportSchema = false)
public abstract class AtomDatabase extends RoomDatabase {

    private static final String DB_NAME = "atom_chat.db";

    private static volatile AtomDatabase instance;

    /** Single background thread for writes (insert/clear), shared app-wide. */
    public static final java.util.concurrent.ExecutorService databaseWriteExecutor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    public abstract ChatMessageDao chatMessageDao();

    public static AtomDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AtomDatabase.class) {
                if (instance == null) {
                    // Application context so the singleton can't leak an Activity.
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AtomDatabase.class,
                            DB_NAME)
                            .build();
                }
            }
        }
        return instance;
    }
}
