package com.atom.infrastructure.adapter.accessibility.oem;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OemFingerprintTest {
    @Test
    void exposesItsValues() {
        OemFingerprint fp = new OemFingerprint(OemSkin.HYPEROS, "Xiaomi", "OS2.0", 816);
        assertThat(fp.skin()).isEqualTo(OemSkin.HYPEROS);
        assertThat(fp.manufacturer()).isEqualTo("Xiaomi");
        assertThat(fp.marketingVersion()).isEqualTo("OS2.0");
        assertThat(fp.skinVersionCode()).isEqualTo(816);
    }
}
