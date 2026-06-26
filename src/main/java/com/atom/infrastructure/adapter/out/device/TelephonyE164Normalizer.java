package com.atom.infrastructure.adapter.out.device;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.telephony.TelephonyManager;

import com.atom.application.port.out.PhoneNumberNormalizerPortOut;

import java.util.Locale;
import java.util.Optional;

/**
 * Normalizes raw numbers to E.164 via {@link PhoneNumberUtils#formatNumberToE164}.
 * Region is inferred dynamically: network ISO → SIM ISO → default locale.
 * Lives behind a port because {@code PhoneNumberUtils} stubs out on a plain JVM.
 */
public final class TelephonyE164Normalizer implements PhoneNumberNormalizerPortOut {

    private final Context context;

    public TelephonyE164Normalizer(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Optional<String> toE164(String rawNumber) {
        if (rawNumber == null || rawNumber.trim().isEmpty()) {
            return Optional.empty();
        }
        String region = resolveRegionIso();
        // formatNumberToE164 returns null when it can't build a valid E.164 number
        // (e.g. a local number whose region is unknown). That null is exactly our
        // "fall back to name search" signal.
        return Optional.ofNullable(PhoneNumberUtils.formatNumberToE164(rawNumber.trim(), region));
    }

    // Network ISO → SIM ISO → default locale; uppercased as required by PhoneNumberUtils.
    private String resolveRegionIso() {
        TelephonyManager telephony =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        String iso = null;
        if (telephony != null) {
            iso = telephony.getNetworkCountryIso();
            if (isBlank(iso)) {
                iso = telephony.getSimCountryIso();
            }
        }
        if (isBlank(iso)) {
            iso = Locale.getDefault().getCountry();
        }
        return iso == null ? null : iso.toUpperCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
