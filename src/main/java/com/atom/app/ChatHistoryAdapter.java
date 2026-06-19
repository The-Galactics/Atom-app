package com.atom.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.atom.app.data.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the conversation transcript: user turns as right-aligned accent
 * bubbles, assistant turns as left-aligned surface bubbles. The row type is
 * chosen from {@link ChatMessage#role} so each turn inflates the correct layout.
 */
public class ChatHistoryAdapter extends RecyclerView.Adapter<ChatHistoryAdapter.MessageViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    private final List<ChatMessage> messages = new ArrayList<>();

    /** Replaces the backing list with a fresh transcript snapshot from Room. */
    public void submit(List<ChatMessage> newMessages) {
        messages.clear();
        if (newMessages != null) {
            messages.addAll(newMessages);
        }
        // The transcript is small and refreshes infrequently (one row per turn),
        // so a full rebind is simpler than diffing and visually indistinguishable.
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return ChatMessage.ROLE_USER.equals(messages.get(position).role)
                ? TYPE_USER : TYPE_ASSISTANT;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_USER
                ? R.layout.item_chat_user
                : R.layout.item_chat_assistant;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        holder.text.setText(messages.get(position).text);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static final class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.chat_text);
        }
    }
}
