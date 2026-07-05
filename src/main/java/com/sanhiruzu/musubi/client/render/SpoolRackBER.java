package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sanhiruzu.musubi.block.SpoolRackBlock;
import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import com.sanhiruzu.musubi.client.model.HankModelLoader;
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

    // Opaque, tintable texture (solid everywhere) so hank faces never sample transparent pixels.
    private static final ResourceLocation HANK_TEX =
        ResourceLocation.fromNamespaceAndPath("musubi", "textures/item/hank.png");
    private static final RenderType RENDER_TYPE = RenderType.entitySolid(HANK_TEX);
    private static final ResourceLocation HANK_MODEL =
        ResourceLocation.fromNamespaceAndPath("musubi", "block/hank");

    private static HankModelLoader.HankModel cachedModel;

    // hank.json is authored for slot 2 (bottom-left): x center ≈5, y 2–7, z 11.5–14.5.
    // Anchor is at the neck center where hank rests on peg.
    private static final float ANCHOR_X = 5f / 16f;
    private static final float ANCHOR_Y = 6.5f / 16f;
    private static final float ANCHOR_Z = 13f / 16f;

    // Per-slot offset from authored position.
    // Slots: [top-left, top-right, bottom-left, bottom-right].
    // Grid offsets: right column +6x, top row +6y.
    private static final float[] HANK_DX = { 0f, 6f/16f, 0f, 6f/16f };
    private static final float[] HANK_DY = { 6f/16f, 6f/16f, 0f, 0f };

    private static final float VISUAL_FULL_COUNT = 256f;

    // Hank size scale by tier (fuller/higher tier = a slightly larger skein).
    private static final float FEATHER_SCALE = 0.85f;
    private static final float CRINKLE_SCALE = 1.0f;
    private static final float RINZU_SCALE = 1.15f;

    public SpoolRackBER(BlockEntityRendererProvider.Context ctx) {
        cachedModel = HankModelLoader.loadModel(HANK_MODEL);
    }

    @Override
    public void render(SpoolRackBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int combinedLight, int combinedOverlay) {

        if (be.getLevel() == null) return;

        if (cachedModel == null) {
            cachedModel = HankModelLoader.loadModel(HANK_MODEL);
        }

        Direction facing  = be.getBlockState().getValue(SpoolRackBlock.FACING);
        boolean   hanging = be.getBlockState().getValue(SpoolRackBlock.HANGING);

        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        int yDeg = switch (facing) { case SOUTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; };
        // Blockstate "y" rotation is clockwise-from-above; JOML Axis.YP is counter-clockwise,
        // so negate to match how the static model is oriented (else the hank lands off-block).
        if (yDeg   != 0) pose.mulPose(Axis.YP.rotationDegrees(-yDeg));
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

            // Fuller storage swells the skein a little (0.85 → 1.0 of the tier size).
            float storageFill = Math.min(1f, contents.count() / VISUAL_FULL_COUNT);
            float fillScale = 0.85f + 0.15f * storageFill;
            float modelScale = getTierScale(tier) * fillScale;

            renderHankModel(vc, m, poseEntry, HANK_DX[i], HANK_DY[i], modelScale, r, g, b, combinedLight);
        }

        pose.popPose();
    }

    private static void renderHankModel(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
                                        float dx, float dy, float scale,
                                        int r, int g, int b, int combinedLight) {
        for (HankModelLoader.Face face : cachedModel.faces) {
            // Uniform scale about the neck anchor, then translate to this slot's peg.
            // Pure scale + translate keeps winding/normals intact (no mirroring).
            float[] scaledPos = new float[12];
            for (int i = 0; i < 12; i += 3) {
                scaledPos[i]     = ANCHOR_X + (face.positions[i]     - ANCHOR_X) * scale + dx;
                scaledPos[i + 1] = ANCHOR_Y + (face.positions[i + 1] - ANCHOR_Y) * scale + dy;
                scaledPos[i + 2] = ANCHOR_Z + (face.positions[i + 2] - ANCHOR_Z) * scale;
            }

            // Render quad
            vc.addVertex(m, scaledPos[0], scaledPos[1], scaledPos[2])
              .setColor(r, g, b, 255).setUv(face.uvs[0], face.uvs[1])
              .setOverlay(OverlayTexture.NO_OVERLAY).setLight(combinedLight)
              .setNormal(pose, face.normal[0], face.normal[1], face.normal[2]);

            vc.addVertex(m, scaledPos[3], scaledPos[4], scaledPos[5])
              .setColor(r, g, b, 255).setUv(face.uvs[2], face.uvs[3])
              .setOverlay(OverlayTexture.NO_OVERLAY).setLight(combinedLight)
              .setNormal(pose, face.normal[0], face.normal[1], face.normal[2]);

            vc.addVertex(m, scaledPos[6], scaledPos[7], scaledPos[8])
              .setColor(r, g, b, 255).setUv(face.uvs[4], face.uvs[5])
              .setOverlay(OverlayTexture.NO_OVERLAY).setLight(combinedLight)
              .setNormal(pose, face.normal[0], face.normal[1], face.normal[2]);

            vc.addVertex(m, scaledPos[9], scaledPos[10], scaledPos[11])
              .setColor(r, g, b, 255).setUv(face.uvs[6], face.uvs[7])
              .setOverlay(OverlayTexture.NO_OVERLAY).setLight(combinedLight)
              .setNormal(pose, face.normal[0], face.normal[1], face.normal[2]);
        }
    }

    private static float getTierScale(int tier) {
        return switch (tier) {
            case 0 -> FEATHER_SCALE;
            case 2 -> RINZU_SCALE;
            default -> CRINKLE_SCALE;
        };
    }

}
