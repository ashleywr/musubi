package com.sanhiruzu.musubi.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sanhiruzu.musubi.client.render.TamaCollectionVfx;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {

    @Inject(
        method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void musubi$hideCollectionItem(ItemEntity entity, float entityYaw, PoseStack poseStack,
                                           MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (TamaCollectionVfx.shouldHideItemEntity(entity.getId())) {
            ci.cancel();
        }
    }
}
