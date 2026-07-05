package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.sanhiruzu.musubi.util.MoonReflectionMath;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;

/**
 * Manages the water moon reflection core shader ({@code musubi:water_moon_reflect}).
 *
 * <p>The shader mirrors the actual moon on still water: the fragment shader reflects
 * the view ray off a rippled surface normal and lights fragments by their alignment
 * with the moon's true sky position, so the reflection is a genuine specular image
 * (it sits between the camera and the moon and moves with both), not a glow decal.</p>
 *
 * <p>Two phases:</p>
 * <ol>
 *   <li>Shader registration ({@link RegisterShadersEvent}) — load the program from assets.</li>
 *   <li>Uniform updates (each frame, via {@link #updateUniforms}) — moon direction,
 *       moon-phase brightness, and camera position.</li>
 * </ol>
 */
public final class WaterMoonShader {

    private static ShaderInstance waterMoonShader;

    private WaterMoonShader() {}

    public static void registerShader(RegisterShadersEvent event) throws IOException {
        event.registerShader(
            new ShaderInstance(
                event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath("musubi", "water_moon_reflect"),
                DefaultVertexFormat.POSITION_COLOR
            ),
            shader -> waterMoonShader = shader
        );
    }

    public static ShaderInstance get() {
        return waterMoonShader;
    }

    /** Pushes the per-frame uniforms; call right before drawing the water mesh. */
    public static void updateUniforms(ClientLevel level, Vec3 cameraPos, float partialTick) {
        ShaderInstance shader = waterMoonShader;
        if (shader == null) return;
        Vec3 moon = MoonReflectionMath.moonDirection(level, partialTick);
        if (shader.safeGetUniform("GameTime") != null)
            shader.safeGetUniform("GameTime").set((float) (level.getGameTime() + partialTick) / 24000.0f);
        if (shader.safeGetUniform("MoonDir") != null)
            shader.safeGetUniform("MoonDir").set((float) moon.x, (float) moon.y, (float) moon.z);
        if (shader.safeGetUniform("MoonBrightness") != null)
            shader.safeGetUniform("MoonBrightness").set(level.getMoonBrightness());
        if (shader.safeGetUniform("CameraPos") != null)
            shader.safeGetUniform("CameraPos").set((float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);
    }

    /**
     * The reflection only exists on clear nights while a bright-enough moon is above
     * the horizon; it is the standing cue for where the moon rite can be performed.
     * The gates are shared with the server-side rite via {@link MoonReflectionMath}.
     */
    public static boolean shouldApply(ClientLevel level) {
        if (level == null) return false;
        if (level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) return false;
        return MoonReflectionMath.isMoonlitNight(level);
    }
}
