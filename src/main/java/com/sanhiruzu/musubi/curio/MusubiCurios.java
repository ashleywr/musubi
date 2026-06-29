package com.sanhiruzu.musubi.curio;

import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.item.TamaItem;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
            if (wearer != null && wearer.level().isClientSide) {
                spawnAmbientSilk(wearer, stack);
            }
        }
    };

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
