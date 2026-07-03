package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sanhiruzu.musubi.network.MoonRiteVfxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side moon rite burst VFX: when the server reports a completed rite, a
 * brighter glow plus an expanding ripple ring plays out at that water source.
 *
 * <p>The standing ambient moon reflection that used to be drawn here as additive
 * glow discs is gone — {@link MoonWaterRenderer} now renders a true specular moon
 * reflection on still water via the {@code musubi:water_moon_reflect} shader, and
 * that reflection is the affordance for where the rite can be performed. Only the
 * one-shot ritual feedback remains in this class.</p>
 *
 * <p>Everything uses additive blending so the dark of night never darkens the
 * water — only the moonlight adds to it.</p>
 */
public final class MoonReflectionVfx {

    /** Active ritual bursts, keyed by server event id. */
    private static final Map<Long, ActiveMoonRite> ACTIVE = new LinkedHashMap<>();

    private MoonReflectionVfx() {}

    public static void accept(ClientLevel level, MoonRiteVfxPayload payload) {
        ACTIVE.put(payload.eventId(), new ActiveMoonRite(payload));
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (ACTIVE.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;

        float moon = level.getMoonBrightness();
        long now = level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float time = now + partial;

        // Collect the live ritual bursts (dropping any that have finished).
        List<float[]> bursts = new ArrayList<>();
        for (Iterator<Map.Entry<Long, ActiveMoonRite>> it = ACTIVE.entrySet().iterator(); it.hasNext();) {
            ActiveMoonRite effect = it.next().getValue();
            float age = (now - effect.startTick()) + partial;
            if (age >= effect.durationTicks()) {
                it.remove();
                continue;
            }
            BlockPos p = effect.payload().pos();
            bursts.add(new float[] { p.getX() + 0.5F, surfaceY(level, p), p.getZ() + 0.5F,
                age, effect.durationTicks() });
        }
        if (bursts.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        // Additive: reflections only ever brighten the water, they never draw a dark box.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        // The glint sits right on the water surface; without disabling the depth test the water
        // itself occludes it. It's a flat surface decal viewed from above, so drawing it on top is fine.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        // Reset the color modulator: the vanilla pipeline can leave it at a low alpha, which would
        // silently scale every vertex down (our glints rendered at ~20% strength without this).
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        BufferBuilder builder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (float[] b : bursts) {
            appendBurst(builder, matrix, b[0], b[1], b[2], b[3], b[4], moon, time);
        }

        // build() returns null when every disc/ripple was culled by its alpha check this frame;
        // buildOrThrow() would crash on the empty buffer, so null-check and skip the draw.
        MeshData mesh = builder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    /** Water top surface height in world Y for the given source block. */
    private static float surfaceY(ClientLevel level, BlockPos pos) {
        FluidState fluid = level.getFluidState(pos);
        float h = fluid.isEmpty() ? 0.9F : fluid.getHeight(level, pos);
        return pos.getY() + h + 0.02F;
    }

    // ── Ritual burst ───────────────────────────────────────────────────────────

    private static void appendBurst(BufferBuilder builder, Matrix4f matrix,
                                    float cx, float cy, float cz, float age, float duration,
                                    float moon, float time) {
        float progress = Mth.clamp(age / Math.max(1.0F, duration), 0.0F, 1.0F);
        float fade = 1.0F - Mth.clamp((progress - 0.62F) * 2.7F, 0.0F, 1.0F);
        float alpha = Mth.clamp((0.30F + moon * 0.60F) * fade, 0.0F, 1.0F);
        if (alpha <= 0.01F) return;

        float pulse = 1.0F + 0.04F * Mth.sin(age * 0.28F);
        float seed = cx * 12.9898F + cz * 78.233F;
        // Bright solid-cored glow with a wider soft halo.
        appendDisc(builder, matrix, cx, cy, cz, 0.90F * pulse, 0.0F, 210, 230, 255, alpha * 0.30F, 24, seed, time);
        appendDisc(builder, matrix, cx, cy, cz, 0.50F * pulse, 0.40F, 235, 245, 255, alpha, 24, seed, time);
        appendRipple(builder, matrix, cx, cy, cz, age, alpha);
    }

    private static void appendRipple(BufferBuilder builder, Matrix4f matrix,
                                     float cx, float cy, float cz, float age, float baseAlpha) {
        float cycle = (age % 36.0F) / 36.0F;
        float ringRadius = Mth.lerp(cycle, 0.24F, 1.45F);
        float halfWidth = 0.10F + 0.04F * Mth.sin((age * 0.18F) + 1.2F);

        float fade = Mth.clamp(1.0F - Math.abs(cycle - 0.45F) / 0.45F, 0.0F, 1.0F);
        float peak = baseAlpha * fade * 0.9F;
        if (peak <= 0.01F) return;

        int segments = 40;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float wob0 = 1.0F + 0.05F * Mth.sin(age * 0.40F + i * 0.7F);
            float wob1 = 1.0F + 0.05F * Mth.sin(age * 0.40F + (i + 1) * 0.7F);
            float mid = ringRadius;
            float in = (ringRadius - halfWidth);
            float out = (ringRadius + halfWidth);
            // inner half fades up to the ring crest, outer half fades back to nothing
            appendAnnulus(builder, matrix, cx, cy, cz, a0, a1, in * wob0, in * wob1, mid * wob0, mid * wob1,
                225, 245, 255, 0.0F, peak);
            appendAnnulus(builder, matrix, cx, cy, cz, a0, a1, mid * wob0, mid * wob1, out * wob0, out * wob1,
                225, 245, 255, peak, 0.0F);
        }
    }

    // ── Geometry helpers (all flat on the water, +Y up) ────────────────────────

    /**
     * A glow with a solid bright core (out to {@code coreFrac} of the radius) that then fades to
     * zero alpha at the rim. A pure gradient (coreFrac 0) spreads its brightness too thin to read on
     * dark water, so a solid core is what makes the glint actually pop.
     */
    private static void appendDisc(BufferBuilder builder, Matrix4f matrix,
                                   float cx, float cy, float cz, float radius, float coreFrac,
                                   int r, int g, int b, float centerAlpha, int segments,
                                   float seed, float time) {
        int ca = Math.round(Mth.clamp(centerAlpha, 0.0F, 1.0F) * 255.0F);
        if (ca <= 1) return;
        float core = radius * Mth.clamp(coreFrac, 0.0F, 1.0F);
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float wob0 = 1.0F + 0.04F * Mth.sin(time * 0.20F + i * 0.9F + seed);
            float wob1 = 1.0F + 0.04F * Mth.sin(time * 0.20F + (i + 1) * 0.9F + seed);
            float s0 = Mth.sin(a0), c0 = Mth.cos(a0);
            float s1 = Mth.sin(a1), c1 = Mth.cos(a1);
            float cr0 = core * wob0, cr1 = core * wob1;
            float rr0 = radius * wob0, rr1 = radius * wob1;

            // Solid core triangle (full alpha across it).
            builder.addVertex(matrix, cx, cy, cz).setColor(r, g, b, ca);
            builder.addVertex(matrix, cx + s0 * cr0, cy, cz + c0 * cr0).setColor(r, g, b, ca);
            builder.addVertex(matrix, cx + s1 * cr1, cy, cz + c1 * cr1).setColor(r, g, b, ca);

            // Halo band: core edge (full alpha) fading out to the rim (zero alpha).
            builder.addVertex(matrix, cx + s0 * cr0, cy, cz + c0 * cr0).setColor(r, g, b, ca);
            builder.addVertex(matrix, cx + s0 * rr0, cy, cz + c0 * rr0).setColor(r, g, b, 0);
            builder.addVertex(matrix, cx + s1 * rr1, cy, cz + c1 * rr1).setColor(r, g, b, 0);
            builder.addVertex(matrix, cx + s0 * cr0, cy, cz + c0 * cr0).setColor(r, g, b, ca);
            builder.addVertex(matrix, cx + s1 * rr1, cy, cz + c1 * rr1).setColor(r, g, b, 0);
            builder.addVertex(matrix, cx + s1 * cr1, cy, cz + c1 * cr1).setColor(r, g, b, ca);
        }
    }

    /** One angular slice of an annulus (as two triangles), with per-edge alpha for soft ring bands. */
    private static void appendAnnulus(BufferBuilder builder, Matrix4f matrix,
                                      float cx, float cy, float cz, float a0, float a1,
                                      float rIn0, float rIn1, float rOut0, float rOut1,
                                      int r, int g, int b, float alphaIn, float alphaOut) {
        int ai = Math.round(Mth.clamp(alphaIn, 0.0F, 1.0F) * 255.0F);
        int ao = Math.round(Mth.clamp(alphaOut, 0.0F, 1.0F) * 255.0F);
        float s0 = Mth.sin(a0), c0 = Mth.cos(a0);
        float s1 = Mth.sin(a1), c1 = Mth.cos(a1);
        float inX0 = cx + s0 * rIn0,  inZ0 = cz + c0 * rIn0;
        float inX1 = cx + s1 * rIn1,  inZ1 = cz + c1 * rIn1;
        float outX0 = cx + s0 * rOut0, outZ0 = cz + c0 * rOut0;
        float outX1 = cx + s1 * rOut1, outZ1 = cz + c1 * rOut1;
        builder.addVertex(matrix, inX0, cy, inZ0).setColor(r, g, b, ai);
        builder.addVertex(matrix, outX0, cy, outZ0).setColor(r, g, b, ao);
        builder.addVertex(matrix, outX1, cy, outZ1).setColor(r, g, b, ao);
        builder.addVertex(matrix, inX0, cy, inZ0).setColor(r, g, b, ai);
        builder.addVertex(matrix, outX1, cy, outZ1).setColor(r, g, b, ao);
        builder.addVertex(matrix, inX1, cy, inZ1).setColor(r, g, b, ai);
    }

    private record ActiveMoonRite(MoonRiteVfxPayload payload, long startTick) {
        private ActiveMoonRite(MoonRiteVfxPayload payload) {
            this(payload, payload.startTick());
        }

        private int durationTicks() {
            return payload.durationTicks();
        }
    }
}
