package com.atom.infrastructure.adapter.accessibility.capture;

/** Native-free projection of one accessibility window. Type constants mirror
 *  android.view.accessibility.AccessibilityWindowInfo. */
public final class WindowSnapshot {
    public static final int TYPE_APPLICATION = 1;
    public static final int TYPE_INPUT_METHOD = 2;
    public static final int TYPE_SYSTEM = 3;

    private final int windowId;
    private final int type;
    private final int layer;
    private final boolean focused;
    private final boolean active;
    private final String packageName;
    private final int boundsL, boundsT, boundsR, boundsB;

    public WindowSnapshot(int windowId, int type, int layer, boolean focused, boolean active,
                          String packageName, int boundsL, int boundsT, int boundsR, int boundsB) {
        this.windowId = windowId;
        this.type = type;
        this.layer = layer;
        this.focused = focused;
        this.active = active;
        this.packageName = packageName == null ? "" : packageName;
        this.boundsL = boundsL;
        this.boundsT = boundsT;
        this.boundsR = boundsR;
        this.boundsB = boundsB;
    }

    public int windowId() { return windowId; }
    public int type() { return type; }
    public int layer() { return layer; }
    public boolean focused() { return focused; }
    public boolean active() { return active; }
    public String packageName() { return packageName; }
    public int boundsL() { return boundsL; }
    public int boundsT() { return boundsT; }
    public int boundsR() { return boundsR; }
    public int boundsB() { return boundsB; }

    public long area() {
        long w = (long) boundsR - boundsL;
        long h = (long) boundsB - boundsT;
        return (w <= 0 || h <= 0) ? 0L : w * h;
    }
}
