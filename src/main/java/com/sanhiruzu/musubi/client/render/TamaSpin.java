package com.sanhiruzu.musubi.client.render;

/**
 * Smooth Y-axis spin state for tama items rendered in the GUI.
 *
 * Driven by wall-clock time so the animation runs at display frame rate without needing
 * a partialTick parameter. Hover state is detected via ItemTooltipEvent (which fires once
 * per frame for the hovered item) and fed to beginFrame() via RenderGuiEvent.Pre.
 *
 * One frame of lag between the tooltip firing and beginFrame() consuming the flag is
 * ~16 ms at 60 FPS — entirely imperceptible.
 */
public class TamaSpin {

    public static final TamaSpin INSTANCE = new TamaSpin();

    /** Degrees/second at full hover speed — roughly one revolution per 1.2 seconds. */
    private static final float HOVER_SPEED = 300f;
    /** Degrees/second² — how quickly the spin ramps up and coasts down. */
    private static final float ACCEL       = 480f;

    private float velocity         = 0f;
    private float angle            = 0f;
    private boolean hoveredThisFrame = false;
    private long lastNanoTime      = System.nanoTime();

    /** Current accumulated spin angle in degrees (unbounded; sin/cos handle wrap naturally). */
    public float getAngle() {
        return angle;
    }

    /**
     * Called from ItemTooltipEvent when a TamaItem's tooltip is being built (i.e. it is hovered).
     * Safe to call multiple times per frame; the flag is consumed on the next beginFrame().
     */
    public void markHoveredThisFrame() {
        hoveredThisFrame = true;
    }

    /**
     * Advance the state machine for this frame. Must be called exactly once per rendered frame,
     * before any item rendering occurs — register on RenderGuiEvent.Pre.
     */
    public void beginFrame() {
        long now = System.nanoTime();
        float dt = Math.min((now - lastNanoTime) / 1_000_000_000f, 0.1f); // cap at 100 ms to survive pauses
        lastNanoTime = now;

        boolean hovered = hoveredThisFrame;
        hoveredThisFrame = false;

        float target = hovered ? HOVER_SPEED : 0f;
        if (velocity < target)      velocity = Math.min(target, velocity + ACCEL * dt);
        else if (velocity > target) velocity = Math.max(target, velocity - ACCEL * dt);
        angle += velocity * dt;
    }
}
