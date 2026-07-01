package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sanhiruzu.musubi.block.ItomakiBlock;
import com.sanhiruzu.musubi.blockentity.ItomakiBlockEntity;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.TamaStorage;
import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Renders the Tama seated on an Itomaki pedestal as a wound kumihimo spindle whose
 * silk winding grows and brightens with how full the Tama is — the pedestal's
 * "storage fullness" readout, mirroring the loop→hank→bundle curve of {@link SpoolRackBER}.
 *
 * <p>Nothing is drawn when the pedestal is empty (the static model shows the bare
 * spindle rest). When a Tama is present:
 * <ul>
 *   <li>Tier-tinted flanges, core, and shaft are drawn from the Tama's own texture.</li>
 *   <li>A silk band winds around the core — taller and thicker as {@code total/capacity}
 *       rises, tinted by the dominant stored item, and rendered full-bright once it is
 *       past half full so a stocked pedestal reads like a lit storage cell.</li>
 *   <li>The whole spindle turns slowly, faster while actively unspooling (SPOOLING).</li>
 * </ul>
 *
 * All geometry is authored in 0–1 block space with the spindle centered on X/Z = 0.5.
 * The constants below are the tuning surface if the spindle needs to sit higher/lower.
 */
public class ItomakiBER implements BlockEntityRenderer<ItomakiBlockEntity> {

    private static final ResourceLocation SKEIN_TEX =
        ResourceLocation.fromNamespaceAndPath("musubi", "textures/item/skein.png");

    private static final int FULL_BRIGHT = 0xF000F0;

    // Spindle profile (block space). Y grows upward; the pedestal opening tops out near 0.75.
    private static final float BOT_FLANGE_LO = 0.62f, BOT_FLANGE_HI = 0.655f;
    private static final float CORE_LO       = 0.655f, CORE_HI      = 0.855f;
    private static final float TOP_FLANGE_LO = 0.855f, TOP_FLANGE_HI = 0.89f;
    private static final float SHAFT_LO      = 0.89f,  SHAFT_HI      = 0.985f;
    private static final float FLANGE_HALF   = 0.185f; // half-extent of the discs
    private static final float CORE_HALF     = 0.085f; // bare core half-extent
    private static final float SHAFT_HALF    = 0.05f;

    // Winding growth: at full it nearly fills the core height and swells toward the flanges.
    // Using a 10-stage curve so unspooling progress reads clearly in discrete jumps.
    private static final float WIND_HALF_MIN = 0.10f,  WIND_HALF_MAX = 0.168f;
    private static final float WIND_BOT_HI   = 0.745f, WIND_BOT_LO   = 0.662f; // lo = fuller (grows down)
    private static final float WIND_TOP_LO   = 0.765f, WIND_TOP_HI   = 0.85f;  // hi = fuller (grows up)
    // Lift the visible spindle slightly so it sits on the same blockbench anchor as the hidden spool cube.
    private static final float MODEL_Y_OFFSET = 1.0f / 16.0f;

    // Emissive thresholds (% full) where the winding brightens as a visual "checkpoint".
    private static final float[] GLOW_THRESHOLDS = { 0.25f, 0.50f, 0.75f };

    // Representative UV rectangles sampled from the shared tama item texture (tama.json layout).
    private static final float[] FLANGE_SIDE = { 0, 6, 6, 7 };
    private static final float[] FLANGE_CAP  = { 0, 0, 6, 6 };
    private static final float[] CORE_SIDE   = { 8, 0, 12, 8 };
    private static final float[] CORE_CAP    = { 8, 8, 12, 12 };
    private static final float[] SHAFT_SIDE  = { 0, 8, 2, 11 };
    private static final float[] SHAFT_CAP   = { 2, 8, 4, 10 };
    private static final float[] SILK_UV     = { 0, 0, 8, 8 };

    public ItomakiBER(BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(ItomakiBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int combinedLight, int combinedOverlay) {

        if (be.getLevel() == null || !be.hasTama()) return;
        ItemStack tama = be.getTama();
        if (!(tama.getItem() instanceof TamaItem tamaItem)) return;

        TamaStorage storage = tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
        int capacity = Math.max(1, tamaItem.storageCapacity(tama));
        float storageFill = Math.min(1f, storage.total() / (float) capacity);
        // 10-stage curve so unspooling progress jumps in discrete, visible steps.
        float visualFill = (float) Math.floor(storageFill * 10) / 10f;

        // Check if we're crossing a glow threshold (25%, 50%, 75% full).
        boolean shouldGlow = false;
        for (float threshold : GLOW_THRESHOLDS) {
            if (storageFill >= threshold && visualFill <= threshold) {
                shouldGlow = true;
                break;
            }
        }

        boolean spooling = be.getBlockState().getValue(ItomakiBlock.SPOOLING);
        float spinDeg = (be.getLevel().getGameTime() + partialTick) * (spooling ? 4.0f : 0.5f);

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(spinDeg));
        pose.translate(-0.5, 0, -0.5);
        pose.translate(0.0, MODEL_Y_OFFSET, 0.0);

        Matrix4f m = pose.last().pose();
        PoseStack.Pose entry = pose.last();

        // ── Spindle body (tier texture) ─────────────────────────────────────────
        int tierTex = tamaItem.tier;
        RenderType bodyType = RenderType.entityCutout(tierTexture(tierTex));
        VertexConsumer body = buffers.getBuffer(bodyType);

        drawBox(body, m, entry, FLANGE_HALF, BOT_FLANGE_LO, FLANGE_HALF, BOT_FLANGE_HI,
            FLANGE_SIDE, FLANGE_CAP, 255, 255, 255, 255, combinedLight);
        drawBox(body, m, entry, CORE_HALF, CORE_LO, CORE_HALF, CORE_HI,
            CORE_SIDE, CORE_CAP, 255, 255, 255, 255, combinedLight);
        drawBox(body, m, entry, FLANGE_HALF, TOP_FLANGE_LO, FLANGE_HALF, TOP_FLANGE_HI,
            FLANGE_SIDE, FLANGE_CAP, 255, 255, 255, 255, combinedLight);
        drawBox(body, m, entry, SHAFT_HALF, SHAFT_LO, SHAFT_HALF, SHAFT_HI,
            SHAFT_SIDE, SHAFT_CAP, 255, 255, 255, 255, combinedLight);

        // ── Silk winding (fullness) ─────────────────────────────────────────────
        if (storage.total() > 0) {
            int color = TamaItem.contentColor(tama);
            int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;

            float half = lerp(WIND_HALF_MIN, WIND_HALF_MAX, visualFill);
            float lo   = lerp(WIND_BOT_HI,   WIND_BOT_LO,   visualFill);
            float hi   = lerp(WIND_TOP_LO,   WIND_TOP_HI,   visualFill);
            // Glow when crossing a checkpoint (25/50/75 full) so the player sees progress,
            // and continue glowing once fully wrapped.
            int windLight = (shouldGlow || visualFill >= 0.75f) ? FULL_BRIGHT : combinedLight;

            VertexConsumer silk = buffers.getBuffer(RenderType.entityCutout(SKEIN_TEX));
            drawBox(silk, m, entry, half, lo, half, hi, SILK_UV, SILK_UV, r, g, b, 255, windLight);
        }

        pose.popPose();
    }

    private static ResourceLocation tierTexture(int tier) {
        String name = switch (tier) {
            case 1 -> "iron_tama";
            case 2 -> "silver_tama";
            case 3 -> "orihime_tama";
            default -> "bone_tama";
        };
        return ResourceLocation.fromNamespaceAndPath("musubi", "textures/item/" + name + ".png");
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Draws an axis-aligned box centered on X/Z = 0.5, spanning [0.5-hx, 0.5+hx] etc.,
     * with {@code side} UVs on the four vertical faces and {@code cap} UVs on top/bottom.
     */
    private static void drawBox(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
                                float hx, float y0, float hz, float y1,
                                float[] side, float[] cap,
                                int r, int g, int b, int a, int light) {
        float x0 = 0.5f - hx, x1 = 0.5f + hx;
        float z0 = 0.5f - hz, z1 = 0.5f + hz;

        // North (-Z)
        quad(vc, m, pose, x1, y1, z0, x0, y1, z0, x0, y0, z0, x1, y0, z0, side, r, g, b, a, light, 0, 0, -1);
        // South (+Z)
        quad(vc, m, pose, x0, y1, z1, x1, y1, z1, x1, y0, z1, x0, y0, z1, side, r, g, b, a, light, 0, 0, 1);
        // West (-X)
        quad(vc, m, pose, x0, y1, z0, x0, y1, z1, x0, y0, z1, x0, y0, z0, side, r, g, b, a, light, -1, 0, 0);
        // East (+X)
        quad(vc, m, pose, x1, y1, z1, x1, y1, z0, x1, y0, z0, x1, y0, z1, side, r, g, b, a, light, 1, 0, 0);
        // Up (+Y)
        quad(vc, m, pose, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, cap, r, g, b, a, light, 0, 1, 0);
        // Down (-Y)
        quad(vc, m, pose, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, cap, r, g, b, a, light, 0, -1, 0);
    }

    /** Emits one quad; {@code uv} is a 16-space rectangle {u0,v0,u1,v1} mapped corner-to-corner. */
    private static void quad(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float[] uv, int r, int g, int b, int a, int light,
                             float nx, float ny, float nz) {
        float u0 = uv[0] / 16f, v0 = uv[1] / 16f, u1 = uv[2] / 16f, v1 = uv[3] / 16f;
        vc.addVertex(m, x0, y0, z0).setColor(r, g, b, a).setUv(u0, v0)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x1, y1, z1).setColor(r, g, b, a).setUv(u1, v0)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x2, y2, z2).setColor(r, g, b, a).setUv(u1, v1)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(m, x3, y3, z3).setColor(r, g, b, a).setUv(u0, v1)
          .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
