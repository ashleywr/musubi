package com.sanhiruzu.musubi;

import com.mojang.logging.LogUtils;
import com.sanhiruzu.musubi.block.MusubiBlocks;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.item.MusubiCreativeTabs;
import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.loot.MusubiLootModifiers;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(Musubi.MOD_ID)
public class Musubi {

    public static final String MOD_ID = "musubi";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Musubi(IEventBus modEventBus, ModContainer modContainer) {
        MusubiDataComponents.COMPONENTS.register(modEventBus);
        MusubiItems.ITEMS.register(modEventBus);
        MusubiBlocks.BLOCKS.register(modEventBus);
        MusubiBlocks.BLOCK_ITEMS.register(modEventBus);
        MusubiBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        MusubiSounds.SOUNDS.register(modEventBus);
        MusubiLootModifiers.LOOT_MODIFIERS.register(modEventBus);
        MusubiCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(MusubiCapabilities::register);
        modEventBus.addListener(Musubi::commonSetup);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(MusubiClient::init);
            modEventBus.addListener(MusubiClient::registerItemColors);
            modEventBus.addListener(MusubiClient::registerRenderers);
            modEventBus.addListener(MusubiClient::registerAdditionalModels);
        }
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
            CauldronInteraction.WATER.map().put(MusubiItems.SILKWORM_COCOON.get(),
                (state, level, pos, player, hand, stack) -> {
                    if (!hasFire(level, pos)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    if (level.isClientSide) return ItemInteractionResult.SUCCESS;
                    int count = stack.getCount();
                    stack.shrink(count);
                    ItemStack silk = new ItemStack(MusubiItems.SILK_THREAD.get(), count);
                    player.getInventory().placeItemBackInInventory(silk);
                    LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                        SoundSource.BLOCKS, 0.5f, 1.1f);
                    return ItemInteractionResult.SUCCESS;
                })
        );
    }

    private static boolean hasFire(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.MAGMA_BLOCK)) return true;
        return below.is(BlockTags.CAMPFIRES) && below.getValue(CampfireBlock.LIT);
    }
}
