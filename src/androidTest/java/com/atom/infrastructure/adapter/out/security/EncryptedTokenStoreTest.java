package com.atom.infrastructure.adapter.out.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EncryptedTokenStoreTest {

    private EncryptedTokenStore store;

    @Before
    public void setUp() {
        Context ctx = ApplicationProvider.getApplicationContext();
        store = new EncryptedTokenStore(ctx);
        store.clear();
    }

    @Test
    public void saveThenReadRoundtrips() {
        store.save("acc", "ref", 1_900L);
        assertEquals("acc", store.getAccessToken());
        assertEquals("ref", store.getRefreshToken());
        assertEquals(1_900L, store.getExpiresAtEpochSeconds());
    }

    @Test
    public void clearRemovesEverything() {
        store.save("acc", "ref", 1_900L);
        store.clear();
        assertNull(store.getAccessToken());
        assertNull(store.getRefreshToken());
        assertEquals(0L, store.getExpiresAtEpochSeconds());
    }
}
