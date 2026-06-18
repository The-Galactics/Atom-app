package com.atom.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.atom.app.data.ChatMessage;

import java.util.Objects;

/**
 * Renders the conversation transcript: user turns as right-aligned accent
 * bubbles, assistant turns as left-aligned surface bubbles. Backed by
 * {@link ListAdapter} so Room emissions diff into granular item updates,
 * preserving insert animations instead of rebinding the whole list.
 */
public class ChatHistoryAdapter extends ListAdapter<ChatMessage, ChatHistoryAdapter.MessageViewHolder> {

    private static final int TYPE_USER = 0;
    private static final int TYPE_ASSISTANT = 1;

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF = new DiffUtil.ItemCallback<ChatMessage>() {
        @Override
        public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return oldItem.id == newItem.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
            return Objects.equals(oldItem.role, newItem.role)
                    && Objects.equals(oldItem.text, newItem.text);
        }
    };

    public ChatHistoryAdapter() {
        super(DIFF);
    }

    @Override
    public int getItemViewType(int position) {
        return ChatMessage.ROLE_USER.equals(getItem(position).role)
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
        holder.text.setText(getItem(position).text);
    }

    static final class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView text;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.chat_text);
        }
    }
}
