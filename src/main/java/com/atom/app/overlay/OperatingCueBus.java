package com.atom.app.overlay;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.atom.app.viewmodel.Event;
import com.atom.domain.action.ActionType;

/**
 * Process-wide, Android-{@code Context}-free bridge carrying the autonomous loop's
 * operating lifecycle from the chat path ({@code ChatViewModel}, which must stay
 * Android-free) to the overlay service, which owns the cross-app cue (pulsing edge
 * handle + live notification).
 *
 * <p>The floating-bubble path drives the cue directly from its own loop and does NOT
 * publish here, so the service never double-drives: bubble actions are handled inline;
 * main-app actions arrive through this bus.
 *
 * <p>Events are one-shot via {@link Event} so a late observer (e.g. the service starting
 * mid-order) does not re-consume an already-handled cue.
 */
public final class OperatingCueBus {

    /** One operating-lifecycle signal: a step started/advanced, or the chain finished. */
    public static final class Cue {
        public enum Kind { STARTED, FINISHED }

        public final Kind kind;
        @Nullable public final ActionType actionType; // STARTED only
        public final int step;                        // STARTED only
        @Nullable public final String message;        // FINISHED only
        public final boolean aborted;                 // FINISHED only

        private Cue(Kind kind, @Nullable ActionType actionType, int step,
                    @Nullable String message, boolean aborted) {
            this.kind = kind;
            this.actionType = actionType;
            this.step = step;
            this.message = message;
            this.aborted = aborted;
        }

        static Cue started(@Nullable ActionType actionType, int step) {
            return new Cue(Kind.STARTED, actionType, step, null, false);
        }

        static Cue finished(@Nullable String message, boolean aborted) {
            return new Cue(Kind.FINISHED, null, 0, message, aborted);
        }
    }

    private final MutableLiveData<Event<Cue>> cues = new MutableLiveData<>();

    /** Observe operating cues; one-shot per {@link Event}. */
    public LiveData<Event<Cue>> cues() {
        return cues;
    }

    /** An executable action started/advanced (per ReAct step). Call on the main thread. */
    public void started(@Nullable ActionType actionType, int step) {
        cues.setValue(new Event<>(Cue.started(actionType, step)));
    }

    /** The autonomous chain finished (completed or aborted). Call on the main thread. */
    public void finished(@Nullable String message, boolean aborted) {
        cues.setValue(new Event<>(Cue.finished(message, aborted)));
    }
}
