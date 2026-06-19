package com.atom.app.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * A single persisted line of conversation — either something the user said/typed
 * or Atom's reply. Stored in Room so the History screen can replay the dialogue
 * across app restarts. Deliberately a flat, UI-facing record (this lives in the
 * app layer, not the hexagonal domain) so it can carry the Room annotations
 * without leaking persistence concerns into {@code com.atom.domain}.
 */
@Entity(tableName = "chat_messages")
public class ChatMessage {

    /** Role marker for a message authored by the user (typed or spoken). */
    public static final String ROLE_USER = "user";
    /** Role marker for a message authored by Atom (the assistant reply). */
    public static final String ROLE_ASSISTANT = "assistant";

    @PrimaryKey(autoGenerate = true)
    public long id;

    /** Either {@link #ROLE_USER} or {@link #ROLE_ASSISTANT}. */
    @NonNull
    public String role;

    /** The message text as shown in the transcript. */
    @NonNull
    public String text;

    /** Wall-clock time the message was recorded, used to order the transcript. */
    public long timestampMs;

    public ChatMessage(@NonNull String role, @NonNull String text, long timestampMs) {
        this.role = role;
        this.text = text;
        this.timestampMs = timestampMs;
    }
}
