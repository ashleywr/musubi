package com.sanhiruzu.musubi.mixin;

import com.sanhiruzu.musubi.curio.MusubiCurios;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public class ItemEntityMergeMixin {

    @Inject(method = "mergeWithNeighbours", at = @At("HEAD"), cancellable = true)
    private void musubi$keepPickupSourcesStable(CallbackInfo ci) {
        if (MusubiCurios.shouldPreventItemMerge((ItemEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
