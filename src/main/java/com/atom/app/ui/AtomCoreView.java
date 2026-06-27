package com.atom.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AnimationUtils;

import com.atom.app.ui.motion.CoreStyle;
import com.atom.app.ui.motion.MotionProfile;
import com.atom.app.ui.motion.Transient;

/**
 * The animated "atom core" centerpiece, drawn by hand with real radial-gradient glows
 * and {@link BlurMaskFilter} blooms. The static-radius blooms (ring underlay, electron
 * glow, nucleus bloom) are pre-rasterized once per size into cached bitmaps in
 * {@link #onSizeChanged}, so {@link #onDraw} blits cheap textured quads instead of
 * re-running an expensive blur pass every frame. It renders, from back to front:
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

    // Teal "operating" accent endpoints, parallel to the lavender ramp above.
    private static final int TEAL = 0xFF35E0D8;
    private static final int TEAL_BRIGHT = 0xFF7FF3EE;
    private static final int TEAL_DEEP = 0xFF14A39C;

    // Live palette: lavender at hueShift 0, blended toward teal at hueShift 1.
    private int accent = ACCENT;
    private int accentBright = ACCENT_BRIGHT;
    private int accentDeep = ACCENT_DEEP;
    private float hueShift = 0f;

    // Muted look: near-grayscale and dimmed so a muted mic reads differently from idle.
    private static final float MUTED_SATURATION = 0.15f;
    private static final int MUTED_ALPHA = 140;

    // BlurMaskFilter only renders on a hardware canvas from API 28; below that we need a
    // software layer. From API 28 we draw with no layer (LAYER_TYPE_NONE) so the GPU canvas
    // handles the blur — a hardware *layer* would just cache an offscreen buffer this
    // every-frame view re-renders anyway.
    private static final boolean BLUR_NEEDS_SOFTWARE = Build.VERSION.SDK_INT < Build.VERSION_CODES.P;

    // Idle re-draw delay: long enough to span two 60Hz frames so the calm idle motion
    // settles to ~30fps instead of tracking the panel's native (up to 120Hz) refresh.
    private static final long IDLE_FRAME_DELAY_MS = 24;

    // Active (listening/thinking) re-draw delay: time-gates the engaged loop to ~60fps so
    // the signature animation doesn't burn the full 120Hz cadence. The chosen delay lands
    // the next vsync at ~16.6ms on both 60Hz and 120Hz panels; motion stays smooth because
    // the phase is integrated from the real frame dt, not the frame count.
    private static final long ACTIVE_FRAME_DELAY_MS = 9;

    // Three orbits: tilt in degrees, angular speed (sign = direction), starting phase offset.
    private static final float[] ORBIT_TILT = {0f, 62f, 121f};
    private static final float[] ORBIT_SPEED = {1.0f, -0.78f, 1.32f};
    private static final float[] ORBIT_OFFSET = {0f, 2.1f, 4.2f};

    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint auraPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint orbitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint electronPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nucleusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // Blits the pre-rasterized blooms; FILTER_BITMAP smooths the per-frame scale. Alpha is
    // re-set per blit to modulate brightness with energy/depth, so no per-frame allocation.
    private final Paint bloomPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bloomDst = new RectF();

    // Size-dependent resources, rebuilt in onSizeChanged so we don't allocate per frame.
    private RadialGradient bgGlowShader;
    private RadialGradient nucleusShader;
    private SweepGradient sweepShader;

    // Pre-rasterized blurred blooms (baked once per size). Each *Extent is the half-size in
    // px the bitmap represents at scale 1.0, so a frame scales it by pulse (and depth).
    private Bitmap ringGlowBmp;
    private Bitmap electronGlowBmp;
    private Bitmap nucleusGlowBmp;
    private float ringGlowExtent;
    private float electronGlowExtent;
    private float nucleusGlowExtent;

    // Desaturating + dimming paint applied to the whole layer while muted (lazy-built).
    private Paint mutedLayerPaint;

    private double phase = 0;        // ever-advancing animation phase
    private float energy = 0f;       // current eased energy
    private float targetEnergy = 0f; // requested energy
    private long lastFrameMs = 0;
    private float cx, cy, radius;

    private MotionProfile motionProfile = MotionProfile.BREATHE;
    // GATHER eases orbits inward; SCAN promotes one electron to a bright directed sweep.
    private float gather = 0f;           // 0 = normal radii, 1 = fully gathered (~12% inward)
    private float gatherTarget = 0f;
    private boolean scan = false;
    // One-shot transients integrated into the render loop (no second animation system).
    private long bloomStartMs = 0;       // 0 = inactive
    private long shudderStartMs = 0;     // 0 = inactive
    private static final long BLOOM_MS = 450;
    private static final long SHUDDER_MS = 350;

    // Vsync-aligned re-draw used to throttle the idle loop to ~30fps.
    private final Runnable invalidateFrame = this::invalidate;

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
        applyDefaultLayer();
        ringPaint.setStyle(Paint.Style.STROKE);
        auraPaint.setStyle(Paint.Style.STROKE);
        orbitPaint.setStyle(Paint.Style.STROKE);
    }

    /** Applies the layer the glows need by default: software below API 28, none from 28. */
    private void applyDefaultLayer() {
        setLayerType(BLUR_NEEDS_SOFTWARE ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_NONE, null);
    }

    /** Sets the engagement level: 0 = calm idle, 1 = fully engaged (listening). */
    public void setEnergy(float value) {
        targetEnergy = Math.max(0f, Math.min(1f, value));
        scheduleNextFrame();
    }

    /** Applies a semantic style: energy drives engagement, hueShift blends lavender->teal. */
    public void setStyle(CoreStyle style) {
        applyHueShift(style.hueShift);
        setMotionProfile(style.motion);
        setEnergy(style.energy);
        if (style.oneShot != Transient.NONE) {
            playTransient(style.oneShot);
        }
    }

    /** Blends the palette toward teal and rebuilds the size-dependent shaders/blooms once. */
    private void applyHueShift(float shift) {
        float clamped = Math.max(0f, Math.min(1f, shift));
        if (clamped == hueShift) {
            return;
        }
        hueShift = clamped;
        accent = ColorBlend.lerp(ACCENT, TEAL, clamped);
        accentBright = ColorBlend.lerp(ACCENT_BRIGHT, TEAL_BRIGHT, clamped);
        accentDeep = ColorBlend.lerp(ACCENT_DEEP, TEAL_DEEP, clamped);
        if (radius > 0) {
            // Rebuild the size-dependent shaders + baked blooms with the new palette.
            onSizeChanged(getWidth(), getHeight(), getWidth(), getHeight());
        }
        invalidate();
    }

    private void setMotionProfile(MotionProfile profile) {
        motionProfile = profile;
        gatherTarget = profile == MotionProfile.GATHER ? 1f : 0f;
        scan = profile == MotionProfile.SCAN;
        scheduleNextFrame();
    }

    public void playTransient(Transient t) {
        long now = AnimationUtils.currentAnimationTimeMillis();
        if (t == Transient.BLOOM) {
            bloomStartMs = now;
        } else if (t == Transient.SHUDDER) {
            shudderStartMs = now;
        }
        scheduleNextFrame();
    }

    /**
     * Mutes the core's look: when muted it renders near-grayscale and dimmed so the
     * "mic off" state is legible at a glance, distinct from the vivid lavender idle.
     * Implemented as a color filter on a layer: the whole composited core is desaturated.
     */
    public void setMuted(boolean muted) {
        if (muted && mutedLayerPaint == null) {
            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(MUTED_SATURATION);
            mutedLayerPaint = new Paint();
            mutedLayerPaint.setColorFilter(new ColorMatrixColorFilter(matrix));
            mutedLayerPaint.setAlpha(MUTED_ALPHA);
        }
        if (muted) {
            // A layer with the filter paint applies the desaturation to the result; the
            // blooms (now bitmaps) composite into it just like the rest of the core.
            setLayerType(BLUR_NEEDS_SOFTWARE ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_HARDWARE, mutedLayerPaint);
        } else {
            applyDefaultLayer();
        }
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Blooms recycled on detach are rebaked lazily by onDraw once we have a size, so a
        // re-attach at the same size (no onSizeChanged) still renders.
        lastFrameMs = 0;
        scheduleNextFrame();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // Restart the loop when the activity returns to the foreground; while hidden it
        // stops on its own because scheduleNextFrame() bails when the view isn't shown.
        if (visibility == VISIBLE) {
            lastFrameMs = 0;
            scheduleNextFrame();
        }
    }

    /**
     * Re-posts the next animation frame, unless the view is off-screen (then the loop
     * stops). At settled idle it throttles to ~30fps since the calm motion doesn't need
     * every vsync; while engaged it is time-gated to ~60fps rather than the panel's full
     * (up to 120Hz) cadence.
     */
    private void scheduleNextFrame() {
        removeCallbacks(invalidateFrame);
        if (!isAttachedToWindow() || getWindowVisibility() != VISIBLE || !isShown()) {
            return;
        }
        boolean transientActive = bloomStartMs != 0 || shudderStartMs != 0;
        if (targetEnergy == 0f && energy < 0.01f && gather < 0.01f && !transientActive) {
            postOnAnimationDelayed(invalidateFrame, IDLE_FRAME_DELAY_MS);
        } else {
            postOnAnimationDelayed(invalidateFrame, ACTIVE_FRAME_DELAY_MS);
        }
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
                new int[]{withAlpha(accent, 90), withAlpha(accentDeep, 38), 0x00000000},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        nucleusShader = new RadialGradient(cx, cy, radius * 0.16f,
                new int[]{0xFFFFFFFF, accentBright, withAlpha(accentDeep, 210)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        sweepShader = new SweepGradient(cx, cy, new int[]{
                withAlpha(accent, 30), withAlpha(accentBright, 255),
                withAlpha(accent, 70), withAlpha(accentBright, 255),
                withAlpha(accent, 30)
        }, new float[]{0f, 0.25f, 0.5f, 0.75f, 1f});

        bakeBlooms();
    }

    /**
     * Pre-rasterizes the three blurred blooms at full alpha so onDraw can blit them with a
     * per-frame alpha (energy/depth) and scale (pulse) instead of running live
     * BlurMaskFilter passes. Recycles any previous bitmaps first. Requires {@code radius>0}.
     */
    private void bakeBlooms() {
        recycleBlooms();
        float ringR = radius * 0.44f;
        float ringStroke = radius * 0.05f;
        float ringBlur = radius * 0.06f;
        ringGlowExtent = ringR + ringStroke / 2f + ringBlur * 2f;
        ringGlowBmp = bakeRingGlow(ringR, ringStroke, ringBlur, accent);

        float electronR = radius * 0.034f * 2.3f;
        float electronBlur = radius * 0.035f;
        electronGlowExtent = electronR + electronBlur * 2f;
        electronGlowBmp = bakeCircleGlow(electronR, electronBlur, accentBright);

        float nucleusR = radius * 0.15f * 1.9f;
        float nucleusBlur = radius * 0.08f;
        nucleusGlowExtent = nucleusR + nucleusBlur * 2f;
        nucleusGlowBmp = bakeCircleGlow(nucleusR, nucleusBlur, accentBright);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(invalidateFrame);
        recycleBlooms();
    }

    private void recycleBlooms() {
        if (ringGlowBmp != null) { ringGlowBmp.recycle(); ringGlowBmp = null; }
        if (electronGlowBmp != null) { electronGlowBmp.recycle(); electronGlowBmp = null; }
        if (nucleusGlowBmp != null) { nucleusGlowBmp.recycle(); nucleusGlowBmp = null; }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (radius <= 0 || bgGlowShader == null) {
            return;
        }
        // The blooms are recycled on detach; if the view was re-attached at the same size,
        // onSizeChanged won't fire to rebake them. Rebake lazily here — we have a real size —
        // so the core never renders blank. Falls through to a skip only if we somehow still
        // have no size to bake against.
        if (nucleusGlowBmp == null && getWidth() > 0 && getHeight() > 0) {
            bakeBlooms();
        }
        if (nucleusGlowBmp == null) {
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
        gather += (gatherTarget - gather) * Math.min(1f, dt * 4f);

        float speed = 0.5f + energy * 1.15f;
        phase += dt * speed;

        double breathe = Math.sin(phase * 1.15);          // slow -1..1 breathing
        float pulse = (float) (1.0 + 0.045 * breathe + 0.03 * energy);

        float shudder = transientProgress(shudderStartMs, SHUDDER_MS);
        canvas.save();
        if (shudder > 0f) {
            float dx = (float) (radius * 0.03f * shudder * Math.sin(phase * 60));
            canvas.translate(dx, 0);
        }

        drawBackgroundGlow(canvas, pulse);
        drawAuraRings(canvas);
        drawOrbitsAndElectrons(canvas, false);   // orbits + electrons passing behind
        drawMainRing(canvas, pulse);
        drawNucleus(canvas, pulse);
        drawOrbitsAndElectrons(canvas, true);    // electrons passing in front

        canvas.restore();

        long nowMs = AnimationUtils.currentAnimationTimeMillis();
        if (bloomStartMs != 0 && nowMs - bloomStartMs >= BLOOM_MS) bloomStartMs = 0;
        if (shudderStartMs != 0 && nowMs - shudderStartMs >= SHUDDER_MS) shudderStartMs = 0;

        scheduleNextFrame();
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
            auraPaint.setColor(withAlpha(accent, alpha));
            canvas.drawCircle(cx, cy, r, auraPaint);
        }
    }

    private void drawMainRing(Canvas canvas, float pulse) {
        float r = radius * 0.44f * pulse;

        // Blurred glow underlay around the ring (pre-rasterized; scaled by pulse,
        // brightness modulated by energy).
        bloomPaint.setAlpha((int) (55 + 90 * energy));
        blitBloom(canvas, ringGlowBmp, cx, cy, ringGlowExtent * pulse);

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

        // Soft bloom behind the nucleus (pre-rasterized; scaled by pulse, brightness by energy).
        float bloom = transientProgress(bloomStartMs, BLOOM_MS); // 1 -> 0 over BLOOM_MS, else 0
        float bloomScale = 1f + 0.35f * bloom;
        bloomPaint.setAlpha((int) Math.min(255, (110 + 110 * energy) + 145 * bloom));
        blitBloom(canvas, nucleusGlowBmp, cx, cy, nucleusGlowExtent * pulse * bloomScale);

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
        float gatherScale = 1f - 0.12f * gather;
        float orbRx = radius * 0.64f * gatherScale;
        float orbRy = radius * 0.26f * gatherScale;

        for (int i = 0; i < 3; i++) {
            if (!front) {
                // Faint orbit path, drawn once (on the back pass).
                canvas.save();
                canvas.rotate(ORBIT_TILT[i], cx, cy);
                orbitPaint.setStrokeWidth(radius * 0.006f);
                orbitPaint.setColor(withAlpha(accent, (int) (26 + 34 * energy)));
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

            // Soft bloom behind the electron (pre-rasterized; scaled + dimmed by depth).
            bloomPaint.setAlpha((int) (130 * depth));
            blitBloom(canvas, electronGlowBmp, px, py, electronGlowExtent * depth);

            int electronAlpha = scan ? (i == 0 ? 255 : 90) : 255;
            electronPaint.setColor(withAlpha(accentBright, electronAlpha));
            canvas.drawCircle(px, py, er, electronPaint);
        }
    }

    /** Blits a pre-rasterized bloom centered at (x,y), scaled so its half-size is {@code extent}. */
    private void blitBloom(Canvas canvas, Bitmap bmp, float x, float y, float extent) {
        bloomDst.set(x - extent, y - extent, x + extent, y + extent);
        canvas.drawBitmap(bmp, null, bloomDst, bloomPaint);
    }

    /** Bakes a blurred filled circle (radius r, color at full alpha) into a square bitmap. */
    private static Bitmap bakeCircleGlow(float r, float blur, int color) {
        float extent = r + blur * 2f;
        int size = Math.max(1, Math.round(extent * 2f));
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(withAlpha(color, 255));
        p.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(extent, extent, r, p);
        return bmp;
    }

    /** Bakes a blurred stroked ring (radius r, stroke width, color at full alpha) into a bitmap. */
    private static Bitmap bakeRingGlow(float r, float strokeWidth, float blur, int color) {
        float extent = r + strokeWidth / 2f + blur * 2f;
        int size = Math.max(1, Math.round(extent * 2f));
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(strokeWidth);
        p.setColor(withAlpha(color, 255));
        p.setMaskFilter(new BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL));
        c.drawCircle(extent, extent, r, p);
        return bmp;
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

    /** Returns a 1->0 decay for an active one-shot, or 0 when inactive/elapsed. */
    private float transientProgress(long startMs, long durationMs) {
        if (startMs == 0) {
            return 0f;
        }
        long elapsed = AnimationUtils.currentAnimationTimeMillis() - startMs;
        if (elapsed >= durationMs) {
            return 0f;
        }
        return 1f - (float) elapsed / durationMs;
    }
}
