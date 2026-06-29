package com.sanhiruzu.musubi.rei;

/**
 * Capability interface for Reiryoku (霊力) — the spiritual energy of Musubi.
 *
 * REI flows between adjacent blocks every 20 ticks, equalizing from
 * generators toward consumers. All amounts are in whole REI units.
 */
public interface IReiStorage {

    /** Current stored REI. */
    int getREI();

    /** Maximum REI this storage can hold. */
    int getMaxREI();

    /**
     * Insert up to {@code amount} REI. Returns the amount actually inserted.
     * If {@code simulate} is true, nothing changes — use for capacity checks.
     */
    int insertREI(int amount, boolean simulate);

    /**
     * Extract up to {@code amount} REI. Returns the amount actually extracted.
     * If {@code simulate} is true, nothing changes — use for availability checks.
     */
    int extractREI(int amount, boolean simulate);

    default boolean canInsert()  { return true; }
    default boolean canExtract() { return true; }

    default boolean isFull()  { return getREI() >= getMaxREI(); }
    default boolean isEmpty() { return getREI() <= 0; }
}
