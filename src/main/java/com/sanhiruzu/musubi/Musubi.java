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
import com.sanhiruzu.musubi.network.MoonRiteVfxPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
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
        NeoForge.EVENT_BUS.addListener(com.sanhiruzu.musubi.rite.MoonRite::onEntityTick);
        NeoForge.EVENT_BUS.addListener(Musubi::onRegisterCommands);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(MusubiClient::init);
            modEventBus.addListener(MusubiClient::registerItemColors);
            modEventBus.addListener(MusubiClient::registerRenderers);
            modEventBus.addListener(MusubiClient::registerAdditionalModels);
            modEventBus.addListener(MusubiClient::registerGuiLayers);
            modEventBus.addListener(com.sanhiruzu.musubi.client.MusubiKeybinds::register);
            modEventBus.addListener((net.neoforged.neoforge.client.event.RegisterShadersEvent e) -> {
                try {
                    com.sanhiruzu.musubi.client.render.WaterMoonShader.registerShader(e);
                } catch (Exception ex) {
                    LOGGER.error("Failed to register water moon shader", ex);
                }
            });
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

    /**
     * Debug command: {@code /musubi_moonrite} fires the moon-rite VFX at the block the player is
     * standing on, bypassing all trigger conditions (moon phase, time, weather, aiming). Handy for
     * iterating on the reflection visuals without hunting for a moonlit ocean.
     */
    private static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder
                .<net.minecraft.commands.CommandSourceStack>literal("musubi_moonrite")
                .executes(ctx -> {
                    net.minecraft.commands.CommandSourceStack src = ctx.getSource();
                    if (!(src.getEntity() instanceof Player player)) {
                        src.sendFailure(Component.literal("Must be run by a player."));
                        return 0;
                    }
                    BlockPos pos = player.blockPosition().below();
                    if (player.level() instanceof ServerLevel sl) {
                        PacketDistributor.sendToAllPlayers(MoonRiteVfxPayload.create(pos, sl.getGameTime(), 48));
                    }
                    src.sendSuccess(() -> Component.literal("Forced moon-rite VFX at " + pos), false);
                    return 1;
                })
        );

        // Debug: /musubi_comet [durationSeconds] — send a comet across the sky for everyone.
        event.getDispatcher().register(
            com.mojang.brigadier.builder.LiteralArgumentBuilder
                .<net.minecraft.commands.CommandSourceStack>literal("musubi_comet")
                .executes(ctx -> launchComet(ctx.getSource(), 60))
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder
                    .<net.minecraft.commands.CommandSourceStack, Integer>argument("durationSeconds",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(5, 1200))
                    .executes(ctx -> launchComet(ctx.getSource(),
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "durationSeconds"))))
        );
    }

    /**
     * Broadcasts a comet crossing to every player. The trajectory rises in the
     * northeast, arcs high across the sky, and sets in the southwest — the position
     * at any instant derives from the shared game time, so all clients stay in sync.
     */
    private static int launchComet(net.minecraft.commands.CommandSourceStack src, int durationSeconds) {
        ServerLevel level = src.getLevel();
        PacketDistributor.sendToAllPlayers(com.sanhiruzu.musubi.network.CometVfxPayload.create(
            level.getGameTime(), durationSeconds * 20,
            140.0F, 8.0F,   // rises: azimuth 140 (NE-ish in yaw terms), 8 deg above horizon
            320.0F, 6.0F)); // sets: opposite side of the sky; the great circle peaks high between
        src.sendSuccess(() -> Component.literal(
            "Comet crossing launched (" + durationSeconds + "s)"), false);
        return 1;
    }

    private static boolean hasFire(Level level, BlockPos pos) {
        BlockState below = level.getBlockState(pos.below());
        if (below.is(Blocks.FIRE) || below.is(Blocks.SOUL_FIRE) || below.is(Blocks.MAGMA_BLOCK)) return true;
        return below.is(BlockTags.CAMPFIRES) && below.getValue(CampfireBlock.LIT);
    }
}
