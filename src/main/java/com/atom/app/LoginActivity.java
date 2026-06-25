package com.atom.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.atom.app.di.AppContainer;
import com.atom.app.viewmodel.AuthViewModel;
import com.atom.app.viewmodel.AuthViewModelFactory;

public class LoginActivity extends AppCompatActivity {

    private AuthViewModel viewModel;
    private boolean registerMode = false;

    private EditText emailInput;
    private EditText passwordInput;
    private Button primaryButton;
    private Button toggleModeButton;
    private Button googleButton;
    private ProgressBar progress;
    private TextView errorText;
    private TextView title;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        AppContainer container = ((AtomApp) getApplication()).getAppContainer();
        viewModel = new ViewModelProvider(this, new AuthViewModelFactory(container.getAuthUseCase()))
                .get(AuthViewModel.class);

        title = findViewById(R.id.title);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        primaryButton = findViewById(R.id.primaryButton);
        toggleModeButton = findViewById(R.id.toggleModeButton);
        googleButton = findViewById(R.id.googleButton);
        progress = findViewById(R.id.progress);
        errorText = findViewById(R.id.errorText);

        primaryButton.setOnClickListener(v -> submit());
        toggleModeButton.setOnClickListener(v -> toggleMode());

        viewModel.state().observe(this, state -> {
            boolean loading = state.getStatus() == AuthViewModel.Status.LOADING;
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            primaryButton.setEnabled(!loading);
            if (state.getStatus() == AuthViewModel.Status.ERROR) {
                errorText.setText(state.getErrorMessage());
                errorText.setVisibility(View.VISIBLE);
            } else {
                errorText.setVisibility(View.GONE);
            }
            if (state.getStatus() == AuthViewModel.Status.SUCCESS) {
                goToMain();
            }
        });
    }

    private void submit() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            errorText.setText(getString(R.string.auth_fields_required));
            errorText.setVisibility(View.VISIBLE);
            return;
        }
        if (registerMode) {
            viewModel.register(email, password, "");
        } else {
            viewModel.login(email, password);
        }
    }

    private void toggleMode() {
        registerMode = !registerMode;
        title.setText(registerMode ? R.string.auth_register_title : R.string.auth_login_title);
        primaryButton.setText(registerMode ? R.string.auth_register_button : R.string.auth_login_button);
        toggleModeButton.setText(registerMode
                ? R.string.auth_switch_to_login : R.string.auth_switch_to_register);
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
