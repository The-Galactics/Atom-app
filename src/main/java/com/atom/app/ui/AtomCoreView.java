package com.atom.app.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

/**
 * The animated "atom core" centerpiece, drawn by hand on a software layer so it can
 * use real radial-gradient glows and {@link BlurMaskFilter} blooms (which are ignored
 * on a hardware canvas below API 28). It renders, from back to front:
 *
 * <ul>
 *   <li>a soft radial atmosphere glow that breathes;</li>
 *   <li>continuous "sonar" aura rings that expand outward and fade;</li>
 *   <li>three tilted elliptical electron orbits with glowing electrons that pass
 *       behind and in front of the nucleus for a sense of depth;</li>
 *   <li>a main energy ring with a rotating sweep-gradient highlight and a blurred glow;</li>
 *   <li>a breathing nucleus with a radial-gradient body and a hot white center.</li>
 * </ul>
 *
 * <p>Motion is driven by an ever-advancing {@code phase} accumulated from frame deltas,
 * so changing {@link #setEnergy(float)} speeds the animation up or down without jumps.
 * Energy (0 = calm idle, 1 = fully engaged) is eased toward its target every frame and
 * brightens the glows, widens the sweep highlight, and accelerates the orbits.
 */
public class AtomCoreView extends View {

    // Brand lavender palette (matches @color/accent*, kept here so the view is self-contained).
    private static final int ACCENT = 0xFFBF94FF;
    private static final int ACCENT_BRIGHT = 0xFFD6B8FF;
    private static final int ACCENT_DEEP = 0xFF8C5CF0;

    // Three orbits: tilt in degrees, angular speed (sign = direction), starting phase offset.
    private static final float[] ORBIT_TILT = {0f, 62f, 121f};
    private static final float[] ORBIT_SPEED = {1.0f, -0.78f, 1.32f};
    private static final float[] ORBIT_OFFSET = {0f, 2.1f, 4.2f};

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint electronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint electronGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nucleusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nucleusGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Size-dependent resources, rebuilt in onSizeChanged so we don't allocate per frame.
    private RadialGradient bgGlowShader;
    private RadialGradient nucleusShader;
    private SweepGradient sweepShader;
    private BlurMaskFilter ringBlur;
    private BlurMaskFilter electronBlur;
    private BlurMaskFilter nucleusBlur;

    private double phase = 0;        // ever-advancing animation phase
    private float energy = 0f;       // current eased energy
    private float targetEnergy = 0f; // requested energy
    private long lastFrameMs = 0;
    private float cx, cy, radius;

    public AtomCoreView(Context context) {
        super(context);
        init();
    }

    public AtomCoreView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AtomCoreView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // BlurMaskFilter is only honoured on a software layer below API 28; force it
        // everywhere so the glows render identically across devices.
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringGlowPaint.setStyle(Paint.Style.STROKE);
        auraPaint.setStyle(Paint.Style.STROKE);
        orbitPaint.setStyle(Paint.Style.STROKE);
    }

    /** Sets the engagement level: 0 = calm idle, 1 = fully engaged (listening). */
    public void setEnergy(float value) {
        targetEnergy = Math.max(0f, Math.min(1f, value));
        if (isAttachedToWindow()) {
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        lastFrameMs = 0;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(w, h) / 2f;
        if (radius <= 0) {
            return;
        }
        bgGlowShader = new RadialGradient(cx, cy, radius * 0.98f,
                new int[]{withAlpha(ACCENT, 90), withAlpha(ACCENT_DEEP, 38), 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        nucleusShader = new RadialGradient(cx, cy, radius * 0.16f,
                new int[]{0xFFFFFFFF, ACCENT_BRIGHT, withAlpha(ACCENT_DEEP, 210)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        sweepShader = new SweepGradient(cx, cy, new int[]{
                withAlpha(ACCENT, 30), withAlpha(ACCENT_BRIGHT, 255),
                withAlpha(ACCENT, 70), withAlpha(ACCENT_BRIGHT, 255),
                withAlpha(ACCENT, 30)
        }, new float[]{0f, 0.25f, 0.5f, 0.75f, 1f});
        ringBlur = new BlurMaskFilter(radius * 0.06f, BlurMaskFilter.Blur.NORMAL);
        electronBlur = new BlurMaskFilter(radius * 0.035f, BlurMaskFilter.Blur.NORMAL);
        nucleusBlur = new BlurMaskFilter(radius * 0.08f, BlurMaskFilter.Blur.NORMAL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (radius <= 0 || bgGlowShader == null) {
            return;
        }

        long now = AnimationUtils.currentAnimationTimeMillis();
        if (lastFrameMs == 0) {
            lastFrameMs = now;
        }
        float dt = (now - lastFrameMs) / 1000f;
        if (dt > 0.05f) {
            dt = 0.05f; // clamp big gaps (after the view was paused) so motion stays smooth
        }
        lastFrameMs = now;

        // Ease energy toward its target for smooth idle <-> listening transitions.
        energy += (targetEnergy - energy) * Math.min(1f, dt * 4f);

        float speed = 0.5f + energy * 1.15f;
        phase += dt * speed;

        double breathe = Math.sin(phase * 1.15);          // slow -1..1 breathing
        float pulse = (float) (1.0 + 0.045 * breathe + 0.03 * energy);

        drawBackgroundGlow(canvas, pulse);
        drawAuraRings(canvas);
        drawOrbitsAndElectrons(canvas, false);   // orbits + electrons passing behind
        drawMainRing(canvas, pulse);
        drawNucleus(canvas, pulse);
        drawOrbitsAndElectrons(canvas, true);    // electrons passing in front

        postInvalidateOnAnimation();
    }

    private void drawBackgroundGlow(Canvas canvas, float pulse) {
        glowPaint.setShader(bgGlowShader);
        glowPaint.setAlpha((int) (140 + 100 * energy));
        canvas.save();
        canvas.scale(pulse, pulse, cx, cy);
        canvas.drawCircle(cx, cy, radius * 0.98f, glowPaint);
        canvas.restore();
        glowPaint.setShader(null);
    }

    private void drawAuraRings(Canvas canvas) {
        auraPaint.setStyle(Paint.Style.STROKE);
        auraPaint.setStrokeWidth(radius * 0.012f);
        for (int k = 0; k < 3; k++) {
            float p = frac((float) (phase * 0.13) + k / 3f);
            float r = lerp(radius * 0.34f, radius * 0.98f, p);
            int alpha = (int) ((1f - p) * (70 + 60 * energy));
            auraPaint.setColor(withAlpha(ACCENT, alpha));
            canvas.drawCircle(cx, cy, r, auraPaint);
        }
    }

    private void drawMainRing(Canvas canvas, float pulse) {
        float r = radius * 0.44f * pulse;

        // Blurred glow underlay around the ring.
        ringGlowPaint.setStrokeWidth(radius * 0.05f);
        ringGlowPaint.setMaskFilter(ringBlur);
        ringGlowPaint.setColor(withAlpha(ACCENT, (int) (55 + 90 * energy)));
        canvas.drawCircle(cx, cy, r, ringGlowPaint);

        // Crisp ring with a rotating sweep-gradient highlight.
        canvas.save();
        canvas.rotate((float) Math.toDegrees(phase * 0.85), cx, cy);
        ringPaint.setShader(sweepShader);
        ringPaint.setStrokeWidth(radius * 0.022f + radius * 0.01f * energy);
        ringPaint.setAlpha(255);
        canvas.drawCircle(cx, cy, r, ringPaint);
        ringPaint.setShader(null);
        canvas.restore();
    }

    private void drawNucleus(Canvas canvas, float pulse) {
        float nr = radius * 0.15f;

        // Soft bloom behind the nucleus.
        nucleusGlowPaint.setMaskFilter(nucleusBlur);
        nucleusGlowPaint.setColor(withAlpha(ACCENT_BRIGHT, (int) (110 + 110 * energy)));
        canvas.drawCircle(cx, cy, nr * 1.9f * pulse, nucleusGlowPaint);

        // Gradient body.
        canvas.save();
        canvas.scale(pulse, pulse, cx, cy);
        nucleusPaint.setShader(nucleusShader);
        nucleusPaint.setAlpha(255);
        canvas.drawCircle(cx, cy, nr, nucleusPaint);
        nucleusPaint.setShader(null);
        canvas.restore();

        // Hot white center.
        nucleusPaint.setColor(0xE6FFFFFF);
        canvas.drawCircle(cx, cy, nr * 0.4f * pulse, nucleusPaint);
    }

    /**
     * Draws the three orbits and their electrons. On the back pass it also paints the
     * faint orbit ellipses; electrons are split by depth so the {@code front} pass only
     * draws those currently on the near side of the nucleus (and vice versa).
     */
    private void drawOrbitsAndElectrons(Canvas canvas, boolean front) {
        float orbRx = radius * 0.64f;
        float orbRy = radius * 0.26f;

        for (int i = 0; i < 3; i++) {
            if (!front) {
                // Faint orbit path, drawn once (on the back pass).
                canvas.save();
                canvas.rotate(ORBIT_TILT[i], cx, cy);
                orbitPaint.setStrokeWidth(radius * 0.006f);
                orbitPaint.setColor(withAlpha(ACCENT, (int) (26 + 34 * energy)));
                canvas.drawOval(cx - orbRx, cy - orbRy, cx + orbRx, cy + orbRy, orbitPaint);
                canvas.restore();
            }

            double a = phase * ORBIT_SPEED[i] * 1.25 + ORBIT_OFFSET[i];
            boolean isFront = Math.sin(a) >= 0;
            if (isFront != front) {
                continue;
            }

            // Position on the tilted ellipse.
            float ex = (float) (orbRx * Math.cos(a));
            float ey = (float) (orbRy * Math.sin(a));
            double tilt = Math.toRadians(ORBIT_TILT[i]);
            float px = cx + (float) (ex * Math.cos(tilt) - ey * Math.sin(tilt));
            float py = cy + (float) (ex * Math.sin(tilt) + ey * Math.cos(tilt));

            // Near electrons are a touch larger/brighter than far ones.
            float depth = (float) (0.62 + 0.38 * Math.sin(a));
            float er = radius * 0.034f * depth;

            electronGlowPaint.setMaskFilter(electronBlur);
            electronGlowPaint.setColor(withAlpha(ACCENT_BRIGHT, (int) (130 * depth)));
            canvas.drawCircle(px, py, er * 2.3f, electronGlowPaint);

            electronPaint.setColor(withAlpha(ACCENT_BRIGHT, 255));
            canvas.drawCircle(px, py, er, electronPaint);
        }
    }

    private static int withAlpha(int color, int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
