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
import com.sanhiruzu.musubi.network.CometVfxPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A comet crossing the sky — the Kimi no Na wa moment.
 *
 * <p>This is a celestial object, not an entity or block-entity renderer: entities
 * live in chunks and get distance-culled, while the comet must hang in the sky
 * dome for everyone at once. It renders in the {@code AFTER_SKY} stage as a
 * camera-anchored direction (like the sun and moon), so it never parallaxes and
 * every player sees it in the same part of the sky. Terrain, clouds, and weather
 * draw after this stage and naturally cover it near the horizon.</p>
 *
 * <p>Synchronization is free: the server sends one {@link CometVfxPayload} when
 * the crossing starts, and the comet's position is a pure function of the shared
 * game time from then on.</p>
 *
 * <p>Geometry: the head is three concentric camera-facing billboard quads
 * (white core, cyan glow, faint halo). The tail is a tapering ribbon whose
 * points sample the head's own earlier positions along the great-circle arc, so
 * it curves along the traversed path exactly like a real comet tail sweeping
 * the sky. Everything is additive so the comet only ever brightens the sky.</p>
 */
public final class CometVfx {

    /** Distance of the sky dome the comet is drawn on (vanilla sun/moon use 100). */
    private static final float SKY_RADIUS = 130.0F;
    private static final float CORE_RADIUS = 2.6F;
    private static final float GLOW_RADIUS = 6.5F;
    private static final float HALO_RADIUS = 13.0F;
    /** Fraction of the whole crossing the tail stretches back over. */
    private static final float TAIL_FRACTION = 0.12F;
    private static final int TAIL_SEGMENTS = 28;
    /** Fraction of the crossing spent fading in/out at the horizon ends. */
    private static final float EDGE_FADE = 0.06F;

    /** Active crossings, keyed by server event id. */
    private static final Map<Long, CometVfxPayload> ACTIVE = new LinkedHashMap<>();

    private CometVfx() {}

    public static void accept(ClientLevel level, CometVfxPayload payload) {
        ACTIVE.put(payload.eventId(), payload);
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (ACTIVE.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        // The comet belongs to the overworld sky; skip dimensions without one.
        if (level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            return;
        }

        long now = level.getGameTime();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);

        // At AFTER_SKY the RenderSystem modelview is still identity — the camera
        // rotation only exists in the event's model-view matrix, so bake it into
        // the vertices ourselves. No camera translation: this is a sky object.
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(event.getModelViewMatrix());
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder builder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;

        for (Iterator<CometVfxPayload> it = ACTIVE.values().iterator(); it.hasNext();) {
            CometVfxPayload comet = it.next();
            float progress = ((now - comet.startTick()) + partial) / Math.max(1.0F, comet.durationTicks());
            if (progress >= 1.0F) {
                it.remove();
                continue;
            }
            if (progress < 0.0F) continue; // start tick not reached yet (clock skew)
            appendComet(builder, matrix, comet, progress, now + partial);
            any = true;
        }

        if (any) {
            RenderSystem.enableBlend();
            // Additive: the comet is pure light against the sky.
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

            MeshData mesh = builder.build();
            if (mesh != null) {
                BufferUploader.drawWithShader(mesh);
            }

            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        } else {
            builder.build(); // release the buffer even when nothing was appended
        }

        poseStack.popPose();
    }

    private static void appendComet(BufferBuilder builder, Matrix4f matrix,
                                    CometVfxPayload comet, float progress, float time) {
        Vec3 start = direction(comet.startAzimuthDeg(), comet.startElevationDeg());
        Vec3 end = direction(comet.endAzimuthDeg(), comet.endElevationDeg());

        // Gentle fade at both horizon ends of the crossing.
        float envelope = smooth01(progress / EDGE_FADE) * smooth01((1.0F - progress) / EDGE_FADE);
        if (envelope <= 0.01F) return;
        float pulse = 1.0F + 0.05F * Mth.sin(time * 0.9F);

        Vec3 headDir = slerp(start, end, progress);
        Vec3 head = headDir.scale(SKY_RADIUS);

        // ── Tail ribbon: samples of where the head has already been ───────────
        float tailSpan = TAIL_FRACTION;
        Vec3 prev = head;
        Vec3 prevSide = null;
        for (int k = 1; k <= TAIL_SEGMENTS; k++) {
            float back = progress - (tailSpan * k / TAIL_SEGMENTS);
            Vec3 pos = slerp(start, end, Math.max(back, 0.0F)).scale(SKY_RADIUS);
            Vec3 seg = pos.subtract(prev);
            if (seg.lengthSqr() < 1.0E-6) break; // tail reached the entry point

            // Width perpendicular to both the path and the view ray -> a ribbon
            // that always faces the camera (which sits at the origin here).
            Vec3 side = seg.cross(prev).normalize();
            if (prevSide == null) prevSide = side;

            float t0 = (k - 1) / (float) TAIL_SEGMENTS;
            float t1 = k / (float) TAIL_SEGMENTS;
            float w0 = CORE_RADIUS * 0.9F * (1.0F - t0) * pulse;
            float w1 = CORE_RADIUS * 0.9F * (1.0F - t1) * pulse;
            float shimmer = 0.88F + 0.12F * Mth.sin(time * 0.7F + k * 1.35F);
            float a0 = envelope * shimmer * (float) Math.pow(1.0F - t0, 1.6) * 0.85F;
            float a1 = envelope * shimmer * (float) Math.pow(1.0F - t1, 1.6) * 0.85F;

            // Teal-blue gradient: near-white at the head, deep sky-blue at the tip.
            int r0 = (int) Mth.lerp(t0, 210, 110), g0 = (int) Mth.lerp(t0, 242, 190), b0 = 255;
            int r1 = (int) Mth.lerp(t1, 210, 110), g1 = (int) Mth.lerp(t1, 242, 190), b1 = 255;

            addRibbonSegment(builder, matrix, prev, pos, prevSide, side, w0, w1,
                r0, g0, b0, (int) (a0 * 255.0F), r1, g1, b1, (int) (a1 * 255.0F));

            prev = pos;
            prevSide = side;
        }

        // ── Head: three camera-facing billboards ──────────────────────────────
        Vec3 up = Math.abs(headDir.y) > 0.98 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 right = headDir.cross(up).normalize();
        Vec3 bUp = right.cross(headDir).normalize();

        addBillboard(builder, matrix, head, right, bUp, HALO_RADIUS * pulse, 130, 200, 255, (int) (envelope * 0.20F * 255));
        addBillboard(builder, matrix, head, right, bUp, GLOW_RADIUS * pulse, 170, 230, 255, (int) (envelope * 0.55F * 255));
        addBillboard(builder, matrix, head, right, bUp, CORE_RADIUS * pulse, 255, 255, 255, (int) (envelope * 255));
    }

    /** Direction from Minecraft-yaw azimuth and elevation, in degrees. */
    private static Vec3 direction(float azimuthDeg, float elevationDeg) {
        float az = azimuthDeg * Mth.DEG_TO_RAD;
        float el = elevationDeg * Mth.DEG_TO_RAD;
        float cosEl = Mth.cos(el);
        return new Vec3(-Mth.sin(az) * cosEl, Mth.sin(el), Mth.cos(az) * cosEl);
    }

    /** Great-circle interpolation between two sky directions. */
    private static Vec3 slerp(Vec3 a, Vec3 b, float t) {
        double dot = Mth.clamp(a.dot(b), -1.0, 1.0);
        double omega = Math.acos(dot);
        if (omega < 1.0E-4) return a;
        double so = Math.sin(omega);
        double f0 = Math.sin((1.0 - t) * omega) / so;
        double f1 = Math.sin(t * omega) / so;
        return new Vec3(
            a.x * f0 + b.x * f1,
            a.y * f0 + b.y * f1,
            a.z * f0 + b.z * f1
        ).normalize();
    }

    private static float smooth01(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** One tapered tail segment as two triangles, with per-end width, color, and alpha. */
    private static void addRibbonSegment(BufferBuilder builder, Matrix4f matrix,
                                         Vec3 p0, Vec3 p1, Vec3 side0, Vec3 side1,
                                         float w0, float w1,
                                         int r0, int g0, int b0, int a0,
                                         int r1, int g1, int b1, int a1) {
        Vec3 l0 = p0.add(side0.scale(w0)), r0v = p0.subtract(side0.scale(w0));
        Vec3 l1 = p1.add(side1.scale(w1)), r1v = p1.subtract(side1.scale(w1));
        vertex(builder, matrix, l0, r0, g0, b0, a0);
        vertex(builder, matrix, r0v, r0, g0, b0, a0);
        vertex(builder, matrix, l1, r1, g1, b1, a1);
        vertex(builder, matrix, r0v, r0, g0, b0, a0);
        vertex(builder, matrix, r1v, r1, g1, b1, a1);
        vertex(builder, matrix, l1, r1, g1, b1, a1);
    }

    /** A camera-facing quad (two triangles) with a bright center fading to the rim. */
    private static void addBillboard(BufferBuilder builder, Matrix4f matrix, Vec3 center,
                                     Vec3 right, Vec3 up, float radius, int r, int g, int b, int a) {
        if (a <= 1) return;
        Vec3 rr = right.scale(radius), uu = up.scale(radius);
        Vec3[] rim = {
            center.subtract(rr).subtract(uu),
            center.add(rr).subtract(uu),
            center.add(rr).add(uu),
            center.subtract(rr).add(uu),
        };
        // Fan from the bright center so the quad reads as a radial glow.
        for (int i = 0; i < 4; i++) {
            vertex(builder, matrix, center, r, g, b, a);
            vertex(builder, matrix, rim[i], r, g, b, 0);
            vertex(builder, matrix, rim[(i + 1) % 4], r, g, b, 0);
        }
    }

    private static void vertex(BufferBuilder builder, Matrix4f matrix, Vec3 p,
                               int r, int g, int b, int a) {
        builder.addVertex(matrix, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }
}
