package com.sanhiruzu.musubi.util;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Shared client/server math for the moon's water reflection.
 *
 * <p>The client draws the reflection with the {@code musubi:water_moon_reflect} core
 * shader; the server decides whether a thrown item landed inside it. Both sides must
 * agree on where the reflection is, so the moon direction, environment gates, and the
 * glitter-path acceptance cone all live here. Tune here and the visual and the rite
 * move together.</p>
 *
 * <p>A specular reflection is viewer-dependent: each player sees the moon path in a
 * different spot on the water (between <em>their</em> eye and the moon's azimuth).
 * Server-side checks therefore always take the observer's eye position.</p>
 */
public final class MoonReflectionMath {

    /**
     * Cosine of the glitter-path envelope half-angle (~17°).
     *
     * <p>Derivation, matching {@code water_moon_reflect.fsh}: the vanilla moon quad
     * spans ~5.7° of half-angle (20 units wide at distance 100), and the shader's four
     * wavelets tilt the surface normal by up to ~0.097 rad, deflecting the reflected
     * ray by up to twice that (~11.1°). 5.7° + 11.1° ≈ 17° — everywhere the player can
     * see sparkle. Raise toward cos(6°) ≈ 0.995 to demand a bullseye on the disc.</p>
     */
    public static final double COS_GLITTER_PATH = Math.cos(Math.toRadians(17.0));

    /**
     * Minimum vanilla moon-phase brightness. Vanilla phases are 0, 0.25, 0.5, 0.75, 1,
     * so this admits every phase except the new moon.
     */
    public static final float MIN_MOON_BRIGHTNESS = 0.05F;

    private MoonReflectionMath() {}

    /**
     * World-space unit vector toward the moon's center. Vanilla renders the celestial
     * quads rotated by YP(-90) then XP(dayFraction * 360), which places the sun at
     * (-sin a, cos a, 0) for sun angle {@code a}; the moon sits directly opposite.
     */
    public static Vec3 moonDirection(Level level, float partialTick) {
        float a = level.getSunAngle(partialTick);
        return new Vec3(Mth.sin(a), -Mth.cos(a), 0.0);
    }

    /**
     * Clear night with a bright-enough moon above the horizon, in a dimension with a
     * normal day/night sky. When this fails, the reflection neither renders nor
     * accepts rites.
     */
    public static boolean isMoonlitNight(Level level) {
        if (!level.dimensionType().natural()) return false;
        if (level.isRaining()) return false;
        if (level.getMoonBrightness() <= MIN_MOON_BRIGHTNESS) return false;
        long timeOfDay = Math.floorMod(level.getDayTime(), 24000L);
        if (timeOfDay < 13000L || timeOfDay > 23000L) return false;
        return moonDirection(level, 1.0F).y > 0.05;
    }

    /**
     * Sky-lit still source water with an open surface — the only water that carries
     * the reflection (and can host the rite).
     */
    public static boolean isReflectiveWater(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) return false;
        if (!level.getFluidState(pos.above()).isEmpty()) return false;
        return level.canSeeSky(pos.above());
    }

    /** World-Y of the water surface for a reflective water block. */
    public static double surfaceY(Level level, BlockPos pos) {
        return pos.getY() + level.getBlockState(pos).getFluidState().getHeight(level, pos);
    }

    /**
     * Whether {@code surfacePoint} (a point on the water surface) lies inside the
     * moon's glitter path as seen from {@code eye}: mirror the eye→point ray across
     * the water plane and test alignment against the moon direction, with the same
     * tolerance the shader's ripples produce on screen.
     */
    public static boolean isInReflection(Level level, Vec3 eye, Vec3 surfacePoint) {
        Vec3 view = surfacePoint.subtract(eye);
        if (view.y >= 0.0 || view.lengthSqr() < 1.0E-6) return false; // eye must be above the surface
        view = view.normalize();
        Vec3 reflected = new Vec3(view.x, -view.y, view.z);
        return reflected.dot(moonDirection(level, 1.0F)) >= COS_GLITTER_PATH;
    }
}
