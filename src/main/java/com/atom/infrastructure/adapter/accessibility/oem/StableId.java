package com.atom.infrastructure.adapter.accessibility.oem;

import com.atom.infrastructure.adapter.accessibility.capture.NodeSnapshot;

/** Allocation-light stable identity for a node (no String/Rect formatting). */
final class StableId {
    private StableId() {
    }

    static long of(NodeSnapshot n) {
        long h = 1125899906842597L; // FNV-ish seed
        String viewId = n.viewId();
        if (viewId != null && !viewId.isEmpty()) {
            // viewId is reused across sibling rows (RecyclerView/ListView), so it
            // is NOT unique on its own — always mix in position so distinct
            // on-screen nodes get distinct ids and only true duplicates collapse.
            h = h * 31 + viewId.hashCode();
        } else {
            h = h * 31 + n.role().hashCode();
            h = h * 31 + n.text().hashCode();
        }
        h = h * 31 + n.boundsL();
        h = h * 31 + n.boundsT();
        h = h * 31 + n.boundsR();
        h = h * 31 + n.boundsB();
        h = h * 31 + n.siblingIndex();
        return mix(h);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
