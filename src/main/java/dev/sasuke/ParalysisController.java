package dev.sasuke;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@Mod.EventBusSubscriber(modid = SasukeMod.ID)
public final class ParalysisController {
    private record Stun(long until, Vec3 position, float yaw, float pitch, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
    private static final Map<LivingEntity, Stun> STUNS = new WeakHashMap<>();
    private record Facing(float yaw, float pitch) {}
    private static final Map<LivingEntity, Facing> CAPTURES = new WeakHashMap<>();
    private static final java.util.UUID CONTROL_LOCK = java.util.UUID.fromString("f8713db4-6aa4-45aa-a168-718e84978f85");

    private static void lockSkills(LivingEntity target) {
        var patch = EpicFightCapabilities.getEntityPatch(target, yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch.class);
        if (patch == null) return;
        var listener = patch.getEventListener();
        listener.addEventListener(yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType.SKILL_CAST_EVENT, CONTROL_LOCK,
            event -> { if (active(target)) event.setCanceled(true); });
        listener.addEventListener(yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType.BASIC_ATTACK_EVENT, CONTROL_LOCK,
            event -> { if (active(target)) event.setCanceled(true); });
    }

    public static void capture(LivingEntity target) {
        CombatController.interruptForCapture(target);
        lockSkills(target);
        CAPTURES.putIfAbsent(target, new Facing(target.getYRot(), target.getXRot()));
        target.stopUsingItem();
        target.stopRiding();
        if (target instanceof Mob mob) mob.getNavigation().stop();
        var patch = EpicFightCapabilities.getEntityPatch(target, LivingEntityPatch.class);
        if (patch != null) {
            CaptureStamina.drain(patch);
            patch.applyStun(yesman.epicfight.world.damagesource.StunType.LONG, 0.1F);
        }
    }

    public static boolean active(LivingEntity target) {
        if (!target.isAlive() || target.isRemoved()) return false;
        if (CombatController.captured(target)) return true;
        Stun stun = STUNS.get(target);
        return stun != null && target.level().dimension().equals(stun.dimension) && target.level().getGameTime() < stun.until;
    }
    public static void apply(LivingEntity target) {
        if (!target.isAlive() || CombatController.superArmor(target)) return;
        lockSkills(target);
        if (CombatController.skillBody(target)) CombatController.interruptForCapture(target);
        STUNS.put(target, new Stun(target.level().getGameTime() + 30, target.position(), target.getYRot(), target.getXRot(), target.level().dimension()));
        target.stopUsingItem();
        target.stopRiding();
        if (target instanceof Mob mob) mob.getNavigation().stop();
        var patch = EpicFightCapabilities.getEntityPatch(target, LivingEntityPatch.class);
        if (patch != null) patch.applyStun(yesman.epicfight.world.damagesource.StunType.LONG, 1.5F);
    }
    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        CAPTURES.entrySet().removeIf(entry -> !entry.getKey().isAlive() || entry.getKey().isRemoved() || !CombatController.captured(entry.getKey()));
        CAPTURES.forEach((target, facing) -> {
            var patch = EpicFightCapabilities.getEntityPatch(target, LivingEntityPatch.class);
            if (patch != null) CaptureStamina.drain(patch);
            target.setDeltaMovement(Vec3.ZERO);
            target.stopUsingItem();
            target.setYRot(facing.yaw); target.setXRot(facing.pitch);
            target.setYHeadRot(facing.yaw); target.setYBodyRot(facing.yaw);
            if (target instanceof Mob mob) mob.getNavigation().stop();
            if (target instanceof ServerPlayer player) player.connection.teleport(target.getX(), target.getY(), target.getZ(), facing.yaw, facing.pitch);
        });
        var iterator = STUNS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var target = entry.getKey();
            var stun = entry.getValue();
            if (!target.isAlive() || target.isRemoved() || target.level().getGameTime() >= stun.until
                || !target.level().dimension().equals(stun.dimension) || target.position().distanceToSqr(stun.position) > 256) {
                iterator.remove(); continue;
            }
            if (CombatController.captured(target)) continue;
            target.setDeltaMovement(Vec3.ZERO);
            target.stopUsingItem();
            target.setYRot(stun.yaw); target.setXRot(stun.pitch); target.setYHeadRot(stun.yaw); target.setYBodyRot(stun.yaw);
            if (target instanceof ServerPlayer player) player.connection.teleport(stun.position.x, stun.position.y, stun.position.z, stun.yaw, stun.pitch);
            else target.teleportTo(stun.position.x, stun.position.y, stun.position.z);
        }
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void livingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (CombatController.captured(event.getEntity())) {
            var patch = EpicFightCapabilities.getEntityPatch(event.getEntity(), LivingEntityPatch.class);
            if (patch != null) CaptureStamina.drain(patch);
        }
        LivingEntity target = event.getEntity();
        if (active(target)) {
            target.hurtTime = 0;
            target.invulnerableTime = 0;
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event) {
        if (event.getSource().getDirectEntity() instanceof LivingEntity attacker && active(attacker)) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void use(LivingEntityUseItemEvent.Start event) { if (active(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent
    public static void interact(PlayerInteractEvent event) { if (event.isCancelable() && active(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent event) { if (active(event.getPlayer())) event.setCanceled(true); }
    @SubscribeEvent
    public static void playerAttack(net.minecraftforge.event.entity.player.AttackEntityEvent event) { if (active(event.getEntity())) event.setCanceled(true); }
    @SubscribeEvent
    public static void mount(net.minecraftforge.event.entity.EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof LivingEntity target && active(target)) event.setCanceled(true);
    }
    @SubscribeEvent
    public static void toss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (active(event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().getInventory().placeItemBackInInventory(event.getEntity().getItem());
        }
    }
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STUNS.clear();
        CAPTURES.clear();
    }
}
