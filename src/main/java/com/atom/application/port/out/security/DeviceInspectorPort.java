package com.atom.application.port.out.security;

public interface DeviceInspectorPort {
    /** Detecta si el dispositivo tiene acceso root. */
    boolean isRooted();
    /** Detecta si la app corre sobre un emulador de Android. */
    boolean isRunningOnEmulator();
    /** Detecta si hay un proxy HTTP/HTTPS activo y no declarado por la app. */
    boolean isProxyActive();
}
