package com.atom.infrastructure.adapter.out.security;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.atom.application.port.out.security.TokenStore;

/**
 * {@link TokenStore} backed by {@link EncryptedSharedPreferences} (AES-256), so the
 * session tokens are encrypted at rest with a key kept in the Android Keystore.
 */
public class EncryptedTokenStore implements TokenStore {

    private static final String FILE = "atom_tokens";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_EXPIRES = "expires_at_epoch_seconds";

    private final SharedPreferences prefs;

    public EncryptedTokenStore(Context context) {
        Context app = context.getApplicationContext();
        try {
            MasterKey masterKey = new MasterKey.Builder(app)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            this.prefs = EncryptedSharedPreferences.create(
                    app,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize encrypted token store", e);
        }
    }

    @Override
    public void save(String accessToken, String refreshToken, long expiresAtEpochSeconds) {
        prefs.edit()
                .putString(KEY_ACCESS, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .putLong(KEY_EXPIRES, expiresAtEpochSeconds)
                .apply();
    }

    @Override
    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS, null);
    }

    @Override
    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH, null);
    }

    @Override
    public long getExpiresAtEpochSeconds() {
        return prefs.getLong(KEY_EXPIRES, 0L);
    }

    @Override
    public void clear() {
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES).apply();
    }
}
