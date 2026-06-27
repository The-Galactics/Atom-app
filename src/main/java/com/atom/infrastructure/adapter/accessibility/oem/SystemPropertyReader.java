package com.atom.infrastructure.adapter.accessibility.oem;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/** Reads ro.* props via android.os.SystemProperties reflection, then getprop. */
public final class SystemPropertyReader implements PropertyReader {

    @Override
    public String get(String key, String def) {
        String viaReflection = readReflectively(key);
        if (viaReflection != null && !viaReflection.isEmpty()) {
            return viaReflection;
        }
        String viaExec = readViaGetprop(key);
        return (viaExec != null && !viaExec.isEmpty()) ? viaExec : def;
    }

    private static String readReflectively(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, key, "");
            return value == null ? "" : value.toString();
        } catch (Throwable ignored) { // LinkageError/SecurityException possible
            return "";
        }
    }

    private static String readViaGetprop(String key) {
        Process process = null;
        try {
            process = new ProcessBuilder("getprop", key).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = r.readLine();
                return line == null ? "" : line.trim();
            }
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }
}
