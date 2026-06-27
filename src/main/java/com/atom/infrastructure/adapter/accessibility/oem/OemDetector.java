package com.atom.infrastructure.adapter.accessibility.oem;

import java.util.Locale;

/** Classifies the active OEM skin from manufacturer/brand + system properties. */
public final class OemDetector {

    private OemDetector() {
    }

    public static OemFingerprint detect(String manufacturer, String brand, PropertyReader props) {
        String maker = lower(manufacturer);
        String brnd = lower(brand);
        int miuiCode = parseInt(props.get("ro.miui.ui.version.code", ""));

        if (isXiaomiFamily(maker, brnd)) {
            String miOs = props.get("ro.mi.os.version.name", "");
            if (!miOs.isEmpty()) {
                return new OemFingerprint(OemSkin.HYPEROS, manufacturer, miOs, miuiCode);
            }
            String miui = props.get("ro.miui.ui.version.name", "");
            if (!miui.isEmpty()) {
                return new OemFingerprint(OemSkin.MIUI, manufacturer, miui, miuiCode);
            }
            return new OemFingerprint(OemSkin.MIUI, manufacturer, "", miuiCode);
        }
        if (isOppoFamily(maker, brnd)) {
            return new OemFingerprint(OemSkin.COLOROS, manufacturer,
                    props.get("ro.build.version.opporom", ""), 0);
        }
        if (isVivoFamily(maker, brnd)) {
            return new OemFingerprint(OemSkin.ORIGINOS_FUNTOUCH, manufacturer,
                    props.get("ro.vivo.os.version", ""), 0);
        }
        if (maker.contains("samsung")) {
            return new OemFingerprint(OemSkin.ONEUI, manufacturer,
                    props.get("ro.build.version.oneui", ""), 0);
        }
        return new OemFingerprint(OemSkin.STOCK, manufacturer, "", 0);
    }

    private static boolean isXiaomiFamily(String maker, String brand) {
        return maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco")
                || brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco");
    }

    private static boolean isOppoFamily(String maker, String brand) {
        return maker.contains("oppo") || maker.contains("realme") || maker.contains("oneplus")
                || brand.contains("oppo") || brand.contains("realme") || brand.contains("oneplus");
    }

    private static boolean isVivoFamily(String maker, String brand) {
        return maker.contains("vivo") || maker.contains("iqoo")
                || brand.contains("vivo") || brand.contains("iqoo");
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private static int parseInt(String s) {
        try {
            return s == null || s.isEmpty() ? 0 : Integer.parseInt(s.trim());
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
