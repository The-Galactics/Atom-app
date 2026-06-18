package com.atom.app.data;

import android.content.Context;

import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * Thin facade over the Room transcript store. Centralizes persistence so every
 * surface that exchanges turns with Atom (the main chat screen and the floating
 * bubble) records the dialogue the same way, and the History screen has a single
 * place to observe and clear it. All writes are dispatched onto the database's
 * background executor so callers can invoke these from the main thread safely.
 */
public class ConversationRepository {

    private final ChatMessageDao dao;

    public ConversationRepository(Context context) {
        this.dao = AtomDatabase.getInstance(context).chatMessageDao();
    }

    /** Records a message the user said or typed. Safe to call on the main thread. */
    public void saveUserMessage(String text) {
        save(ChatMessage.ROLE_USER, text);
    }

    /** Records a reply Atom produced. Safe to call on the main thread. */
    public void saveAssistantMessage(String text) {
        save(ChatMessage.ROLE_ASSISTANT, text);
    }

    private void save(String role, String text) {
        if (text == null || text.trim().isEmpty()) {
            return; // never persist blank turns (e.g. empty/aborted responses)
        }
        final String trimmed = text.trim();
        final long now = System.currentTimeMillis();
        AtomDatabase.databaseWriteExecutor.execute(
                () -> dao.insert(new ChatMessage(role, trimmed, now)));
    }

    /** Live transcript, oldest-first, for the History screen. */
    public LiveData<List<ChatMessage>> observeAll() {
        return dao.observeAll();
    }

    /** Clears the whole transcript off the main thread. */
    public void clear() {
        AtomDatabase.databaseWriteExecutor.execute(dao::clear);
    }
}
