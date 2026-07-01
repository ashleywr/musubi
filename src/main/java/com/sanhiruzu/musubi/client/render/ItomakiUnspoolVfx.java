package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.MusubiClient;
import com.sanhiruzu.musubi.network.ItomakiUnspoolVfxPayload;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side VFX for Itomaki unspooling — threads flow from an Itomaki pedestal
 * toward nearby Spool Rack pegs, the reverse of the item-pickup silk pull.
 *
 * <p>On each frame, active effects are rendered with rope strands animating from
 * the itomaki source toward each target peg. The effect fades and expires based on
 * the unspool duration (default ~60 ticks per dispatch).
 */
public final class ItomakiUnspoolVfx {

    private static final long EFFECT_DURATION_TICKS = 60L; // How long each unspool wave lasts
    private static final ResourceLocation ROPE_TEX =
        ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "textures/block/shimenawa_rope.png");
    private static final RenderType ROPE_TYPE = RenderType.entityCutoutNoCull(ROPE_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    private static final Map<Long, ActiveUnspool> ACTIVE = new LinkedHashMap<>();

    private ItomakiUnspoolVfx() {}

    /** Register and begin rendering an unspool effect. Called from the client packet handler. */
    public static void accept(ItomakiUnspoolVfxPayload payload) {
        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        long startTick = payload.startTick() > 0L ? payload.startTick() : now;
        long key = System.nanoTime(); // Unique key for this effect instance
        ActiveUnspool effect = new ActiveUnspool(payload, startTick);
        ACTIVE.put(key, effect);
    }

    /** Render hook called from the event bus at AFTER_PARTICLES stage. */
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

        render(pose, bufferSource, camera, partial);
        bufferSource.endBatch();
        poseStack.popPose();
    }

    /** Render all active unspool effects. Called from a world render event. */
    private static void render(Matrix4f pose, MultiBufferSource buffers, Vec3 camera, float partialTick) {
        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        VertexConsumer vc = buffers.getBuffer(ROPE_TYPE);

        for (Iterator<Map.Entry<Long, ActiveUnspool>> it = ACTIVE.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, ActiveUnspool> entry = it.next();
            ActiveUnspool effect = entry.getValue();
            long age = now - effect.startTick();

            if (age > EFFECT_DURATION_TICKS) {
                it.remove();
                continue;
            }

            float progress = (age + partialTick) / EFFECT_DURATION_TICKS;
            float fade = 1.0f - Mth.clamp((progress - 0.85f) * 6.5f, 0.0f, 1.0f); // Fade out in last ~9% of duration

            if (fade > 0.01f) {
                ItomakiUnspoolVfxPayload payload = effect.payload();
                Vec3 source = new Vec3(payload.sourceX(), payload.sourceY(), payload.sourceZ());
                Vec3[] targets = new Vec3[payload.targetX().length];
                for (int i = 0; i < targets.length; i++) {
                    targets[i] = new Vec3(payload.targetX()[i], payload.targetY()[i], payload.targetZ()[i]);
                }
                renderThreadsToTargets(vc, pose, camera, source, targets, payload.itemId(), progress, fade);
            }
        }
    }

    private static void renderThreadsToTargets(VertexConsumer vc, Matrix4f pose, Vec3 camera,
                                                Vec3 source, Vec3[] targets, ResourceLocation itemId,
                                                float progress, float fade) {
        int itemColor = MusubiClient.getDominantColor(itemId);
        for (Vec3 target : targets) {
            renderRopeStrand(vc, pose, camera, source, target, itemColor, progress, fade);
        }
    }

    /**
     * Draws a rope strand from source to target, animating along progress (0→1).
     * Reuses the rope-drawing pattern from TamaCollectionVfx but simplified.
     */
    private static void renderRopeStrand(VertexConsumer vc, Matrix4f pose, Vec3 camera,
                                         Vec3 source, Vec3 target, int itemColor,
                                         float progress, float fade) {
        Vec3 delta = target.subtract(source);
        double distance = delta.length();
        if (distance < 0.001) return;

        delta = delta.normalize();

        // Subdivide the path into ~8 segments for smooth rope.
        int segments = 8;
        Vec3 previous = null;
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            // Progress animates from 0→1; a segment becomes visible when progress >= t.
            double fade_t = Mth.clamp((progress - t) * 2.5, 0.0, 1.0);
            if (fade_t < 0.001) {
                previous = null;
                continue;
            }

            Vec3 point = source.add(delta.scale(Math.sqrt(distance) * t));
            if (previous != null) {
                float alpha = (float) fade_t * fade * 0.8f;
                int color = blendColor(itemColor, 0xC8A864, (float) t);
                addRibbon(vc, pose, camera, previous, point, color, alpha, 0.012f);
            }
            previous = point;
        }
    }

    private static void addRibbon(VertexConsumer vc, Matrix4f pose, Vec3 camera,
                                  Vec3 start, Vec3 end, int color, float alpha, float width) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int a = (int) (alpha * 255);

        // Billboard the quad perpendicular to the camera direction.
        Vec3 toCamera = camera.subtract(start.add(end).scale(0.5));
        Vec3 direction = end.subtract(start).normalize();
        Vec3 side = toCamera.cross(direction).normalize().scale(width / 2.0);

        Vec3 c0 = start.add(side);
        Vec3 c1 = start.subtract(side);
        Vec3 c2 = end.subtract(side);
        Vec3 c3 = end.add(side);
        Vec3 normal = direction.cross(side);
        if (normal.lengthSqr() < 1.0e-6) {
            normal = direction;
        } else {
            normal = normal.normalize();
        }

        vc.addVertex(pose, (float) c0.x, (float) c0.y, (float) c0.z)
          .setColor(r, g, b, a).setUv(0, 0)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
          .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        vc.addVertex(pose, (float) c1.x, (float) c1.y, (float) c1.z)
          .setColor(r, g, b, a).setUv(1, 0)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
          .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        vc.addVertex(pose, (float) c2.x, (float) c2.y, (float) c2.z)
          .setColor(r, g, b, a).setUv(1, 1)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
          .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        vc.addVertex(pose, (float) c3.x, (float) c3.y, (float) c3.z)
          .setColor(r, g, b, a).setUv(0, 1)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT)
          .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static int blendColor(int c1, int c2, float t) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (r << 16) | (g << 8) | b;
    }

    private record ActiveUnspool(ItomakiUnspoolVfxPayload payload, long startTick) {}
}
