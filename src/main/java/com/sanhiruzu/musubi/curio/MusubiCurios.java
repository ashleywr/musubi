package com.sanhiruzu.musubi.curio;

import com.sanhiruzu.musubi.component.MusubiDataComponents;
import com.sanhiruzu.musubi.component.TamaFilter;
import com.sanhiruzu.musubi.component.TamaStorage;
import com.sanhiruzu.musubi.item.MusubiItems;
import com.sanhiruzu.musubi.item.TamaItem;
import com.sanhiruzu.musubi.util.PickupVfxControl;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
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
 * the Tama items are made valid for it via the {@code data/curios/tags/item/belt.json} tag.
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
                magnetTick(slotContext, player, stack);
            }
        }
    };

    /**
     * Pulls nearby matching dropped items toward the wearer; the existing bind-on-pickup hook
     * binds them once they arrive. Items with an active pickup delay (fresh drops) are left alone,
     * so a player can still drop filtered items without them being instantly re-vacuumed.
     */
    private static void magnetTick(SlotContext slotContext, Player player, ItemStack tama) {
        if (!(tama.getItem() instanceof TamaItem t)) return;
        // Run every other tick — plenty responsive, half the entity scans.
        if ((player.level().getGameTime() & 1L) != 0L) return;
        // No pull when the filter is empty or the tama is full.
        if (tama.getOrDefault(MusubiDataComponents.TAMA_FILTER, TamaFilter.EMPTY).size() == 0) return;
        TamaStorage storage = tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
        if (storage.total() >= t.storageCapacity(tama)) return;

        Level level = player.level();
        double range = t.pickupRange(tama);
        AABB box = player.getBoundingBox().inflate(range);
        java.util.List<ItemEntity> nearby = level.getEntitiesOfClass(ItemEntity.class, box);
        if (nearby.isEmpty()) return;

        Vec3 target = beltAnchor(player);
        for (ItemEntity ie : nearby) {
            if (!ie.isAlive() || ie.hasPickUpDelay()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(ie.getItem().getItem());
            if (!TamaItem.accepts(tama, id)) continue;

            int before = ie.getItem().getCount();
            TamaItem.playCollectionEffect(player, ie, id, target, before);
            PickupVfxControl.withoutBindPickupVfx(() -> player.getInventory().add(ie.getItem()));
            int moved = before - ie.getItem().getCount();
            if (moved <= 0) continue;

            if (ie.getItem().isEmpty()) {
                ie.discard();
            }
        }
    }

    /**
     * Approximate world-space anchor for the belt curio.
     *
     * <p>This sits low on the torso and slightly off the player's right side so the pull and
     * pickup VFX read as going into the worn tama instead of toward the head.
     */
    public static Vec3 beltAnchor(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0, look.x);
        double y = player.getY() + (player.isCrouching() ? 0.86 : 0.98);
        return new Vec3(player.getX(), y, player.getZ())
            .add(right.scale(0.18))
            .add(look.scale(0.06));
    }

    /**
     * True when this dropped item is close enough to a worn Tama that we want to keep it as a
     * stable source instead of letting vanilla merge it into neighboring item stacks first.
     */
    public static boolean shouldPreventItemMerge(ItemEntity item) {
        if (item == null || !item.isAlive() || item.level() == null) return false;

        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
        if (id == null) return false;

        Level level = item.level();
        double maxRange = 0.0;
        for (Player player : level.getEntitiesOfClass(Player.class, item.getBoundingBox().inflate(10.0))) {
            ItemStack tama = findWornTama(player);
            if (tama == null) continue;
            if (!(tama.getItem() instanceof TamaItem t)) continue;
            if (!TamaItem.accepts(tama, id)) continue;

            TamaStorage storage = tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
            if (storage.total() >= t.storageCapacity(tama)) continue;

            maxRange = Math.max(maxRange, t.pickupRange(tama));
            if (player.distanceToSqr(item) <= maxRange * maxRange) {
                return true;
            }
        }
        return false;
    }

    /**
     * Client-side cosmetic: faint colored silk wisps drift from the wearer's belt and trail behind
     * their movement, tinted by what the Tama holds (soft cream when empty). Spawns at most a mote
     * or two per tick so it reads as gently swaying thread, never a cloud.
     */
    private static void spawnAmbientSilk(LivingEntity wearer, ItemStack tama) {
        Level level = wearer.level();
        RandomSource rnd = level.random;
        // Wisp density scales with how full the tama is: a near-empty one sheds the odd wisp,
        // a near-full one trails noticeably more silk.
        TamaStorage storage = tama.getOrDefault(MusubiDataComponents.TAMA_STORAGE, TamaStorage.EMPTY);
        float fill = 0f;
        if (tama.getItem() instanceof TamaItem t && t.storageCapacity(tama) > 0) {
            fill = Math.min(1f, storage.total() / (float) t.storageCapacity(tama));
        }
        // 0.15 when empty → ~0.6 when full.
        float chance = 0.15f + 0.45f * fill;
        if (rnd.nextFloat() > chance) return;

        int color = TamaItem.contentColor(tama);
        Vector3f rgb = new Vector3f(
            ((color >> 16) & 0xFF) / 255f,
            ((color >>  8) & 0xFF) / 255f,
            ( color        & 0xFF) / 255f);
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.7f);

        // 1 mote normally; a third more likely as it fills toward capacity.
        int motes = 1 + (rnd.nextFloat() < 0.25f + 0.4f * fill ? 1 : 0);
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

    /**
     * Short pull trail that runs from the dropped item to the belt anchor.
     *
     * <p>The sequence starts as a fray at the source, twists into a silk strand in flight, then
     * cinches into a tight wrap around the tama at the belt anchor.
     */
    private static void spawnPullVfx(ServerLevel level, Vec3 source, Vec3 target, int color) {
        Vec3 delta = target.subtract(source);
        double dist = delta.length();
        if (dist < 0.001) return;

        Vec3 dir = delta.scale(1.0 / dist);
        Vec3 side = arbitraryPerpendicular(dir);
        Vec3 lift = dir.cross(side).normalize();
        int threadColor = blendColor(color, 0xE8D8B0, 0.70f);
        int wrapColor = blendColor(color, 0xF8F1E0, 0.88f);

        spawnFrayBurst(level, source, color);
        spawnTwistTrail(level, source, target, dir, side, lift, threadColor);
        spawnTargetWrap(level, target, dir, side, lift, wrapColor);
    }

    /** Small burst at the source so the item reads as loosening into fiber. */
    private static void spawnFrayBurst(ServerLevel level, Vec3 source, int color) {
        Vector3f rgb = unpackRgb(color);
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.9f);
        level.sendParticles(dust, source.x, source.y + 0.02, source.z, 4,
            0.06, 0.06, 0.06, 0.01);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
            source.x, source.y + 0.02, source.z, 1, 0.02, 0.02, 0.02, 0.0);
    }

    /**
     * A twisting strand from source to target. The color fades toward silk cream as it nears the
     * tama so the motion reads as "item turning into thread".
     */
    private static void spawnTwistTrail(ServerLevel level, Vec3 source, Vec3 target, Vec3 dir,
                                        Vec3 side, Vec3 lift, int startColor) {
        Vec3 delta = target.subtract(source);
        double dist = delta.length();
        int steps = Math.max(10, Math.min(18, (int) Math.ceil(dist * 5.0)));
        double phase = level.getGameTime() * 0.5;
        for (int i = 0; i < steps; i++) {
            double t = (i + 1.0) / (steps + 1.0);
            double angle = phase + t * Math.PI * 8.0;
            double radius = 0.15 * (1.0 - t) + 0.03;
            Vec3 pos = source.add(delta.scale(t))
                .add(side.scale(Math.cos(angle) * radius))
                .add(lift.scale(Math.sin(angle) * radius));
            int stepColor = blendColor(startColor, 0xF8F1E0, (float) Math.pow(t, 1.2));
            Vector3f rgb = unpackRgb(stepColor);
            DustParticleOptions dust = new DustParticleOptions(rgb, 0.8f);
            level.sendParticles(dust, pos.x, pos.y, pos.z, 1,
                dir.x * 0.03, dir.y * 0.03 + 0.01, dir.z * 0.03, 0.0);
            if (i % 3 == 0) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    pos.x, pos.y, pos.z, 1, 0.0, 0.01, 0.0, 0.0);
            }
        }
    }

    /**
     * Tightens the strand around the tama anchor in a few shrinking coils to sell the wrap.
     */
    private static void spawnTargetWrap(ServerLevel level, Vec3 target, Vec3 dir, Vec3 side,
                                        Vec3 lift, int color) {
        Vector3f rgb = unpackRgb(color);
        DustParticleOptions dust = new DustParticleOptions(rgb, 0.85f);
        double phase = level.getGameTime() * 0.65;
        for (int i = 0; i < 9; i++) {
            double t = i / 8.0;
            double angle = phase + t * Math.PI * 5.0;
            double radius = 0.11 * (1.0 - t) + 0.02;
            Vec3 pos = target
                .add(dir.scale(0.05 + 0.03 * (1.0 - t)))
                .add(side.scale(Math.cos(angle) * radius))
                .add(lift.scale(Math.sin(angle) * radius));
            level.sendParticles(dust, pos.x, pos.y, pos.z, 1,
                -dir.x * 0.01, 0.01, -dir.z * 0.01, 0.0);
        }
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
            target.x, target.y + 0.03, target.z, 2, 0.08, 0.05, 0.08, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
            target.x, target.y + 0.02, target.z, 1, 0.01, 0.03, 0.01, 0.0);
    }

    /** Picks any stable perpendicular direction for the helix basis. */
    private static Vec3 arbitraryPerpendicular(Vec3 dir) {
        Vec3 up = Math.abs(dir.y) > 0.85 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = dir.cross(up);
        if (side.lengthSqr() < 1.0e-6) {
            side = dir.cross(new Vec3(0.0, 0.0, 1.0));
        }
        return side.normalize();
    }

    /** Linear blend between two packed RGB colors. */
    private static int blendColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int fr = (from >> 16) & 0xFF;
        int fg = (from >>  8) & 0xFF;
        int fb = (from      ) & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >>  8) & 0xFF;
        int tb = (to      ) & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }

    /** Unpacks 0xRRGGBB into a normalized RGB vector for DustParticleOptions. */
    private static Vector3f unpackRgb(int packed) {
        return new Vector3f(
            ((packed >> 16) & 0xFF) / 255f,
            ((packed >>  8) & 0xFF) / 255f,
            ( packed        & 0xFF) / 255f
        );
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
