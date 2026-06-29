package com.atom.infrastructure.adapter.accessibility.capture;

import com.atom.infrastructure.adapter.accessibility.oem.OemCompatibilityAdapter;
import java.util.List;

/**
 * One capture attempt's native window set. {@link #close()} recycles every native
 * window and root exactly once; nothing native escapes this object.
 */
public interface WindowSession extends AutoCloseable {
    List<WindowSnapshot> windows();
    CaptureProbe probe(WindowSnapshot target, int prevTopLevelChildCount, long msSinceLastMutation);
    List<NodeSnapshot> extract(WindowSnapshot target, OemCompatibilityAdapter adapter);
    @Override
    void close();
}
