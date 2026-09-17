package dev.sasuke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SasukeMod.ID)
public final class BlackFlameController {
    private record Pool(UUID owner, Vec3 position, float radius, long expires) {}
    private record Burn(UUID owner, long expires) {}
    private static final Map<ServerLevel, List<Pool>> POOLS = new WeakHashMap<>();
    private static final Map<ServerLevel, List<Pool>> CLOUDS = new WeakHashMap<>();
    private static final Map<LivingEntity, Burn> BURNS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Long> AURAS = new WeakHashMap<>();

    private static final class FlameDamageSource extends CombatController.SkillDamageSource {
        FlameDamageSource(ServerPlayer player) { super(player); }
    }

    public static boolean damage(ServerPlayer owner, net.minecraft.world.entity.Entity target, float amount) {
        if (!CombatController.validTarget(owner, target)) return false;
        if (target instanceof LivingEntity living) burn(owner, living);
        return target.hurt(new FlameDamageSource(owner), CombatController.scaledSkillDamage(owner, amount));
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void cookDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        Burn burn = BURNS.get(event.getEntity());
        boolean blackFlame = burn != null && burn.expires > level.getGameTime();
        blackFlame |= event.getSource() instanceof FlameDamageSource;
        if (!blackFlame && event.getSource() instanceof yesman.epicfight.world.damagesource.EpicFightDamageSource epic
            && epic.getEntity() instanceof ServerPlayer owner && hasAura(owner)) {
            blackFlame = epic.getAnimation().equals(SasukeAnimations.player("dash_spin_slash"));
        }
        if (!blackFlame) return;
        for (var drop : event.getDrops()) {
            var raw = drop.getItem();
            var food = raw.getFoodProperties(event.getEntity());
            if (food == null || !food.isMeat()) continue;
            var inventory = new net.minecraft.world.SimpleContainer(raw.copyWithCount(1));
            var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, inventory, level);
            if (recipe.isEmpty()) continue;
            var cooked = recipe.get().assemble(inventory, level.registryAccess());
            if (cooked.isEmpty() || !cooked.isEdible()) continue;
            cooked.setCount(cooked.getCount() * raw.getCount());
            drop.setItem(cooked);
        }
    }

    public static void pool(ServerPlayer owner, Vec3 position, float radius) {
        var pools = POOLS.computeIfAbsent(owner.serverLevel(), ignored -> new ArrayList<>());
        if (pools.stream().filter(pool -> pool.owner.equals(owner.getUUID())).count() >= 48) {
            for (int index = 0; index < pools.size(); index++) {
                if (pools.get(index).owner.equals(owner.getUUID())) { pools.remove(index); break; }
            }
        }
        pools.add(new Pool(owner.getUUID(), position, radius, owner.level().getGameTime() + 160));
        SasukeNetwork.flame(owner.serverLevel(), position, radius, -1, 0);
    }

    public static void aura(ServerPlayer owner) {
        AURAS.put(owner, owner.level().getGameTime() + 100);
    }

    public static boolean hasAura(ServerPlayer owner) {
        return AURAS.getOrDefault(owner, 0L) > owner.level().getGameTime();
    }

    public static void cloud(ServerPlayer owner, Vec3 position, float radius) {
        var clouds = CLOUDS.computeIfAbsent(owner.serverLevel(), ignored -> new ArrayList<>());
        if (clouds.size() >= 128) clouds.remove(0);
        clouds.add(new Pool(owner.getUUID(), position, radius, owner.level().getGameTime() + 25));
    }

    public static void burn(ServerPlayer owner, LivingEntity target) {
        if (CombatController.validTarget(owner, target)) BURNS.put(target, new Burn(owner.getUUID(), target.level().getGameTime() + 100));
    }

    private static boolean visible(ServerLevel level, Vec3 origin, LivingEntity target) {
        return level.clip(new ClipContext(origin, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, target)).getType() == HitResult.Type.MISS;
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (now % 5 != 0) return;
        var clouds = CLOUDS.get(level);
        if (clouds != null) {
            clouds.removeIf(cloud -> cloud.expires <= now);
            for (var cloud : clouds) {
                var owner = level.getServer().getPlayerList().getPlayer(cloud.owner);
                if (owner == null || !owner.isAlive() || owner.level() != level) continue;
                for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(cloud.position, cloud.position).inflate(cloud.radius))) {
                    if (target.getBoundingBox().distanceToSqr(cloud.position) <= cloud.radius * cloud.radius && visible(level, cloud.position, target)) burn(owner, target);
                }
            }
            if (clouds.isEmpty()) CLOUDS.remove(level);
        }
        var pools = POOLS.get(level);
        if (pools != null) {
            pools.removeIf(pool -> {
                var owner = level.getServer().getPlayerList().getPlayer(pool.owner);
                return pool.expires <= now || owner == null || !owner.isAlive() || owner.level() != level;
            });
            for (var pool : pools) {
                var owner = level.getServer().getPlayerList().getPlayer(pool.owner);
                if (owner == null || owner.level() != level || !owner.isAlive()) continue;
                if (now % 20 == 0) SasukeNetwork.flame(level, pool.position, pool.radius, -1, 0);
                var area = new AABB(pool.position, pool.position).inflate(pool.radius, 0.65, pool.radius);
                for (var target : level.getEntitiesOfClass(LivingEntity.class, area)) {
                    if (target.getBoundingBox().distanceToSqr(pool.position) > pool.radius * pool.radius || !visible(level, pool.position.add(0, 0.15, 0), target)) continue;
                    if (target == owner) aura(owner);
                    else burn(owner, target);
                }
            }
            if (pools.isEmpty()) POOLS.remove(level);
        }
        AURAS.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().hasDisconnected()
            || (entry.getKey().level() == level && entry.getValue() <= now));
        for (var entry : AURAS.entrySet()) {
            ServerPlayer owner = entry.getKey();
            if (owner.level() != level) continue;
            if (now % 20 == 0) SasukeNetwork.flame(level, owner.position(), 0.8F, owner.getId(), 2);
            for (var target : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(2.5))) {
                if (target.getBoundingBox().distanceToSqr(owner.getBoundingBox().getCenter()) <= 6.25 && owner.hasLineOfSight(target)) burn(owner, target);
            }
        }
        var iterator = BURNS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var target = entry.getKey();
            if (!target.isAlive()) { iterator.remove(); continue; }
            if (target.level() != level) continue;
            var owner = level.getServer().getPlayerList().getPlayer(entry.getValue().owner);
            if (entry.getValue().expires <= now || owner == null || owner.level() != level || !CombatController.validTarget(owner, target)) {
                iterator.remove();
                continue;
            }
            if (now % 20 == 0) {
                target.hurt(new FlameDamageSource(owner), CombatController.scaledSkillDamage(owner, 4F));
                SasukeNetwork.flame(level, target.position(), Math.max(0.35F, target.getBbWidth() * 0.6F), target.getId(), 1);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        POOLS.clear();
        CLOUDS.clear();
        BURNS.clear();
        AURAS.clear();
    }
}
