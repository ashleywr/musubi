package com.sanhiruzu.musubi.client.render;

/**
 * Per-position orbit state for the Marudai BER.
 *
 * The tama (bobbins) ease up to {@link #MAX_SPEED} while the marudai is winding and
 * coast back to rest when it finishes — a linear ramp on angular velocity, the same
 * approach used by {@link WheelSpin}. Gentler than the spinning wheel: braiding turns
 * slowly (~one revolution every 4.5 s at full speed).
 *
 * Updated lazily from the BER render call; marudai are only rendered while loaded.
 */
public class MarudaiSpin {

    /** Degrees per tick at full speed (~4.5 s per revolution). */
    private static final double MAX_SPEED = 4.0;
    /** Degrees per tick² — how quickly the orbit ramps toward its target speed. */
    private static final double ACCEL = 0.3;
    /** Peak vertical bob, in blocks (~0.7px) — kept subtle. */
    private static final double BOB_AMPLITUDE = 0.045;
    /** Bob oscillations per full revolution. */
    private static final double BOB_CYCLES = 2.0;

    // Stored per-tick (not per-frame) so partialTick interpolation is meaningful.
    // Kept unbounded (never wrapped mod 360) so interpolation never sees a jump.
    private double prevAngle;
    private double angle;
    private double velocity;

    private long lastTick = Long.MIN_VALUE;

    /**
     * Advance the orbit to {@code gameTick}, ramping toward full speed when
     * {@code winding} and toward rest otherwise. Called once per render frame;
     * skips frames where the game tick hasn't advanced.
     */
    public void updateTo(long gameTick, boolean winding) {
        if (lastTick == Long.MIN_VALUE) {
            lastTick = gameTick;
            return;
        }
        long delta = gameTick - lastTick;
        if (delta <= 0) return;
        lastTick = gameTick;

        prevAngle = angle;
        long steps = Math.min(delta, 5L); // don't simulate a backlog if the chunk was unloaded
        double target = winding ? MAX_SPEED : 0.0;
        for (long t = 0; t < steps; t++) {
            if (velocity < target)      velocity = Math.min(target, velocity + ACCEL);
            else if (velocity > target) velocity = Math.max(target, velocity - ACCEL);
            angle += velocity;
        }
    }

    /** Smoothly interpolated angle (degrees) between the last two game ticks. */
    public float lerpAngle(float partialTick) {
        return (float) (prevAngle + (angle - prevAngle) * partialTick);
    }

    /**
     * Vertical bob offset (blocks) for the orbiting ring, oscillating {@link #BOB_CYCLES}
     * times per revolution. Amplitude scales with current speed so the bob eases out to
     * flat as the orbit coasts to rest, rather than freezing mid-rise.
     */
    public double bobOffset(float partialTick) {
        double speedFraction = Math.min(1.0, velocity / MAX_SPEED);
        if (speedFraction <= 0.0) return 0.0;
        double rad = Math.toRadians(lerpAngle(partialTick) * BOB_CYCLES);
        return BOB_AMPLITUDE * speedFraction * Math.sin(rad);
    }
}
