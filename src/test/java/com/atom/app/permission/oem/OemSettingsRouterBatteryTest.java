// src/test/java/com/atom/app/permission/oem/OemSettingsRouterBatteryTest.java
package com.atom.app.permission.oem;

import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class OemSettingsRouterBatteryTest {

    /** Fake resolver: a fixed set of resolvable actions, no components resolve. */
    private static IntentResolver actionsResolving(Set<String> actions) {
        return new IntentResolver() {
            @Override public boolean resolvesComponent(String pkg, String cls) { return false; }
            @Override public boolean resolvesAction(String action) { return actions.contains(action); }
        };
    }

    @Test
    void battery_prefersDirectRequestExemptionWhenAvailable() {
        OemRedirect r = OemSettingsRouter.chooseBattery(
                actionsResolving(Set.of(OemSettingsRouter.ACTION_REQUEST_IGNORE_BATTERY)));
        assertThat(r.isComponent()).isFalse();
        assertThat(r.action()).isEqualTo(OemSettingsRouter.ACTION_REQUEST_IGNORE_BATTERY);
    }

    @Test
    void battery_fallsBackToSettingsListWhenRequestUnavailable() {
        OemRedirect r = OemSettingsRouter.chooseBattery(actionsResolving(Set.of()));
        assertThat(r.action()).isEqualTo(OemSettingsRouter.ACTION_BATTERY_SETTINGS_LIST);
    }

    @Test
    void component_factory_marksComponentTarget() {
        OemRedirect r = OemRedirect.component("pkg", "cls");
        assertThat(r.isComponent()).isTrue();
        assertThat(r.packageName()).isEqualTo("pkg");
        assertThat(r.className()).isEqualTo("cls");
    }
}
