package com.sanhiruzu.musubi.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sanhiruzu.musubi.item.MusubiItems;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

import com.google.common.base.Suppliers;
import java.util.function.Supplier;

public class AddSilkwormModifier extends LootModifier {

    public static final Supplier<MapCodec<AddSilkwormModifier>> CODEC = Suppliers.memoize(() ->
        RecordCodecBuilder.<AddSilkwormModifier>mapCodec(inst ->
            codecStart(inst).apply(inst, AddSilkwormModifier::new)));

    private static final float DROP_CHANCE = 0.05f;

    protected AddSilkwormModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        BlockState state = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        if (state == null || !state.is(Blocks.CHERRY_LEAVES)) return generatedLoot;

        // Only when broken with a tool, not bare-hand.
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool == null || tool.isEmpty()) return generatedLoot;

        if (context.getRandom().nextFloat() < DROP_CHANCE) {
            generatedLoot.add(new ItemStack(MusubiItems.SILKWORM.get()));
        }
        return generatedLoot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC.get();
    }
}
