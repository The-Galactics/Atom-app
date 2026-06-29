package com.atom.app.overlay;

import static org.assertj.core.api.Assertions.assertThat;

import com.atom.app.viewmodel.Event;
import com.atom.domain.action.ActionType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class OperatingCueBusTest {

    // LiveData.setValue() asserts the main thread via ArchTaskExecutor; route everything
    // synchronously onto the calling thread so the bus is testable without an Android runtime.
    @BeforeEach
    void setup() {
        androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(
                new androidx.arch.core.executor.TaskExecutor() {
                    @Override public void executeOnDiskIO(Runnable r) { r.run(); }
                    @Override public void postToMainThread(Runnable r) { r.run(); }
                    @Override public boolean isMainThread() { return true; }
                });
    }

    @AfterEach
    void tearDown() {
        androidx.arch.core.executor.ArchTaskExecutor.getInstance().setDelegate(null);
    }

    private List<OperatingCueBus.Cue> observe(OperatingCueBus bus) {
        List<OperatingCueBus.Cue> seen = new ArrayList<>();
        bus.cues().observeForever(e -> {
            OperatingCueBus.Cue c = e.getContentIfNotHandled();
            if (c != null) {
                seen.add(c);
            }
        });
        return seen;
    }

    @Test
    void started_deliversStartedCueWithTypeAndStep() {
        OperatingCueBus bus = new OperatingCueBus();
        List<OperatingCueBus.Cue> seen = observe(bus);

        bus.started(ActionType.OPEN_APP, 3);

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).kind).isEqualTo(OperatingCueBus.Cue.Kind.STARTED);
        assertThat(seen.get(0).actionType).isEqualTo(ActionType.OPEN_APP);
        assertThat(seen.get(0).step).isEqualTo(3);
    }

    @Test
    void finished_deliversFinishedCueWithMessageAndAbortedFlag() {
        OperatingCueBus bus = new OperatingCueBus();
        List<OperatingCueBus.Cue> seen = observe(bus);

        bus.finished("All done", true);

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).kind).isEqualTo(OperatingCueBus.Cue.Kind.FINISHED);
        assertThat(seen.get(0).message).isEqualTo("All done");
        assertThat(seen.get(0).aborted).isTrue();
    }

    @Test
    void event_isOneShot_notReDeliveredToSecondConsumerOfSameEvent() {
        OperatingCueBus bus = new OperatingCueBus();
        List<OperatingCueBus.Cue> first = observe(bus);

        bus.started(ActionType.TOGGLE_SETTING, 1);

        // A late observer attaches and receives the retained Event, but its content was
        // already consumed by `first`, so it must see nothing.
        List<OperatingCueBus.Cue> late = observe(bus);

        assertThat(first).hasSize(1);
        assertThat(late).isEmpty();
    }
}
