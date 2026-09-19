package dev.sasuke;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.utils.LevelUtil;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SasukeMod.ID)
public final class LightningController {
    private static final Map<ServerPlayer, Anchor> ANCHORS = new WeakHashMap<>();
    private static final class Anchor {
        Display.ItemDisplay sword;
        long expires;
        Vec3 orb;
        Vec3 fracture;
    }

    public static void plant(ServerPlayer player) {
        Anchor old = ANCHORS.remove(player);
        if (old != null) old.sword.discard();
        Vec3 front = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(0.7));
        var floor = player.level().clip(new ClipContext(front.add(0, 1, 0), front.add(0, -2, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (floor.getType() == HitResult.Type.MISS) return;
        var sword = new Display.ItemDisplay(SasukeMod.PLANTED_SWORD.get(), player.level());
        CompoundTag data = new CompoundTag();
        data.put("item", new ItemStack(SasukeMod.KUSANAGI.get()).save(new CompoundTag()));
        data.putString("item_display", "fixed");
        CompoundTag transform = new CompoundTag();
        transform.put("translation", floats(0, 0, 0));
        transform.put("scale", floats(2, 2, 2));
        transform.put("left_rotation", floats(0, 0, 0.9238795F, 0.3826834F));
        transform.put("right_rotation", floats(0, 0, 0, 1));
        data.put("transformation", transform);
        sword.load(data);
        sword.setPos(floor.getLocation().add(0, 0.8, 0));
        sword.setYRot(player.getYRot());
        player.level().addFreshEntity(sword);
        player.serverLevel().sendParticles(yesman.epicfight.particle.EpicFightParticles.GROUND_SLAM.get(),
            floor.getLocation().x, floor.getLocation().y, floor.getLocation().z, 1, 0.9, 10, 0.35, 0.8);
        Vec3 fractureCenter = floor.getBlockPos().getCenter();
        LevelUtil.circleSlamFracture(player, player.level(), fractureCenter, 1.1D, false, false);
        Anchor anchor = new Anchor();
        anchor.sword = sword;
        anchor.fracture = fractureCenter;
        anchor.expires = player.level().getGameTime() + 10 * 20;
        ANCHORS.put(player, anchor);
        strike(player, sword.position());
    }

    private static ListTag floats(float... values) {
        ListTag list = new ListTag();
        for (float value : values) list.add(FloatTag.valueOf(value));
        return list;
    }

    public static boolean hasAnchor(ServerPlayer player) {
        Anchor anchor = ANCHORS.get(player);
        return anchor != null && anchor.sword.isAlive() && anchor.sword.level() == player.level()
            && player.level().getGameTime() < anchor.expires && anchor.orb == null;
    }

    private static void resetFourthCycle(ServerPlayer player) {
        if (Math.floorMod(player.getPersistentData().getInt("sasukeComboStage"), 3) != 2) return;
        player.getPersistentData().putInt("sasukeComboStage", 0);
        var patch = yesman.epicfight.world.capabilities.EpicFightCapabilities.getEntityPatch(player,
            yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch.class);
        if (patch != null) {
            var data = patch.getSkill(yesman.epicfight.skill.SkillSlots.BASIC_ATTACK).getDataManager();
            var key = yesman.epicfight.skill.SkillDataKeys.COMBO_COUNTER.get();
            data.setData(key, Math.floorMod(data.getDataValue(key), 4));
        }
    }

    public static void launch(ServerPlayer player) {
        Anchor anchor = ANCHORS.get(player);
        if (hasAnchor(player)) {
            anchor.orb = player.position().add(0, 0.9, 0);
            SasukeNetwork.flame(player.serverLevel(), anchor.orb, 0.42F, player.getId(), 6);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var iterator = ANCHORS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var player = entry.getKey();
            var anchor = entry.getValue();
            if (!player.isAlive() || player.hasDisconnected() || player.level() != anchor.sword.level() || !anchor.sword.isAlive() || player.level().getGameTime() >= anchor.expires) {
                anchor.sword.discard(); iterator.remove(); resetFourthCycle(player); continue;
            }
            if (player.level().getGameTime() % 10 == 0) {
                LevelUtil.circleSlamFracture(player, player.level(), anchor.fracture, 1.1D, true, true);
            }
            if (anchor.orb == null) continue;
            Vec3 destination = anchor.sword.position();
            Vec3 direction = destination.subtract(anchor.orb);
            Vec3 next = direction.length() > 0.75 ? anchor.orb.add(direction.normalize().scale(0.75)) : destination;
            Vec3 impact = null;
            double nearest = Double.MAX_VALUE;
            for (var target : player.level().getEntities(player, new AABB(anchor.orb, next).inflate(0.65), entity -> CombatController.validTarget(player, entity))) {
                var bounds = target.getBoundingBox().inflate(0.65);
                Vec3 contact = bounds.contains(anchor.orb) ? anchor.orb : bounds.clip(anchor.orb, next).orElse(null);
                if (contact != null && contact.distanceToSqr(anchor.orb) < nearest) {
                    nearest = contact.distanceToSqr(anchor.orb);
                    impact = contact;
                }
            }
            if (impact == null && direction.length() > 0.75) {
                anchor.orb = next;
                SasukeNetwork.flame(player.serverLevel(), anchor.orb, 0.42F, player.getId(), 6);
                continue;
            }
            if (impact != null) destination = impact;
            strike(player, destination);
            anchor.sword.discard();
            iterator.remove();
        }
    }

    private static void strike(ServerPlayer player, Vec3 position) {
        CombatAudio.playAt(player, "lightning_burst", position);
        SasukeNetwork.flame(player.serverLevel(), position, 2.8F, player.getId(), 9);
        for (var target : player.level().getEntities(player, new AABB(position, position).inflate(2.8), entity -> CombatController.validTarget(player, entity))) {
            if (target.getBoundingBox().distanceToSqr(position) <= 7.84 && player.level().clip(new ClipContext(position, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS) {
                if (CombatController.damage(player, target, 12F) && target instanceof net.minecraft.world.entity.LivingEntity living) ParalysisController.apply(living);
            }
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { ANCHORS.clear(); }
}
