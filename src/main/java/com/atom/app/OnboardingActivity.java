package com.atom.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.atom.app.settings.AtomPreferences;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/**
 * First-run onboarding. Walks the user through a short ViewPager2 flow:
 * welcome -> "Your name" -> "Assistant name" (which doubles as the wake word)
 * -> a permissions intro that finishes onboarding. The two name steps write
 * straight into {@link AtomPreferences}; finishing marks onboarding complete and
 * routes on to {@link MainActivity}. Shown only when onboarding isn't complete
 * (the routing decision lives in MainActivity).
 */
public class OnboardingActivity extends AppCompatActivity {

    // Page kinds; controls which fields each onboarding step shows.
    private static final int PAGE_WELCOME = 0;
    private static final int PAGE_USER_NAME = 1;
    private static final int PAGE_ASSISTANT_NAME = 2;
    private static final int PAGE_PERMISSIONS = 3;

    private AtomPreferences preferences;
    private ViewPager2 pager;
    private MaterialButton nextButton;

    // Captured live from the name steps' EditTexts so we can persist on finish.
    private String userName = "";
    private String assistantName = "";

    private final List<Page> pages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        preferences = new AtomPreferences(this);

        // Prefill from any existing values so re-running onboarding isn't destructive.
        userName = preferences.getUserName();
        assistantName = preferences.getAssistantName();

        buildPages();

        pager = findViewById(R.id.onboarding_pager);
        nextButton = findViewById(R.id.onboarding_next);
        pager.setAdapter(new OnboardingAdapter());

        // Update the action button label as the last page comes into view.
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateNextButton(position);
            }
        });
        updateNextButton(0);

        nextButton.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < pages.size() - 1) {
                pager.setCurrentItem(current + 1, true);
            } else {
                finishOnboarding();
            }
        });
    }

    private void buildPages() {
        pages.clear();
        pages.add(new Page(PAGE_WELCOME,
                getString(R.string.onboarding_welcome_title),
                getString(R.string.onboarding_welcome_body)));
        pages.add(new Page(PAGE_USER_NAME,
                getString(R.string.onboarding_user_name_title),
                getString(R.string.onboarding_user_name_body)));
        pages.add(new Page(PAGE_ASSISTANT_NAME,
                getString(R.string.onboarding_assistant_name_title),
                getString(R.string.onboarding_assistant_name_body)));
        pages.add(new Page(PAGE_PERMISSIONS,
                getString(R.string.onboarding_permissions_title),
                getString(R.string.onboarding_permissions_body)));
    }

    /** Last page reads "Get started"; earlier pages read "Next". */
    private void updateNextButton(int position) {
        boolean last = position == pages.size() - 1;
        nextButton.setText(last ? R.string.onboarding_get_started : R.string.onboarding_next);
    }

    private void finishOnboarding() {
        preferences.setUserName(userName);
        preferences.setAssistantName(assistantName);
        // The assistant name also serves as the wake word, mirroring Settings.
        preferences.setWakeWordName(preferences.getAssistantName());
        preferences.setOnboardingComplete(true);

        // Route on to the main screen and close onboarding so back doesn't return here.
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /** Immutable description of a single onboarding step. */
    private static final class Page {
        final int kind;
        final String title;
        final String body;

        Page(int kind, String title, String body) {
            this.kind = kind;
            this.title = title;
            this.body = body;
        }
    }

    /** Binds {@link Page}s into the reusable page layout, showing the input only for name steps. */
    private final class OnboardingAdapter extends RecyclerView.Adapter<OnboardingAdapter.PageViewHolder> {

        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_onboarding_page, parent, false);
            return new PageViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
            Page page = pages.get(position);
            holder.title.setText(page.title);
            holder.body.setText(page.body);

            // Detach any previous watcher so a recycled row doesn't write to the wrong field.
            if (holder.watcher != null) {
                holder.input.removeTextChangedListener(holder.watcher);
                holder.watcher = null;
            }

            if (page.kind == PAGE_USER_NAME || page.kind == PAGE_ASSISTANT_NAME) {
                final boolean isUser = page.kind == PAGE_USER_NAME;
                holder.input.setVisibility(View.VISIBLE);
                holder.input.setHint(isUser
                        ? R.string.settings_your_name_hint
                        : R.string.settings_assistant_name_hint);
                holder.input.setText(isUser ? userName : assistantName);
                holder.watcher = new SimpleTextWatcher(text -> {
                    if (isUser) {
                        userName = text;
                    } else {
                        assistantName = text;
                    }
                });
                holder.input.addTextChangedListener(holder.watcher);
            } else {
                holder.input.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return pages.size();
        }

        final class PageViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView body;
            final EditText input;
            TextWatcher watcher;

            PageViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.onboarding_page_title);
                body = itemView.findViewById(R.id.onboarding_page_body);
                input = itemView.findViewById(R.id.onboarding_page_input);
            }
        }
    }

    /** Minimal TextWatcher that forwards the current text on every change. */
    private static final class SimpleTextWatcher implements TextWatcher {
        interface OnTextChanged { void onChanged(String text); }

        private final OnTextChanged callback;

        SimpleTextWatcher(OnTextChanged callback) {
            this.callback = callback;
        }

        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable s) {
            callback.onChanged(s.toString());
        }
    }
}
