package com.sanhiruzu.musubi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.sanhiruzu.musubi.client.render.TamaSpin;
import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a Y-axis spin to TamaItem renders in the GUI.
 *
 * The rotation is injected before the item's display transform runs, so it adds to
 * the existing Y component of the GUI display transform (rotation [25, 45, 0]).
 * The effect is equivalent to viewing the bobbin from a continuously changing angle —
 * the 25° viewer tilt is preserved while the bobbin spins around its shaft axis.
 *
 * The pushed/popped flag is safe because ItemRenderer is a singleton on the render thread.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    @Unique
    private boolean musubi$pushed = false;

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("HEAD")
    )
    private void musubi$preRender(
            ItemStack stack, ItemDisplayContext displayContext, boolean leftHand,
            PoseStack poseStack, MultiBufferSource buffer,
            int combinedLight, int combinedOverlay, BakedModel model,
            CallbackInfo ci) {
        if (displayContext != ItemDisplayContext.GUI) return;
        if (!(stack.getItem() instanceof TamaItem)) return;

        float angle = TamaSpin.INSTANCE.getAngle();
        if (angle == 0f) return;

        poseStack.pushPose();
        musubi$pushed = true;
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
    }

    @Inject(
        method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V",
        at = @At("RETURN")
    )
    private void musubi$postRender(
            ItemStack stack, ItemDisplayContext displayContext, boolean leftHand,
            PoseStack poseStack, MultiBufferSource buffer,
            int combinedLight, int combinedOverlay, BakedModel model,
            CallbackInfo ci) {
        if (musubi$pushed) {
            poseStack.popPose();
            musubi$pushed = false;
        }
    }
}
