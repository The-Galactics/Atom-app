package com.atom.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.atom.app.data.ConversationRepository;

/**
 * Conversation transcript screen. Observes the Room-backed transcript so it
 * updates live, renders each turn as a chat bubble, shows an empty state when
 * there is nothing yet, and offers a "clear history" affordance in the header.
 */
public class HistoryActivity extends AppCompatActivity {

    private ConversationRepository conversationRepository;
    private ChatHistoryAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        conversationRepository = new ConversationRepository(this);

        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnClear = findViewById(R.id.btn_clear_history);
        RecyclerView list = findViewById(R.id.history_list);
        emptyView = findViewById(R.id.history_empty);

        btnBack.setOnClickListener(v -> finish());
        btnClear.setOnClickListener(v -> confirmClear());

        adapter = new ChatHistoryAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        // Keep the newest turns in view as the transcript grows.
        layoutManager.setStackFromEnd(true);
        list.setLayoutManager(layoutManager);
        list.setAdapter(adapter);

        // Live transcript: Room re-emits whenever a new turn is saved (from the
        // main screen or the floating bubble), so the list stays current.
        conversationRepository.observeAll().observe(this, messages -> {
            adapter.submit(messages);
            boolean empty = messages == null || messages.isEmpty();
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        });
    }

    /** Confirms before wiping the transcript, since a clear can't be undone. */
    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.history_clear_title)
                .setMessage(R.string.history_clear_message)
                .setPositiveButton(R.string.history_clear_confirm,
                        (d, w) -> conversationRepository.clear())
                .setNegativeButton(R.string.action_confirm_no, null)
                .show();
    }
}
