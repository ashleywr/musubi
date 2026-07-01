package com.sanhiruzu.musubi;

import com.mojang.logging.LogUtils;
import com.sanhiruzu.musubi.block.MusubiBlocks;
import com.sanhiruzu.musubi.blockentity.MusubiBlockEntities;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.item.MusubiCreativeTabs;
import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.loot.MusubiLootModifiers;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import com.sanhiruzu.musubi.util.PlayerInventoryUtil;
import com.sanhiruzu.musubi.worldgen.MusubiStructures;
import com.sanhiruzu.musubi.worldgen.MusubiStructures;
import com.sanhiruzu.musubi.network.MoonRiteVfxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
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
        MusubiStructures.STRUCTURE_TYPES.register(modEventBus);
        MusubiStructures.STRUCTURE_PIECES.register(modEventBus);
        MusubiDataComponents.COMPONENTS.register(modEventBus);
        MusubiItems.ITEMS.register(modEventBus);
        MusubiBlocks.BLOCKS.register(modEventBus);
        MusubiBlocks.BLOCK_ITEMS.register(modEventBus);
        MusubiBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        com.sanhiruzu.musubi.recipe.MusubiRecipes.SERIALIZERS.register(modEventBus);
        MusubiSounds.SOUNDS.register(modEventBus);
        MusubiLootModifiers.LOOT_MODIFIERS.register(modEventBus);
        MusubiCreativeTabs.TABS.register(modEventBus);
        MusubiStructures.STRUCTURE_TYPES.register(modEventBus);
        MusubiStructures.STRUCTURE_PIECES.register(modEventBus);

        modEventBus.addListener(MusubiCapabilities::register);
        modEventBus.addListener(Musubi::commonSetup);
        modEventBus.addListener(com.sanhiruzu.musubi.network.MusubiNetwork::register);
        NeoForge.EVENT_BUS.addListener(Musubi::onRightClickBlock);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(MusubiClient::init);
            modEventBus.addListener(MusubiClient::registerItemColors);
            modEventBus.addListener(MusubiClient::registerRenderers);
            modEventBus.addListener(MusubiClient::registerAdditionalModels);
            modEventBus.addListener(MusubiClient::registerGuiLayers);
            modEventBus.addListener(com.sanhiruzu.musubi.client.MusubiKeybinds::register);
        }
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(com.sanhiruzu.musubi.curio.MusubiCurios::register);
        event.enqueueWork(() ->
            CauldronInteraction.WATER.map().put(MusubiItems.SILKWORM_COCOON.get(),
                (state, level, pos, player, hand, stack) -> {
                    if (!hasFire(level, pos)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                    if (level.isClientSide) return ItemInteractionResult.SUCCESS;
                    int count = stack.getCount();
                    stack.shrink(count);
                    ItemStack silk = new ItemStack(MusubiItems.SILK_THREAD.get(), count);
                    PlayerInventoryUtil.giveDirect(player, silk);
                    LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                    level.playSound(null, pos, SoundEvents.BUCKET_EMPTY,
                        SoundSource.BLOCKS, 0.5f, 1.1f);
                    return ItemInteractionResult.SUCCESS;
                })
        );
    }

    private static boolean isMoonRiteTarget(Level level, BlockPos pos, ItemStack stack) {
        if (!stack.is(Items.FEATHER)) return false;
        if (!level.getBlockState(pos).is(Blocks.WATER)) return false;
        if (!level.getBlockState(pos).getFluidState().isSource()) return false;
        if (!level.canSeeSky(pos.above())) return false;
        if (level.isRainingAt(pos.above())) return false;
        return level.isNight() && level.getMoonBrightness() >= 0.25F;
    }

    private static void onRightClickBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        var player = event.getEntity();
        Level level = player.level();
        BlockPos pos = event.getPos();
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!isMoonRiteTarget(level, pos, stack)) return;

        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        event.setCanceled(true);
        if (level.isClientSide) return;

        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        PlayerInventoryUtil.giveDirect(player, new ItemStack(MusubiItems.BONE_TAMA.get()));

        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.BLOCKS, 0.8f, 1.35f);
        if (level instanceof ServerLevel sl) {
            PacketDistributor.sendToAllPlayers(MoonRiteVfxPayload.create(pos, sl.getGameTime(), 48));
            sl.sendParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5,
                10, 0.18, 0.08, 0.18, 0.01);
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                pos.getX() + 0.5, pos.getY() + 0.85, pos.getZ() + 0.5,
                12, 0.22, 0.08, 0.22, 0.08);
        }
        player.displayClientMessage(Component.translatable("musubi.moon_ritual.complete"), true);
    }

    private static boolean hasFire(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.MAGMA_BLOCK)) return true;
        return below.is(BlockTags.CAMPFIRES) && below.getValue(CampfireBlock.LIT);
    }
}
