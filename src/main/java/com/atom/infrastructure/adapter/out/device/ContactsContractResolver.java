package com.atom.infrastructure.adapter.out.device;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import com.atom.application.port.out.ContactResolverPortOut;
import com.atom.domain.utils.TextNormalizer;

import java.util.Optional;

public final class ContactsContractResolver implements ContactResolverPortOut {

    private final Context context;

    public ContactsContractResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Optional<String> resolveNumber(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return Optional.empty();
        }

        ContentResolver resolver = context.getContentResolver();
        // Project both columns; the authoritative match runs in Java via
        // TextNormalizer.fold. We do NOT use a SQLite LIKE prefilter: LIKE only
        // folds ASCII A-Z, so an uppercase accented row (e.g. "JOSÉ") would be
        // silently excluded before Java ever sees it. Scanning all phone rows
        // guarantees accented-uppercase contacts are reachable.
        String[] projection = { Phone.DISPLAY_NAME, Phone.NUMBER };
        String wantedFold = TextNormalizer.fold(name);

        try (Cursor cursor = resolver.query(Phone.CONTENT_URI, projection, null, null, null)) {
            if (cursor == null) {
                return Optional.empty();
            }
            int nameIdx = cursor.getColumnIndex(Phone.DISPLAY_NAME);
            int numberIdx = cursor.getColumnIndex(Phone.NUMBER);
            if (nameIdx < 0 || numberIdx < 0) {
                return Optional.empty();
            }

            // Prefer an exact fold match; otherwise remember the first substring
            // match and fall back to it. Preferring exact over arbitrary-first
            // also fixes the prior "moveToFirst arbitrary row" behaviour.
            String fallbackNumber = null;
            while (cursor.moveToNext()) {
                String number = cursor.getString(numberIdx);
                if (number == null || number.trim().isEmpty()) {
                    continue;
                }
                String candFold = TextNormalizer.fold(cursor.getString(nameIdx));
                if (candFold.equals(wantedFold)) {
                    return Optional.of(number.trim());
                }
                if (fallbackNumber == null && candFold.contains(wantedFold)) {
                    fallbackNumber = number.trim();
                }
            }
            return Optional.ofNullable(fallbackNumber);
        }
    }
}
