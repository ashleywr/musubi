package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.sanhiruzu.musubi.block.SpinningWheelBlock;
import com.sanhiruzu.musubi.blockentity.SpinningWheelBlockEntity;
import com.sanhiruzu.musubi.sound.MusubiSounds;
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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.Map;

/**
 * Renders the rotating wheel disc of a Spinning Wheel on top of its static frame model.
 *
 * The disc lives in its own model ({@link #WHEEL_MODEL}), registered as a standalone
 * model so it bakes without being attached to any blockstate. It spins around the Z axis
 * through the wheel center (8,8,7 in pixel space), easing up to speed while the block is
 * working (SPINNING=true) and coasting to a stop when it finishes — see {@link WheelSpin}.
 */
public class SpinningWheelBER implements BlockEntityRenderer<SpinningWheelBlockEntity> {

    /** Standalone model baked for the spinning disc; also registered in MusubiClient. */
    public static final ModelResourceLocation WHEEL_MODEL = new ModelResourceLocation(
        ResourceLocation.fromNamespaceAndPath("musubi", "block/spinning_wheel_wheel"),
        "standalone");

    // Wheel center in 0–1 block space (model defined for NORTH facing).
    private static final float CENTER_X = 0.5f;
    private static final float CENTER_Y = 0.5f;
    private static final float CENTER_Z = 7f / 16f;

    /** Per-position spin state. Entries are never evicted; the leak is negligible. */
    private static final Map<BlockPos, WheelSpin> SPINS = new HashMap<>();

    public SpinningWheelBER(BlockEntityRendererProvider.Context ctx) { }

    @Override
    public void render(SpinningWheelBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int combinedLight, int combinedOverlay) {

        if (be.getLevel() == null) return;

        Minecraft mc = Minecraft.getInstance();
        BakedModel wheel = mc.getModelManager().getModel(WHEEL_MODEL);
        if (wheel == mc.getModelManager().getMissingModel()) return;

        BlockState state = be.getBlockState();
        boolean   spinning = state.getValue(SpinningWheelBlock.SPINNING);
        Direction facing   = state.getValue(SpinningWheelBlock.FACING);

        BlockPos pos = be.getBlockPos();
        WheelSpin spin = SPINS.computeIfAbsent(pos, k -> new WheelSpin());
        spin.updateTo(be.getLevel().getGameTime(), spinning);
        float angle = spin.lerpAngle(partialTick);

        // Transition sounds — client-side only, fired once per state change.
        if (spin.consumeStart() && Minecraft.getInstance().level != null) {
            Minecraft.getInstance().level.playLocalSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                MusubiSounds.WHEEL_SPIN_UP.get(), SoundSource.BLOCKS, 0.8f, 1.0f, false);
        }
        if (spin.consumeStop() && Minecraft.getInstance().level != null) {
            Minecraft.getInstance().level.playLocalSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                MusubiSounds.WHEEL_COAST_STOP.get(), SoundSource.BLOCKS, 0.6f, 0.85f, false);
        }

        pose.pushPose();

        // Mirror the facing rotation the blockstates JSON applies to the static model.
        pose.translate(0.5, 0.5, 0.5);
        int yDeg = switch (facing) { case SOUTH -> 180; case WEST -> 270; case EAST -> 90; default -> 0; };
        if (yDeg != 0) pose.mulPose(Axis.YP.rotationDegrees(yDeg));
        pose.translate(-0.5, -0.5, -0.5);

        // Spin around Z through the wheel center.
        pose.translate(CENTER_X, CENTER_Y, CENTER_Z);
        pose.mulPose(Axis.ZP.rotationDegrees(angle));
        pose.translate(-CENTER_X, -CENTER_Y, -CENTER_Z);

        ModelBlockRenderer renderer = mc.getBlockRenderer().getModelRenderer();
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        renderer.renderModel(pose.last(), vc, state, wheel,
            1f, 1f, 1f, combinedLight, combinedOverlay, ModelData.EMPTY, RenderType.cutout());

        pose.popPose();
    }
}
