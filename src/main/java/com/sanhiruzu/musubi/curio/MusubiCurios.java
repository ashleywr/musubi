package com.sanhiruzu.musubi.curio;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.TamaFilter;
import com.sanhiruzu.musubi.component.TamaStorage;
import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Curios integration: lets a Tama be worn in the player's belt slot so it binds items
 * on pickup without occupying the main hand or an inventory slot.
 *
 * <p>The belt slot is assigned to the player via {@code data/musubi/curios/entities/player.json};
 * the Tama items are made valid for it via the {@code #curios:belt} tag.
 */
public final class MusubiCurios {

    private MusubiCurios() {}

    /**
     * The Tama just needs to be equippable (binding is handled by the inventory hook); the only
     * behaviour here is the ambient "loose silk" wisps that trail from it while worn.
     */
    private static final ICurioItem TAMA_CURIO = new ICurioItem() {
        @Override
        public void curioTick(SlotContext slotContext, ItemStack stack) {
            LivingEntity wearer = slotContext.entity();
            if (wearer == null) return;
            if (wearer.level().isClientSide) {
                spawnAmbientSilk(wearer, stack);
            } else if (wearer instanceof Player player) {
                magnetTick(player, stack);
            }
        }
    };

    /**
     * Pulls nearby matching dropped items toward the wearer; the existing bind-on-pickup hook
     * binds them once they arrive. Items with an active pickup delay (fresh drops) are left alone,
     * so a player can still drop filtered items without them being instantly re-vacuumed.
     */
    private static void magnetTick(Player player, ItemStack tama) {
        if (!(tama.getItem() instanceof TamaItem t)) return;
        // Run every other tick — plenty responsive, half the entity scans.
        if ((player.level().getGameTime() & 1L) != 0L) return;
        // No pull when the filter is empty or the tama is full.
        if (tama.getOrDefault(MusubiDataComponents.TAMA_FILTER, TamaFilter.EMPTY).size() == 0) return;
        TamaStorage storage = tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
        if (storage.total() >= t.storageCapacity()) return;

        Level level = player.level();
        double range = t.pickupRange();
        AABB box = player.getBoundingBox().inflate(range);
        java.util.List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box);
        if (nearby.isEmpty()) return;

        Vec3 target = new Vec3(player.getX(), player.getY() + 0.4, player.getZ());
        for (ItemEntity ie : nearby) {
            if (!ie.isAlive() || ie.hasPickUpDelay()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(ie.getItem().getItem());
            if (!TamaItem.accepts(tama, id)) continue;

            Vec3 toPlayer = target.subtract(ie.position());
            double dist = toPlayer.length();
            if (dist < 0.7) continue; // close enough — let normal pickup take it
            // Speed scales with distance: snappy from across the room, gentle up close.
            double speed = Math.min(0.42, 0.12 + dist * 0.05);
            Vec3 pull = toPlayer.normalize().scale(speed);
            // Damp existing motion so items home in instead of orbiting/overshooting.
            ie.setDeltaMovement(ie.getDeltaMovement().scale(0.5).add(pull));
            ie.hasImpulse = true;
        }
    }

    /**
     * Client-side cosmetic: faint colored silk wisps drift from the wearer's belt and trail behind
     * their movement, tinted by what the Tama holds (soft cream when empty). Spawns at most a mote
     * or two per tick so it reads as gently swaying thread, never a cloud.
     */
    private static void spawnAmbientSilk(LivingEntity wearer, ItemStack tama) {
        Level level = wearer.level();
        RandomSource rnd = level.random;
        // A fuller tama sheds a touch more silk; an empty one only the occasional wisp.
        boolean empty = tama.getOrDefault(
            com.sanhiruzu.musubi.component.MusubiDataComponents.TAMA_STORAGE,
            com.sanhiruzu.musubi.component.TamaStorage.EMPTY).isEmpty();
        float chance = empty ? 0.20f : 0.45f;
        if (rnd.nextFloat() > chance) return;

        int color = TamaItem.contentColor(tama);
        Vector3f rgb = new Vector3f(
            ((color >> 16) & 0xFF) / 255f,
            ((color >>  8) & 0xFF) / 255f,
            ( color        & 0xFF) / 255f);
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.7f);

        int motes = empty ? 1 : 1 + (rnd.nextFloat() < 0.4f ? 1 : 0);
        Vec3 motion = wearer.getDeltaMovement();
        for (int i = 0; i < motes; i++) {
            double angle = rnd.nextDouble() * Math.PI * 2.0;
            double radius = 0.22 + rnd.nextDouble() * 0.14;
            double px = wearer.getX() + Math.cos(angle) * radius;
            double pz = wearer.getZ() + Math.sin(angle) * radius;
            double py = wearer.getY() + 0.75 + (rnd.nextDouble() - 0.5) * 0.25;
            // Trail behind movement, with a slow downward sag like a hanging thread.
            double vx = -motion.x * 0.5 + (rnd.nextDouble() - 0.5) * 0.005;
            double vy = -0.012;
            double vz = -motion.z * 0.5 + (rnd.nextDouble() - 0.5) * 0.005;
            level.addParticle(dust, px, py, pz, vx, vy, vz);
        }
    }

    /** Registers every Tama tier as a curio. Call from common setup (enqueued work). */
    public static void register() {
        List<? extends net.minecraft.world.item.Item> tamas = List.of(
            MusubiItems.BONE_TAMA.get(),
            MusubiItems.IRON_TAMA.get(),
            MusubiItems.SILVER_TAMA.get(),
            MusubiItems.ORIHIME_TAMA.get()
        );
        for (net.minecraft.world.item.Item tama : tamas) {
            CuriosApi.registerCurio(tama, TAMA_CURIO);
        }
    }

    /** Returns the live Tama ItemStack worn in a curio slot, or null if none is equipped. */
    @Nullable
    public static ItemStack findWornTama(Player player) {
        return CuriosApi.getCuriosInventory(player)
            .flatMap(handler -> handler.findFirstCurio(s -> s.getItem() instanceof TamaItem))
            .map(SlotResult::stack)
            .orElse(null);
    }
}
