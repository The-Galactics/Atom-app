package com.atom.app.viewmodel;

/**
 * Wraps a value for one-shot LiveData delivery. A retained LiveData replays its last
 * value to each new observer; consuming via {@link #getContentIfNotHandled()} ensures
 * the value is acted on at most once, so dialogs don't re-fire on Activity recreation.
 */
public final class Event<T> {
    private final T content;
    private boolean handled;

    public Event(T content) {
        this.content = content;
    }

    /** Returns the content the first call only; null once already handled. */
    public T getContentIfNotHandled() {
        if (handled) {
            return null;
        }
        handled = true;
        return content;
    }

    /** Returns the content without consuming it (e.g. for inspection/logging). */
    public T peekContent() {
        return content;
    }
}
