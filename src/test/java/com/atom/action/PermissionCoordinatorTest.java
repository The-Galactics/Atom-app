package com.atom.action;

import com.atom.app.permission.PermissionCoordinator;
import com.atom.domain.action.ActionType;
import com.atom.domain.action.ResolvedAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PermissionCoordinator#requiresAccessibility(ResolvedAction)}.
 * Pure: exercises only the enum switch, no Android calls.
 */
class PermissionCoordinatorTest {

    private static ResolvedAction action(ActionType type) {
        return new ResolvedAction(type, Collections.emptyMap(), "", 1.0f, false);
    }

    @Test
    @DisplayName("Accessibility-powered actions require the service")
    void accessibilityActionsRequireService() {
        assertThat(PermissionCoordinator.requiresAccessibility(action(ActionType.NAVIGATE))).isTrue();
        assertThat(PermissionCoordinator.requiresAccessibility(action(ActionType.SCROLL))).isTrue();
        assertThat(PermissionCoordinator.requiresAccessibility(action(ActionType.READ_SCREEN))).isTrue();
        assertThat(PermissionCoordinator.requiresAccessibility(action(ActionType.TAP_ELEMENT))).isTrue();
    }

    @Test
    @DisplayName("Intent-based actions and null do not require accessibility")
    void otherActionsDoNotRequireService() {
        assertThat(PermissionCoordinator.requiresAccessibility(action(ActionType.OPEN_APP))).isFalse();
        assertThat(PermissionCoordinator.requiresAccessibility(action(ActionType.MAKE_CALL))).isFalse();
        assertThat(PermissionCoordinator.requiresAccessibility(action(ActionType.NONE))).isFalse();
        assertThat(PermissionCoordinator.requiresAccessibility(null)).isFalse();
    }
}
