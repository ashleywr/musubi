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
import net.minecraft.client.renderer.ShaderInstance;
import com.sanhiruzu.musubi.util.MoonReflectionMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the moon's reflection onto still-water surfaces.
 *
 * <p>A thin overlay quad is laid over every sky-lit source-water surface near the
 * camera and drawn with the {@code musubi:water_moon_reflect} core shader. The
 * shader does the optics per fragment — reflecting the view ray off a procedurally
 * rippled normal and matching it against the moon's real sky position — so the
 * bright path lands where a mirror would put it and shifts as the player or the
 * moon moves. Depth testing stays on (terrain occludes the reflection correctly);
 * blending is additive so moonlight only ever brightens the water.</p>
 *
 * <p>Water surfaces are rescanned on an interval rather than per frame; the mesh
 * itself is rebuilt each frame because it is baked camera-relative.</p>
 */
public final class MoonWaterRenderer {

    private static final int SCAN_INTERVAL = 20;  // ticks between water rescans (~1s)
    private static final int SCAN_RADIUS   = 32;  // horizontal blocks around the camera
    private static final int SCAN_UP       = 4;   // blocks above the camera to search
    private static final int SCAN_DOWN     = 16;  // blocks below the camera to search
    private static final float EDGE_FEATHER = 6.0F;   // fade band at the scan boundary, blocks
    private static final float SURFACE_LIFT = 0.004F; // keeps the overlay from z-fighting the water

    /** Cached still-water surfaces as {blockX, surfaceY, blockZ}. */
    private static final List<float[]> SURFACES = new ArrayList<>();
    private static long lastScanTick = -SCAN_INTERVAL;

    private MoonWaterRenderer() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!event.getStage().toString().equals("minecraft:after_entities")) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || WaterMoonShader.get() == null) return;

        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (!WaterMoonShader.shouldApply(level)) {
            SURFACES.clear();
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        long now = level.getGameTime();
        if (now - lastScanTick >= SCAN_INTERVAL) {
            rescanSurfaces(level, camera);
            lastScanTick = now;
        }
        if (SURFACES.isEmpty()) return;

        MeshData mesh = buildMesh(event.getPoseStack(), camera);
        if (mesh == null) return;

        RenderSystem.enableBlend();
        // Additive: the reflection only ever adds moonlight, it never darkens water.
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(WaterMoonShader::get);

        ShaderInstance shader = WaterMoonShader.get();
        if (shader != null) {
            WaterMoonShader.updateUniforms(level, camera, partial);

            // Set matrix uniforms from the current RenderSystem state
            if (shader.safeGetUniform("ProjMat") != null)
                shader.safeGetUniform("ProjMat").set(RenderSystem.getProjectionMatrix());
            if (shader.safeGetUniform("ModelViewMat") != null)
                shader.safeGetUniform("ModelViewMat").set(RenderSystem.getModelViewMatrix());
        }

        BufferUploader.drawWithShader(mesh);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Bakes one overlay quad per cached surface, camera-relative. Vertex alpha
     * feathers quads near the scan radius so the effect fades out instead of
     * ending at a hard square boundary.
     */
    private static MeshData buildMesh(PoseStack poseStack, Vec3 camera) {
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        BufferBuilder builder = Tesselator.getInstance()
            .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (float[] s : SURFACES) {
            float x = s[0], y = s[1], z = s[2];
            int a00 = cornerAlpha(x, z, camera);
            int a10 = cornerAlpha(x + 1, z, camera);
            int a11 = cornerAlpha(x + 1, z + 1, camera);
            int a01 = cornerAlpha(x, z + 1, camera);
            if (a00 + a10 + a11 + a01 == 0) continue;

            builder.addVertex(matrix, x, y, z).setColor(255, 255, 255, a00);
            builder.addVertex(matrix, x + 1, y, z).setColor(255, 255, 255, a10);
            builder.addVertex(matrix, x + 1, y, z + 1).setColor(255, 255, 255, a11);
            builder.addVertex(matrix, x, y, z + 1).setColor(255, 255, 255, a01);
        }

        poseStack.popPose();
        // build() returns null when every quad was culled; the caller skips the draw.
        return builder.build();
    }

    private static int cornerAlpha(float x, float z, Vec3 camera) {
        float dx = x - (float) camera.x;
        float dz = z - (float) camera.z;
        float dist = (float) Math.sqrt(dx * dx + dz * dz);
        float fade = Mth.clamp((SCAN_RADIUS - dist) / EDGE_FEATHER, 0.0F, 1.0F);
        return (int) (fade * 255.0F);
    }

    /**
     * Finds the topmost sky-lit source-water surface in each column of a vertical
     * window around the camera. Flowing or roofed-over water never reflects, which
     * also keeps the reflection an honest cue for valid moon-rite water.
     */
    private static void rescanSurfaces(ClientLevel level, Vec3 center) {
        SURFACES.clear();
        int cx = Mth.floor(center.x);
        int cy = Mth.floor(center.y);
        int cz = Mth.floor(center.z);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int x = cx + dx;
                int z = cz + dz;
                for (int y = cy + SCAN_UP; y >= cy - SCAN_DOWN; y--) {
                    m.set(x, y, z);
                    if (!level.getBlockState(m).is(Blocks.WATER)) continue;
                    // Same water rules the server-side rite uses (still, open, sky-lit).
                    if (MoonReflectionMath.isReflectiveWater(level, m)) {
                        SURFACES.add(new float[] {
                            x, (float) MoonReflectionMath.surfaceY(level, m) + SURFACE_LIFT, z });
                    }
                    break;
                }
            }
        }
    }
}
