package com.atom.app.permission.oem;

import com.atom.infrastructure.adapter.accessibility.oem.OemSkin;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class OemSettingsRouterAutostartTest {

    private static IntentResolver componentsResolving(Set<String> classNames) {
        return new IntentResolver() {
            @Override public boolean resolvesComponent(String pkg, String cls) { return classNames.contains(cls); }
            @Override public boolean resolvesAction(String action) { return true; }
        };
    }

    private static IntentResolver nothingResolves() {
        return new IntentResolver() {
            @Override public boolean resolvesComponent(String pkg, String cls) { return false; }
            @Override public boolean resolvesAction(String action) { return true; }
        };
    }

    @Test
    void supportsAutostart_trueForHeavyOems_falseForStockAndOneUi() {
        assertThat(OemSettingsRouter.supportsAutostart(OemSkin.HYPEROS)).isTrue();
        assertThat(OemSettingsRouter.supportsAutostart(OemSkin.MIUI)).isTrue();
        assertThat(OemSettingsRouter.supportsAutostart(OemSkin.COLOROS)).isTrue();
        assertThat(OemSettingsRouter.supportsAutostart(OemSkin.ORIGINOS_FUNTOUCH)).isTrue();
        assertThat(OemSettingsRouter.supportsAutostart(OemSkin.ONEUI)).isFalse();
        assertThat(OemSettingsRouter.supportsAutostart(OemSkin.STOCK)).isFalse();
    }

    @Test
    void autostart_picksMiuiSecurityCenterForHyperOs() {
        OemRedirect r = OemSettingsRouter.chooseAutostart(OemSkin.HYPEROS,
                componentsResolving(Set.of("com.miui.permcenter.autostart.AutoStartManagementActivity")));
        assertThat(r.isComponent()).isTrue();
        assertThat(r.packageName()).isEqualTo("com.miui.securitycenter");
        assertThat(r.className()).isEqualTo("com.miui.permcenter.autostart.AutoStartManagementActivity");
    }

    @Test
    void autostart_picksFirstResolvableColorOsCandidate() {
        // Only the OPPO-safe variant resolves; the coloros one does not.
        OemRedirect r = OemSettingsRouter.chooseAutostart(OemSkin.COLOROS,
                componentsResolving(Set.of("com.oppo.safe.permission.startup.StartupAppListActivity")));
        assertThat(r.packageName()).isEqualTo("com.oppo.safe");
        assertThat(r.className()).isEqualTo("com.oppo.safe.permission.startup.StartupAppListActivity");
    }

    @Test
    void autostart_picksMiuiSecurityCenterForMiui() {
        OemRedirect r = OemSettingsRouter.chooseAutostart(OemSkin.MIUI,
                componentsResolving(java.util.Set.of("com.miui.permcenter.autostart.AutoStartManagementActivity")));
        assertThat(r.packageName()).isEqualTo("com.miui.securitycenter");
        assertThat(r.className()).isEqualTo("com.miui.permcenter.autostart.AutoStartManagementActivity");
    }

    @Test
    void autostart_fallsBackToAppDetailsWhenNoComponentResolves() {
        OemRedirect r = OemSettingsRouter.chooseAutostart(OemSkin.ORIGINOS_FUNTOUCH, nothingResolves());
        assertThat(r.isComponent()).isFalse();
        assertThat(r.action()).isEqualTo(OemSettingsRouter.ACTION_APP_DETAILS);
    }
}
