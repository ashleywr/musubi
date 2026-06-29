package com.sanhiruzu.musubi.storage;

import com.sanhiruzu.musubi.Musubi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Level-attached store for full ItemStack DataComponent maps of complex sewn items.
 *
 * Skeins hold only a compact representation (item ID + count + UUID ref). The actual
 * DataComponent payload for complex items lives here, keyed by UUID. This keeps skein
 * items lightweight for inventory sync while still supporting arbitrary item data.
 *
 * Lifecycle: entries are written when a complex item is sewn; removed when the referencing
 * skein is consumed or destroyed. Orphaned entries are pruned at world load.
 */
public class MusubiSavedData extends SavedData {

    private static final String SAVE_KEY = Musubi.MOD_ID + "_item_store";
    private static final String TAG_ENTRIES = "entries";
    private static final String TAG_UUID = "uuid";
    private static final String TAG_DATA = "data";

    private final Map<UUID, CompoundTag> store = new HashMap<>();

    public static MusubiSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(MusubiSavedData::new, MusubiSavedData::load),
            SAVE_KEY
        );
    }

    private static MusubiSavedData load(CompoundTag tag) {
        MusubiSavedData data = new MusubiSavedData();
        ListTag entries = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            UUID id = entry.getUUID(TAG_UUID);
            CompoundTag payload = entry.getCompound(TAG_DATA);
            data.store.put(id, payload);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        store.forEach((id, payload) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID(TAG_UUID, id);
            entry.put(TAG_DATA, payload);
            entries.add(entry);
        });
        tag.put(TAG_ENTRIES, entries);
        return tag;
    }

    public UUID store(CompoundTag payload) {
        UUID id = UUID.randomUUID();
        store.put(id, payload);
        setDirty();
        return id;
    }

    public Optional<CompoundTag> retrieve(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    public void remove(UUID id) {
        if (store.remove(id) != null) {
            setDirty();
        }
    }

    public boolean contains(UUID id) {
        return store.containsKey(id);
    }
}
