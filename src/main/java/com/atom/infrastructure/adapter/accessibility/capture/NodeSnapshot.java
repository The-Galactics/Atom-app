package com.atom.infrastructure.adapter.accessibility.capture;

/** Plain, native-free projection of one accessibility node. */
public final class NodeSnapshot {
    private final String text;
    private final String role;
    private final boolean clickable;
    private final boolean focusable;
    private final boolean editable;
    private final boolean scrollable;
    private final String viewId;
    private final String packageName;
    private final int boundsL, boundsT, boundsR, boundsB;
    private final int depth;
    private final int siblingIndex;
    private final boolean visibleToUser;

    private NodeSnapshot(Builder b) {
        this.text = b.text;
        this.role = b.role;
        this.clickable = b.clickable;
        this.focusable = b.focusable;
        this.editable = b.editable;
        this.scrollable = b.scrollable;
        this.viewId = b.viewId;
        this.packageName = b.packageName;
        this.boundsL = b.boundsL;
        this.boundsT = b.boundsT;
        this.boundsR = b.boundsR;
        this.boundsB = b.boundsB;
        this.depth = b.depth;
        this.siblingIndex = b.siblingIndex;
        this.visibleToUser = b.visibleToUser;
    }

    public String text() { return text; }
    public String role() { return role; }
    public boolean clickable() { return clickable; }
    public boolean focusable() { return focusable; }
    public boolean editable() { return editable; }
    public boolean scrollable() { return scrollable; }
    public String viewId() { return viewId; }
    public String packageName() { return packageName; }
    public int boundsL() { return boundsL; }
    public int boundsT() { return boundsT; }
    public int boundsR() { return boundsR; }
    public int boundsB() { return boundsB; }
    public int depth() { return depth; }
    public int siblingIndex() { return siblingIndex; }
    public boolean visibleToUser() { return visibleToUser; }

    public int area() {
        int w = boundsR - boundsL;
        int h = boundsB - boundsT;
        return (w <= 0 || h <= 0) ? 0 : w * h;
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String text = "";
        private String role = "";
        private boolean clickable, focusable, editable, scrollable, visibleToUser;
        private String viewId = "";
        private String packageName = "";
        private int boundsL, boundsT, boundsR, boundsB;
        private int depth, siblingIndex;

        public Builder text(String v) { this.text = v == null ? "" : v; return this; }
        public Builder role(String v) { this.role = v == null ? "" : v; return this; }
        public Builder clickable(boolean v) { this.clickable = v; return this; }
        public Builder focusable(boolean v) { this.focusable = v; return this; }
        public Builder editable(boolean v) { this.editable = v; return this; }
        public Builder scrollable(boolean v) { this.scrollable = v; return this; }
        public Builder visibleToUser(boolean v) { this.visibleToUser = v; return this; }
        public Builder viewId(String v) { this.viewId = v == null ? "" : v; return this; }
        public Builder packageName(String v) { this.packageName = v == null ? "" : v; return this; }
        public Builder bounds(int l, int t, int r, int b) {
            this.boundsL = l; this.boundsT = t; this.boundsR = r; this.boundsB = b; return this;
        }
        public Builder depth(int v) { this.depth = v; return this; }
        public Builder siblingIndex(int v) { this.siblingIndex = v; return this; }
        public NodeSnapshot build() { return new NodeSnapshot(this); }
    }
}
