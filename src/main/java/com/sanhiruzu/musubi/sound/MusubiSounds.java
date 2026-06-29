package com.sanhiruzu.musubi.sound;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class MusubiSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
        DeferredRegister.create(Registries.SOUND_EVENT, Musubi.MOD_ID);

    /** Soft click on each item drain tick while sewing. */
    public static final DeferredHolder<SoundEvent, SoundEvent> NEEDLE_COLLECT =
        register("needle_collect");

    /** Fabric thud when a skein is deposited into the Spool Rack. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RACK_DEPOSIT =
        register("rack_deposit");

    /** Mechanical loom-click emitted each time the Spinning Wheel ejects a batch. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_SPIN =
        register("wheel_spin");

    /** One-shot sound when a skein is loaded into the Spinning Wheel. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_LOAD =
        register("wheel_load");

    /** Client-side one-shot played when the wheel begins spinning (velocity crosses threshold). */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_SPIN_UP =
        register("wheel_spin_up");

    /** Client-side one-shot played when the wheel coasts to a stop. */
    public static final DeferredHolder<SoundEvent, SoundEvent> WHEEL_COAST_STOP =
        register("wheel_coast_stop");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Musubi.MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
