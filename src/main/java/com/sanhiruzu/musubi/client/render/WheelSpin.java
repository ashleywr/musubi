package com.sanhiruzu.musubi.client.render;

/**
 * Per-position spin state for the Spinning Wheel BER.
 *
 * The wheel eases up to {@link #MAX_SPEED} while the block is working and coasts
 * back to a stop when it finishes — a linear ramp on angular velocity, which reads
 * as a believable spin-up/spin-down. Updated lazily from the BER render call (same
 * approach as {@link SkeinPendulum}); spinning wheels are only rendered while loaded.
 */
public class WheelSpin {

    /** Degrees per tick at full speed (~2.3 s per revolution). */
    private static final double MAX_SPEED = 7.8;
    /** Degrees per tick² — how quickly speed ramps toward its target. */
    private static final double ACCEL = 0.55;
    /** Velocity threshold that separates "effectively stopped" from "moving". */
    private static final double MOVING_THRESHOLD = 0.1;

    // Stored per-tick (not per-frame) so partialTick interpolation is meaningful.
    // Kept unbounded (never wrapped mod 360) so interpolation never sees a jump;
    // a double holds exact integer-degree growth for far longer than any session.
    private double prevAngle;
    private double angle;
    private double velocity;

    // Transition flags set during updateTo (once per game tick), consumed by the BER.
    private boolean didStart = false;
    private boolean didStop  = false;

    private long lastTick = Long.MIN_VALUE;

    /**
     * Advance the spin to {@code gameTick}, ramping toward full speed when
     * {@code spinning} and toward rest otherwise. Called once per render frame;
     * skips frames where the game tick hasn't advanced.
     */
    public void updateTo(long gameTick, boolean spinning) {
        if (lastTick == Long.MIN_VALUE) {
            lastTick = gameTick;
            return;
        }
        long delta = gameTick - lastTick;
        if (delta <= 0) return;
        lastTick = gameTick;

        prevAngle = angle;
        long steps = Math.min(delta, 5L); // don't simulate a backlog if the chunk was unloaded
        double target = spinning ? MAX_SPEED : 0.0;
        double velBefore = velocity;
        for (long t = 0; t < steps; t++) {
            if (velocity < target)      velocity = Math.min(target, velocity + ACCEL);
            else if (velocity > target) velocity = Math.max(target, velocity - ACCEL);
            angle += velocity;
        }
        if (velBefore < MOVING_THRESHOLD && velocity >= MOVING_THRESHOLD) didStart = true;
        if (velBefore >= MOVING_THRESHOLD && velocity < MOVING_THRESHOLD)  didStop  = true;
    }

    /** Returns true (and clears the flag) if the wheel crossed the moving threshold upward this tick. */
    public boolean consumeStart() { boolean v = didStart; didStart = false; return v; }

    /** Returns true (and clears the flag) if the wheel crossed the moving threshold downward this tick. */
    public boolean consumeStop()  { boolean v = didStop;  didStop  = false; return v; }

    /** Smoothly interpolated angle (degrees) between the last two game ticks. */
    public float lerpAngle(float partialTick) {
        return (float) (prevAngle + (angle - prevAngle) * partialTick);
    }
}
