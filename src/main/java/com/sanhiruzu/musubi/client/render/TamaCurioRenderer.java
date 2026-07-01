package com.sanhiruzu.musubi.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.renderer.texture.OverlayTexture;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/**
 * Renders a tama directly on the player's belt Curios slot.
 *
 * <p>The renderer draws the actual item stack so the current tama tier, tint, and strand
 * property all stay intact.
 */
public final class TamaCurioRenderer implements ICurioRenderer {

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
        ItemStack stack,
        SlotContext slotContext,
        PoseStack matrixStack,
        RenderLayerParent<T, M> renderLayerParent,
        MultiBufferSource renderTypeBuffer,
        int light,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        LivingEntity wearer = slotContext.entity();
        if (wearer == null || stack.isEmpty()) return;

        matrixStack.pushPose();
        ICurioRenderer.translateIfSneaking(matrixStack, wearer);
        ICurioRenderer.rotateIfSneaking(matrixStack, wearer);

        float time = ageInTicks + partialTicks;
        float sway = Mth.sin(time * 0.12F + slotContext.index() * 0.7F) * 10.0F;
        float bob = Mth.sin(time * 0.18F + slotContext.index()) * 0.03F;

        // Tuck the tama against the right side of the body, just above the hip.
        matrixStack.translate(0.28D, 0.78D + bob, 0.11D);
        matrixStack.mulPose(Axis.YP.rotationDegrees(180.0F + sway));
        matrixStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        matrixStack.scale(0.58F, 0.58F, 0.58F);

        Minecraft.getInstance().getItemRenderer().renderStatic(
            wearer,
            stack,
            ItemDisplayContext.FIXED,
            false,
            matrixStack,
            renderTypeBuffer,
            wearer.level(),
            light,
            OverlayTexture.NO_OVERLAY,
            slotContext.index()
        );

        matrixStack.popPose();
    }
}
