package com.atom.infrastructure.adapter.accessibility.oem;

import java.util.EnumMap;
import java.util.Map;

/** Resolves the adapter for a detected skin. Adapters are stateless singletons. */
public final class OemAdapterRegistry {

    private final Map<OemSkin, OemCompatibilityAdapter> bySkin;
    private final OemCompatibilityAdapter fallback;

    public OemAdapterRegistry(Map<OemSkin, OemCompatibilityAdapter> bySkin,
                              OemCompatibilityAdapter fallback) {
        this.bySkin = new EnumMap<>(bySkin);
        this.fallback = fallback;
    }

    public OemCompatibilityAdapter adapterFor(OemFingerprint fingerprint) {
        return bySkin.getOrDefault(fingerprint.skin(), fallback);
    }

    public static OemAdapterRegistry createDefault() {
        OemCompatibilityAdapter hyperOs = new HyperOsAdapter();
        OemCompatibilityAdapter dflt = new DefaultOemAdapter();
        Map<OemSkin, OemCompatibilityAdapter> map = new EnumMap<>(OemSkin.class);
        map.put(OemSkin.HYPEROS, hyperOs);
        map.put(OemSkin.MIUI, hyperOs);
        return new OemAdapterRegistry(map, dflt);
    }
}
