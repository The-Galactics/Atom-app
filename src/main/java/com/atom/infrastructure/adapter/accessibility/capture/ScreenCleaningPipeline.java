package com.atom.infrastructure.adapter.accessibility.capture;

import com.atom.infrastructure.adapter.accessibility.AtomAccessibilityService.ScreenNode;
import com.atom.infrastructure.adapter.accessibility.oem.OemCompatibilityAdapter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure-Java cleaning: filter phantoms, dedup by stable id, deterministic reindex. */
public final class ScreenCleaningPipeline {

    public List<ScreenNode> clean(List<NodeSnapshot> raw, OemCompatibilityAdapter adapter) {
        List<ScreenNode> out = new ArrayList<>(raw.size());
        Set<Long> seen = new HashSet<>(raw.size() * 2);
        int index = 0;
        for (NodeSnapshot n : raw) {
            if (adapter.isPhantom(n)) {
                continue;
            }
            if (!seen.add(adapter.synthesizeStableId(n))) {
                continue;
            }
            out.add(new ScreenNode(n.text(), n.role(), n.clickable(), n.focusable(),
                    n.editable(), n.scrollable(), index++));
        }
        return out;
    }
}
