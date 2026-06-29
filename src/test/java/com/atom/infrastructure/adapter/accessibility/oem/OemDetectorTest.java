package com.atom.infrastructure.adapter.accessibility.oem;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class OemDetectorTest {
    private static PropertyReader props(Map<String, String> m) {
        return (key, def) -> m.getOrDefault(key, def);
    }

    @Test
    void classifiesHyperOsFromMiOsProperty() {
        OemFingerprint fp = OemDetector.detect("Xiaomi", "Redmi",
                props(Map.of("ro.mi.os.version.name", "OS2.0.1",
                             "ro.miui.ui.version.code", "816")));
        assertThat(fp.skin()).isEqualTo(OemSkin.HYPEROS);
        assertThat(fp.skinVersionCode()).isEqualTo(816);
    }

    @Test
    void classifiesMiuiWhenNoMiOsProperty() {
        OemFingerprint fp = OemDetector.detect("Xiaomi", "POCO",
                props(Map.of("ro.miui.ui.version.name", "V14",
                             "ro.miui.ui.version.code", "14")));
        assertThat(fp.skin()).isEqualTo(OemSkin.MIUI);
    }

    @Test
    void classifiesColorOsForOppoFamily() {
        OemFingerprint fp = OemDetector.detect("OPPO", "realme",
                props(Map.of("ro.build.version.opporom", "V13")));
        assertThat(fp.skin()).isEqualTo(OemSkin.COLOROS);
    }

    @Test
    void classifiesOriginOsForVivo() {
        OemFingerprint fp = OemDetector.detect("vivo", "iQOO",
                props(Map.of("ro.vivo.os.version", "13")));
        assertThat(fp.skin()).isEqualTo(OemSkin.ORIGINOS_FUNTOUCH);
    }

    @Test
    void classifiesOneUiForSamsung() {
        OemFingerprint fp = OemDetector.detect("samsung", "Galaxy", props(Map.of()));
        assertThat(fp.skin()).isEqualTo(OemSkin.ONEUI);
    }

    @Test
    void fallsBackToStockForUnknownManufacturer() {
        OemFingerprint fp = OemDetector.detect("Google", "Pixel", props(Map.of()));
        assertThat(fp.skin()).isEqualTo(OemSkin.STOCK);
    }
}
