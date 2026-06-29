package com.atom.application.port.out;

import com.atom.domain.contact.ContactResolution;

import java.util.Optional;

public interface ContactResolverPortOut {

    /**
     * Resolves a contact display name against the device address book, classifying
     * the match (exact / fuzzy / ambiguous) so callers can require confirmation for
     * anything less than an exact unique match (US-11.1 / 2B.5).
     */
    ContactResolution resolveContact(String name);

    /**
     * Convenience: just the matched number (empty for ambiguous/none). Used by the
     * messaging path, which always confirms separately.
     */
    default Optional<String> resolveNumber(String name) {
        return resolveContact(name).number();
    }
}
