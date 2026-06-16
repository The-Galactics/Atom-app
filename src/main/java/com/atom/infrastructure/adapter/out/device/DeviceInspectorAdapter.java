package com.atom.infrastructure.adapter.out.device;




import android.os.Build;

import com.atom.application.port.out.security.DeviceInspectorPort;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.List;

public class DeviceInspectorAdapter implements DeviceInspectorPort {

    private static final String[] ROOT_BINARIES = {
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
    };

    @Override
    public boolean isRooted() {
        for (String path : ROOT_BINARIES) {
            if (new File(path).exists()) {
                return true;
            }
        }

        try {
            Process process = Runtime.getRuntime().exec(new String[]{"which", "su"});
            int exitCode = process.waitFor();
            if (exitCode == 0) return true;
        } catch (Exception ignored) {}

        return false;
    }

    @Override
    public boolean isRunningOnEmulator() {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.PRODUCT.equals("google_sdk")
                || Build.PRODUCT.contains("sdk")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu"));
    }

    @Override
    public boolean isProxyActive() {
        try {
            List<Proxy> proxies = ProxySelector.getDefault()
                    .select(new URI("https://api.atom.app"));

            for (Proxy proxy : proxies) {
                if (proxy.type() != Proxy.Type.DIRECT) {
                    InetSocketAddress addr = (InetSocketAddress) proxy.address();
                    if (addr != null && isUntrustedProxy(addr)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}

        String httpProxy  = System.getenv("http_proxy");
        String httpsProxy = System.getenv("https_proxy");
        return (httpProxy != null && !httpProxy.isBlank())
                || (httpsProxy != null && !httpsProxy.isBlank());
    }

    private boolean isUntrustedProxy(InetSocketAddress addr) {
        String host = addr.getHostString();
        int port    = addr.getPort();

        boolean isLocalhost = host.equals("127.0.0.1")
                || host.equals("localhost")
                || host.equals("0.0.0.0")
                || host.equals("::1");

        boolean isSuspiciousPort = port == 8080 || port == 8888
                || port == 9090 || port == 1080
                || port == 8443 || port == 4444;

        return isLocalhost && isSuspiciousPort;
    }
}
