package com.atom.app.data;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Room access for the conversation transcript. The observable query returns
 * {@link LiveData} so the History screen updates live as new turns are saved,
 * and Room runs both the query and its updates off the main thread for us.
 */
@Dao
public interface ChatMessageDao {

    /** Inserts a message; call from a background thread (Room enforces this). */
    @Insert
    void insert(ChatMessage message);

    /** All messages oldest-first, observed so the transcript refreshes live. */
    @Query("SELECT * FROM chat_messages ORDER BY timestampMs ASC, id ASC")
    LiveData<List<ChatMessage>> observeAll();

    // Most-recent `limit` messages, returned in ascending display order (oldest→newest)
    // so the overlay panel renders them top-to-bottom like the full list.
    @Query("SELECT * FROM (SELECT * FROM chat_messages ORDER BY timestampMs DESC, id DESC LIMIT :limit) "
            + "ORDER BY timestampMs ASC, id ASC")
    LiveData<List<ChatMessage>> observeRecent(int limit);

    /** Wipes the whole transcript (the History "clear" affordance). */
    @Query("DELETE FROM chat_messages")
    void clear();
}
