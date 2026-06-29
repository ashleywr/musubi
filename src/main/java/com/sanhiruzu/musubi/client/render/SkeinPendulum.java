package com.sanhiruzu.musubi.client.render;

/**
 * Per-peg pendulum state for the Spool Rack BER.
 *
 * Physics: simple gravity-spring pendulum in the YZ plane (skein swings
 * toward/away from the viewer). Updated lazily from the BER render call
 * rather than a separate tick listener — avoids an event registration and
 * is fine because spool racks are only rendered while loaded.
 */
public class SkeinPendulum {

    private static final double GRAVITY   = 4.5;   // radians/s²  (tuned for silk feel)
    private static final double DAMPING   = 0.97;  // velocity multiplier per tick
    private static final double MAX_ANGLE = 0.45;  // ~26° clamp

    // Stored per-tick (not per-frame) so partialTick interpolation is meaningful.
    public float  prevAngle;
    public float  angle;
    private double angularVelocity;

    /** Last slot count seen — used to detect deposits and trigger a kick. */
    public int lastCount;

    private long lastTick = Long.MIN_VALUE;

    /**
     * Advance physics to {@code gameTick}. Called once per render frame;
     * skips ticks where gameTick hasn't advanced (same tick, multiple frames).
     */
    public void updateTo(long gameTick) {
        if (lastTick == Long.MIN_VALUE) {
            lastTick = gameTick;
            return;
        }
        long delta = gameTick - lastTick;
        if (delta <= 0) return;
        lastTick = gameTick;

        prevAngle = angle;
        long steps = Math.min(delta, 5L); // don't simulate more than 5 ticks if the chunk was unloaded
        for (long t = 0; t < steps; t++) {
            angularVelocity -= GRAVITY * (1.0 / 20.0) * Math.sin(angle);
            angularVelocity *= DAMPING;
            double next = angle + angularVelocity / 20.0;
            angle = (float) Math.max(-MAX_ANGLE, Math.min(MAX_ANGLE, next));
        }
    }

    /**
     * Apply an angular impulse — call when a skein is deposited onto this peg.
     * {@code impulse} is in radians/tick; sign is chosen randomly for variety.
     */
    public void kick(float impulse) {
        angularVelocity += impulse;
    }

    /** Smoothly interpolated angle between the last two game ticks. */
    public float lerpAngle(float partialTick) {
        return prevAngle + (angle - prevAngle) * partialTick;
    }
}
