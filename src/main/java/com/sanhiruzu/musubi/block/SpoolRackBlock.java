package com.sanhiruzu.musubi.block;

import com.mojang.serialization.MapCodec;
import com.sanhiruzu.musubi.blockentity.SpoolRackBlockEntity;
import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.SkeinContents;
import com.sanhiruzu.musubi.item.SkeinItem;
import com.sanhiruzu.musubi.sound.MusubiSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Stores up to four item types (SLOTS × SLOT_CAPACITY items each).
 *
 * FACING is the direction the rack's display face points — set to face the player
 * on placement so the spool icons (rendered by a BER after the model is built) are
 * always visible from the natural viewing angle.
 *
 * Right-click with a skein   → deposit skein contents; skein stays in hand with
 *                              remainder if the rack fills for some variants.
 * Right-click with empty hand → take the full skein from the first non-empty peg.
 * Shift + right-click         → take all skeins from all pegs.
 */
public class SpoolRackBlock extends BaseEntityBlock {

    public static final MapCodec<SpoolRackBlock> CODEC = simpleCodec(SpoolRackBlock::new);
    public static final DirectionProperty FACING  = BlockStateProperties.HORIZONTAL_FACING;
    /** True when the rack is mounted on the underside of a block (pegs hang downward). */
    public static final BooleanProperty   HANGING = BooleanProperty.create("hanging");
    /** True when the rack holds at least one skein/thread entry. */
    public static final BooleanProperty   LOADED  = BooleanProperty.create("loaded");

    private record BoxDef(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    // Approximate the rack with a stepped profile instead of a full cube. The pegs are
    // visual detail, but the body should still feel like a wall-mounted textile shelf.
    private static final BoxDef[] PROFILE_BOXES = {
        new BoxDef(0, 0, 14, 16, 16, 16),
        new BoxDef(0, 10, 8, 16, 14, 14),
        new BoxDef(0, 13, 4, 16, 16, 8)
    };

    private static final VoxelShape[] WALL_SHAPES = buildShapes(false);
    private static final VoxelShape[] HANGING_SHAPES = buildShapes(true);

    @Override
    public MapCodec<SpoolRackBlock> codec() {
        return CODEC;
    }

    public SpoolRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(HANGING, false)
            .setValue(LOADED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HANGING, LOADED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean hanging = context.getClickedFace() == Direction.DOWN;
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(HANGING, hanging)
            .setValue(LOADED, false);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HANGING)) {
            return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
        }
        Direction behind = state.getValue(FACING).getOpposite();
        return Block.canSupportCenter(level, pos.relative(behind), behind.getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpoolRackBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getRackShape(state);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getRackShape(state);
    }

    @Override
    public VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getRackShape(state);
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getRackShape(state);
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {

        // Dump a Tama's bound essence onto the rack.
        if (stack.getItem() instanceof com.sanhiruzu.musubi.item.TamaItem) {
            if (level.isClientSide) return ItemInteractionResult.SUCCESS;
            SpoolRackBlockEntity rack = getBlockEntity(level, pos);
            if (rack == null) return ItemInteractionResult.FAIL;
            return dumpTama(stack, state, level, pos, rack);
        }

        if (!(stack.getItem() instanceof SkeinItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        SpoolRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return ItemInteractionResult.FAIL;

        SkeinContents contents = stack.get(MusubiDataComponents.SKEIN_CONTENTS);
        if (contents == null || contents.count() == 0) return ItemInteractionResult.FAIL;

        SkeinContents remainder = be.depositSkein(contents);
        if (remainder == null) {
            stack.shrink(1);
        } else {
            stack.set(MusubiDataComponents.SKEIN_CONTENTS, remainder);
        }
        BlockState updated = level.getBlockState(pos);
        level.sendBlockUpdated(pos, updated, updated, 3);
        level.playSound(null, pos, MusubiSounds.RACK_DEPOSIT.get(), SoundSource.BLOCKS,
            0.8f, 0.9f + level.random.nextFloat() * 0.2f);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {

        if (level.isClientSide) return InteractionResult.SUCCESS;

        SpoolRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return InteractionResult.PASS;

        if (player.isShiftKeyDown()) {
            be.extractAll(player);
        } else {
            be.extractOne(player);
        }
        BlockState updated = level.getBlockState(pos);
        level.sendBlockUpdated(pos, updated, updated, 3);
        return InteractionResult.SUCCESS;
    }

    // ── Comparator output ─────────────────────────────────────────────────────

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, net.minecraft.world.level.Level level, BlockPos pos) {
        SpoolRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return 0;
        int total = 0;
        for (int i = 0; i < SpoolRackBlockEntity.SLOTS; i++) total += be.getSlotCount(i);
        if (total == 0) return 0;
        int maxTotal = SpoolRackBlockEntity.SLOTS * SpoolRackBlockEntity.SLOT_CAPACITY;
        return 1 + (int) (total / (float) maxTotal * 14);
    }

    // ── Break behaviour ───────────────────────────────────────────────────────

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            SpoolRackBlockEntity be = getBlockEntity(level, pos);
            if (be != null) be.dropContents(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * Dumps a Tama's bound essence into the rack, depositing each type and writing back
     * any remainder that did not fit. The Tama itself stays in hand.
     */
    private ItemInteractionResult dumpTama(
            ItemStack tama, BlockState state, Level level, BlockPos pos, SpoolRackBlockEntity rack) {
        com.sanhiruzu.musubi.component.TamaStorage storage =
            tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE,
                com.sanhiruzu.musubi.component.TamaStorage.EMPTY);
        if (storage.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        java.util.List<SkeinContents.VariantEntry> leftover = new java.util.ArrayList<>();
        int dumped = 0;
        for (SkeinContents.VariantEntry e : storage.entries()) {
            int dep = rack.tryDeposit(e.itemId(), e.count());
            dumped += dep;
            if (dep < e.count()) {
                leftover.add(new SkeinContents.VariantEntry(e.itemId(), e.count() - dep));
            }
        }

        if (dumped == 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        tama.set(MusubiDataComponents.TAMA_STORAGE,
            new com.sanhiruzu.musubi.component.TamaStorage(java.util.List.copyOf(leftover)));
        BlockState updated = level.getBlockState(pos);
        level.sendBlockUpdated(pos, updated, updated, 3);
        level.playSound(null, pos, MusubiSounds.RACK_DEPOSIT.get(), SoundSource.BLOCKS,
            0.8f, 0.9f + level.random.nextFloat() * 0.2f);
        return ItemInteractionResult.CONSUME;
    }

    private static VoxelShape getRackShape(BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean hanging = state.getValue(HANGING);
        int idx = switch (facing) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        return hanging ? HANGING_SHAPES[idx] : WALL_SHAPES[idx];
    }

    private static VoxelShape[] buildShapes(boolean hanging) {
        VoxelShape[] shapes = new VoxelShape[4];
        shapes[0] = buildShape(Direction.NORTH, hanging);
        shapes[1] = buildShape(Direction.EAST, hanging);
        shapes[2] = buildShape(Direction.SOUTH, hanging);
        shapes[3] = buildShape(Direction.WEST, hanging);
        return shapes;
    }

    private static VoxelShape buildShape(Direction facing, boolean hanging) {
        VoxelShape shape = Shapes.empty();
        for (BoxDef box : PROFILE_BOXES) {
            double[] coords = transform(box, facing, hanging);
            shape = Shapes.or(shape, Block.box(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]));
        }
        return shape;
    }

    private static double[] transform(BoxDef box, Direction facing, boolean hanging) {
        double minX = box.minX();
        double minY = box.minY();
        double minZ = box.minZ();
        double maxX = box.maxX();
        double maxY = box.maxY();
        double maxZ = box.maxZ();

        if (hanging) {
            double flippedMinY = 16.0 - maxY;
            double flippedMaxY = 16.0 - minY;
            minY = flippedMinY;
            maxY = flippedMaxY;
        }

        return switch (facing) {
            case NORTH -> new double[] { minX, minY, minZ, maxX, maxY, maxZ };
            case SOUTH -> new double[] { 16.0 - maxX, minY, 16.0 - maxZ, 16.0 - minX, maxY, 16.0 - minZ };
            case WEST  -> new double[] { minZ, minY, 16.0 - maxX, maxZ, maxY, 16.0 - minX };
            case EAST  -> new double[] { 16.0 - maxZ, minY, minX, 16.0 - minZ, maxY, maxX };
            default    -> new double[] { minX, minY, minZ, maxX, maxY, maxZ };
        };
    }

    @Nullable
    private static SpoolRackBlockEntity getBlockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SpoolRackBlockEntity rack ? rack : null;
    }
}
