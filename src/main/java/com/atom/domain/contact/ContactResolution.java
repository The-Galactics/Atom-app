package com.atom.domain.contact;

import java.util.Optional;

/**
 * Outcome of resolving a spoken contact name to a phone number (US-11.1 / 2B.5).
 * The {@link Kind} drives how the call is placed: an {@link Kind#EXACT} unique
 * match may be dialed directly; a {@link Kind#FUZZY} match opens the dialer for
 * the user to confirm; {@link Kind#AMBIGUOUS}/{@link Kind#NONE} do not call.
 */
public final class ContactResolution {

    public enum Kind { EXACT, FUZZY, AMBIGUOUS, NONE }

    private final String number; // nullable for AMBIGUOUS / NONE
    private final Kind kind;

    private ContactResolution(String number, Kind kind) {
        this.number = number;
        this.kind = kind;
    }

    public static ContactResolution exact(String number) {
        return new ContactResolution(number, Kind.EXACT);
    }

    public static ContactResolution fuzzy(String number) {
        return new ContactResolution(number, Kind.FUZZY);
    }

    public static ContactResolution ambiguous() {
        return new ContactResolution(null, Kind.AMBIGUOUS);
    }

    public static ContactResolution none() {
        return new ContactResolution(null, Kind.NONE);
    }

    public Kind kind() {
        return kind;
    }

    public Optional<String> number() {
        return Optional.ofNullable(number);
    }

    public boolean isExact() {
        return kind == Kind.EXACT;
    }

    public boolean isFuzzy() {
        return kind == Kind.FUZZY;
    }
}
