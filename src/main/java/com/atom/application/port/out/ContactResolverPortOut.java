package com.atom.application.port.out;

import java.util.Optional;

public interface ContactResolverPortOut {

    /**
     * Resolves a contact display name to a phone number from the device address book.
     *
     * @return the matched number, or empty when permission is missing or no row matches.
     */
    Optional<String> resolveNumber(String name);
}
