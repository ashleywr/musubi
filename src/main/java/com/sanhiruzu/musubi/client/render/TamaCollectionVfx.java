package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.MusubiClient;
import com.sanhiruzu.musubi.curio.MusubiCurios;
import com.sanhiruzu.musubi.network.TamaBindVfxPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Client-side strand animation for Tama collection.
 */
public final class TamaCollectionVfx {

    private static final long REPLAY_CACHE_TICKS = 180L;
    private static final int MAX_CATCH_UP_TICKS = 10;
    private static final Map<Long, ActiveEffect> ACTIVE = new LinkedHashMap<>();
    private static final Map<Long, Long> RECENT = new LinkedHashMap<>();
    private static final ResourceLocation ROPE_TEX =
        ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "textures/block/shimenawa_rope.png");
    private static final ResourceLocation SHIDE_TEX =
        ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "textures/block/shimenawa_shide.png");
    private static final RenderType ROPE_TYPE = RenderType.entityCutoutNoCull(ROPE_TEX);
    private static final RenderType SHIDE_TYPE = RenderType.entityTranslucentCull(SHIDE_TEX);
    private static final int FULL_BRIGHT = 0xF000F0;

    private TamaCollectionVfx() {}

    public static void accept(ClientLevel level, TamaBindVfxPayload payload) {
        long now = level != null ? level.getGameTime() : 0L;
        purgeRecent(now);
        Long replayUntil = RECENT.get(payload.eventId());
        if (replayUntil != null && replayUntil > now) {
            return;
        }
        RECENT.put(payload.eventId(), now + REPLAY_CACHE_TICKS);

        long startTick = payload.startTick() > 0L ? payload.startTick() : now;
        long delay = Math.max(0L, now - startTick);
        if (delay > MAX_CATCH_UP_TICKS) {
            startTick = now - MAX_CATCH_UP_TICKS;
        }
        MusubiClient.ItemColorPalette palette = MusubiClient.sampleItemPalette(payload.itemId());
        ActiveEffect effect = new ActiveEffect(payload, startTick, palette);
        ActiveEffect previous = ACTIVE.put(payload.eventId(), effect);
        if (level != null && payload.hideSourceEntity() && previous == null) {
            spawnSourceBurst(level, payload, palette);
        }
    }

    public static boolean shouldHideItemEntity(int entityId) {
        purgeExpired();
        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        for (ActiveEffect effect : ACTIVE.values()) {
            if (effect.hideSourceEntity() && effect.sourceEntityId() == entityId && !effect.expired(now)) {
                return true;
            }
        }
        return false;
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || ACTIVE.isEmpty()) {
            return;
        }

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long now = level.getGameTime();
        Vec3 camera = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f pose = poseStack.last().pose();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        boolean renderedRope = renderPass(level, now, partial, camera, pose, bufferSource, ROPE_TYPE, true, false);
        boolean renderedShide = renderPass(level, now, partial, camera, pose, bufferSource, SHIDE_TYPE, false, true);

        if (renderedRope) {
            bufferSource.endBatch(ROPE_TYPE);
        }
        if (renderedShide) {
            bufferSource.endBatch(SHIDE_TYPE);
        }
        poseStack.popPose();
    }

    private static boolean renderPass(ClientLevel level, long now, float partial, Vec3 camera,
                                      Matrix4f pose, MultiBufferSource.BufferSource bufferSource,
                                      RenderType renderType, boolean drawRope, boolean drawShide) {
        boolean rendered = false;
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        for (Iterator<Map.Entry<Long, ActiveEffect>> it = ACTIVE.entrySet().iterator(); it.hasNext();) {
            ActiveEffect effect = it.next().getValue();
            StackStyle style = stackStyle(effect.stackCount());
            Vec3 source = resolveSource(level, effect, partial);
            Vec3 target = resolveTarget(level, effect, partial);
            effect.refreshLifetime(source, target, style);
            if (effect.expired(now)) {
                if (drawRope) {
                    it.remove();
                }
                continue;
            }
            rendered |= renderEffect(level, effect, partial, camera, pose, consumer, drawRope, drawShide,
                source, target, style);
        }
        return rendered;
    }

    private static boolean renderEffect(ClientLevel level, ActiveEffect effect, float partial,
                                        Vec3 camera, Matrix4f pose, VertexConsumer consumer,
                                        boolean drawRope, boolean drawShide,
                                        Vec3 source, Vec3 target, StackStyle style) {
        StrandData strands = buildStrands(level, effect, source, target, partial);
        if (strands == null) {
            return false;
        }

        EffectColors colors = effectColors(effect.palette(), style);
        boolean rendered = false;
        if (drawRope && strands.mainFade() > 0.001f) {
            drawRopeStrands(pose, consumer, camera, strands, colors, strands.thicknessScale(), strands.seed(), style);
            rendered = true;
        }
        if (drawRope && strands.tailFade() > 0.001f) {
            drawTailWisps(pose, consumer, camera, strands, colors, strands.thicknessScale(), strands.seed(), style);
            rendered = true;
        }
        if (drawShide) {
            drawShideFray(pose, consumer, camera, strands.source(), strands.target(), strands.mainProgress(),
                colors.paperColor(), strands.mainFade(), strands.tailFade(), style);
            rendered = true;
        }
        return rendered;
    }

    private static void drawRopeStrands(Matrix4f pose, VertexConsumer consumer, Vec3 camera,
                                        StrandData strands, EffectColors colors,
                                        float thicknessScale, long seed, StackStyle style) {
        float burst = openingBurst(strands.mainProgress());
        drawRopeStrand(pose, consumer, camera, strands.strand0(), colors.baseColor(), colors.ropeOuter(),
            1.0f, strands.mainProgress(), 0.020f, 0.009f, thicknessScale, burst, seed, 0);
        drawRopeStrand(pose, consumer, camera, strands.strand1(), colors.baseColor(), colors.ropeInner(),
            0.92f, strands.mainProgress(), 0.016f, 0.007f, thicknessScale, burst, seed, 1);
        if (style.ropeStrands() > 2) {
            drawRopeStrand(pose, consumer, camera, strands.strand2(), colors.accentColor(), colors.ropeAccent(),
                0.76f, strands.mainProgress(), 0.013f, 0.0055f, thicknessScale, burst, seed, 2);
        }
        if (style.ropeStrands() > 3) {
            int shimmer = blendColor(colors.sheenColor(), colors.paperColor(), 0.55f);
            drawRopeStrand(pose, consumer, camera, strands.strand3(), shimmer, colors.sheenColor(),
                0.62f, strands.mainProgress(), 0.010f, 0.0045f, thicknessScale, burst, seed, 3);
        }
    }

    private static void drawRopeStrand(Matrix4f pose, VertexConsumer consumer, Vec3 camera,
                                       Vec3[] points, int itemColor, int strandColor,
                                       float alphaScale, double progress, float width,
                                       float innerWidth, float thicknessScale, float openingBurst, long seed,
                                       int strandIndex) {
        Vec3 previous = null;
        for (int i = 0; i < points.length; i++) {
            Vec3 point = points[i];
            if (point == null) {
                previous = null;
                continue;
            }
            if (previous != null) {
                double t = i / (double) (points.length - 1);
                double fade = 1.0 - Mth.clamp(Math.abs(t - progress) * 1.9, 0.0, 1.0);
                float burstBoost = 1.0f + openingBurst * 0.45f;
                float alpha = (float) Mth.clamp((0.24 + 0.76 * fade) * alphaScale * burstBoost, 0.0, 1.0);
                if (alpha > 0.0f) {
                    float jitter = widthJitter(seed, strandIndex, t) * (0.92f + openingBurst * 0.10f);
                    int outerColor = blendColor(strandColor, 0xFFFFFF, (float) (0.22 + 0.60 * t));
                    int innerColor = blendColor(itemColor, 0xFFFFFF, (float) (0.14 + 0.76 * t));
                    addRibbon(consumer, pose, camera, previous, point, outerColor,
                        alpha, width * thicknessScale * jitter * (0.96f + openingBurst * 0.18f), true);
                    addRibbon(consumer, pose, camera, previous, point, innerColor,
                        alpha * 0.72f, innerWidth * thicknessScale
                            * (0.88f + (jitter - 1.0f) * 0.6f + openingBurst * 0.12f), true);
                }
            }
            previous = point;
        }
    }

    private static void drawShideFray(Matrix4f pose, VertexConsumer consumer, Vec3 camera,
                                      Vec3 source, Vec3 target, double progress, int color,
                                      float sourceFade, float targetFade, StackStyle style) {
        Vec3 delta = target.subtract(source);
        double distance = delta.length();
        if (distance < 0.001) {
            return;
        }

        Vec3 dir = delta.scale(1.0 / distance);
        Vec3 side = arbitraryPerpendicular(dir);
        Vec3 lift = dir.cross(side);
        if (lift.lengthSqr() < 1.0e-6) {
            lift = arbitraryPerpendicular(side);
        } else {
            lift = lift.normalize();
        }

        float sourceAlpha = (float) Mth.clamp((1.0 - progress * 1.35) * sourceFade * style.paperStrength(), 0.0, 1.0);
        float targetAlpha = (float) Mth.clamp((progress - 0.28) * 1.6 * (0.35f + 0.65f * targetFade) * style.paperStrength(), 0.0, 1.0);
        float sourceWidth = (0.015f + (float) (0.007 * (1.0 - progress))) * style.paperWidth();
        float targetWidth = (0.010f + (float) (0.005 * progress)) * style.paperWidth();
        double pulse = Math.sin((progress * Math.PI * 5.0) + distance * 0.35);
        double sway = Math.cos((progress * Math.PI * 6.0) + distance * 0.21);

        int lanes = Math.max(1, style.paperLanes());
        double center = (lanes - 1) * 0.5;
        for (int i = 0; i < lanes; i++) {
            double laneBias = i - center;
            double spread = laneBias * (0.020 + 0.003 * lanes);
            double curl = pulse + laneBias * 1.55;
            float laneSourceAlpha = sourceAlpha * (0.84f + 0.10f * (float) (1.0 - Math.abs(laneBias) / (center + 1.0)));
            float laneTargetAlpha = targetAlpha * (0.78f + 0.12f * (float) (1.0 - Math.abs(laneBias) / (center + 1.0)));

            Vec3 sourceStart = source
                .add(side.scale(spread))
                .add(lift.scale(Math.sin(curl) * (0.010 + 0.0015 * lanes)));
            Vec3 sourceEnd = source
                .add(dir.scale(0.06 + i * 0.016 + progress * (0.03 + 0.003 * lanes)))
                .add(side.scale(spread * 0.40 + sway * 0.005))
                .add(lift.scale(Math.cos(curl) * (0.012 + 0.001 * lanes)));

            addRibbon(consumer, pose, camera, sourceStart, sourceEnd, color,
                laneSourceAlpha * (0.76f + i * 0.06f), sourceWidth + i * 0.0016f, false);

            Vec3 targetStart = target
                .subtract(dir.scale(0.06 + i * 0.014))
                .add(side.scale(spread * 0.32))
                .add(lift.scale(Math.sin(curl + 1.4) * (0.010 + 0.001 * lanes)));
            Vec3 targetEnd = target
                .add(side.scale(spread * 0.12))
                .add(lift.scale(Math.cos(curl + 1.4) * (0.010 + 0.0008 * lanes)));

            addRibbon(consumer, pose, camera, targetStart, targetEnd, color,
                laneTargetAlpha * (0.60f + i * 0.05f), targetWidth + i * 0.0012f, false);
        }
    }

    private static void drawTailWisps(Matrix4f pose, VertexConsumer consumer, Vec3 camera,
                                      StrandData strands, EffectColors colors,
                                      float thicknessScale, long seed, StackStyle style) {
        Vec3 source = strands.source();
        Vec3 target = strands.target();
        Vec3 delta = target.subtract(source);
        double distance = delta.length();
        if (distance < 0.001) {
            return;
        }

        Vec3 dir = delta.scale(1.0 / distance);
        Vec3 side = arbitraryPerpendicular(dir);
        Vec3 lift = dir.cross(side);
        if (lift.lengthSqr() < 1.0e-6) {
            lift = arbitraryPerpendicular(side);
        } else {
            lift = lift.normalize();
        }

        double tailProgress = strands.tailProgress();
        double tailFade = strands.tailFade();
        float alpha = (float) Mth.clamp((0.22 + 0.78 * tailFade) * style.tailStrength(), 0.0, 1.0);
        int tailColor = blendColor(colors.paperColor(), colors.sheenColor(), style.whiteMix());
        float baseWidth = 0.0095f * thicknessScale * style.tailWidth();
        float innerWidth = 0.0045f * thicknessScale * style.tailWidth();

        int lanes = Math.max(1, style.tailLanes());
        double center = (lanes - 1) * 0.5;
        for (int lane = 0; lane < lanes; lane++) {
            double laneBias = lane - center;
            float laneAlpha = alpha * (0.90f - 0.10f * (float) Math.abs(laneBias));
            float laneWidth = baseWidth * (0.92f - 0.08f * (float) Math.abs(laneBias));
            float laneInnerWidth = innerWidth * (0.90f - 0.07f * (float) Math.abs(laneBias));
            drawTailWispRibbon(pose, consumer, camera, source, target, dir, side, lift,
                tailProgress, seed, lane, tailColor, laneAlpha, laneWidth, laneInnerWidth, tailFade);
        }

        float collapse = (float) smoothstep((tailProgress - 0.72) / 0.28);
        if (collapse > 0.001f) {
            drawTailCollapseWisps(pose, consumer, camera, source, target, dir, side, lift,
                tailProgress, seed, tailColor, alpha, baseWidth, innerWidth, tailFade, collapse);
        }
    }

    private static void drawTailWispRibbon(Matrix4f pose, VertexConsumer consumer, Vec3 camera,
                                           Vec3 source, Vec3 target, Vec3 dir, Vec3 side, Vec3 lift,
                                           double tailProgress, long seed, int lane, int color,
                                           float alpha, float width, float innerWidth, double tailFade) {
        double lead = lane == 0 ? 0.22 - 0.10 * tailProgress : 0.16 - 0.06 * tailProgress;
        Vec3 start = source.add(target.subtract(source).scale(1.0 - lead));
        double curl = (seed * 0.0017) + lane * 1.9 + tailProgress * Math.PI * 6.0;
        Vec3 bentStart = start
            .add(side.scale(Math.cos(curl) * (0.018 * tailFade)))
            .add(lift.scale(Math.sin(curl) * (0.014 * tailFade)));
        Vec3 bentEnd = target
            .add(side.scale(Math.cos(curl + 1.0) * (0.010 * tailFade)))
            .add(lift.scale(Math.sin(curl + 1.0) * (0.008 * tailFade)));
        float w = width * (0.86f + 0.18f * widthJitter(seed, lane, tailProgress));
        float iw = innerWidth * (0.90f + 0.12f * widthJitter(seed + 17L, lane, tailProgress));
        addRibbon(consumer, pose, camera, bentStart, bentEnd, color, alpha, w, true);
        addRibbon(consumer, pose, camera, bentStart, bentEnd, blendColor(color, 0xFFFFFF, 0.24f), alpha * 0.82f, iw, true);
    }

    private static void drawTailCollapseWisps(Matrix4f pose, VertexConsumer consumer, Vec3 camera,
                                              Vec3 source, Vec3 target, Vec3 dir, Vec3 side, Vec3 lift,
                                              double tailProgress, long seed, int color, float alpha,
                                              float baseWidth, float innerWidth, double tailFade,
                                              float collapse) {
        float collapsedAlpha = alpha * collapse;
        float collapsedWidth = baseWidth * (0.56f - 0.18f * collapse);
        float collapsedInner = innerWidth * (0.52f - 0.14f * collapse);
        int silkColor = blendColor(color, 0xFFFFFF, 0.36f);
        for (int lane = 0; lane < 2; lane++) {
            double laneBias = lane == 0 ? -0.5 : 0.5;
            double curl = (seed * 0.0019) + laneBias * 2.3 + tailProgress * Math.PI * 7.0;
            Vec3 anchor = source.add(target.subtract(source).scale(0.72 + 0.12 * collapse + lane * 0.05));
            Vec3 start = anchor
                .add(side.scale(laneBias * (0.010 + 0.004 * collapse)))
                .add(lift.scale(Math.sin(curl) * (0.010 * tailFade)));
            Vec3 end = target
                .add(side.scale(laneBias * 0.004))
                .add(lift.scale(Math.cos(curl + 0.8) * (0.004 * tailFade)));
            float laneAlpha = collapsedAlpha * (0.88f - 0.12f * (float) Math.abs(laneBias));
            float laneWidth = collapsedWidth * (0.88f - 0.10f * (float) Math.abs(laneBias));
            float laneInnerWidth = collapsedInner * (0.88f - 0.10f * (float) Math.abs(laneBias));
            addRibbon(consumer, pose, camera, start, end, silkColor, laneAlpha, laneWidth, true);
            addRibbon(consumer, pose, camera, start, end, blendColor(silkColor, 0xFFFFFF, 0.28f),
                laneAlpha * 0.84f, laneInnerWidth, true);
        }
    }

    private static StrandData buildStrands(ClientLevel level, ActiveEffect effect, Vec3 source,
                                           Vec3 target, float partial) {
        Vec3 delta = target.subtract(source);
        double distance = delta.length();
        if (distance < 0.001) {
            return null;
        }

        StackStyle style = stackStyle(effect.stackCount());
        double age = Math.max(0.0, (level.getGameTime() - effect.startTick()) + partial);
        double burst = openingBurst(Mth.clamp(age / Math.max(1.0, effect.durationTicks() + 2.0), 0.0, 1.0));
        double distanceDrive = Math.max(0.0, distance - 1.5);
        double mainDuration = Math.max(
            Math.max(1.0, effect.durationTicks()),
            6.0 + distance * 2.1 + distanceDrive * 0.35 + Math.min(6.0, effect.stackCount() * 0.10)
        );
        double tailDuration = Math.max(
            5.0,
            3.0 + distance * 0.22 + Math.min(6.0, effect.stackCount() / 10.0) + distanceDrive * 0.12
        ) * style.tailDuration();
        double mainProgress = Mth.clamp(age / mainDuration, 0.0, 1.0);
        double tailProgress = age <= mainDuration ? 0.0 : Mth.clamp((age - mainDuration) / tailDuration, 0.0, 1.0);
        float mainFade = (float) (1.0 - smoothstep(tailProgress));
        float tailFade = (float) smoothstep(tailProgress);
        double twist = level.getGameTime() * 0.5 + effect.sourceEntityId() * 0.31;
        float thicknessScale = thicknessScale(effect.stackCount()) * style.thicknessScale();
        long seed = effectSeed(effect);

        Vec3 control1 = source.add(delta.scale(0.20 + burst * 0.08)).add(0.0, 0.36 + distance * 0.05 + burst * 0.16, 0.0);
        Vec3 control2 = target.subtract(delta.scale(0.18 + burst * 0.04)).add(0.0, 0.14 + distance * 0.02 + burst * 0.08, 0.0);

        int samples = Math.max(10, (int) Math.ceil(distance * 9.0 * style.sampleDensity()));
        Vec3[] strand0 = new Vec3[samples + 1];
        Vec3[] strand1 = new Vec3[samples + 1];
        Vec3[] strand2 = new Vec3[samples + 1];
        Vec3[] strand3 = new Vec3[samples + 1];
        for (int i = 0; i <= samples; i++) {
            double t = i / (double) samples;
            double strandAlpha = Mth.clamp((0.12 + mainProgress * 0.88 - t) * 4.75 + 0.20, 0.0, 1.0);
            if (strandAlpha <= 0.0) {
                continue;
            }

            Vec3 core = cubicBezier(source, control1, control2, target, t);
            Vec3 tangent = cubicBezierTangent(source, control1, control2, target, t);
            Vec3 side = arbitraryPerpendicular(tangent);
            Vec3 lift = tangent.cross(side);
            if (lift.lengthSqr() < 1.0e-6) {
                lift = arbitraryPerpendicular(side);
            } else {
                lift = lift.normalize();
            }

            double burstEdge = burst * (1.0 - t);
            double taper = (0.042 + 0.020 * (1.0 - t)) * (0.92 + burst * 0.72);
            double flow = twist + t * Math.PI * (6.0 + burst * 1.2);
            double flowOffset = Math.sin(flow * 1.35 + burst * 0.7) * taper * (0.32 + burst * 0.22);
            Vec3 wobble = side.scale(Math.cos(flow) * taper * (0.88 + burst * 0.25))
                .add(lift.scale((Math.sin(flow) * taper + flowOffset) * (0.90 + burst * 0.20)));
            if (burstEdge > 0.001) {
                wobble = wobble
                    .add(side.scale(Math.sin(flow * 0.5 + burst) * taper * 0.22 * burstEdge))
                    .add(lift.scale(Math.cos(flow * 0.5 + burst) * taper * 0.16 * burstEdge));
            }

            strand0[i] = core.add(wobble);
            strand1[i] = core.add(side.scale(Math.cos(flow + (Math.PI * 2.0 / 3.0)) * taper)
                .add(lift.scale(Math.sin(flow + (Math.PI * 2.0 / 3.0)) * taper)));
            strand2[i] = core.add(side.scale(Math.cos(flow + (Math.PI * 4.0 / 3.0)) * taper)
                .add(lift.scale(Math.sin(flow + (Math.PI * 4.0 / 3.0)) * taper)));
            strand3[i] = core.add(side.scale(Math.cos(flow + Math.PI) * (taper * 0.72))
                .add(lift.scale(Math.sin(flow + Math.PI) * (taper * 0.72))));
        }

        return new StrandData(source, target, mainProgress, tailProgress, mainFade, tailFade,
            strand0, strand1, strand2, strand3, thicknessScale, seed);
    }

    private static void addRibbon(VertexConsumer consumer, Matrix4f pose, Vec3 camera,
                                  Vec3 a, Vec3 b, int color, float alpha, float width,
                                  boolean glow) {
        Vec3 tangent = b.subtract(a);
        double length = tangent.length();
        if (length < 1.0e-6) {
            return;
        }

        Vec3 dir = tangent.scale(1.0 / length);
        Vec3 mid = a.add(b).scale(0.5);
        Vec3 view = camera.subtract(mid);
        Vec3 side = dir.cross(view);
        if (side.lengthSqr() < 1.0e-6) {
            side = arbitraryPerpendicular(dir);
        } else {
            side = side.normalize();
        }
        Vec3 offset = side.scale(width);
        Vec3 p0 = a.add(offset);
        Vec3 p1 = a.subtract(offset);
        Vec3 p2 = b.subtract(offset);
        Vec3 p3 = b.add(offset);

        int a255 = Mth.clamp(Math.round(alpha * 255.0f), 0, 255);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int bl = color & 0xFF;
        int light = glow ? FULL_BRIGHT : 0xF000F0;

        addQuad(consumer, pose, p0, p1, p2, p3, r, g, bl, a255, light, side);
        addQuad(consumer, pose, p3, p2, p1, p0, r, g, bl, a255, light, side.scale(-1.0));
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f pose, Vec3 p0, Vec3 p1,
                                Vec3 p2, Vec3 p3, int r, int g, int bl, int a, int light,
                                Vec3 normal) {
        consumer.addVertex(pose, (float) p0.x, (float) p0.y, (float) p0.z)
            .setColor(r, g, bl, a)
            .setUv(0.0f, 0.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) p1.x, (float) p1.y, (float) p1.z)
            .setColor(r, g, bl, a)
            .setUv(1.0f, 0.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) p2.x, (float) p2.y, (float) p2.z)
            .setColor(r, g, bl, a)
            .setUv(1.0f, 1.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
        consumer.addVertex(pose, (float) p3.x, (float) p3.y, (float) p3.z)
            .setColor(r, g, bl, a)
            .setUv(0.0f, 1.0f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal((float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static Vec3 resolveSource(ClientLevel level, ActiveEffect effect, float partial) {
        if (!effect.hideSourceEntity() && effect.sourceEntityId() >= 0) {
            Entity entity = level.getEntity(effect.sourceEntityId());
            if (entity != null && entity.isAlive()) {
                return interpolate(entity, partial).add(0.0, 0.08, 0.0);
            }
        }
        return new Vec3(effect.sourceX(), effect.sourceY(), effect.sourceZ());
    }

    private static Vec3 resolveTarget(ClientLevel level, ActiveEffect effect, float partial) {
        Entity entity = effect.targetEntityId() >= 0 ? level.getEntity(effect.targetEntityId()) : null;
        if (entity instanceof Player player && player.isAlive()) {
            return MusubiCurios.beltAnchor(player);
        }
        if (entity != null && entity.isAlive()) {
            return interpolate(entity, partial).add(0.0, 0.18, 0.0);
        }
        return new Vec3(effect.targetX(), effect.targetY(), effect.targetZ());
    }

    private static Vec3 interpolate(Entity entity, float partial) {
        return new Vec3(
            Mth.lerp(partial, entity.xo, entity.getX()),
            Mth.lerp(partial, entity.yo, entity.getY()),
            Mth.lerp(partial, entity.zo, entity.getZ())
        );
    }

    private static Vec3 cubicBezier(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double inv = 1.0 - t;
        double inv2 = inv * inv;
        double inv3 = inv2 * inv;
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(
            p0.x * inv3 + 3.0 * p1.x * inv2 * t + 3.0 * p2.x * inv * t2 + p3.x * t3,
            p0.y * inv3 + 3.0 * p1.y * inv2 * t + 3.0 * p2.y * inv * t2 + p3.y * t3,
            p0.z * inv3 + 3.0 * p1.z * inv2 * t + 3.0 * p2.z * inv * t2 + p3.z * t3
        );
    }

    private static Vec3 cubicBezierTangent(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double inv = 1.0 - t;
        Vec3 derivative = new Vec3(
            3.0 * (p1.x - p0.x) * inv * inv + 6.0 * (p2.x - p1.x) * inv * t + 3.0 * (p3.x - p2.x) * t * t,
            3.0 * (p1.y - p0.y) * inv * inv + 6.0 * (p2.y - p1.y) * inv * t + 3.0 * (p3.y - p2.y) * t * t,
            3.0 * (p1.z - p0.z) * inv * inv + 6.0 * (p2.z - p1.z) * inv * t + 3.0 * (p3.z - p2.z) * t * t
        );
        return derivative.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 1.0, 0.0) : derivative.normalize();
    }

    private static Vec3 arbitraryPerpendicular(Vec3 dir) {
        Vec3 up = Math.abs(dir.y) > 0.85 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = dir.cross(up);
        if (side.lengthSqr() < 1.0e-6) {
            side = dir.cross(new Vec3(0.0, 0.0, 1.0));
        }
        return side.normalize();
    }

    private static int blendColor(int from, int to, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }

    private static void spawnSourceBurst(ClientLevel level, TamaBindVfxPayload payload,
                                         MusubiClient.ItemColorPalette palette) {
        Vec3 source = new Vec3(payload.sourceX(), payload.sourceY(), payload.sourceZ());
        int baseColor = palette.base();
        int accentColor = palette.accent();
        int sheenColor = palette.sheen();
        DustParticleOptions dust = new DustParticleOptions(unpackRgb(blendColor(baseColor, sheenColor, 0.28f)), 0.9f);
        DustParticleOptions baseDust = new DustParticleOptions(unpackRgb(baseColor), 0.82f);
        DustParticleOptions accentDust = new DustParticleOptions(unpackRgb(accentColor), 0.78f);
        DustParticleOptions whiteDust = new DustParticleOptions(unpackRgb(sheenColor), 0.72f);
        int stackCount = Math.max(1, payload.stackCount());
        int motes = 10 + Math.min(18, (int) Math.ceil(Math.sqrt(stackCount) * 2.0));

        level.addParticle(dust, source.x, source.y, source.z, 0.0, 0.02, 0.0);
        level.addParticle(ParticleTypes.END_ROD, source.x, source.y, source.z, 0.0, 0.01, 0.0);
        level.addParticle(ParticleTypes.ENCHANTED_HIT, source.x, source.y, source.z, 0.0, 0.01, 0.0);
        for (int i = 0; i < motes; i++) {
            double angle = (Math.PI * 2.0 * i) / motes;
            double radius = 0.03 + (i % 3) * 0.01 + Math.min(0.04, stackCount * 0.0005);
            double vx = Math.cos(angle) * radius;
            double vy = 0.02 + (i % 2) * 0.008;
            double vz = Math.sin(angle) * radius;
            level.addParticle(dust, source.x, source.y, source.z, vx, vy, vz);
            level.addParticle((i % 3 == 0) ? accentDust : ((i & 1) == 0 ? baseDust : whiteDust),
                source.x, source.y, source.z,
                vx * (i % 3 == 0 ? 0.8 : 0.6),
                vy * (i % 3 == 0 ? 0.7 : 0.5),
                vz * (i % 3 == 0 ? 0.8 : 0.6));
        }
    }

    private static Vector3f unpackRgb(int packed) {
        return new Vector3f(
            ((packed >> 16) & 0xFF) / 255f,
            ((packed >> 8) & 0xFF) / 255f,
            (packed & 0xFF) / 255f
        );
    }

    private static void purgeExpired() {
        long now = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
        purgeRecent(now);
        ACTIVE.values().removeIf(effect -> effect.expired(now));
    }

    private static void purgeRecent(long now) {
        RECENT.entrySet().removeIf(entry -> entry.getValue() <= now);
    }

    private static final class ActiveEffect {
        private final long eventId;
        private final TamaBindVfxPayload payload;
        private final long startTick;
        private final MusubiClient.ItemColorPalette palette;
        private long expireAtTick;

        private ActiveEffect(TamaBindVfxPayload payload, long startTick, MusubiClient.ItemColorPalette palette) {
            this.eventId = payload.eventId();
            this.payload = payload;
            this.startTick = startTick;
            this.palette = palette;
            this.expireAtTick = startTick + Math.max(1, durationTicks()) + tailTicks() + 2L;
        }

        private long eventId() { return eventId; }
        private int sourceEntityId() { return payload.sourceEntityId(); }
        private int targetEntityId() { return payload.targetEntityId(); }
        private double sourceX() { return payload.sourceX(); }
        private double sourceY() { return payload.sourceY(); }
        private double sourceZ() { return payload.sourceZ(); }
        private double targetX() { return payload.targetX(); }
        private double targetY() { return payload.targetY(); }
        private double targetZ() { return payload.targetZ(); }
        private int stackCount() { return payload.stackCount(); }
        private int durationTicks() { return payload.durationTicks(); }
        private boolean hideSourceEntity() { return payload.hideSourceEntity(); }
        private long startTick() { return startTick; }
        private MusubiClient.ItemColorPalette palette() { return palette; }
        private void refreshLifetime(Vec3 source, Vec3 target, StackStyle style) {
            double distance = source.distanceTo(target);
            double distanceDrive = Math.max(0.0, distance - 1.5);
            double mainDuration = Math.max(
                Math.max(1.0, durationTicks()),
                6.0 + distance * 2.1 + distanceDrive * 0.35 + Math.min(6.0, stackCount() * 0.10)
            );
            double tailDuration = Math.max(
                5.0,
                3.0 + distance * 0.22 + Math.min(6.0, stackCount() / 10.0) + distanceDrive * 0.12
            ) * style.tailDuration();
            long candidate = startTick + (long) Math.ceil(mainDuration + tailDuration + 2.0);
            if (candidate > expireAtTick) {
                expireAtTick = candidate;
            }
        }
        private boolean expired(long now) {
            return now > expireAtTick;
        }
        private int tailTicks() {
            return 6 + Math.min(6, stackCount() / 12);
        }
    }

    private record EffectColors(int baseColor, int accentColor, int sheenColor,
                                int ropeOuter, int ropeInner, int ropeAccent, int paperColor) {}

    private record StrandData(Vec3 source, Vec3 target, double mainProgress, double tailProgress,
                              float mainFade, float tailFade,
                              Vec3[] strand0, Vec3[] strand1, Vec3[] strand2, Vec3[] strand3,
                              float thicknessScale, long seed) {}

    private static EffectColors effectColors(MusubiClient.ItemColorPalette palette, StackStyle style) {
        int baseColor = palette.base();
        int accentColor = palette.accent();
        int sheenColor = palette.sheen();
        int silkWhite = 0xF8F1E5;
        int silkIvory = 0xEFE7D8;
        float whiteMix = style.whiteMix();
        return new EffectColors(
            baseColor,
            accentColor,
            sheenColor,
            blendColor(silkIvory, silkWhite, 0.65f + whiteMix * 0.18f),
            blendColor(baseColor, sheenColor, 0.20f + whiteMix * 0.14f),
            blendColor(accentColor, silkIvory, 0.30f - whiteMix * 0.08f),
            blendColor(sheenColor, baseColor, 0.14f + whiteMix * 0.10f)
        );
    }

    private static float thicknessScale(int stackCount) {
        int clamped = Math.max(1, stackCount);
        float scale = 0.88f + (float) Math.min(0.28, Math.log1p(clamped) * 0.07);
        return Mth.clamp(scale, 0.78f, 1.18f);
    }

    private static long effectSeed(ActiveEffect effect) {
        long seed = 1469598103934665603L;
        seed ^= effect.eventId();
        seed *= 1099511628211L;
        seed ^= effect.startTick();
        seed *= 1099511628211L;
        seed ^= effect.sourceEntityId();
        seed *= 1099511628211L;
        seed ^= effect.stackCount();
        seed *= 1099511628211L;
        return seed;
    }

    private static float widthJitter(long seed, int lane, double t) {
        double wave = Math.sin(seed * 0.00017 + lane * 1.93 + t * Math.PI * 8.0);
        return (float) Mth.clamp(0.92 + wave * 0.10, 0.80, 1.14);
    }

    private static float openingBurst(double mainProgress) {
        return (float) (1.0 - smoothstep(mainProgress / 0.24));
    }

    private static double smoothstep(double t) {
        t = Mth.clamp(t, 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static StackStyle stackStyle(int stackCount) {
        if (stackCount <= 4) {
            return new StackStyle(0.80f, 0.86f, 0.88f, 0.50f, 0.86f, 0.80f, 0.82f, 0.88f, 2, 1, 2);
        }
        if (stackCount <= 16) {
            return new StackStyle(0.96f, 1.00f, 1.00f, 0.34f, 0.98f, 0.92f, 0.96f, 0.98f, 3, 2, 3);
        }
        return new StackStyle(1.08f, 1.10f, 1.08f, 0.26f, 1.06f, 1.00f, 1.04f, 1.04f, 4, 3, 4);
    }

    private record StackStyle(float thicknessScale, float tailDuration, float sampleDensity,
                              float whiteMix, float tailStrength, float tailWidth,
                              float paperStrength, float paperWidth, int ropeStrands,
                              int tailLanes, int paperLanes) {}
}
