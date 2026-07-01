package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.sanhiruzu.musubi.network.MoonRiteVfxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side moon rite VFX.
 *
 * <p>This renders only when the server says the ritual fired, so the moon response is reliable
 * instead of depending on whatever the client happens to be targeting.</p>
 */
public final class MoonReflectionVfx {

    private static final ResourceLocation MOON_TEX =
        ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
    private static final RenderType MOON_TYPE = RenderType.entityTranslucent(MOON_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Map<Long, ActiveMoonRite> ACTIVE = new LinkedHashMap<>();

    private MoonReflectionVfx() {}

    public static void accept(ClientLevel level, MoonRiteVfxPayload payload) {
        ACTIVE.put(payload.eventId(), new ActiveMoonRite(payload));
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || ACTIVE.isEmpty()) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        VertexConsumer moonConsumer = bufferSource.getBuffer(MOON_TYPE);
        long now = level.getGameTime();

        boolean rendered = false;
        for (Iterator<Map.Entry<Long, ActiveMoonRite>> it = ACTIVE.entrySet().iterator(); it.hasNext();) {
            ActiveMoonRite effect = it.next().getValue();
            float age = (now - effect.startTick()) + partial;
            if (age >= effect.durationTicks()) {
                it.remove();
                continue;
            }
            rendered |= renderEffect(level, poseStack, moonConsumer, effect.payload(), age);
        }

        if (rendered) {
            bufferSource.endBatch(MOON_TYPE);
        }
        poseStack.popPose();
    }

    private static boolean renderEffect(Level level, PoseStack poseStack, VertexConsumer moonConsumer,
                                        MoonRiteVfxPayload payload, float age) {
        BlockPos pos = payload.pos();
        float duration = Math.max(1.0F, payload.durationTicks());
        float progress = Mth.clamp(age / duration, 0.0F, 1.0F);

        float moonBrightness = level.getMoonBrightness();
        float fade = 1.0F - Mth.clamp((progress - 0.62F) * 2.7F, 0.0F, 1.0F);
        float alpha = Mth.clamp((0.25F + moonBrightness * 0.65F) * fade, 0.0F, 1.0F);
        if (alpha <= 0.01F) return false;

        float pulse = 1.0F + 0.03F * Mth.sin(age * 0.28F);
        float size = 0.58F * pulse;
        float glowSize = size * 1.55F;
        float[] uv = moonUv(level.getMoonPhase());

        poseStack.pushPose();
        poseStack.translate(pos.getX() + 0.5D, pos.getY() + 0.995D, pos.getZ() + 0.5D);
        Matrix4f matrix = poseStack.last().pose();

        drawRippleRing(matrix, age, alpha);
        drawMoonQuad(moonConsumer, matrix, glowSize, uv, 0.95F, 0.97F, 1.0F, alpha * 0.26F);
        drawMoonQuad(moonConsumer, matrix, size, uv, 0.98F, 0.99F, 1.0F, alpha);
        poseStack.popPose();
        return true;
    }

    /**
     * Matches vanilla moon phase UV selection in LevelRenderer.
     */
    private static float[] moonUv(int moonPhase) {
        int column = moonPhase % 4;
        int row = moonPhase / 4 % 2;
        float u0 = column / 4.0F;
        float v0 = row / 2.0F;
        float u1 = (column + 1) / 4.0F;
        float v1 = (row + 1) / 2.0F;
        return new float[] { u0, v0, u1, v1 };
    }

    private static void drawMoonQuad(VertexConsumer vc, Matrix4f matrix, float size, float[] uv,
                                     float r, float g, float b, float a) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        vc.addVertex(matrix, -size, 0.0F,  size).setColor(r, g, b, a).setUv(u1, v1)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT);
        vc.addVertex(matrix,  size, 0.0F,  size).setColor(r, g, b, a).setUv(u0, v1)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT);
        vc.addVertex(matrix,  size, 0.0F, -size).setColor(r, g, b, a).setUv(u0, v0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT);
        vc.addVertex(matrix, -size, 0.0F, -size).setColor(r, g, b, a).setUv(u1, v0)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT);
    }

    private static void drawRippleRing(Matrix4f matrix, float age, float baseAlpha) {
        float cycle = (age % 36.0F) / 36.0F;
        float ringRadius = Mth.lerp(cycle, 0.22F, 1.42F);
        float ringWidth = 0.10F + 0.04F * Mth.sin((age * 0.18F) + 1.2F);
        float inner = Math.max(0.01F, ringRadius - ringWidth);
        float outer = ringRadius + ringWidth;

        float fade = 1.0F - Math.abs(cycle - 0.45F) / 0.45F;
        fade = Mth.clamp(fade, 0.0F, 1.0F);
        float alpha = baseAlpha * fade;
        if (alpha <= 0.01F) return;

        float edgeAlpha = alpha * 0.95F;
        float glowAlpha = alpha * 0.28F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            float a0 = (float) (i * Math.PI * 2.0 / segments);
            float a1 = (float) ((i + 1) * Math.PI * 2.0 / segments);
            float s0 = Mth.sin(a0), c0 = Mth.cos(a0);
            float s1 = Mth.sin(a1), c1 = Mth.cos(a1);

            float wobble0 = 1.0F + 0.05F * Mth.sin(age * 0.40F + i * 0.7F);
            float wobble1 = 1.0F + 0.05F * Mth.sin(age * 0.40F + (i + 1) * 0.7F);
            float in0 = inner * wobble0;
            float in1 = inner * wobble1;
            float out0 = outer * wobble0;
            float out1 = outer * wobble1;

            addRingQuad(builder, matrix,
                s0 * in0, 0.003F, c0 * in0,
                s1 * in1, 0.003F, c1 * in1,
                s1 * out1, 0.003F, c1 * out1,
                s0 * out0, 0.003F, c0 * out0,
                220, 245, 255, Math.round(glowAlpha * 255.0F));
            addRingQuad(builder, matrix,
                s0 * (in0 * 0.98F), 0.004F, c0 * (in0 * 0.98F),
                s1 * (in1 * 0.98F), 0.004F, c1 * (in1 * 0.98F),
                s1 * (outer * 0.92F), 0.004F, c1 * (outer * 0.92F),
                s0 * (outer * 0.92F), 0.004F, c0 * (outer * 0.92F),
                245, 250, 255, Math.round(edgeAlpha * 255.0F));
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void addRingQuad(BufferBuilder builder, Matrix4f matrix,
                                    float x0, float y0, float z0,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    int r, int g, int b, int a) {
        builder.addVertex(matrix, x0, y0, z0).setColor(r, g, b, a);
        builder.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        builder.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
        builder.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
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
