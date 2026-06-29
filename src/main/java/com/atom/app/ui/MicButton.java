package com.atom.app.ui;

import android.content.Context;
import android.graphics.Outline;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.appcompat.widget.AppCompatImageButton;

/**
 * Circular microphone button whose touch hitbox follows the animation rather than the
 * other way around. The visible FAB is a circle that the listening pulse scales up and
 * down; this button makes the <em>tappable</em> region that same circle:
 *
 * <ul>
 *   <li>Touches landing in the square corners outside the disc are ignored, so the
 *       hitbox is the circle you actually see — not the bounding box.</li>
 *   <li>Because the parent maps incoming touch points back through this view's scale,
 *       hit-testing against the resting radius automatically tracks the pulse: when the
 *       disc grows, the circular hitbox grows with it. The parent is given headroom
 *       (a larger touch cell) so the enlarged circle stays fully tappable.</li>
 * </ul>
 *
 * A circular {@link ViewOutlineProvider} keeps any clipping/outline consistent with the
 * disc as well.
 */
public class MicButton extends AppCompatImageButton {

    public MicButton(Context context) {
        super(context);
        init();
    }

    public MicButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MicButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                int size = Math.min(view.getWidth(), view.getHeight());
                int left = (view.getWidth() - size) / 2;
                int top = (view.getHeight() - size) / 2;
                outline.setOval(left, top, left + size, top + size);
            }
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float dx = event.getX() - cx;
        float dy = event.getY() - cy;
        float radius = Math.min(cx, cy);
        // Outside the visible disc: don't handle the touch, so only the circle is tappable.
        if (dx * dx + dy * dy > radius * radius) {
            return false;
        }
        return super.onTouchEvent(event);
    }
}
