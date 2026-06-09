package com.atom.app;

import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.airbnb.lottie.LottieAnimationView;
import com.atom.app.viewmodel.ChatViewModel;

public class MainActivity extends AppCompatActivity {

    private LottieAnimationView atomCore;
    private ImageButton btnMic, btnSettings, btnHistory, btnKeyboard, btnVolume;
    private TextView statusText, subStatusText;
    private ChatViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize ViewModel
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

        // Initialize UI Components
        atomCore = findViewById(R.id.atom_core_animation);
        btnMic = findViewById(R.id.btn_mic);
        btnSettings = findViewById(R.id.btn_settings);
        btnHistory = findViewById(R.id.btn_history);
        btnKeyboard = findViewById(R.id.btn_keyboard);
        btnVolume = findViewById(R.id.btn_volume);
        statusText = findViewById(R.id.status_text);
        subStatusText = findViewById(R.id.sub_status_text);

        // Observe Data from Back-end (The Messenger)
        setupObservers();

        // Set Click Listeners
        btnMic.setOnClickListener(v -> {
            // Simulate sending a voice prompt to the back-end
            viewModel.sendMessage("Hello Atom, can you help me?");
        });
        
        btnSettings.setOnClickListener(v -> {
            // Future navigation to Settings
        });
    }

    private void setupObservers() {
        // When the back-end responds
        viewModel.getChatResponse().observe(this, response -> {
            statusText.setText(response);
            subStatusText.setText("ATOM RESPONDED");
        });

        // When waiting for the back-end
        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                statusText.setText("Thinking...");
                subStatusText.setText("WAITING FOR BACK-END");
            }
        });

        // When something goes wrong
        viewModel.getErrorMessage().observe(this, error -> {
            statusText.setText("Error");
            subStatusText.setText(error != null ? error.toUpperCase() : "UNKNOWN ERROR");
        });
    }
}
