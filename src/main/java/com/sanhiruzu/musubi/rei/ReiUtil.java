package com.sanhiruzu.musubi.rei;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/**
 * Helpers for REI distribution between adjacent blocks.
 *
 * Called from block entity server ticks. Each block with REI pushes
 * up to PUSH_PER_FACE REI into each adjacent REI-capable face per call.
 * Run this every 20 ticks (once per second) for natural equalization.
 */
public final class ReiUtil {

    /** Maximum REI pushed to a single neighbor face per distribution tick. */
    private static final int PUSH_PER_FACE = 20;

    private ReiUtil() {}

    /**
     * Push REI from {@code source} outward to all adjacent blocks that accept it.
     * Returns total REI distributed.
     */
    public static int distribute(IReiStorage source, Level level, BlockPos pos) {
        if (source.isEmpty() || !source.canExtract()) return 0;
        int total = 0;
        for (Direction dir : Direction.values()) {
            IReiStorage neighbor = level.getCapability(ReiCapability.BLOCK, pos.relative(dir), null);
            if (neighbor == null || !neighbor.canInsert() || neighbor.isFull()) continue;
            int canPush  = source.extractREI(PUSH_PER_FACE, true);
            if (canPush <= 0) break; // source exhausted
            int accepted = neighbor.insertREI(canPush, false);
            if (accepted > 0) {
                source.extractREI(accepted, false);
                total += accepted;
            }
        }
        return total;
    }
}
