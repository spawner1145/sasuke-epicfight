package dev.sasuke;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.ServerPlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;

@Mod.EventBusSubscriber(modid = SasukeMod.ID)
public final class CombatController {
    public enum Phase { NORMAL, DRAW, READY, DASH, SHEATHE, AMATERASU_ONE, SECOND_READY, AMATERASU_TWO, COMBO, COMBO_RECOVERY }
    private static final Map<ServerPlayer, State> STATES = new WeakHashMap<>();

    private static final Map<ServerPlayer, java.util.Set<net.minecraft.world.effect.MobEffect>> BODY_LEASES = new WeakHashMap<>();

    private static void syncBodyImmunity(ServerPlayer player) {
        State state = STATES.get(player);
        boolean live = player.isAlive() && equipped(player) && state != null;
        boolean hard = live && !captured(player) && switch (state.phase) {
            case DRAW, DASH, SHEATHE, AMATERASU_ONE, AMATERASU_TWO, COMBO, COMBO_RECOVERY -> true;
            default -> false;
        };
        boolean armor = live && state.spirit != null && state.spirit.isAlive() && !state.spirit.dissolving();
        syncBodyEffect(player, SasukeMod.HARD_BODY.get(), hard);
        syncBodyEffect(player, SasukeMod.SUPER_ARMOR.get(), armor);
    }

    private static void syncBodyEffect(ServerPlayer player, net.minecraft.world.effect.MobEffect effect, boolean active) {
        var leases = BODY_LEASES.computeIfAbsent(player, ignored -> new java.util.HashSet<>());
        var current = player.getEffect(effect);
        boolean owned = leases.contains(effect) && current != null && !current.isInfiniteDuration() && current.getDuration() <= 2;
        if (active && (current == null || owned)) {
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect, 2, 0, false, false, true));
            leases.add(effect);
        } else {
            if (!active && owned) player.removeEffect(effect);
            leases.remove(effect);
        }
        if (leases.isEmpty()) BODY_LEASES.remove(player);
    }
    private static final UUID LISTENER = UUID.fromString("c1fe110d-5216-4e5d-a873-293b2cf47a91");
    private static final UUID SUSANOO_SPEED = UUID.fromString("6359a1bd-ef3e-46e3-93ec-b02492eae3e7");
    private static final UUID SUSANOO_KNOCKBACK = UUID.fromString("ade2c302-2bf7-4f47-a1b3-1945f39de20c");
    public static final int SUSANOO_COOLDOWN = 12 * 20;
    public static final int AMATERASU_COOLDOWN = 10 * 20;
    public static final int AMATERASU_SECOND_COOLDOWN = 15 * 20;
    public static final int READY_WINDOW = 60;
    public static final int SUSANOO_READY_WINDOW = 100;
    public static final int SHEATHE_ATTACK_WINDOW = 12; // 0.60s; 4a1 now lasts 0.60 / 1.10 seconds.
    private static final int BASIC_COMBO_WINDOW = 4; // 0.2 seconds at 20 ticks/second.
    public static final float BASE_ATTACK_DAMAGE = 12F;
    private static final int COMBO_BURST_START = 25;
    private static final int COMBO_BURST_END = 65;
    private static final int COMBO_RELEASE = 68;

    public static final class State {
        public Phase phase = Phase.NORMAL;
        public long firstReady;
        public long secondReady;
        long began;
        long until;
        Vec3 anchor = Vec3.ZERO;
        Vec3 direction = Vec3.ZERO;
        Vec3 origin = Vec3.ZERO;
        Vec3 impact = Vec3.ZERO;
        FlameCast flame;
        boolean queuedAttack;
        long specialUntil;
        boolean specialAttack;
        float lockedYaw;
        float lockedPitch;
        String basic = "";
        long basicBegan;
        long comboExpires;
        long basicSheatheAt = -1;
        long lastBasicInput = Long.MIN_VALUE / 2;
        Vec3 basicSheatheAnchor = Vec3.ZERO;
        long comboInputUntil;
        long reverseFlameInputUntil;
        int flameVoice = 1;
        long secondVoiceAt = -1;
        Vec3 comboGrip = Vec3.ZERO;
        boolean comboGrabbed;
        boolean basicTriggered;
        int shieldHits;
        boolean shieldBreakPending;
        long skeletonUntil;
        long summonAt = -1;
        boolean summonFlame;
        boolean startingAction;
        final List<LivingEntity> swept = new ArrayList<>();
        SusanooEntity spirit;
        ServerPlayerPatch installedPatch;
        final List<Entity> captured = new ArrayList<>();
    }

    private static final class FlameCast {
        final net.minecraft.world.level.Level level;
        final FlameLifecycle lifecycle;
        final Vec3 direction;
        final List<LivingEntity> swept = new ArrayList<>();
        Vec3 impact;
        int step;
        long burstAt = -1;
        FlameCast(ServerPlayer player) {
            level = player.level();
            lifecycle = new FlameLifecycle(level.getGameTime());
            direction = horizontal(player);
            impact = player.position();
        }
    }

    static boolean flameSecondReady(ServerPlayer player, State state) {
        return state.flame != null && state.flame.lifecycle.ready(player.level().getGameTime());
    }

    private static void interruptFlame(ServerPlayer player, State state) {
        if (state.flame != null && !state.flame.lifecycle.survivesInterrupt(player.level().getGameTime())) state.flame = null;
        // Stage two is consumed when cast. Canceling its motion cannot refund it.
    }

    private static void tickFlame(ServerPlayer player, State state, long now) {
        FlameCast flame = state.flame;
        if (flame == null) return;
        if (!player.isAlive() || player.hasDisconnected() || flame.level != player.level()
                || flame.lifecycle.expired(now)) { state.flame = null; return; }
        if (!flame.lifecycle.launched(now) || flame.lifecycle.exploded) return;
        if (flame.burstAt < 0) {
            Vec3 next = groundStep(player, flame.impact, flame.direction);
            if (next != null) {
                flame.impact = next;
                for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(1.5, 2, 1.5))) {
                    if (validTarget(player, target) && player.hasLineOfSight(target) && !flame.swept.contains(target)) flame.swept.add(target);
                }
                flame.swept.removeIf(target -> !validTarget(player, target) || target.level() != player.level());
                for (LivingEntity target : flame.swept) {
                    // Recheck every step, including targets armored after entering the wave.
                    if (superArmor(target)) continue;
                    Vec3 pull = next.subtract(target.position());
                    target.stopRiding();
                    target.move(MoverType.SELF, pull.scale(Math.min(1, 1.5 / Math.max(0.01, pull.length()))));
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                    if (target instanceof ServerPlayer other) {
                        // Move the skill's stationary anchor too, so its next tick cannot undo suction.
                        State targetState = STATES.get(other);
                        if (targetState != null && skillBody(other)) targetState.anchor = target.position();
                        other.connection.teleport(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                    }
                }
                erupt(player, next, 0.7F, 12F);
            }
            if (next == null || ++flame.step >= 14) {
                flame.burstAt = now + 3;
                SasukeNetwork.burst(player, flame.impact, -3F);
            }
        } else if (now >= flame.burstAt) {
            erupt(player, flame.impact, 3F, 24F);
            flame.lifecycle.exploded = true;
            flame.swept.clear();
        }
    }

    public static boolean equipped(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && player.getMainHandItem().is(SasukeMod.KUSANAGI.get());
    }

    public static boolean isDrawn(ServerPlayer player) {
        State state = STATES.get(player);
        return state != null && (state.phase == Phase.READY || state.phase == Phase.DASH);
    }

    public static boolean specialAttack(ServerPlayer player) {
        State state = STATES.get(player);
        return state != null && state.specialAttack;
    }

    private static boolean cooldownReady(ServerPlayer player, long now, long readyAt) {
        // Only bypass cooldowns, never phase gates or second-stage input windows.
        return player.isCreative() || now >= readyAt;
    }

    private static boolean allowsBasicAttack(Phase phase) {
        return phase == Phase.NORMAL || phase == Phase.SECOND_READY;
    }

    public static boolean skillBody(LivingEntity target) {
        return target.hasEffect(SasukeMod.HARD_BODY.get());
    }

    static class SkillDamageSource extends net.minecraft.world.damagesource.DamageSource {
        SkillDamageSource(ServerPlayer player) {
            super(player.damageSources().playerAttack(player).typeHolder(), player);
        }
    }

    private static boolean skillDamage(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) return false;
        if (source instanceof SkillDamageSource) return true;
        // Our skeleton slash uses Epic Fight's animation damage source.
        // Other mods' non-basic attacks are not Sasuke skills.
        return source instanceof yesman.epicfight.world.damagesource.EpicFightDamageSource epic
            && epic.getAnimation().equals(SasukeAnimations.player("dash_spin_slash"));
    }

    private static boolean breakHardBody(LivingEntity target, net.minecraft.world.damagesource.DamageSource source) {
        if (superArmor(target) || !skillBody(target) || !skillDamage(source)) return false;
        interruptForCapture(target);
        return true;
    }

    public static boolean superArmor(LivingEntity target) {
        return target.hasEffect(SasukeMod.SUPER_ARMOR.get());
    }

    public static void interruptForCapture(LivingEntity target) {
        target.removeEffect(SasukeMod.HARD_BODY.get());
        if (target instanceof ServerPlayer player) {
            State state = STATES.get(player);
            // Interrupt the motion without destroying the skeleton that supplies armor.
            if (state != null) cancelSkillMotion(player, state);
        }
    }

    public static boolean captured(LivingEntity target) {
        for (var entry : STATES.entrySet()) {
            State state = entry.getValue();
            if (state.phase == Phase.COMBO && entry.getKey().isAlive() && !entry.getKey().hasDisconnected()
                && target.level() == entry.getKey().level() && state.spirit != null && state.spirit.isAlive()
                && state.captured.contains(target)) return true;
        }
        return false;
    }

    public static boolean comboExpired(ServerPlayer player) {
        State state = STATES.get(player);
        return state == null || player.level().getGameTime() >= state.comboExpires;
    }

    public static void sheatheEnded(ServerPlayer player, boolean completed) {
        State state = STATES.get(player);
        if (state == null || state.phase != Phase.SHEATHE) return;
        clear(player, state);
        if (completed) state.specialUntil = player.level().getGameTime() + SHEATHE_ATTACK_WINDOW;
    }

    public static void basicAttackEnded(ServerPlayer player) {
        State state = STATES.get(player);
        if (state != null && allowsBasicAttack(state.phase) && equipped(player)) {
            // Defer until the animator has finished switching animations. Any new
            // action cancels this request through ACTION_EVENT_SERVER below.
            state.basicSheatheAt = player.level().getGameTime() + 4;
        }
    }

    private static void tickBasicSheathe(ServerPlayer player, State state, ServerPlayerPatch patch, long now) {
        var current = patch.getAnimator().getPlayerFor(null).getRealAnimation();
        if (current.equals(SasukeAnimations.player("basic_sheathe"))) {
            if (player.isUsingItem() || !player.onGround() || player.isPassenger()
                    || player.position().subtract(state.basicSheatheAnchor).horizontalDistanceSqr() > 0.01) {
                restoreMovementAnimation(player, patch);
            }
            return;
        }
        if (state.basicSheatheAt < 0 || now < state.basicSheatheAt) return;
        // Held attack packets arrive every two ticks, including during recovery.
        // Wait for a genuine input gap instead of stealing the next combo attack.
        if (now - state.lastBasicInput <= 4) return;
        state.basicSheatheAt = -1;
        if (!allowsBasicAttack(state.phase) || patch.getEntityState().inaction()
                || patch.getEntityState().hurt() || ParalysisController.active(player)
                || player.isUsingItem() || !player.onGround() || player.isPassenger()
                || player.getDeltaMovement().horizontalDistanceSqr() > 0.0025) return;
        state.basicSheatheAnchor = player.position();
        patch.playAnimationSynchronized(SasukeAnimations.player("basic_sheathe"), 0F);
    }

    private static State state(ServerPlayer player) {
        return STATES.computeIfAbsent(player, ignored -> {
            State created = new State();
            created.firstReady = player.getPersistentData().getLong("sasukeFirstReady");
            created.secondReady = player.getPersistentData().getLong("sasukeSecondReady");
            return created;
        });
    }

    public static void input(ServerPlayer player, SasukeNetwork.Input input) {
        if (!equipped(player) || ParalysisController.active(player)) return;
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null || !patch.isEpicFightMode()) return;
        State state = state(player);
        long now = player.level().getGameTime();
        if (input.key() == 5) {
            state.lastBasicInput = now;
            if (!allowsBasicAttack(state.phase) || player.isUsingItem() || player.isPassenger() || !player.onGround() || !patch.getEntityState().canBasicAttack()) return;
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try { patch.getSkill(yesman.epicfight.skill.SkillSlots.BASIC_ATTACK).requestCasting(patch, buffer); }
            finally { buffer.release(); }
            return;
        }
        if (input.key() == 4) {
            if (state.phase == Phase.SHEATHE) {
                clear(player, state);
                restoreMovementAnimation(player, patch);
            }
            return;
        }
        if (state.phase == Phase.SHEATHE && input.key() != 3) {
            clear(player, state);
            restoreMovementAnimation(player, patch);
        }
        if (input.key() == 1 && state.phase == Phase.AMATERASU_TWO && now - state.began < 6 && cooldownReady(player, now, state.firstReady)) {
            summonWithBlackFlame(player, state, now);
            return;
        }
        // While stage two is available, allow the reverse chord as well.
        // Defer the ordinary summon briefly so the two orders produce one burst.
        if (input.key() == 1 && (state.phase == Phase.SECOND_READY || state.phase == Phase.NORMAL)
                && flameSecondReady(player, state) && !patch.getEntityState().inaction()
                && !patch.getEntityState().hurt() && !captured(player) && cooldownReady(player, now, state.firstReady)) {
            if (state.reverseFlameInputUntil == 0) state.reverseFlameInputUntil = now + 6;
            return;
        }
        if (input.key() == 3) {
            if (state.phase == Phase.DRAW) state.queuedAttack = true;
            else if (state.phase == Phase.READY) start(player, state, Phase.DASH, "dash_spin_slash", 29);
            return;
        }
        if (input.key() == 2 && state.phase == Phase.DRAW && now < state.comboInputUntil && cooldownReady(player, now, state.secondReady)) {
            state.comboInputUntil = 0;
            state.firstReady = now + SUSANOO_COOLDOWN;
            state.secondReady = now + AMATERASU_COOLDOWN;
            state.captured.clear();
            Vec3 facing = horizontal(player);
            Vec3 center = player.position().add(facing.scale(3)).add(0, 1.5, 0);
            state.comboGrip = center;
            state.comboGrabbed = false;
            start(player, state, Phase.COMBO, "amaterasu_combo", 83);
            persist(player, state);
            return;
        }
        if (input.key() == 2 && flameSecondReady(player, state)
                && (state.phase == Phase.SECOND_READY || state.phase == Phase.NORMAL || state.phase == Phase.READY)) {
            if (patch.getEntityState().inaction() || patch.getEntityState().hurt() || captured(player) || player.isPassenger()) return;
            if (now < state.reverseFlameInputUntil && cooldownReady(player, now, state.firstReady)) {
                summonWithBlackFlame(player, state, now);
                return;
            }
            Vec3 forward = state.flame.direction;
            Vec3 left = new Vec3(forward.z, 0, -forward.x);
            Vec3 direction = forward.scale(input.forward()).add(left.scale(input.left()));
            Vec3 destination = state.flame.impact;
            for (int step = 0; direction.lengthSqr() >= 0.01 && step < 4; step++) {
                Vec3 next = groundStep(player, destination, direction.normalize());
                if (next == null) break;
                destination = next;
            }
            state.impact = destination;
            state.flame.lifecycle.consumed = true;
            state.secondReady = now + AMATERASU_SECOND_COOLDOWN;
            start(player, state, Phase.AMATERASU_TWO, "amaterasu_2", 24);
            state.secondVoiceAt = now + 6;
            persist(player, state);
            return;
        }
        boolean skeletonCast = input.key() == 2 && (state.phase == Phase.READY
            || state.phase == Phase.DRAW && now >= state.comboInputUntil);
        if (state.phase != Phase.NORMAL && !skeletonCast || patch.getEntityState().inaction() || player.isPassenger()) return;
        if (input.key() == 1 && state.phase == Phase.NORMAL && cooldownReady(player, now, state.firstReady)) {
            state.firstReady = now + SUSANOO_COOLDOWN;
            summon(player, state);
            state.comboInputUntil = now + 6;
        } else if (input.key() == 2 && (state.flame == null || state.flame.lifecycle.consumed) && cooldownReady(player, now, state.secondReady) && player.onGround()) {
            state.secondReady = now + AMATERASU_COOLDOWN;
            state.flameVoice = CombatAudio.next(player, "flame", 3);
            CombatAudio.play(player, "flame_1_" + state.flameVoice);
            state.direction = horizontal(player);
            state.origin = state.impact = player.position();
            state.swept.clear();
            state.flame = new FlameCast(player);
            start(player, state, Phase.AMATERASU_ONE, "amaterasu_1", 21);
        }
        persist(player, state);
    }

    private static void persist(ServerPlayer player, State state) {
        player.getPersistentData().putLong("sasukeFirstReady", state.firstReady);
        player.getPersistentData().putLong("sasukeSecondReady", state.secondReady);
        SasukeNetwork.status(player, state);
    }

    private static void summonWithBlackFlame(ServerPlayer player, State state, long now) {
        if (state.flame != null) state.flame.lifecycle.consumed = true;
        state.reverseFlameInputUntil = 0;
        state.secondVoiceAt = -1;
        state.comboInputUntil = 0;
        state.firstReady = now + SUSANOO_COOLDOWN;
        state.secondReady = now + AMATERASU_SECOND_COOLDOWN;
        summon(player, state, false);
        persist(player, state);
    }

    private static void restoreMovementAnimation(ServerPlayer player, ServerPlayerPatch patch) {
        patch.modifyLivingMotionByCurrentItem();
        String animation = player.isSprinting() ? "run_sheathed"
            : player.getDeltaMovement().horizontalDistanceSqr() > 0.0025D ? "walk_sheathed" : "idle";
        patch.playAnimationSynchronized(SasukeAnimations.player(animation), 0F);
    }

    private static void start(ServerPlayer player, State state, Phase phase, String animation, int ticks) {
        if (phase == Phase.COMBO) {
            state.lockedYaw = player.getYRot();
            state.lockedPitch = player.getXRot();
        }
        state.phase = phase;
        syncBodyImmunity(player);
        state.began = player.level().getGameTime();
        state.until = state.began + ticks;
        state.anchor = player.position();
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        patch.modifyLivingMotionByCurrentItem();
        state.startingAction = true;
        try { patch.playAnimationSynchronized(SasukeAnimations.player(animation), 0F); }
        finally { state.startingAction = false; }
        if (phase == Phase.DASH && state.spirit != null) {
            state.spirit.discard();
            state.spirit = null;
        }
        if (state.spirit != null && state.spirit.isAlive() && phase != Phase.DASH) state.spirit.animate(animation);
        SasukeNetwork.status(player, state);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        try { tickCombat(event); }
        finally { syncBodyImmunity(player); }
    }

    private static void tickCombat(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        State state = state(player);
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) return;
        if (state.shieldBreakPending) {
            clear(player, state);
            if (!captured(player)) restoreMovementAnimation(player, patch);
        }
        if (state.spirit != null && player.level().getGameTime() >= state.skeletonUntil) {
            state.spirit.dissolve();
            state.spirit = null;
            state.shieldHits = 0;
        }
        if (superArmor(player) && !captured(player)) patch.setStamina(patch.getMaxStamina());
        var resistance = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        // Remove the legacy state-driven modifier; the body effects now own resistance.
        if (resistance != null) resistance.removeModifier(SUSANOO_KNOCKBACK);
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            boolean enabled = equipped(player) && state.spirit != null && state.spirit.isAlive() && !state.spirit.dissolving();
            if (enabled && speed.getModifier(SUSANOO_SPEED) == null) {
                speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SUSANOO_SPEED, "Susanoo speed", 0.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
            } else if (!enabled) speed.removeModifier(SUSANOO_SPEED);
        }
        if (state.installedPatch != patch) {
            state.installedPatch = patch;
            patch.getEventListener().addEventListener(EventType.STAMINA_CONSUME_EVENT, LISTENER, consume -> {
                if (superArmor(player)) consume.setAmount(0F);
            });
            patch.getEventListener().addEventListener(EventType.SKILL_CAST_EVENT, LISTENER, cast -> {
                if (ParalysisController.active(player)) cast.setCanceled(true);
            });
            patch.getEventListener().addEventListener(EventType.ACTION_EVENT_SERVER, LISTENER, action -> {
                if (!state.startingAction && state.phase != Phase.NORMAL && state.phase != Phase.READY
                        && state.phase != Phase.SECOND_READY
                        && !(state.phase == Phase.COMBO_RECOVERY && action.getAnimation().equals(SasukeAnimations.player("amaterasu_combo")))) cancelSkillMotion(player, state);
                state.basicSheatheAt = -1;
                if (equipped(player)) {
                    for (String name : new String[]{"1a", "2a", "3a", "4a1", "4a2", "4a3", "dash_spin_slash", "basic_sheathe", "sheathe_flourish"}) {
                        if (action.getAnimation().equals(SasukeAnimations.player(name))) { CombatAudio.action(player, name); break; }
                    }
                }
                if (action.getAnimation().equals(SasukeAnimations.player("basic_sheathe"))) return;
                state.basic = "";
                if (!state.specialAttack) {
                    state.comboExpires = 0;
                    for (String name : new String[]{"1a", "2a", "3a", "4a1", "4a2", "4a3"}) {
                        if (action.getAnimation().equals(SasukeAnimations.ATTACKS.get(name))) {
                            state.comboExpires = player.level().getGameTime() + RecoveryAttackAnimation.durationTicks(SasukeAnimations.duration(name)) + BASIC_COMBO_WINDOW;
                            break;
                        }
                    }
                }
                for (String name : new String[]{"3a", "4a1", "4a2", "4a3"}) {
                    if (action.getAnimation().equals(SasukeAnimations.ATTACKS.get(name)) && equipped(player)) {
                        state.basic = name;
                        state.basicBegan = player.level().getGameTime();
                        state.basicTriggered = false;
                    }
                }
            });
            patch.getEventListener().addEventListener(EventType.DEAL_DAMAGE_EVENT_DAMAGE, LISTENER, hit -> {
                if (hit.getAttackDamage() > 0 && (state.phase == Phase.DASH || hit.getDamageSource().getAnimation().equals(SasukeAnimations.ATTACKS.get("4a1")))) ParalysisController.apply(hit.getTarget());
                if (state.phase == Phase.DASH && BlackFlameController.hasAura(player) && hit.getAttackDamage() > 0) BlackFlameController.burn(player, hit.getTarget());
            });
            patch.getEventListener().addEventListener(EventType.BASIC_ATTACK_EVENT, LISTENER, attack -> {
                if (ParalysisController.active(player)) { attack.setCanceled(true); return; }
                if (!equipped(player)) return;
                state.lastBasicInput = player.level().getGameTime();
                if (state.phase == Phase.SHEATHE) clear(player, state);
                if (allowsBasicAttack(state.phase) && player.level().getGameTime() < state.specialUntil) {
                    attack.setCanceled(true);
                    state.specialAttack = true;
                    try {
                        patch.playAnimationSynchronized(SasukeAnimations.ATTACKS.get("4a1"), 0F);
                    } finally {
                        state.specialAttack = false;
                    }
                    return;
                }
                if (state.phase == Phase.READY || state.phase == Phase.DRAW) {
                    attack.setCanceled(true);
                    input(player, new SasukeNetwork.Input(3, 0, 0));
                } else if (!allowsBasicAttack(state.phase)) attack.setCanceled(true);
                else {
                    var data = patch.getSkill(yesman.epicfight.skill.SkillSlots.BASIC_ATTACK).getDataManager();
                    int counter = data.getDataValue(yesman.epicfight.skill.SkillDataKeys.COMBO_COUNTER.get());
                    if (Math.floorMod(player.getPersistentData().getInt("sasukeComboStage"), 3) == 2
                            && !LightningController.hasAnchor(player)) {
                        player.getPersistentData().putInt("sasukeComboStage", 0);
                        counter = Math.floorMod(counter, 4);
                        data.setData(yesman.epicfight.skill.SkillDataKeys.COMBO_COUNTER.get(), counter);
                    }
                    if (counter == 0 || player.level().getGameTime() >= state.comboExpires) {
                        int stage = Math.floorMod(player.getPersistentData().getInt("sasukeComboStage"), 3);
                        data.setData(yesman.epicfight.skill.SkillDataKeys.COMBO_COUNTER.get(), stage * 4);
                    }
                }
            });
        }
        long now = player.level().getGameTime();
        tickFlame(player, state, now);
        if (state.phase == Phase.AMATERASU_TWO && (patch.getEntityState().hurt() || ParalysisController.active(player))) cancelSkillMotion(player, state);
        if (state.summonAt >= 0 && now >= state.summonAt && equipped(player)
                && !patch.getEntityState().hurt() && !ParalysisController.active(player)) activateSkeleton(player, state);
        if (state.secondVoiceAt >= 0) {
            if (state.phase != Phase.AMATERASU_TWO || !equipped(player) || patch.getEntityState().hurt()
                    || ParalysisController.active(player)) state.secondVoiceAt = -1;
            else if (now >= state.secondVoiceAt) {
                CombatAudio.play(player, "flame_2_" + state.flameVoice);
                state.secondVoiceAt = -1;
            }
        }
        if (state.reverseFlameInputUntil != 0) {
            if ((state.phase != Phase.SECOND_READY && state.phase != Phase.NORMAL) || !flameSecondReady(player, state) || !equipped(player) || !patch.isEpicFightMode()
                    || patch.getEntityState().hurt() || ParalysisController.active(player)) {
                state.reverseFlameInputUntil = 0;
            } else if (now >= state.reverseFlameInputUntil) {
                state.reverseFlameInputUntil = 0;
                if (cooldownReady(player, now, state.firstReady)) {
                    state.firstReady = now + SUSANOO_COOLDOWN;
                    summon(player, state);
                    persist(player, state);
                }
            }
        }
        if (patch.getEntityState().hurt() || ParalysisController.active(player)) {
            state.basicSheatheAt = -1;
            state.comboExpires = 0;
            state.specialUntil = 0;
            if (equipped(player)) {
                int stage = Math.floorMod(player.getPersistentData().getInt("sasukeComboStage"), 3);
                patch.getSkill(yesman.epicfight.skill.SkillSlots.BASIC_ATTACK).getDataManager()
                    .setData(yesman.epicfight.skill.SkillDataKeys.COMBO_COUNTER.get(), stage * 4);
            }
        }
        if (!equipped(player) || !patch.isEpicFightMode() || (patch.getEntityState().hurt() && state.spirit == null)) {
            state.basicSheatheAt = -1;
            state.basic = "";
            state.specialUntil = 0;
            if (state.phase != Phase.NORMAL) clear(player, state);
            return;
        }
        if (allowsBasicAttack(state.phase)) tickBasic(player, state);
        tickBasicSheathe(player, state, patch, now);
        if (state.phase == Phase.NORMAL) {
            if (player.tickCount % 10 == 0) SasukeNetwork.status(player, state);
            return;
        }
        int elapsed = (int)(now - state.began);
        if (state.phase == Phase.SHEATHE && (player.isUsingItem() || player.position().subtract(state.anchor).horizontalDistanceSqr() > 0.01 || !player.onGround())) {
            clear(player, state);
            restoreMovementAnimation(player, patch);
            return;
        }
        if (state.phase == Phase.COMBO || state.phase == Phase.COMBO_RECOVERY || state.phase == Phase.AMATERASU_ONE || state.phase == Phase.AMATERASU_TWO) {
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
            if (player.position().distanceToSqr(state.anchor) > 0.0025) player.connection.teleport(state.anchor.x, player.getY(), state.anchor.z, player.getYRot(), player.getXRot());
        }
        if (state.phase == Phase.COMBO || state.phase == Phase.COMBO_RECOVERY) {
            player.setYRot(state.lockedYaw);
            player.setYHeadRot(state.lockedYaw);
            player.setYBodyRot(state.lockedYaw);
            player.setXRot(state.lockedPitch);
            player.connection.teleport(state.anchor.x, player.getY(), state.anchor.z, state.lockedYaw, state.lockedPitch);
        }
        if (state.phase == Phase.DASH && elapsed >= 5 && elapsed <= 14) {
            if (elapsed >= 6 && state.spirit != null) {
                state.spirit.dissolve();
                state.spirit = null;
            }
            player.move(MoverType.SELF, horizontal(player).scale(RecoveryAttackAnimation.FOURTH_FORWARD_DISTANCE * 0.9 / 10.0));
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            if (elapsed >= 6) {
                var joint = patch.getArmature().searchJointByName("Tool_R");
                var local = patch.getArmature().getBoundTransformFor(patch.getAnimator().getPose(1F), joint).toTranslationVector();
                Vec3 blade = player.position().add(new Vec3(-local.x, local.y, -local.z).yRot((float)Math.toRadians(-player.yBodyRot)));
                SasukeNetwork.flame(player.serverLevel(), player.position().add(0, 1, 0), 0.7F, player.getId(), 5);
                SasukeNetwork.flame(player.serverLevel(), blade, 0.45F, -1, 10);
                if (BlackFlameController.hasAura(player)) SasukeNetwork.flame(player.serverLevel(), blade, 0.45F, player.getId(), 7);
            }
        }
        if (state.phase == Phase.AMATERASU_TWO && elapsed == 1) SasukeNetwork.burst(player, state.impact, -3.5F);
        if (state.phase == Phase.AMATERASU_TWO && elapsed == 10) erupt(player, state.impact, 3.5F, 30F);
        if (state.phase == Phase.COMBO) {
            if (elapsed == 6) captureCombo(player, state);
            if ((state.spirit == null || !state.spirit.isAlive()) && state.summonAt < 0) { clear(player, state); return; }
            Vec3 grip = state.comboGrip;
            state.captured.removeIf(entity -> !validTarget(player, entity) || entity.level() != player.level() || entity.position().distanceToSqr(player.position()) > 144);
            if (elapsed >= 22 && !state.comboGrabbed) {
                state.phase = Phase.COMBO_RECOVERY;
                state.began = now;
                state.until = now + 14;
                patch.playAnimationSynchronized(SasukeAnimations.player("amaterasu_combo"), -3.4F);
                state.spirit.animate("combo_retract");
                SasukeNetwork.status(player, state);
                return;
            }
            if (elapsed == 22) {
                SasukeNetwork.burst(player, grip, -1.8F, true);
            }
            for (Entity target : state.captured) {
                Vec3 held = grip.add(0, -target.getBbHeight() * 0.5, 0);
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
                target.fallDistance = 0;
                if (target instanceof ServerPlayer other) other.connection.teleport(held.x, held.y, held.z, other.getYRot(), other.getXRot());
                else target.teleportTo(held.x, held.y, held.z);
            }
            if (elapsed >= COMBO_BURST_START && elapsed <= COMBO_BURST_END && (elapsed - COMBO_BURST_START) % 5 == 0) {
                boolean finisher = elapsed == COMBO_BURST_END;
                float radius = finisher ? 3.0F : 2.1F;
                SasukeNetwork.burst(player, grip, radius, true);
                if ((elapsed - COMBO_BURST_START) % 10 == 0) BlackFlameController.cloud(player, grip, radius);
                for (Entity target : player.level().getEntities(player, new AABB(grip, grip).inflate(radius), entity -> validTarget(player, entity))) {
                    if (target.getBoundingBox().distanceToSqr(grip) > radius * radius) continue;
                    var obstruction = player.level().clip(new ClipContext(grip, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    if (obstruction.getType() != HitResult.Type.MISS) continue;
                    BlackFlameController.comboDamage(player, target, finisher ? 40F : 15F);
                    if (target instanceof LivingEntity living) BlackFlameController.burn(player, living);
                }
            }
            if (elapsed >= COMBO_RELEASE) state.captured.clear();
        }
        if (now >= state.until) {
            switch (state.phase) {
                case DRAW -> {
                    if (state.queuedAttack) {
                        state.queuedAttack = false;
                        start(player, state, Phase.DASH, "dash_spin_slash", 29);
                    } else start(player, state, Phase.READY, "idle_sword_side", SUSANOO_READY_WINDOW);
                }
                case READY -> {
                    clear(player, state);
                    restoreMovementAnimation(player, patch);
                }
                case DASH -> start(player, state, Phase.SHEATHE, "sheathe_flourish", 50);
                case SHEATHE -> clear(player, state);
                case COMBO_RECOVERY -> {
                    clear(player, state);
                    restoreMovementAnimation(player, patch);
                }
                case AMATERASU_ONE -> {
                    state.phase = Phase.SECOND_READY;
                    state.until = state.flame == null ? now : state.flame.lifecycle.began + 21 + READY_WINDOW;
                    SasukeNetwork.status(player, state);
                }
                case SECOND_READY, AMATERASU_TWO -> {
                    if (state.spirit != null && state.spirit.isAlive() && now < state.skeletonUntil) {
                        start(player, state, Phase.READY, "idle_sword_side", (int)(state.skeletonUntil - now));
                    } else clear(player, state);
                }
                default -> clear(player, state);
            }
        }
        if (player.tickCount % 10 == 0) SasukeNetwork.status(player, state);
    }

    private static void tickBasic(ServerPlayer player, State state) {
        int elapsed = (int)(player.level().getGameTime() - state.basicBegan);
        if (elapsed > 18 || state.basic.isEmpty()) return;
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) return;
        var animationPlayer = patch.getAnimator().getPlayerFor(null);
        if (!animationPlayer.getRealAnimation().equals(SasukeAnimations.ATTACKS.get(state.basic))
                || animationPlayer.getAnimation().get().isLinkAnimation()) return;
        float animationTicks = animationPlayer.getElapsedTime() * 20F;
        int end = switch (state.basic) { case "3a" -> 6; case "4a1", "4a2" -> 11; default -> 16; };
        if (animationTicks > end) return;
        if (!state.basic.equals("4a2") && elapsed > 0 && elapsed % 3 == 0) SasukeNetwork.flame(player.serverLevel(), player.position(), 1F, player.getId(), 8);
        if (state.basic.equals("4a1") && elapsed % 2 == 0) SasukeNetwork.flame(player.serverLevel(), player.position().add(0, 1, 0), 0.9F, player.getId(), 5);
        int trigger = state.basic.equals("4a2") ? 7 : 14;
        if (!state.basicTriggered && animationTicks >= trigger) {
            state.basicTriggered = true;
            if (state.basic.equals("4a2")) LightningController.plant(player);
            if (state.basic.equals("4a3")) LightningController.launch(player);
        }
    }

    private static Vec3 horizontal(ServerPlayer player) {
        double yaw = Math.toRadians(player.getYRot());
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    private static Vec3 groundStep(ServerPlayer player, Vec3 from, Vec3 direction) {
        Vec3 next = from.add(direction);
        if (!player.level().hasChunkAt(BlockPos.containing(next))) return null;
        var wall = player.level().clip(new ClipContext(from.add(0, 2.05, 0), next.add(0, 2.05, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (wall.getType() != HitResult.Type.MISS) return null;
        var floor = player.level().clip(new ClipContext(next.add(0, 2.05, 0), next.add(0, -2.5, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (floor.getType() == HitResult.Type.MISS || floor.getLocation().y - from.y > 2.01) return null;
        return floor.getLocation().add(0, 0.02, 0);
    }

    static boolean validTarget(ServerPlayer player, Entity target) {
        return target != player && target.isAlive() && !(target instanceof net.minecraft.world.entity.item.ItemEntity)
            && !(target instanceof SusanooEntity) && !target.isSpectator() && !player.isAlliedTo(target)
            && (!(target instanceof ServerPlayer other) || (!other.isCreative() && player.canHarmPlayer(other)));
    }

    static float scaledSkillDamage(ServerPlayer player, float baseDamage) {
        double attack = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        return (float)(baseDamage * Math.max(0D, attack) / BASE_ATTACK_DAMAGE);
    }

    static boolean damage(ServerPlayer player, Entity target, float amount) {
        if (!validTarget(player, target)) return false;
        return target.hurt(new SkillDamageSource(player), scaledSkillDamage(player, amount));
    }

    private static void erupt(ServerPlayer player, Vec3 position, float radius, float amount) {
        SasukeNetwork.burst(player, position, radius);
        if (radius >= 1F) amaterasuFracture(player, position, radius);
        BlackFlameController.pool(player, position, radius);
        AABB area = new AABB(position.x - radius, position.y - 0.5, position.z - radius, position.x + radius, position.y + radius * 1.6, position.z + radius);
        for (Entity target : player.level().getEntities(player, area, entity -> validTarget(player, entity))) {
            Vec3 center = target.getBoundingBox().getCenter();
            var obstruction = player.level().clip(new ClipContext(position.add(0, 0.2, 0), center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (obstruction.getType() == HitResult.Type.MISS) {
                BlackFlameController.damage(player, target, amount);
                if (target instanceof LivingEntity living) BlackFlameController.burn(player, living);
            }
        }
    }

    private static void amaterasuFracture(ServerPlayer player, Vec3 position, float radius) {
        if (!player.level().hasChunkAt(BlockPos.containing(position))) return;
        var floor = player.level().clip(new ClipContext(position.add(0, 1, 0), position.add(0, -2.5, 0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (floor.getType() == HitResult.Type.MISS) return;
        // Same terrain effect as 4a2; explosion damage remains owned by Amaterasu.
        yesman.epicfight.api.utils.LevelUtil.circleSlamFracture(player, player.level(),
            floor.getBlockPos().getCenter(), radius, false, false, false);
    }

    private static void summon(ServerPlayer player, State state) {
        summon(player, state, true);
    }

    private static void summon(ServerPlayer player, State state, boolean burst) {
        CombatAudio.play(player, "susanoo_" + CombatAudio.next(player, "susanoo", 2));
        state.summonAt = player.level().getGameTime() + 6;
        state.summonFlame = !burst;
        start(player, state, Phase.DRAW, "draw_to_side", 12);
    }

    private static void activateSkeleton(ServerPlayer player, State state) {
        state.summonAt = -1;
        if (state.spirit != null) state.spirit.dissolve();
        state.shieldHits = 3;
        state.shieldBreakPending = false;
        state.skeletonUntil = player.level().getGameTime() + SUSANOO_READY_WINDOW;
        state.spirit = new SusanooEntity(SasukeMod.SUSANOO.get(), player.level());
        state.spirit.tame(player);
        state.spirit.setPos(player.position());
        player.level().addFreshEntity(state.spirit);
        state.spirit.animate(state.phase == Phase.COMBO ? "amaterasu_combo" : "draw_to_side");
        syncBodyImmunity(player);
        if (state.summonFlame) {
            BlackFlameController.aura(player);
            combinedSummonBurst(player);
        } else summonBurst(player);
        state.summonFlame = false;
    }

    private static void cancelSkillMotion(ServerPlayer player, State state) {
        interruptFlame(player, state);
        state.summonAt = -1;
        state.summonFlame = false;
        state.captured.clear();
        state.comboInputUntil = 0;
        state.reverseFlameInputUntil = 0;
        state.secondVoiceAt = -1;
        state.queuedAttack = false;
        state.basic = "";
        state.basicSheatheAt = -1;
        state.phase = Phase.NORMAL;
        syncBodyImmunity(player);
        if (state.spirit != null && state.spirit.isAlive() && !state.spirit.dissolving()) state.spirit.animate("idle_sword_side");
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch != null) patch.modifyLivingMotionByCurrentItem();
        SasukeNetwork.status(player, state);
    }

    private static void captureCombo(ServerPlayer player, State state) {
        Entity firstGrab = null;
        double firstDistance = Double.MAX_VALUE;
        for (Entity target : player.level().getEntities(player, new AABB(state.comboGrip, state.comboGrip).inflate(4.5), entity -> validTarget(player, entity))) {
            if (target.getBoundingBox().distanceToSqr(state.comboGrip) <= 20.25 && player.hasLineOfSight(target)) {
                double distance = target.getBoundingBox().distanceToSqr(state.comboGrip);
                if (distance < firstDistance) { firstDistance = distance; firstGrab = target; }
            }
        }
        state.comboGrabbed = firstGrab != null;
        if (firstGrab != null) {
            firstGrab.stopRiding();
            state.captured.add(firstGrab);
            if (firstGrab instanceof LivingEntity living) ParalysisController.capture(living);
            damage(player, firstGrab, 20F);
            SasukeNetwork.comboCg(player, firstGrab, state.began);
        }
    }

    private static void combinedSummonBurst(ServerPlayer player) {
        Vec3 position = player.position();
        Vec3 center = player.getBoundingBox().getCenter();
        float radius = 3.5F;
        AABB flameArea = new AABB(position.x - radius, position.y - 0.5, position.z - radius,
            position.x + radius, position.y + radius * 1.6, position.z + radius);
        AABB skeletonArea = player.getBoundingBox().inflate(3);
        SasukeNetwork.summon(player);
        SasukeNetwork.burst(player, position, radius);
        amaterasuFracture(player, position, radius);
        SasukeNetwork.flame(player.serverLevel(), position.add(0, 1, 0), radius, -1, 10);
        BlackFlameController.pool(player, position, radius);
        for (Entity target : player.level().getEntities(player, flameArea.minmax(skeletonArea), entity -> validTarget(player, entity))) {
            boolean skeletonHit = skeletonArea.intersects(target.getBoundingBox())
                && target.getBoundingBox().distanceToSqr(center) <= 9 && player.hasLineOfSight(target);
            boolean flameHit = flameArea.intersects(target.getBoundingBox()) && player.level().clip(new ClipContext(
                position.add(0, 0.2, 0), target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS;
            float amount = (skeletonHit ? 16F : 0F) + (flameHit ? 30F : 0F);
            if (flameHit) BlackFlameController.damage(player, target, amount);
            else if (skeletonHit) damage(player, target, amount);
            // Skill damage breaks hard body first; surviving armor still blocks the push.
            boolean armored = target instanceof LivingEntity living && (superArmor(living) || skillBody(living));
            if (skeletonHit && !armored) {
                Vec3 outward = target.position().subtract(position).multiply(1, 0, 1).normalize();
                target.push(outward.x * 0.55, 0.15, outward.z * 0.55);
                target.hurtMarked = true;
            }
        }
    }

    private static void summonBurst(ServerPlayer player) {
        SasukeNetwork.summon(player);
        amaterasuFracture(player, player.position(), 3F);
        SasukeNetwork.flame(player.serverLevel(), player.position().add(0, 1, 0), 3.0F, -1, 10);
        Vec3 center = player.getBoundingBox().getCenter();
        for (Entity target : player.level().getEntities(player, player.getBoundingBox().inflate(3), entity -> validTarget(player, entity))) {
            if (target.getBoundingBox().distanceToSqr(center) > 9 || !player.hasLineOfSight(target)) continue;
            damage(player, target, 16F);
            boolean armored = target instanceof LivingEntity living && (superArmor(living) || skillBody(living));
            if (armored) continue;
            Vec3 outward = target.position().subtract(player.position()).multiply(1, 0, 1).normalize();
            target.push(outward.x * 0.55, 0.15, outward.z * 0.55);
            target.hurtMarked = true;
        }
    }

    private static void clear(ServerPlayer player, State state) {
        interruptFlame(player, state);
        var resistance = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (resistance != null) resistance.removeModifier(SUSANOO_KNOCKBACK);
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SUSANOO_SPEED);
        state.summonAt = -1;
        state.summonFlame = false;
        state.comboInputUntil = 0;
        state.basicSheatheAt = -1;
        state.secondVoiceAt = -1;
        state.reverseFlameInputUntil = 0;
        state.shieldHits = 0;
        state.shieldBreakPending = false;
        state.swept.clear();
        state.specialUntil = 0;
        if (state.spirit != null) state.spirit.dissolve();
        state.spirit = null;
        state.captured.clear();
        state.queuedAttack = false;
        state.phase = Phase.NORMAL;
        syncBodyImmunity(player);
        if (state.spirit != null && state.spirit.isAlive() && !state.spirit.dissolving()) state.spirit.animate("idle_sword_side");
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch != null) patch.modifyLivingMotionByCurrentItem();
        SasukeNetwork.status(player, state);
    }

    @SubscribeEvent
    public static void cloned(PlayerEvent.Clone event) {
        for (String key : List.of("sasukeFirstReady", "sasukeSecondReady")) {
            event.getEntity().getPersistentData().putLong(key, event.getOriginal().getPersistentData().getLong(key));
        }
        event.getEntity().getPersistentData().putInt("sasukeComboStage", event.getOriginal().getPersistentData().getInt("sasukeComboStage"));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            State state = STATES.remove(player);
            syncBodyImmunity(player);
            if (state != null && state.spirit != null) state.spirit.discard();
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && STATES.containsKey(player)) {
            STATES.get(player).flame = null;
            clear(player, STATES.get(player));
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { STATES.clear(); BODY_LEASES.clear(); }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void preventStun(yesman.epicfight.api.forgeevent.EntityStunEvent event) {
        LivingEntity target = event.getStunnedEntityPatch().getOriginal();
        if (captured(target)) return;
        // Check armor before HOLD: Epic Fight uses HOLD for forced hitstun,
        // not only grabs. Cancel before it interrupts our skill or drains stun armor.
        if (superArmor(target)) {
            event.setCanceled(true);
            return;
        }
        if (skillBody(target)) {
            // HOLD alone is not evidence of a grab. Actual grabs use the capture path.
            if (!breakHardBody(target, event.getDamageSource())) event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void preventKnockback(net.minecraftforge.event.entity.living.LivingKnockBackEvent event) {
        if (superArmor(event.getEntity()) || skillBody(event.getEntity())) {
            event.setStrength(0.0F);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void protect(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        State state = STATES.get(player);
        if (state == null) return;
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        boolean fourth = patch != null && patch.getAnimator().getPlayerFor(null).getAnimation().get().getRealAnimation().equals(SasukeAnimations.ATTACKS.get("4a1"))
            && !patch.getAnimator().getPlayerFor(null).isEnd();
        boolean activeDash = state.phase == Phase.DASH && player.level().getGameTime() - state.began >= 6;
        boolean activeFourth = fourth && patch.getAnimator().getPlayerFor(null).getElapsedTime() >= 10F / 60F;
        if (activeDash || activeFourth) { event.setCanceled(true); return; }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void mitigate(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (event.getAmount() <= 0 || target.level().isClientSide()) return;
        if (breakHardBody(target, event.getSource())) {
            var targetPatch = EpicFightCapabilities.getEntityPatch(target, yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch.class);
            if (targetPatch != null) targetPatch.applyStun(yesman.epicfight.world.damagesource.StunType.SHORT, 0.25F);
        }
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        State state = STATES.get(player);
        if (state == null) return;
        boolean skeletonProtected = state.shieldHits > 0 && state.spirit != null && state.spirit.isAlive() && !state.spirit.dissolving();
        if (!skeletonProtected) return;
        CombatAudio.play(player, "susanoo_hurt");
        event.setAmount(event.getAmount() * 0.1F);
        if (--state.shieldHits == 0) {
            // LivingHurtEvent precedes hitstun and knockback. Keep armor through
            // this hit's complete resolution, then retire it on the player tick.
            state.shieldBreakPending = true;
        }
    }
}
