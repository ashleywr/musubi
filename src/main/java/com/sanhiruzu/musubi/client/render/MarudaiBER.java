package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sanhiruzu.musubi.block.MarudaiBlock;
import com.sanhiruzu.musubi.blockentity.MarudaiBlockEntity;
import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders the four tama bobbins orbiting a Marudai's mirror disc on top of its static
 * base model.
 *
 * The tama live in their own model ({@link #TAMA_MODEL}), registered as a standalone
 * model so it bakes without being attached to any blockstate. They orbit the Y axis
 * through the block center (8,_,8 in pixel space), easing up to speed while the block
 * is working (WINDING=true) and coasting to a stop when it finishes — see
 * {@link MarudaiSpin}. Mirrors {@link SpinningWheelBER}, but rotation is around Y.
 */
public class MarudaiBER implements BlockEntityRenderer<MarudaiBlockEntity> {

    /** Standalone model baked for the orbiting tama; also registered in MusubiClient. */
    public static final ModelResourceLocation TAMA_MODEL = new ModelResourceLocation(
        ResourceLocation.fromNamespaceAndPath("musubi", "block/marudai_tama"),
        "standalone");

    // Block center in 0–1 block space (only X/Z matter for a Y-axis orbit).
    private static final float CENTER_X = 0.5f;
    private static final float CENTER_Z = 0.5f;

    /** Per-position orbit state. Entries are never evicted; the leak is negligible. */
    private static final Map<BlockPos, MarudaiSpin> SPINS = new HashMap<>();

    public MarudaiBER(BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(MarudaiBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int combinedLight, int combinedOverlay) {

        if (be.getLevel() == null) return;

        Minecraft mc = Minecraft.getInstance();
        BakedModel tama = mc.getModelManager().getModel(TAMA_MODEL);
        if (tama == mc.getModelManager().getMissingModel()) return;

        BlockState state = be.getBlockState();
        boolean   winding = state.getValue(MarudaiBlock.WINDING);
        Direction facing  = state.getValue(MarudaiBlock.FACING);

        BlockPos pos = be.getBlockPos();
        MarudaiSpin spin = SPINS.computeIfAbsent(pos, k -> new MarudaiSpin());
        spin.updateTo(be.getLevel().getGameTime(), winding);
        float angle = spin.lerpAngle(partialTick);
        double bob = spin.bobOffset(partialTick);

        pose.pushPose();

        // Mirror the facing rotation the blockstate JSON applies to the base model.
        pose.translate(0.5, 0.5, 0.5);
        int yDeg = switch (facing) { case SOUTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; };
        if (yDeg != 0) pose.mulPose(Axis.YP.rotationDegrees(yDeg));
        pose.translate(-0.5, -0.5, -0.5);

        // Orbit the tama around the vertical axis through the block center,
        // with a subtle vertical bob (world Y is preserved by the Y-axis rotations).
        pose.translate(CENTER_X, bob, CENTER_Z);
        pose.mulPose(Axis.YP.rotationDegrees(angle));
        pose.translate(-CENTER_X, 0, -CENTER_Z);

        ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        renderer.renderModel(pose.last(), vc, state, tama,
            1f, 1f, 1f, combinedLight, combinedOverlay, ModelData.EMPTY, RenderType.cutout());

        if (winding) {
            spawnPullParticles(be);
            spawnCenterPullParticles(be);
        }

        pose.popPose();
    }

    private void spawnPullParticles(MarudaiBlockEntity be) {
        if (be.getLevel() == null) return;

        long time = be.getLevel().getGameTime();
        if ((time + be.getBlockPos().asLong()) % 2L != 0L) return;

        BlockPos pos = be.getBlockPos();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.95;
        double cz = pos.getZ() + 0.5;
        DustParticleOptions silk = new DustParticleOptions(new Vector3f(0.97f, 0.94f, 0.87f), 0.85f);

        int spawned = 0;
        for (int dx = -4; dx <= 4 && spawned < 3; dx++) {
            for (int dy = -2; dy <= 2 && spawned < 3; dy++) {
                for (int dz = -4; dz <= 4 && spawned < 3; dz++) {
                    BlockPos rackPos = pos.offset(dx, dy, dz);
                    if (!(be.getLevel().getBlockEntity(rackPos) instanceof SpoolRackBlockEntity rack)) continue;
                    if (!rack.hasContents()) continue;

                    double sx = rackPos.getX() + 0.5;
                    double sy = rackPos.getY() + 0.65;
                    double sz = rackPos.getZ() + 0.5;
                    for (int i = 0; i < 3; i++) {
                        double t = (i + 1) / 4.0;
                        be.getLevel().addParticle(silk,
                            sx + (cx - sx) * t,
                            sy + (cy - sy) * t,
                            sz + (cz - sz) * t,
                            (cx - sx) * 0.03,
                            0.01,
                            (cz - sz) * 0.03);
                    }
                    spawned++;
                }
            }
        }
    }

    private void spawnCenterPullParticles(MarudaiBlockEntity be) {
        if (be.getLevel() == null) return;

        long time = be.getLevel().getGameTime();
        if ((time + be.getBlockPos().asLong()) % 3L != 0L) return;

        BlockPos pos = be.getBlockPos();
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.92;
        double cz = pos.getZ() + 0.5;
        DustParticleOptions silk = new DustParticleOptions(new Vector3f(0.97f, 0.94f, 0.87f), 0.75f);

        for (int i = 0; i < 4; i++) {
            double angle = (time * 0.2 + i) * (Math.PI * 0.5);
            double radius = 0.18 + (i * 0.03);
            be.getLevel().addParticle(
                silk,
                cx + Math.cos(angle) * radius,
                cy - 0.08 + (i * 0.015),
                cz + Math.sin(angle) * radius,
                -Math.cos(angle) * 0.02,
                0.01,
                -Math.sin(angle) * 0.02
            );
        }
    }
}
