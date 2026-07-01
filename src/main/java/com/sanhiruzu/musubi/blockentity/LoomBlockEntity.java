package com.sanhiruzu.musubi.blockentity;

import com.sanhiruzu.musubi.block.LoomBlock;
import com.sanhiruzu.musubi.item.WeavingPatternItem;
import com.sanhiruzu.musubi.rei.IReiStorage;
import com.sanhiruzu.musubi.rei.SimpleReiStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * The loom — Musubi's first autocrafter.
 *
 * Install a Weaving Pattern (an item encoded with a target item) and the loom
 * repeatedly crafts that target: it resolves a crafting recipe whose result is
 * the target, pulls the ingredients from adjacent containers, spends REI per
 * craft, and deposits the result (pushing it into an adjacent container if one
 * has room, otherwise holding it in its output slot).
 *
 * REI is supplied passively by adjacent shrine stones (which push energy each
 * second); the loom is a pure consumer and never pushes REI back out.
 *
 * Limitation: the result is the recipe's default result item. Recipes whose
 * output depends on specific ingredient data (e.g. dyeing, fireworks) produce
 * their plain default and are not fully supported.
 */
public class LoomBlockEntity extends BlockEntity {

    public static final int REI_CAPACITY  = 500;
    public static final int REI_PER_CRAFT = 10;
    public static final int CRAFT_TICKS   = 40;

    private static final int PATTERN_SLOT = 0;
    private static final int OUTPUT_SLOT  = 1;

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (slot == PATTERN_SLOT) cachedTarget = null;
        }
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == PATTERN_SLOT && stack.getItem() instanceof WeavingPatternItem;
        }
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            // External automation may pull finished output but never the pattern.
            if (slot == PATTERN_SLOT) return ItemStack.EMPTY;
            return super.extractItem(slot, amount, simulate);
        }
    };

    private final SimpleReiStorage rei = new SimpleReiStorage(REI_CAPACITY);

    private int craftTimer    = 0;  // ticks remaining in the current craft cycle
    private int craftDuration = 0;  // total ticks for the current cycle (progress %)

    // Server-side recipe cache for the installed pattern's target.
    private ItemStack cachedTarget = null;
    private List<RecipeHolder<CraftingRecipe>> cachedCandidates = null;

    public LoomBlockEntity(BlockPos pos, BlockState state) {
        super(MusubiBlockEntities.LOOM.get(), pos, state);
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state, LoomBlockEntity be) {
        ItemStack target = WeavingPatternItem.getTarget(be.inventory.getStackInSlot(PATTERN_SLOT));

        boolean working = false;

        if (!target.isEmpty() && be.rei.getREI() >= REI_PER_CRAFT) {
            for (RecipeHolder<CraftingRecipe> holder : be.resolveCandidates(level, target)) {
                ItemStack result = holder.value().getResultItem(level.registryAccess());
                if (!be.canAcceptOutput(result)) continue;
                List<Reservation> plan = be.planIngredients(level, pos, holder.value());
                if (plan == null) continue;

                working = true;
                if (!state.getValue(LoomBlock.WEAVING)) {
                    be.craftDuration = CRAFT_TICKS;
                    be.craftTimer    = CRAFT_TICKS;
                    level.setBlock(pos, state.setValue(LoomBlock.WEAVING, true), 3);
                }
                be.craftTimer--;
                if (be.craftTimer <= 0) {
                    be.commitCraft(plan, result.copy());
                    be.rei.consume(REI_PER_CRAFT);
                    be.craftTimer    = CRAFT_TICKS;
                    be.craftDuration = CRAFT_TICKS;
                    be.pushOutput(level, pos);
                    be.setChanged();
                    level.sendBlockUpdated(pos, state, state, 3);
                }
                break;
            }
        }

        if (!working) {
            if (state.getValue(LoomBlock.WEAVING)) {
                level.setBlock(pos, state.setValue(LoomBlock.WEAVING, false), 3);
            }
            be.craftTimer    = 0;
            be.craftDuration = 0;
            be.pushOutput(level, pos); // flush leftover output while idle
        }

        if (be.rei.isDirty()) {
            be.rei.clearDirty();
            be.setChanged();
        }
    }

    /** Find (and cache) every crafting recipe whose result matches the pattern target. */
    private List<RecipeHolder<CraftingRecipe>> resolveCandidates(Level level, ItemStack target) {
        if (cachedTarget != null && cachedCandidates != null
                && ItemStack.isSameItemSameComponents(cachedTarget, target)) {
            return cachedCandidates;
        }
        cachedTarget = target.copy();
        List<RecipeHolder<CraftingRecipe>> found = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> holder : level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
            ItemStack result = holder.value().getResultItem(level.registryAccess());
            if (!result.isEmpty() && result.is(target.getItem())) {
                found.add(holder);
            }
        }
        cachedCandidates = found;
        return found;
    }

    /**
     * Try to reserve one item per ingredient from adjacent containers.
     * Returns the reservations, or null if any ingredient cannot be satisfied.
     */
    private List<Reservation> planIngredients(Level level, BlockPos pos, CraftingRecipe recipe) {
        List<Ingredient> needed = new ArrayList<>();
        for (Ingredient ing : recipe.getIngredients()) {
            if (!ing.isEmpty()) needed.add(ing);
        }
        if (needed.isEmpty()) return null; // no inputs → refuse (don't mint free items)

        List<Source> sources = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, pos.relative(dir), dir.getOpposite());
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) sources.add(new Source(handler, slot, stack));
            }
        }

        List<Reservation> plan = new ArrayList<>();
        for (Ingredient ing : needed) {
            boolean matched = false;
            for (Source src : sources) {
                if (src.remaining > 0 && ing.test(src.stack)
                        && !src.handler.extractItem(src.slot, 1, true).isEmpty()) {
                    src.remaining--;
                    plan.add(new Reservation(src.handler, src.slot));
                    matched = true;
                    break;
                }
            }
            if (!matched) return null;
        }
        return plan;
    }

    private void commitCraft(List<Reservation> plan, ItemStack result) {
        for (Reservation r : plan) {
            r.handler().extractItem(r.slot(), 1, false);
        }
        ItemStack out = inventory.getStackInSlot(OUTPUT_SLOT);
        if (out.isEmpty()) {
            inventory.setStackInSlot(OUTPUT_SLOT, result);
        } else {
            out.grow(result.getCount());
        }
    }

    private boolean canAcceptOutput(ItemStack result) {
        if (result.isEmpty()) return false;
        ItemStack out = inventory.getStackInSlot(OUTPUT_SLOT);
        if (out.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(out, result)
            && out.getCount() + result.getCount() <= out.getMaxStackSize();
    }

    private void pushOutput(Level level, BlockPos pos) {
        ItemStack out = inventory.getStackInSlot(OUTPUT_SLOT);
        if (out.isEmpty()) return;
        for (Direction dir : Direction.values()) {
            IItemHandler handler = level.getCapability(
                Capabilities.ItemHandler.BLOCK, pos.relative(dir), dir.getOpposite());
            if (handler == null || handler == inventory) continue;
            ItemStack remainder = ItemHandlerHelper.insertItem(handler, out, false);
            inventory.setStackInSlot(OUTPUT_SLOT, remainder);
            out = remainder;
            if (out.isEmpty()) return;
        }
    }

    private record Reservation(IItemHandler handler, int slot) {}

    private static final class Source {
        final IItemHandler handler;
        final int slot;
        final ItemStack stack;
        int remaining;
        Source(IItemHandler handler, int slot, ItemStack stack) {
            this.handler = handler;
            this.slot = slot;
            this.stack = stack;
            this.remaining = stack.getCount();
        }
    }

    // ── API ───────────────────────────────────────────────────────────────────

    public IItemHandler getItemHandler() { return inventory; }
    public IReiStorage getReiStorage()   { return rei; }

    public ItemStack getPattern() { return inventory.getStackInSlot(PATTERN_SLOT); }
    public ItemStack getOutput()  { return inventory.getStackInSlot(OUTPUT_SLOT); }

    /** Progress as 0.0–1.0; 0 if not weaving. */
    public float getCraftProgress() {
        if (craftDuration <= 0) return 0f;
        return 1f - (craftTimer / (float) craftDuration);
    }

    /** Install a single pattern; returns the previously installed pattern (may be empty). */
    public ItemStack installPattern(ItemStack stack, Level level, BlockPos pos, BlockState state) {
        ItemStack previous = inventory.getStackInSlot(PATTERN_SLOT).copy();
        ItemStack one = stack.copy();
        one.setCount(1);
        inventory.setStackInSlot(PATTERN_SLOT, one);
        stack.shrink(1);
        cachedTarget = null;
        setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
        return previous;
    }

    public ItemStack retrievePattern(Level level, BlockPos pos, BlockState state) {
        ItemStack held = inventory.getStackInSlot(PATTERN_SLOT).copy();
        inventory.setStackInSlot(PATTERN_SLOT, ItemStack.EMPTY);
        craftTimer    = 0;
        craftDuration = 0;
        cachedTarget  = null;
        if (state.getValue(LoomBlock.WEAVING)) {
            level.setBlock(pos, state.setValue(LoomBlock.WEAVING, false), 3);
        }
        setChanged();
        return held;
    }

    public ItemStack retrieveOutput(Level level, BlockPos pos, BlockState state) {
        ItemStack held = inventory.getStackInSlot(OUTPUT_SLOT).copy();
        inventory.setStackInSlot(OUTPUT_SLOT, ItemStack.EMPTY);
        setChanged();
        return held;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                level.addFreshEntity(new ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("inventory", inventory.serializeNBT(registries));
        tag.putInt("rei",         rei.getRaw());
        tag.putInt("craft_timer", craftTimer);
        tag.putInt("craft_dur",   craftDuration);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        rei.setRaw(tag.getInt("rei"));
        craftTimer    = tag.getInt("craft_timer");
        craftDuration = tag.getInt("craft_dur");
        cachedTarget  = null;
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
