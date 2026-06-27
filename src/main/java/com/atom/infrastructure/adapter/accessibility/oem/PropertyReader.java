package com.atom.infrastructure.adapter.accessibility.oem;

/** Reads system properties; injectable so classification is unit-testable. */
public interface PropertyReader {
    String get(String key, String def);
}
