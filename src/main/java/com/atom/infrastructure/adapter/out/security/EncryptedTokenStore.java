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
    private static final String KEY_USER_ID = "server_user_id";

    private final SharedPreferences prefs;

    /** Builds the encrypted prefs for the given attempt (0 = first, 1 = post-wipe retry). */
    public interface PrefsFactory {
        Object create(int attempt) throws Exception;
    }

    /** Opens prefs; on first-attempt failure, runs {@code wipe} and retries once. */
    public static Object openWithRecovery(PrefsFactory factory, Runnable wipe) {
        try {
            return factory.create(0);
        } catch (Exception first) {
            // Master key invalidated (key reset / restore-to-new-device). Wipe the
            // corrupt prefs + key alias and rebuild so the user re-logs in instead
            // of being permanently crash-looped on launch.
            wipe.run();
            try {
                return factory.create(1);
            } catch (Exception second) {
                throw new IllegalStateException(
                        "Failed to initialize encrypted token store after recovery", second);
            }
        }
    }

    public EncryptedTokenStore(Context context) {
        Context app = context.getApplicationContext();
        this.prefs = (SharedPreferences) openWithRecovery(
                (attempt) -> {
                    MasterKey masterKey = new MasterKey.Builder(app)
                            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                            .build();
                    return EncryptedSharedPreferences.create(
                            app, FILE, masterKey,
                            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
                },
                () -> wipeCorruptStore(app));
    }

    /** Deletes the encrypted prefs file and the Keystore master-key alias. */
    private static void wipeCorruptStore(Context app) {
        app.deleteSharedPreferences(FILE);
        try {
            java.security.KeyStore ks = java.security.KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry("_androidx_security_master_key_");
        } catch (Exception ignored) {
            // Best-effort: deleting the prefs file alone usually suffices.
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
        prefs.edit().remove(KEY_ACCESS).remove(KEY_REFRESH).remove(KEY_EXPIRES)
                .remove(KEY_USER_ID).apply();
    }

    @Override
    public void saveUserId(String userId) {
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    @Override
    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }
}
