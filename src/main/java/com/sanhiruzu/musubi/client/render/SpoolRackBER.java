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
    private static final RenderType RENDER_TYPE   = RenderType.entityCutout(SKEIN_TEX);
    private static final RenderType SHIMMER_TYPE  = RenderType.entityTranslucentCull(SHIMMER_TEX);

    // Peg X centers in 0–1 block space (model is defined for NORTH facing).
    private static final float[] PEG_X = { 2f/16f, 6f/16f, 10f/16f, 14f/16f };
    private static final float   PEG_Y = 13f/16f;  // top surface = pendulum pivot
    private static final float   PEG_Z = 9f/16f;   // midpoint along peg length (z=4..14)

    // Skein dimensions scale linearly with slot fill (0 → 1).
    private static final float PENDULUM_MIN = 0.18f,  PENDULUM_MAX = 0.30f;
    private static final float SKEIN_W_MIN  = 0.12f,  SKEIN_W_MAX  = 0.28f;
    private static final float SKEIN_H_MIN  = 0.20f,  SKEIN_H_MAX  = 0.58f;

    private static final float KICK_BASE   = 0.28f;
    private static final float KICK_JITTER = 0.14f;

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
        VertexConsumer vc         = buffers.getBuffer(RENDER_TYPE);
        VertexConsumer shimmerVc  = buffers.getBuffer(SHIMMER_TYPE);
        int rainbowRgb = MusubiClient.rainbowColor() & 0xFFFFFF;

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

            float fill   = Math.min(1f, count / (float) SpoolRackBlockEntity.SLOT_CAPACITY);
            float pLen   = PENDULUM_MIN + (PENDULUM_MAX - PENDULUM_MIN) * fill;
            float skeinW = SKEIN_W_MIN  + (SKEIN_W_MAX  - SKEIN_W_MIN)  * fill;
            float skeinH = SKEIN_H_MIN  + (SKEIN_H_MAX  - SKEIN_H_MIN)  * fill;

            // Ambient sway: slow sine so skeins are never completely still.
            float ambient = (float)(Math.sin(gameTick * 0.04 + i * 1.5 + partialTick * 0.04) * 0.012);
            float angle   = p.lerpAngle(partialTick) + ambient;

            drawSkein(poseEntry, vc, shimmerVc, i, angle, color, rainbowRgb, pLen, skeinW, skeinH);
        }

        pose.popPose();
    }

    private static void drawSkein(PoseStack.Pose poseEntry,
                                  VertexConsumer vc, VertexConsumer shimmerVc,
                                  int pegIndex, float angle, int color, int shimmerRgb,
                                  float pendulumLen, float skeinW, float skeinH) {
        float cx = PEG_X[pegIndex];
        float cy = PEG_Y - pendulumLen * (float) Math.cos(angle);
        float cz = PEG_Z + pendulumLen * (float) Math.sin(angle);

        float w2 = skeinW / 2f;
        float h2 = skeinH / 2f;
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        int sr = (shimmerRgb >> 16) & 0xFF, sg = (shimmerRgb >> 8) & 0xFF, sb = shimmerRgb & 0xFF;

        Matrix4f m = poseEntry.pose();

        // Base layer — front and back faces.
        quad(vc, m, poseEntry,
             cx - w2, cy + h2, cz,   0f, 0f,
             cx + w2, cy + h2, cz,   1f, 0f,
             cx + w2, cy - h2, cz,   1f, 1f,
             cx - w2, cy - h2, cz,   0f, 1f,
             r, g, b, 255, 0f, 0f, -1f);
        quad(vc, m, poseEntry,
             cx - w2, cy - h2, cz,   0f, 1f,
             cx + w2, cy - h2, cz,   1f, 1f,
             cx + w2, cy + h2, cz,   1f, 0f,
             cx - w2, cy + h2, cz,   0f, 0f,
             r, g, b, 255, 0f, 0f, 1f);

        // Shimmer overlay — offset slightly toward each face's viewer to avoid z-fighting.
        float czF = cz - 0.001f;
        float czB = cz + 0.001f;
        quad(shimmerVc, m, poseEntry,
             cx - w2, cy + h2, czF,  0f, 0f,
             cx + w2, cy + h2, czF,  1f, 0f,
             cx + w2, cy - h2, czF,  1f, 1f,
             cx - w2, cy - h2, czF,  0f, 1f,
             sr, sg, sb, 160, 0f, 0f, -1f);
        quad(shimmerVc, m, poseEntry,
             cx - w2, cy - h2, czB,  0f, 1f,
             cx + w2, cy - h2, czB,  1f, 1f,
             cx + w2, cy + h2, czB,  1f, 0f,
             cx - w2, cy + h2, czB,  0f, 0f,
             sr, sg, sb, 160, 0f, 0f, 1f);
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
