package com.sanhiruzu.musubi.rei;

/**
 * Standard mutable IReiStorage implementation.
 *
 * Tracks a dirty flag so block entities can call setChanged() only when
 * the stored value actually changes.
 */
public class SimpleReiStorage implements IReiStorage {

    private int stored;
    private final int capacity;
    private final int maxInsertPerOp;
    private final int maxExtractPerOp;
    private boolean dirty;

    public SimpleReiStorage(int capacity, int maxInsertPerOp, int maxExtractPerOp) {
        this.capacity         = capacity;
        this.maxInsertPerOp   = maxInsertPerOp;
        this.maxExtractPerOp  = maxExtractPerOp;
    }

    /** Convenience constructor — unlimited insert/extract rate. */
    public SimpleReiStorage(int capacity) {
        this(capacity, capacity, capacity);
    }

    // ── IReiStorage ──────────────────────────────────────────────────────────

    @Override public int getREI()    { return stored; }
    @Override public int getMaxREI() { return capacity; }

    @Override
    public int insertREI(int amount, boolean simulate) {
        int accepted = Math.min(amount, Math.min(maxInsertPerOp, capacity - stored));
        if (accepted <= 0) return 0;
        if (!simulate) { stored += accepted; dirty = true; }
        return accepted;
    }

    @Override
    public int extractREI(int amount, boolean simulate) {
        int taken = Math.min(amount, Math.min(maxExtractPerOp, stored));
        if (taken <= 0) return 0;
        if (!simulate) { stored -= taken; dirty = true; }
        return taken;
    }

    // ── Direct mutation (for generators ticking internally) ──────────────────

    /** Add REI up to capacity without rate limiting. Returns amount added. */
    public int generate(int amount) {
        int added = Math.min(amount, capacity - stored);
        if (added > 0) { stored += added; dirty = true; }
        return added;
    }

    /** Consume REI without rate limiting. Returns true if the full amount was available. */
    public boolean consume(int amount) {
        if (stored < amount) return false;
        stored -= amount;
        dirty = true;
        return true;
    }

    // ── Persistence helpers ──────────────────────────────────────────────────

    public int getRaw()           { return stored; }
    public void setRaw(int value) { stored = Math.max(0, Math.min(capacity, value)); dirty = true; }

    public boolean isDirty()     { return dirty; }
    public void clearDirty()     { dirty = false; }
}
