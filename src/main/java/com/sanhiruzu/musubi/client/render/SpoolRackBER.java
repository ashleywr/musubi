package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sanhiruzu.musubi.MusubiClient;
import com.sanhiruzu.musubi.block.SpoolRackBlock;
import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders colored silk skeins hanging from each occupied peg on a Spool Rack.
 *
 * Each occupied slot gets a skein quad placed below the peg's midpoint,
 * swinging on a simulated pendulum in the YZ plane. A brief kick is applied
 * when a new skein is deposited. Skeins render at full brightness so they
 * read clearly even in unlit storage rooms.
 */
public class SpoolRackBER implements BlockEntityRenderer<SpoolRackBlockEntity> {

    private static final ResourceLocation SKEIN_TEX =
        ResourceLocation.fromNamespaceAndPath("musubi", "textures/item/skein.png");
    private static final ResourceLocation SHIMMER_TEX =
        ResourceLocation.fromNamespaceAndPath("musubi", "textures/item/skein_shimmer.png");
    private static final ResourceLocation EDGE_TEX =
        ResourceLocation.fromNamespaceAndPath("musubi", "textures/item/skein_depth.png");
    private static final RenderType RENDER_TYPE   = RenderType.entityCutout(SKEIN_TEX);
    private static final RenderType SHIMMER_TYPE  = RenderType.entityTranslucentCull(SHIMMER_TEX);
    private static final RenderType EDGE_TYPE     = RenderType.entityCutout(EDGE_TEX);

    // Peg X centers in 0–1 block space (model is defined for NORTH facing).
    private static final float[] PEG_X = { 2f/16f, 6f/16f, 10f/16f, 14f/16f };
    private static final float   PEG_Y = 13f/16f;  // top surface = pendulum pivot
    private static final float   PEG_Z = 9f/16f;   // midpoint along peg length (z=4..14)

    // The rack can hold thousands of items, but the display needs to read at
    // ordinary stack sizes. We therefore map count to a visual fullness curve
    // and then switch the silhouette from loop -> hank -> bundled strands.
    private static final float VISUAL_FULL_COUNT = 256f;
    private static final float LOOP_STAGE_END    = 0.34f;
    private static final float HANK_STAGE_END    = 0.76f;
    private static final float TAU               = (float) (Math.PI * 2.0);

    private static final float KICK_BASE   = 0.28f;
    private static final float KICK_JITTER = 0.14f;

    private record SkeinRenderData(int pegIndex, float angle, int color, int shimmerRgb,
                                   float pendulumLen, float skeinW, float skeinH, float skeinD,
                                   int strandCount) {}

    /** Accumulated per-position pendulum state. Entries are never evicted;
     *  the leak is negligible (a few hundred bytes per rack ever placed). */
    private static final Map<BlockPos, SkeinPendulum[]> PENDULUMS = new HashMap<>();

    public SpoolRackBER(BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(SpoolRackBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int combinedLight, int combinedOverlay) {

        if (be.getLevel() == null) return;
        long gameTick = be.getLevel().getGameTime();

        SkeinPendulum[] pendulums = PENDULUMS.computeIfAbsent(
            be.getBlockPos(), k -> new SkeinPendulum[SpoolRackBlockEntity.SLOTS]);

        Direction facing  = be.getBlockState().getValue(SpoolRackBlock.FACING);
        boolean   hanging = be.getBlockState().getValue(SpoolRackBlock.HANGING);

        // Mirror the facing rotation the blockstates JSON applies to the static model.
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        int yDeg = switch (facing) { case SOUTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; };
        if (yDeg   != 0) pose.mulPose(Axis.YP.rotationDegrees(yDeg));
        if (hanging)     pose.mulPose(Axis.XP.rotationDegrees(180f));
        pose.translate(-0.5, -0.5, -0.5);

        PoseStack.Pose poseEntry  = pose.last();
        int rainbowRgb = MusubiClient.rainbowColor() & 0xFFFFFF;
        SkeinRenderData[] renderData = new SkeinRenderData[SpoolRackBlockEntity.SLOTS];

        for (int i = 0; i < SpoolRackBlockEntity.SLOTS; i++) {
            int count = be.getSlotCount(i);
            if (pendulums[i] == null) pendulums[i] = new SkeinPendulum();
            SkeinPendulum p = pendulums[i];

            // Deposit detected → kick with random sign so adjacent skeins sway independently.
            if (count > p.lastCount) {
                float sign   = (Math.random() > 0.5) ? 1f : -1f;
                float jitter = (float)(Math.random() * KICK_JITTER);
                p.kick(sign * (KICK_BASE + jitter));
            }
            p.lastCount = count;
            p.updateTo(gameTick);

            if (count == 0) continue;

            var itemId = be.getSlotItem(i);
            int color  = (itemId != null) ? MusubiClient.getDominantColor(itemId) : 0xC8A864;

            float storageFill = Math.min(1f, count / VISUAL_FULL_COUNT);
            float visualFill = (float) Math.sqrt(storageFill);
            int stage = visualFill < LOOP_STAGE_END ? 0 : (visualFill < HANK_STAGE_END ? 1 : 2);
            float stageMix = switch (stage) {
                case 0 -> visualFill / LOOP_STAGE_END;
                case 1 -> (visualFill - LOOP_STAGE_END) / (HANK_STAGE_END - LOOP_STAGE_END);
                default -> (visualFill - HANK_STAGE_END) / (1f - HANK_STAGE_END);
            };

            float pLen = switch (stage) {
                case 0 -> lerp(0.34f, 0.28f, stageMix);
                case 1 -> lerp(0.28f, 0.31f, stageMix);
                default -> lerp(0.29f, 0.33f, stageMix);
            };
            float skeinW = switch (stage) {
                case 0 -> lerp(0.15f, 0.22f, stageMix);
                case 1 -> lerp(0.24f, 0.30f, stageMix);
                default -> lerp(0.30f, 0.37f, stageMix);
            };
            float skeinH = switch (stage) {
                case 0 -> lerp(0.28f, 0.40f, stageMix);
                case 1 -> lerp(0.42f, 0.54f, stageMix);
                default -> lerp(0.56f, 0.68f, stageMix);
            };
            float skeinD = switch (stage) {
                case 0 -> lerp(0.018f, 0.024f, stageMix);
                case 1 -> lerp(0.028f, 0.035f, stageMix);
                default -> lerp(0.040f, 0.050f, stageMix);
            };
            int strandCount = stage == 0 ? 1 : (stage == 1 ? 3 : 5);

            // Ambient sway: slow sine so skeins are never completely still.
            float ambient = (float)(Math.sin(gameTick * 0.04 + i * 1.5 + partialTick * 0.04) * 0.012);
            float angle   = p.lerpAngle(partialTick) + ambient;

            renderData[i] = new SkeinRenderData(i, angle, color, rainbowRgb, pLen, skeinW, skeinH, skeinD, strandCount);
        }

        VertexConsumer vc = buffers.getBuffer(RENDER_TYPE);
        for (SkeinRenderData data : renderData) {
            if (data == null) continue;
            drawSkeinFaces(poseEntry, vc, data);
        }

        VertexConsumer edgeVc = buffers.getBuffer(EDGE_TYPE);
        for (SkeinRenderData data : renderData) {
            if (data == null) continue;
            drawSkeinEdges(poseEntry, edgeVc, data);
        }

        VertexConsumer shimmerVc  = buffers.getBuffer(SHIMMER_TYPE);
        for (SkeinRenderData data : renderData) {
            if (data == null) continue;
            drawSkeinShimmer(poseEntry, shimmerVc, data);
        }

        pose.popPose();
    }

    private static void drawSkeinFaces(PoseStack.Pose poseEntry, VertexConsumer vc, SkeinRenderData data) {
        float cx = PEG_X[data.pegIndex()];
        float cy = PEG_Y - data.pendulumLen() * (float) Math.cos(data.angle());
        float cz = PEG_Z + data.pendulumLen() * (float) Math.sin(data.angle());

        int strands = data.strandCount();
        int color = data.color();
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;

        Matrix4f m = poseEntry.pose();

        float baseBundleW = data.skeinW();
        float baseBundleH = data.skeinH();
        float baseBundleD = data.skeinD();
        float widthScale = strands == 1 ? 1.0f : (strands == 3 ? 0.60f : 0.43f);
        float heightScale = strands == 1 ? 1.0f : (strands == 3 ? 0.92f : 0.84f);
        float depthScale = strands == 1 ? 1.0f : (strands == 3 ? 1.08f : 1.12f);
        float ringX = strands == 1 ? 0.0f : (strands == 3 ? 0.016f : 0.022f);
        float ringY = strands == 1 ? 0.0f : (strands == 3 ? 0.028f : 0.040f);
        float ringZ = strands == 1 ? 0.0f : (strands == 3 ? 0.010f : 0.014f);

        for (int strand = 0; strand < strands; strand++) {
            float t = (strands == 1) ? 0.5f : strand / (float) (strands - 1);
            float phase = t * TAU + data.angle() * 0.55f + data.pegIndex() * 0.8f;
            float twist = (float) Math.sin(phase * 1.3f);
            float curl = (float) Math.cos(phase);
            float strandX = curl * ringX;
            float strandY = -Math.abs(twist) * ringY - (strands == 1 ? 0.0f : 0.010f);
            float strandZ = twist * ringZ;
            float strandW = baseBundleW * widthScale;
            float strandH = baseBundleH * heightScale;
            float strandD = baseBundleD * depthScale;
            float w2 = strandW / 2f;
            float h2 = strandH / 2f;
            float d2 = strandD / 2f;
            float squeeze = strands == 1 ? 1f : 1f - 0.08f * Math.abs(twist);
            float lift = strands == 1 ? 0f : 0.012f * (1f - Math.abs(twist));
            int alpha = strands == 1 ? 255 : (strand == 0 || strand == strands - 1 ? 210 : 245);

            drawRibbonFaces(vc, m, poseEntry,
                cx + strandX,
                cy + strandY + lift,
                cz + strandZ,
                w2 * squeeze,
                h2 * squeeze,
                d2,
                r, g, b, alpha);
        }
    }

    private static void drawSkeinEdges(PoseStack.Pose poseEntry, VertexConsumer edgeVc, SkeinRenderData data) {
        float cx = PEG_X[data.pegIndex()];
        float cy = PEG_Y - data.pendulumLen() * (float) Math.cos(data.angle());
        float cz = PEG_Z + data.pendulumLen() * (float) Math.sin(data.angle());

        int strands = data.strandCount();
        int color = data.color();
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;

        Matrix4f m = poseEntry.pose();

        float baseBundleW = data.skeinW();
        float baseBundleH = data.skeinH();
        float baseBundleD = data.skeinD();
        float widthScale = strands == 1 ? 1.0f : (strands == 3 ? 0.60f : 0.43f);
        float heightScale = strands == 1 ? 1.0f : (strands == 3 ? 0.92f : 0.84f);
        float depthScale = strands == 1 ? 1.0f : (strands == 3 ? 1.08f : 1.12f);
        float ringX = strands == 1 ? 0.0f : (strands == 3 ? 0.016f : 0.022f);
        float ringY = strands == 1 ? 0.0f : (strands == 3 ? 0.028f : 0.040f);
        float ringZ = strands == 1 ? 0.0f : (strands == 3 ? 0.010f : 0.014f);

        for (int strand = 0; strand < strands; strand++) {
            float t = (strands == 1) ? 0.5f : strand / (float) (strands - 1);
            float phase = t * TAU + data.angle() * 0.55f + data.pegIndex() * 0.8f;
            float twist = (float) Math.sin(phase * 1.3f);
            float curl = (float) Math.cos(phase);
            float strandX = curl * ringX;
            float strandY = -Math.abs(twist) * ringY - (strands == 1 ? 0.0f : 0.010f);
            float strandZ = twist * ringZ;
            float strandW = baseBundleW * widthScale;
            float strandH = baseBundleH * heightScale;
            float strandD = baseBundleD * depthScale;
            float w2 = strandW / 2f;
            float h2 = strandH / 2f;
            float d2 = strandD / 2f;
            float squeeze = strands == 1 ? 1f : 1f - 0.08f * Math.abs(twist);
            float lift = strands == 1 ? 0f : 0.012f * (1f - Math.abs(twist));
            int alpha = strands == 1 ? 160 : (strand == 0 || strand == strands - 1 ? 100 : 145);

            // Edges give the skein depth so the bundle doesn't read as a flat billboard.
            drawEdgeBox(edgeVc, m, poseEntry, cx + strandX, cy + strandY + lift, cz + strandZ,
                w2 * squeeze, h2 * squeeze, d2, (int) (r * 0.72f), (int) (g * 0.72f), (int) (b * 0.72f), alpha);
        }
    }

    private static void drawSkeinShimmer(PoseStack.Pose poseEntry, VertexConsumer shimmerVc, SkeinRenderData data) {
        float cx = PEG_X[data.pegIndex()];
        float cy = PEG_Y - data.pendulumLen() * (float) Math.cos(data.angle());
        float cz = PEG_Z + data.pendulumLen() * (float) Math.sin(data.angle());

        int strands = data.strandCount();
        int shimmerRgb = data.shimmerRgb();
        int sr = (shimmerRgb >> 16) & 0xFF, sg = (shimmerRgb >> 8) & 0xFF, sb = shimmerRgb & 0xFF;

        Matrix4f m = poseEntry.pose();

        float ringX = strands == 1 ? 0.0f : (strands == 3 ? 0.016f : 0.022f);
        float ringY = strands == 1 ? 0.0f : (strands == 3 ? 0.028f : 0.040f);
        float ringZ = strands == 1 ? 0.0f : (strands == 3 ? 0.010f : 0.014f);
        float widthScale = strands == 1 ? 1.0f : (strands == 3 ? 0.60f : 0.43f);
        float heightScale = strands == 1 ? 1.0f : (strands == 3 ? 0.92f : 0.84f);
        float depthScale = strands == 1 ? 1.0f : (strands == 3 ? 1.08f : 1.12f);

        for (int strand = 0; strand < strands; strand++) {
            float t = (strands == 1) ? 0.5f : strand / (float) (strands - 1);
            float phase = t * TAU + data.angle() * 0.55f + data.pegIndex() * 0.8f;
            float twist = (float) Math.sin(phase * 1.3f);
            float curl = (float) Math.cos(phase);
            float strandX = curl * ringX;
            float strandY = -Math.abs(twist) * ringY - (strands == 1 ? 0.0f : 0.010f);
            float strandZ = twist * ringZ;
            float strandW = data.skeinW() * widthScale;
            float strandH = data.skeinH() * heightScale;
            float strandD = data.skeinD() * depthScale;
            float w2 = strandW / 2f;
            float h2 = strandH / 2f;
            float d2 = strandD / 2f;
            float squeeze = strands == 1 ? 1f : 1f - 0.08f * Math.abs(twist);
            float lift = strands == 1 ? 0f : 0.012f * (1f - Math.abs(twist));
            int alpha = strands == 1 ? 160 : (strand == 0 || strand == strands - 1 ? 100 : 145);

            // Shimmer sits on each strand so the bundle reads as woven mass, not flat foil.
            float czF = cz + strandZ + d2 - 0.001f;
            float czB = cz + strandZ - d2 + 0.001f;
            quad(shimmerVc, m, poseEntry,
                 cx + strandX - w2 * squeeze, cy + strandY + lift + h2 * squeeze, czF,  0f, 0f,
                 cx + strandX + w2 * squeeze, cy + strandY + lift + h2 * squeeze, czF,  1f, 0f,
                 cx + strandX + w2 * squeeze, cy + strandY + lift - h2 * squeeze, czF,  1f, 1f,
                 cx + strandX - w2 * squeeze, cy + strandY + lift - h2 * squeeze, czF,  0f, 1f,
                 sr, sg, sb, alpha, 0f, 0f, -1f);
            quad(shimmerVc, m, poseEntry,
                 cx + strandX - w2 * squeeze, cy + strandY + lift - h2 * squeeze, czB,  0f, 1f,
                 cx + strandX + w2 * squeeze, cy + strandY + lift - h2 * squeeze, czB,  1f, 1f,
                 cx + strandX + w2 * squeeze, cy + strandY + lift + h2 * squeeze, czB,  1f, 0f,
                 cx + strandX - w2 * squeeze, cy + strandY + lift + h2 * squeeze, czB,  0f, 0f,
                 sr, sg, sb, alpha, 0f, 0f, 1f);
        }
    }

    private static void drawRibbonFaces(VertexConsumer vc, Matrix4f m, PoseStack.Pose poseEntry,
                                        float cx, float cy, float cz,
                                        float w2, float h2, float d2,
                                        int r, int g, int b, int a) {
        quad(vc, m, poseEntry,
             cx - w2, cy + h2, cz + d2,   0f, 0f,
             cx + w2, cy + h2, cz + d2,   1f, 0f,
             cx + w2, cy - h2, cz + d2,   1f, 1f,
             cx - w2, cy - h2, cz + d2,   0f, 1f,
             r, g, b, a, 0f, 0f, -1f);
        quad(vc, m, poseEntry,
             cx - w2, cy - h2, cz - d2,   0f, 1f,
             cx + w2, cy - h2, cz - d2,   1f, 1f,
             cx + w2, cy + h2, cz - d2,   1f, 0f,
             cx - w2, cy + h2, cz - d2,   0f, 0f,
             r, g, b, a, 0f, 0f, 1f);
    }

    private static void drawEdgeBox(VertexConsumer edgeVc, Matrix4f m, PoseStack.Pose poseEntry,
                                    float cx, float cy, float cz, float w2, float h2, float d2,
                                    int r, int g, int b, int a) {
        // Sides: left, right, top, bottom.
        quad(edgeVc, m, poseEntry,
             cx - w2, cy + h2, cz - d2,  0f, 0f,
             cx - w2, cy + h2, cz + d2,  1f, 0f,
             cx - w2, cy - h2, cz + d2,  1f, 1f,
             cx - w2, cy - h2, cz - d2,  0f, 1f,
             r, g, b, a, -1f, 0f, 0f);
        quad(edgeVc, m, poseEntry,
             cx + w2, cy + h2, cz + d2,  0f, 0f,
             cx + w2, cy + h2, cz - d2,  1f, 0f,
             cx + w2, cy - h2, cz - d2,  1f, 1f,
             cx + w2, cy - h2, cz + d2,  0f, 1f,
             r, g, b, a, 1f, 0f, 0f);
        quad(edgeVc, m, poseEntry,
             cx - w2, cy + h2, cz + d2,  0f, 0f,
             cx + w2, cy + h2, cz + d2,  1f, 0f,
             cx + w2, cy + h2, cz - d2,  1f, 1f,
             cx - w2, cy + h2, cz - d2,  0f, 1f,
             r, g, b, a, 0f, 1f, 0f);
        quad(edgeVc, m, poseEntry,
             cx - w2, cy - h2, cz - d2,  0f, 1f,
             cx + w2, cy - h2, cz - d2,  1f, 1f,
             cx + w2, cy - h2, cz + d2,  1f, 0f,
             cx - w2, cy - h2, cz + d2,  0f, 0f,
             r, g, b, a, 0f, -1f, 0f);
    }

    private static float lerp(float start, float end, float t) {
        return start + (end - start) * t;
    }

    private static void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            int r, int g, int b, int a,
            float nx, float ny, float nz) {
        // 0xF000F0 = LightTexture.FULL_BRIGHT packed value
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(pose, nx, ny, nz);
    }
}
