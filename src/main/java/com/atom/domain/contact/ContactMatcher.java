package com.atom.domain.contact;

import com.atom.domain.utils.TextNormalizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure, Android-free classification of address-book candidates against a spoken
 * name (US-11.1 / 2B.5). Prefers an exact (accent/case-folded) name match; with
 * no exact match, a single distinct fuzzy (substring) name is returned for
 * dialer confirmation, while several distinct fuzzy names are AMBIGUOUS so the
 * app never silently calls the wrong contact.
 */
public final class ContactMatcher {

    /** One address-book row: a display name and one of its phone numbers. */
    public record Candidate(String name, String number) {}

    private ContactMatcher() {
    }

    public static ContactResolution match(String wanted, List<Candidate> candidates) {
        if (wanted == null || candidates == null) {
            return ContactResolution.none();
        }
        String wantedFold = TextNormalizer.fold(wanted);
        if (wantedFold.isEmpty()) {
            return ContactResolution.none();
        }

        String exactNumber = null;
        // foldedName -> first usable number; distinct keys = distinct people.
        Map<String, String> fuzzyByName = new LinkedHashMap<>();

        for (Candidate c : candidates) {
            if (c == null) {
                continue;
            }
            String number = c.number() == null ? "" : c.number().trim();
            if (number.isEmpty()) {
                continue;
            }
            String candFold = c.name() == null ? "" : TextNormalizer.fold(c.name());
            if (candFold.equals(wantedFold)) {
                if (exactNumber == null) {
                    exactNumber = number;
                }
            } else if (!candFold.isEmpty() && candFold.contains(wantedFold)) {
                fuzzyByName.putIfAbsent(candFold, number);
            }
        }

        if (exactNumber != null) {
            return ContactResolution.exact(exactNumber);
        }
        if (fuzzyByName.size() == 1) {
            return ContactResolution.fuzzy(fuzzyByName.values().iterator().next());
        }
        if (fuzzyByName.size() > 1) {
            return ContactResolution.ambiguous();
        }
        return ContactResolution.none();
    }
}
