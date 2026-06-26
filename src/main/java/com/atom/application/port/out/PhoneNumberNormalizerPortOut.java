package com.atom.application.port.out;

import java.util.Optional;

public interface PhoneNumberNormalizerPortOut {

    /**
     * Returns the E.164 form of {@code rawNumber} (region inferred from device),
     * or empty when null/blank or the number can't be normalized — the empty signal
     * triggers a name-search fallback instead of a number deep link.
     */
    Optional<String> toE164(String rawNumber);
}
