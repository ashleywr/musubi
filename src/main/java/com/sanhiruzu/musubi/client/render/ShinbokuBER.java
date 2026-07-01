package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sanhiruzu.musubi.Musubi;
import com.sanhiruzu.musubi.blockentity.ShinbokuBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * Renders the Shinboku (神木) as two layers:
 *
 *   1. The wrapped block's own model — rendered via BlockRenderDispatcher so that
 *      oak logs show oak texture, amethyst clusters show their crystal model, etc.
 *      The stored BlockState includes orientation, so a sideways log looks sideways.
 *
 *   2. The Shimenawa overlay — two horizontal rope bands around the block's waist,
 *      plus four shide (paper zigzag) strips hanging from the lower band, one per
 *      cardinal face.
 *
 * Geometry sits ~0.003 outside each block face to avoid z-fighting with the wrapped
 * block's own pixels.
 */
public class ShinbokuBER implements BlockEntityRenderer<ShinbokuBlockEntity> {

    // ── Textures ──────────────────────────────────────────────────────────────

    private static final ResourceLocation ROPE_TEX =
        ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "textures/block/shimenawa_rope.png");
    private static final ResourceLocation SHIDE_TEX =
        ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, "textures/block/shimenawa_shide.png");

    private static final RenderType ROPE_TYPE  = RenderType.entityCutout(ROPE_TEX);
    private static final RenderType SHIDE_TYPE = RenderType.entityTranslucentCull(SHIDE_TEX);

    // ── Rope geometry ─────────────────────────────────────────────────────────

    /** How far outside the block face the rope sits (avoids z-fighting). */
    private static final float OFF = 0.003f;

    /** Lower rope band: Y = 0.38 – 0.46 */
    private static final float R1_MIN = 0.38f, R1_MAX = 0.46f;

    /** Upper rope band: Y = 0.54 – 0.62 */
    private static final float R2_MIN = 0.54f, R2_MAX = 0.62f;

    // ── Shide geometry ────────────────────────────────────────────────────────

    private static final float SHIDE_W    = 0.14f;  // half = 0.07
    private static final float SHIDE_TOP  = R1_MIN; // hangs from the bottom of the lower rope
    private static final float SHIDE_BOT  = 0.18f;

    // ── Rope vertex colours (warm hemp / straw) ───────────────────────────────
    private static final int ROPE_R = 0xD0, ROPE_G = 0xA8, ROPE_B = 0x44;

    // ── Shide vertex colours (off-white paper) ────────────────────────────────
    private static final int SHIDE_R = 0xF0, SHIDE_G = 0xEC, SHIDE_B = 0xD8;

    // ─────────────────────────────────────────────────────────────────────────

    public ShinbokuBER(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(ShinbokuBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buf, int light, int overlay) {

        // ── Layer 1: wrapped block ─────────────────────────────────────────────
        BlockState wrapped = be.getWrappedState();
        if (wrapped != null && !wrapped.isAir()) {
            Minecraft.getInstance().getBlockRenderer()
                .renderSingleBlock(wrapped, pose, buf, light, overlay, ModelData.EMPTY, null);
        }

        // ── Layer 2: Shimenawa rope ────────────────────────────────────────────
        PoseStack.Pose entry = pose.last();
        Matrix4f m = entry.pose();

        VertexConsumer ropeVc = buf.getBuffer(ROPE_TYPE);
        renderRopeRing(ropeVc, m, entry, R1_MIN, R1_MAX, light);
        renderRopeRing(ropeVc, m, entry, R2_MIN, R2_MAX, light);

        // ── Layer 3: shide paper strips ───────────────────────────────────────
        VertexConsumer shideVc = buf.getBuffer(SHIDE_TYPE);
        renderShide(shideVc, m, entry, light);
    }

    // ── Rope ring ─────────────────────────────────────────────────────────────

    /**
     * Draws one horizontal rope band as four outward-facing quads, slightly
     * proud of each block face, from yMin to yMax.
     */
    private static void renderRopeRing(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
                                        float yMin, float yMax, int light) {
        float lo = -OFF, hi = 1f + OFF;

        // North face — outward normal: (0, 0, -1)
        quad(vc, m, pose,
             hi, yMax, lo, 0f, 0f,
             lo, yMax, lo, 1f, 0f,
             lo, yMin, lo, 1f, 1f,
             hi, yMin, lo, 0f, 1f,
             ROPE_R, ROPE_G, ROPE_B, 255, light, 0f, 0f, -1f);

        // South face — outward normal: (0, 0, 1)
        quad(vc, m, pose,
             lo, yMax, hi, 0f, 0f,
             hi, yMax, hi, 1f, 0f,
             hi, yMin, hi, 1f, 1f,
             lo, yMin, hi, 0f, 1f,
             ROPE_R, ROPE_G, ROPE_B, 255, light, 0f, 0f, 1f);

        // East face — outward normal: (1, 0, 0)
        quad(vc, m, pose,
             hi, yMax, hi, 0f, 0f,
             hi, yMax, lo, 1f, 0f,
             hi, yMin, lo, 1f, 1f,
             hi, yMin, hi, 0f, 1f,
             ROPE_R, ROPE_G, ROPE_B, 255, light, 1f, 0f, 0f);

        // West face — outward normal: (-1, 0, 0)
        quad(vc, m, pose,
             lo, yMax, lo, 0f, 0f,
             lo, yMax, hi, 1f, 0f,
             lo, yMin, hi, 1f, 1f,
             lo, yMin, lo, 0f, 1f,
             ROPE_R, ROPE_G, ROPE_B, 255, light, -1f, 0f, 0f);
    }

    // ── Shide strips ──────────────────────────────────────────────────────────

    /**
     * Draws one shide (rectangular paper strip) per cardinal face, centered on
     * each face and hanging below the lower rope band.
     * Each strip is rendered double-sided so it's visible from inside the block too.
     */
    private static void renderShide(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, int light) {
        float hw  = SHIDE_W / 2f;
        float top = SHIDE_TOP;
        float bot = SHIDE_BOT;
        float off = OFF + 0.002f; // slightly more proud than the rope

        // South face (z = hi), centered on X.
        float sz = 1f + off;
        shidePair(vc, m, pose, 0.5f - hw, 0.5f + hw, top, bot, sz, sz, true, light);

        // North face (z = lo), centered on X.
        float nz = -off;
        shidePair(vc, m, pose, 0.5f - hw, 0.5f + hw, top, bot, nz, nz, false, light);

        // East face (x = hi), centered on Z — use Z axis for width.
        float ex = 1f + off;
        shidePairX(vc, m, pose, ex, 0.5f - hw, 0.5f + hw, top, bot, true, light);

        // West face (x = lo), centered on Z.
        float wx = -off;
        shidePairX(vc, m, pose, wx, 0.5f - hw, 0.5f + hw, top, bot, false, light);
    }

    /** Double-sided shide strip on a Z-constant face (south/north). */
    private static void shidePair(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
                                   float xMin, float xMax, float yTop, float yBot, float z,
                                   float z2, boolean facingSouth, int light) {
        float nz = facingSouth ? 1f : -1f;
        // Front face
        quad(vc, m, pose,
             xMin, yTop, z, 0f, 0f,
             xMax, yTop, z, 1f, 0f,
             xMax, yBot, z, 1f, 1f,
             xMin, yBot, z, 0f, 1f,
             SHIDE_R, SHIDE_G, SHIDE_B, 220, light, 0f, 0f, nz);
        // Back face (flipped winding)
        quad(vc, m, pose,
             xMax, yTop, z2, 0f, 0f,
             xMin, yTop, z2, 1f, 0f,
             xMin, yBot, z2, 1f, 1f,
             xMax, yBot, z2, 0f, 1f,
             SHIDE_R, SHIDE_G, SHIDE_B, 220, light, 0f, 0f, -nz);
    }

    /** Double-sided shide strip on an X-constant face (east/west). */
    private static void shidePairX(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
                                    float x, float zMin, float zMax, float yTop, float yBot,
                                    boolean facingEast, int light) {
        float nx = facingEast ? 1f : -1f;
        quad(vc, m, pose,
             x, yTop, zMax, 0f, 0f,
             x, yTop, zMin, 1f, 0f,
             x, yBot, zMin, 1f, 1f,
             x, yBot, zMax, 0f, 1f,
             SHIDE_R, SHIDE_G, SHIDE_B, 220, light, nx, 0f, 0f);
        quad(vc, m, pose,
             x, yTop, zMin, 0f, 0f,
             x, yTop, zMax, 1f, 0f,
             x, yBot, zMax, 1f, 1f,
             x, yBot, zMin, 0f, 1f,
             SHIDE_R, SHIDE_G, SHIDE_B, 220, light, -nx, 0f, 0f);
    }

    // ── Vertex helper ─────────────────────────────────────────────────────────

    private static void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
            float x0, float y0, float z0, float u0, float v0,
            float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2,
            float x3, float y3, float z3, float u3, float v3,
            int r, int g, int b, int a, int light,
            float nx, float ny, float nz) {
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v1)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setUv(u2, v2)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a).setUv(u3, v3)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
