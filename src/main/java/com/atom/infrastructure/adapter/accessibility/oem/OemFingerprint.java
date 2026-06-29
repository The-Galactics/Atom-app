package com.atom.infrastructure.adapter.accessibility.oem;

/**
 * Immutable description of the active OEM skin. Built from build/property
 * strings only — holds no Context or native reference.
 */
public record OemFingerprint(OemSkin skin, String manufacturer,
                             String marketingVersion, int skinVersionCode) {
}
