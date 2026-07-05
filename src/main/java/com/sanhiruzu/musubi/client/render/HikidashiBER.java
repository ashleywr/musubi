package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sanhiruzu.musubi.blockentity.HikidashiBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Parrot;

/**
 * Renders Minecraft's parrot model on the Hikidashi pedestal.
 * Placeholder until a proper crane model is created.
 */
public class HikidashiBER implements BlockEntityRenderer<HikidashiBlockEntity> {

    public HikidashiBER(BlockEntityRendererProvider.Context context) { }

    @Override
    public void render(HikidashiBlockEntity blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.getLevel() == null) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);

        // Gentle bobbing animation
        float bobHeight = Mth.sin((blockEntity.getLevel().getGameTime() + partialTick) * 0.05f) * 0.1f;
        poseStack.translate(0, bobHeight, 0);

        // Slow rotation
        float rotationDegrees = (blockEntity.getLevel().getGameTime() + partialTick) * 0.5f;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDegrees));

        // Create a temporary parrot entity to render
        Parrot parrot = new Parrot(net.minecraft.world.entity.EntityType.PARROT, blockEntity.getLevel());
        parrot.setPos(blockEntity.getBlockPos().getCenter());

        // Render the parrot using Minecraft's entity renderer
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.render(parrot, 0, 0, 0, 0, partialTick, poseStack, bufferSource, packedLight);

        poseStack.popPose();
    }
}
