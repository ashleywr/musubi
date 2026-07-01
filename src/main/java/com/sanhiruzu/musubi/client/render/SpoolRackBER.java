package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sanhiruzu.musubi.block.SpoolRackBlock;
import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import com.sanhiruzu.musubi.component.SkeinContents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Renders hanging yarn hanks on each peg of a Spool Rack.
 *
 * A 2×2 peg grid displays colorful hanging hanks on pegs.
 * Each hank is tinted from the stored skein contents so the visual stays aligned with
 * the data carried when the skein is moved off the rack.
 */
public class SpoolRackBER implements BlockEntityRenderer<SpoolRackBlockEntity> {

    private static final ResourceLocation SKEIN_TEX =
        ResourceLocation.fromNamespaceAndPath("musubi", "textures/item/skein.png");
    private static final RenderType RENDER_TYPE = RenderType.entityCutout(SKEIN_TEX);

    // 2×2 peg grid: [top-left, top-right, bottom-left, bottom-right]
    // Pegs extend from z=8 to z=14 (outward from wall)
    private static final float[] PEG_X = { 4f/16f, 12f/16f, 4f/16f, 12f/16f };
    private static final float[] PEG_Y = { 13f/16f, 13f/16f, 11f/16f, 11f/16f };
    private static final float[] PEG_Z = { 11f/16f, 11f/16f, 11f/16f, 11f/16f };

    private static final float VISUAL_FULL_COUNT = 256f;
    private static final float Z_STACK_OFFSET = 0.025f; // offset between stacked hanks

    // Tier 0: Feather silk (delicate, lightweight)
    private static final float FEATHER_WIDTH = 0.10f;
    private static final float FEATHER_HEIGHT = 0.14f;
    private static final float FEATHER_ROPE = 0.012f;
    private static final int FEATHER_SEGMENTS = 10;

    // Tier 1: Crinkle silk (medium weight, standard)
    private static final float CRINKLE_WIDTH = 0.14f;
    private static final float CRINKLE_HEIGHT = 0.18f;
    private static final float CRINKLE_ROPE = 0.016f;
    private static final int CRINKLE_SEGMENTS = 12;

    // Tier 2: Rinzu silk (heavy, ornate, substantial)
    private static final float RINZU_WIDTH = 0.18f;
    private static final float RINZU_HEIGHT = 0.22f;
    private static final float RINZU_ROPE = 0.020f;
    private static final int RINZU_SEGMENTS = 14;

    public SpoolRackBER(BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(SpoolRackBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int combinedLight, int combinedOverlay) {

        if (be.getLevel() == null) return;

        Direction facing  = be.getBlockState().getValue(SpoolRackBlock.FACING);
        boolean   hanging = be.getBlockState().getValue(SpoolRackBlock.HANGING);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        int yDeg = switch (facing) { case SOUTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; };
        if (yDeg   != 0) pose.mulPose(Axis.YP.rotationDegrees(yDeg));
        if (hanging)     pose.mulPose(Axis.XP.rotationDegrees(180f));
        pose.translate(-0.5, -0.5, -0.5);

        PoseStack.Pose poseEntry = pose.last();
        VertexConsumer vc = buffers.getBuffer(RENDER_TYPE);
        Matrix4f m = poseEntry.pose();

        for (int i = 0; i < SpoolRackBlockEntity.SLOTS; i++) {
            SkeinContents contents = be.getSlotContents(i);
            if (contents == null || contents.count() == 0) continue;

            int color = contents.displayColor();
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
            int tier = contents.needleTier();
            int count = contents.count();

            // Map count to 1-4 hanks based on fullness
            float storageFill = Math.min(1f, count / VISUAL_FULL_COUNT);
            int hankCount = (int) Math.ceil(storageFill * 4f);

            // Scale loop size slightly based on count (fuller = bigger loop)
            float sizeScale = switch (hankCount) {
                case 1 -> 0.75f;
                case 2 -> 0.9f;
                case 3 -> 1.0f;
                default -> 1.1f;
            };

            float cx = PEG_X[i];
            float cy = PEG_Y[i] - getTierHeight(tier) / 2f;
            float czBase = PEG_Z[i];

            // Stack hanks with slight Z offset for visual depth
            for (int h = 0; h < hankCount; h++) {
                float cz = czBase + (h - (hankCount - 1) / 2f) * Z_STACK_OFFSET;
                drawHankLoop(vc, m, poseEntry, cx, cy, cz, tier, sizeScale, r, g, b);
            }
        }

        pose.popPose();
    }

    private static void drawHankLoop(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
                                     float cx, float cy, float cz, int tier, float sizeScale,
                                     int r, int g, int b) {
        int a = 255;
        float tau = (float) (Math.PI * 2);

        float bundleLength = getTierHeight(tier) * sizeScale;
        float bundleRadius = getTierWidth(tier) * sizeScale * 0.15f;
        float ropeThick = getTierRopeThickness(tier);
        int segments = getTierSegments(tier);
        int coilTurns = 3 + (int)(sizeScale * 2f);

        // Render as hanging coil bundle
        float rt2 = ropeThick / 2f;
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;

            // Parametric coil: spirals down from peg
            float angle0 = t0 * tau * coilTurns;
            float angle1 = t1 * tau * coilTurns;

            float x0 = cx + bundleRadius * (float) Math.cos(angle0);
            float y0 = cy - t0 * bundleLength;
            float z0 = cz + bundleRadius * (float) Math.sin(angle0);

            float x1 = cx + bundleRadius * (float) Math.cos(angle1);
            float y1 = cy - t1 * bundleLength;
            float z1 = cz + bundleRadius * (float) Math.sin(angle1);

            // Calculate normal for shading along the coil
            float nx = (float) Math.cos(angle0 + tau / 4f);
            float nz = (float) Math.sin(angle0 + tau / 4f);

            // Create quad ribbon segment (rope thickness)
            quad(vc, m, pose,
                 x0, y0 + rt2, z0,   0f, 0f,
                 x1, y1 + rt2, z1,   1f, 0f,
                 x1, y1 - rt2, z1,   1f, 1f,
                 x0, y0 - rt2, z0,   0f, 1f,
                 r, g, b, a, nx, 0f, nz);
        }
    }

    private static float getTierWidth(int tier) {
        return switch (tier) {
            case 0 -> FEATHER_WIDTH;
            case 2 -> RINZU_WIDTH;
            default -> CRINKLE_WIDTH;
        };
    }

    private static float getTierHeight(int tier) {
        return switch (tier) {
            case 0 -> FEATHER_HEIGHT;
            case 2 -> RINZU_HEIGHT;
            default -> CRINKLE_HEIGHT;
        };
    }

    private static float getTierRopeThickness(int tier) {
        return switch (tier) {
            case 0 -> FEATHER_ROPE;
            case 2 -> RINZU_ROPE;
            default -> CRINKLE_ROPE;
        };
    }

    private static int getTierSegments(int tier) {
        return switch (tier) {
            case 0 -> FEATHER_SEGMENTS;
            case 2 -> RINZU_SEGMENTS;
            default -> CRINKLE_SEGMENTS;
        };
    }

    private static void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            int r, int g, int b, int a,
            float nx, float ny, float nz) {
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
