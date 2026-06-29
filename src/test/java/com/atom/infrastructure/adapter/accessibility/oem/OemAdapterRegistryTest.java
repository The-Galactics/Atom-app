package com.atom.infrastructure.adapter.accessibility.oem;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OemAdapterRegistryTest {
    private final OemAdapterRegistry registry = OemAdapterRegistry.createDefault();

    @Test
    void hyperOsAndMiuiUseHyperOsAdapter() {
        OemFingerprint hyper = new OemFingerprint(OemSkin.HYPEROS, "Xiaomi", "OS2", 816);
        OemFingerprint miui = new OemFingerprint(OemSkin.MIUI, "Xiaomi", "V14", 14);
        assertThat(registry.adapterFor(hyper)).isInstanceOf(HyperOsAdapter.class);
        assertThat(registry.adapterFor(miui)).isInstanceOf(HyperOsAdapter.class);
    }

    @Test
    void otherSkinsUseDefaultAdapter() {
        OemFingerprint oneui = new OemFingerprint(OemSkin.ONEUI, "samsung", "", 0);
        OemFingerprint stock = new OemFingerprint(OemSkin.STOCK, "Google", "", 0);
        assertThat(registry.adapterFor(oneui)).isInstanceOf(DefaultOemAdapter.class);
        assertThat(registry.adapterFor(stock)).isInstanceOf(DefaultOemAdapter.class);
    }
}
