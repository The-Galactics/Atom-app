package com.atom.infrastructure.adapter.out.device;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;

import com.atom.application.port.out.ContactResolverPortOut;
import com.atom.domain.contact.ContactMatcher;
import com.atom.domain.contact.ContactResolution;

import java.util.ArrayList;
import java.util.List;

public final class ContactsContractResolver implements ContactResolverPortOut {

    private final Context context;

    public ContactsContractResolver(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public ContactResolution resolveContact(String name) {
        if (name == null || name.trim().isEmpty()) {
            return ContactResolution.none();
        }
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            return ContactResolution.none();
        }

        ContentResolver resolver = context.getContentResolver();
        // Project both columns; the authoritative match runs in Java (ContactMatcher,
        // accent/case-folded). We do NOT use a SQLite LIKE prefilter: LIKE only folds
        // ASCII A-Z, so an uppercase accented row (e.g. "JOSÉ") would be silently
        // excluded before Java ever sees it. Scanning all phone rows guarantees
        // accented-uppercase contacts are reachable.
        String[] projection = { Phone.DISPLAY_NAME, Phone.NUMBER };

        try (Cursor cursor = resolver.query(Phone.CONTENT_URI, projection, null, null, null)) {
            if (cursor == null) {
                return ContactResolution.none();
            }
            int nameIdx = cursor.getColumnIndex(Phone.DISPLAY_NAME);
            int numberIdx = cursor.getColumnIndex(Phone.NUMBER);
            if (nameIdx < 0 || numberIdx < 0) {
                return ContactResolution.none();
            }
            List<ContactMatcher.Candidate> candidates = new ArrayList<>();
            while (cursor.moveToNext()) {
                candidates.add(new ContactMatcher.Candidate(
                        cursor.getString(nameIdx), cursor.getString(numberIdx)));
            }
            return ContactMatcher.match(name, candidates);
        }
    }
}
