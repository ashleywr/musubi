package com.sanhiruzu.musubi.util;

import java.util.function.Supplier;

/**
 * Thread-local switch used to suppress the regular bind pickup flourish when Musubi performs a
 * direct collection pickup and plays a custom strand effect instead.
 */
public final class PickupVfxControl {

    private static final ThreadLocal<Integer> SUPPRESS_BIND_VFX = ThreadLocal.withInitial(() -> 0);

    private PickupVfxControl() {}

    public static void withoutBindPickupVfx(Runnable action) {
        SUPPRESS_BIND_VFX.set(SUPPRESS_BIND_VFX.get() + 1);
        try {
            action.run();
        } finally {
            SUPPRESS_BIND_VFX.set(Math.max(0, SUPPRESS_BIND_VFX.get() - 1));
        }
    }

    public static <T> T withoutBindPickupVfx(Supplier<T> action) {
        SUPPRESS_BIND_VFX.set(SUPPRESS_BIND_VFX.get() + 1);
        try {
            return action.get();
        } finally {
            SUPPRESS_BIND_VFX.set(Math.max(0, SUPPRESS_BIND_VFX.get() - 1));
        }
    }

    public static boolean isBindPickupVfxSuppressed() {
        return SUPPRESS_BIND_VFX.get() > 0;
    }
}
