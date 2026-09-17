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
    private static final UUID LISTENER = UUID.fromString("c1fe110d-5216-4e5d-a873-293b2cf47a91");
    private static final UUID SUSANOO_SPEED = UUID.fromString("6359a1bd-ef3e-46e3-93ec-b02492eae3e7");
    private static final UUID SUSANOO_KNOCKBACK = UUID.fromString("ade2c302-2bf7-4f47-a1b3-1945f39de20c");
    public static final int COOLDOWN = 160;
    public static final int READY_WINDOW = 60;
    public static final int SUSANOO_READY_WINDOW = 100;
    public static final int SHEATHE_ATTACK_WINDOW = 22;

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
        int waveStep;
        boolean waveDone;
        int waveBurstAt;
        boolean queuedAttack;
        long specialUntil;
        boolean specialAttack;
        float lockedYaw;
        float lockedPitch;
        String basic = "";
        long basicBegan;
        long comboExpires;
        long comboInputUntil;
        Vec3 comboGrip = Vec3.ZERO;
        boolean basicTriggered;
        int shieldHits;
        final List<LivingEntity> swept = new ArrayList<>();
        SusanooEntity spirit;
        ServerPlayerPatch installedPatch;
        final List<Entity> captured = new ArrayList<>();
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

    private static boolean allowsBasicAttack(Phase phase) {
        return phase == Phase.NORMAL || phase == Phase.SECOND_READY;
    }

    public static boolean skillBody(LivingEntity target) {
        if (!(target instanceof ServerPlayer player) || !equipped(player) || captured(target)) return false;
        State state = STATES.get(player);
        return state != null && switch (state.phase) {
            case DRAW, DASH, SHEATHE, AMATERASU_ONE, AMATERASU_TWO, COMBO, COMBO_RECOVERY -> true;
            default -> false;
        };
    }

    private static final class SkillDamageSource extends net.minecraft.world.damagesource.DamageSource {
        SkillDamageSource(ServerPlayer player) {
            super(player.damageSources().playerAttack(player).typeHolder(), player);
        }
    }

    private static boolean skillDamage(net.minecraft.world.damagesource.DamageSource source) {
        if (source == null) return false;
        if (source instanceof SkillDamageSource) return true;
        return source instanceof yesman.epicfight.world.damagesource.EpicFightDamageSource epic
            && !epic.isBasicAttack() && epic.getEntity() instanceof net.minecraft.world.entity.player.Player
            && !epic.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
    }

    public static boolean superArmor(LivingEntity target) {
        if (!(target instanceof ServerPlayer player) || captured(target)) return false;
        State state = STATES.get(player);
        return state != null && equipped(player) && (state.phase == Phase.COMBO || state.phase == Phase.COMBO_RECOVERY
            || state.spirit != null && state.spirit.isAlive() && !state.spirit.dissolving());
    }

    public static void interruptForCapture(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            State state = STATES.get(player);
            if (state != null && state.phase != Phase.NORMAL) clear(player, state);
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
            if (!allowsBasicAttack(state.phase) || player.isUsingItem() || player.isPassenger() || !player.onGround() || !patch.getEntityState().canBasicAttack()) return;
            var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try { patch.getSkill(yesman.epicfight.skill.SkillSlots.BASIC_ATTACK).requestCasting(patch, buffer); }
            finally { buffer.release(); }
            return;
        }
        if (input.key() == 4) {
            if (state.phase == Phase.SHEATHE) {
                clear(player, state);
                patch.playAnimationSynchronized(SasukeAnimations.player("idle"), 0F);
            }
            return;
        }
        if (state.phase == Phase.SHEATHE && input.key() != 3) {
            clear(player, state);
            patch.playAnimationSynchronized(SasukeAnimations.player("idle"), 0F);
        }
        if (input.key() == 1 && state.phase == Phase.AMATERASU_TWO && now >= state.firstReady) {
            state.firstReady = now + COOLDOWN;
            erupt(player, player.position(), 3.5F, 12F);
            BlackFlameController.aura(player);
            summon(player, state);
            persist(player, state);
            return;
        }
        if (input.key() == 3) {
            if (state.phase == Phase.DRAW) state.queuedAttack = true;
            else if (state.phase == Phase.READY) start(player, state, Phase.DASH, "dash_spin_slash", 29);
            return;
        }
        if (input.key() == 2 && state.phase == Phase.DRAW && now < state.comboInputUntil && now >= state.secondReady) {
            state.comboInputUntil = 0;
            state.firstReady = state.secondReady = now + COOLDOWN;
            state.captured.clear();
            Vec3 facing = horizontal(player);
            Vec3 center = player.position().add(facing.scale(3)).add(0, 1.5, 0);
            state.comboGrip = center;
            for (Entity target : player.level().getEntities(player, new AABB(center, center).inflate(4.5), entity -> validTarget(player, entity))) {
                if (target.getBoundingBox().distanceToSqr(center) <= 20.25 && player.hasLineOfSight(target)) {
                    target.stopRiding();
                    state.captured.add(target);
                    if (target instanceof LivingEntity living) ParalysisController.capture(living);
                    damage(player, target, 6F);
                }
            }
            start(player, state, Phase.COMBO, "amaterasu_combo", 83);
            persist(player, state);
            return;
        }
        if (input.key() == 2 && state.phase == Phase.SECOND_READY) {
            Vec3 forward = state.direction;
            Vec3 left = new Vec3(forward.z, 0, -forward.x);
            Vec3 direction = forward.scale(input.forward()).add(left.scale(input.left()));
            Vec3 destination = state.impact;
            for (int step = 0; direction.lengthSqr() >= 0.01 && step < 6; step++) {
                Vec3 next = groundStep(player, destination, direction.normalize());
                if (next == null) break;
                destination = next;
            }
            state.impact = destination;
            start(player, state, Phase.AMATERASU_TWO, "amaterasu_2", 24);
            return;
        }
        if (state.phase != Phase.NORMAL || patch.getEntityState().inaction() || player.isPassenger()) return;
        if (input.key() == 1 && now >= state.firstReady) {
            state.firstReady = now + COOLDOWN;
            summon(player, state);
            state.comboInputUntil = now + 6;
        } else if (input.key() == 2 && now >= state.secondReady && player.onGround()) {
            state.secondReady = now + COOLDOWN;
            state.direction = horizontal(player);
            state.origin = state.impact = player.position();
            state.waveStep = 0;
            state.swept.clear();
            state.waveDone = false;
            state.waveBurstAt = -1;
            start(player, state, Phase.AMATERASU_ONE, "amaterasu_1", 21);
        }
        persist(player, state);
    }

    private static void persist(ServerPlayer player, State state) {
        player.getPersistentData().putLong("sasukeFirstReady", state.firstReady);
        player.getPersistentData().putLong("sasukeSecondReady", state.secondReady);
        SasukeNetwork.status(player, state);
    }

    private static void start(ServerPlayer player, State state, Phase phase, String animation, int ticks) {
        if (phase == Phase.COMBO) {
            state.lockedYaw = player.getYRot();
            state.lockedPitch = player.getXRot();
        }
        state.phase = phase;
        state.began = player.level().getGameTime();
        state.until = state.began + ticks;
        state.anchor = player.position();
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        patch.modifyLivingMotionByCurrentItem();
        patch.playAnimationSynchronized(SasukeAnimations.player(animation), 0F);
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
        State state = state(player);
        var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
        if (patch == null) return;
        if (superArmor(player)) patch.setStamina(patch.getMaxStamina());
        var resistance = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (resistance != null) {
            if (superArmor(player)) {
                if (resistance.getModifier(SUSANOO_KNOCKBACK) == null) resistance.addTransientModifier(
                    new net.minecraft.world.entity.ai.attributes.AttributeModifier(SUSANOO_KNOCKBACK, "Susanoo knockback resistance", 1.0,
                        net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));
            } else resistance.removeModifier(SUSANOO_KNOCKBACK);
        }
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
                state.basic = "";
                if (!state.specialAttack) {
                    state.comboExpires = 0;
                    for (String name : new String[]{"1a", "2a", "3a", "4a1", "4a2", "4a3"}) {
                        if (action.getAnimation().equals(SasukeAnimations.ATTACKS.get(name))) {
                            state.comboExpires = player.level().getGameTime() + (SasukeAnimations.duration(name) + 2) / 3 + 10;
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
                if (!equipped(player)) return;
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
                    if (counter == 0 || player.level().getGameTime() >= state.comboExpires) {
                        int stage = Math.floorMod(player.getPersistentData().getInt("sasukeComboStage"), 3);
                        data.setData(yesman.epicfight.skill.SkillDataKeys.COMBO_COUNTER.get(), stage * 4);
                    }
                }
            });
        }
        long now = player.level().getGameTime();
        if (patch.getEntityState().hurt() || ParalysisController.active(player)) {
            state.comboExpires = 0;
            state.specialUntil = 0;
            if (equipped(player)) {
                int stage = Math.floorMod(player.getPersistentData().getInt("sasukeComboStage"), 3);
                patch.getSkill(yesman.epicfight.skill.SkillSlots.BASIC_ATTACK).getDataManager()
                    .setData(yesman.epicfight.skill.SkillDataKeys.COMBO_COUNTER.get(), stage * 4);
            }
        }
        if (!equipped(player) || !patch.isEpicFightMode() || (patch.getEntityState().hurt() && state.spirit == null)) {
            state.basic = "";
            state.specialUntil = 0;
            if (state.phase != Phase.NORMAL) clear(player, state);
            return;
        }
        if (allowsBasicAttack(state.phase)) tickBasic(player, state);
        if (state.phase == Phase.NORMAL) {
            if (player.tickCount % 10 == 0) SasukeNetwork.status(player, state);
            return;
        }
        int elapsed = (int)(now - state.began);
        if (state.phase == Phase.SHEATHE && (player.isUsingItem() || player.position().subtract(state.anchor).horizontalDistanceSqr() > 0.01 || !player.onGround())) {
            clear(player, state);
            patch.playAnimationSynchronized(SasukeAnimations.player("idle"), 0F);
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
            player.move(MoverType.SELF, horizontal(player).scale(0.45));
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            if (elapsed >= 6) {
                var joint = patch.getArmature().searchJointByName("Tool_R");
                var local = patch.getArmature().getBoundTransformFor(patch.getAnimator().getPose(1F), joint).toTranslationVector();
                Vec3 blade = player.position().add(new Vec3(-local.x, local.y, -local.z).yRot((float)Math.toRadians(-player.yBodyRot)));
                SasukeNetwork.flame(player.serverLevel(), blade, 0.4F, -1, 4);
                SasukeNetwork.flame(player.serverLevel(), player.position().add(0, 1, 0), 0.7F, player.getId(), 5);
                if (BlackFlameController.hasAura(player)) SasukeNetwork.flame(player.serverLevel(), blade, 0.45F, -1, 7);
            }
        }
        if (state.phase == Phase.AMATERASU_ONE && elapsed >= 3 && !state.waveDone) {
            Vec3 next = groundStep(player, state.impact, state.direction);
            if (next != null) {
                state.impact = next;
                for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(next, next).inflate(1.5, 2, 1.5))) {
                    if (validTarget(player, target) && player.hasLineOfSight(target) && !state.swept.contains(target)) state.swept.add(target);
                }
                state.swept.removeIf(target -> !validTarget(player, target) || target.level() != player.level());
                for (LivingEntity target : state.swept) {
                    Vec3 pull = next.subtract(target.position());
                    target.stopRiding();
                    target.move(MoverType.SELF, pull.scale(Math.min(1, 1.5 / Math.max(0.01, pull.length()))));
                    target.setDeltaMovement(Vec3.ZERO);
                    target.hurtMarked = true;
                    if (target instanceof ServerPlayer other) other.connection.teleport(target.getX(), target.getY(), target.getZ(), target.getYRot(), target.getXRot());
                }
                erupt(player, next, 0.7F, 2F);
            }
            if (next == null || ++state.waveStep >= 14) {
                state.waveDone = true;
                state.waveBurstAt = elapsed + 3;
                SasukeNetwork.burst(player, state.impact, -3F);
            }
        }
        if (state.phase == Phase.AMATERASU_ONE && elapsed == state.waveBurstAt) erupt(player, state.impact, 3F, 9F);
        if (state.phase == Phase.AMATERASU_TWO && elapsed == 1) SasukeNetwork.burst(player, state.impact, -3.5F);
        if (state.phase == Phase.AMATERASU_TWO && elapsed == 10) erupt(player, state.impact, 3.5F, 12F);
        if (state.phase == Phase.COMBO) {
            if (state.spirit == null || !state.spirit.isAlive()) { clear(player, state); return; }
            Vec3 grip = state.comboGrip;
            state.captured.removeIf(entity -> !validTarget(player, entity) || entity.level() != player.level() || entity.position().distanceToSqr(player.position()) > 144);
            if (elapsed == 22) {
                if (state.captured.isEmpty()) {
                    if (state.spirit != null) state.spirit.dissolve();
                    state.spirit = null;
                    start(player, state, Phase.COMBO_RECOVERY, "draw_to_guard", 10);
                    return;
                }
                SasukeNetwork.burst(player, grip, -1.8F, true);
            }
            if (elapsed >= 30 && elapsed <= 70) {
                for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, new AABB(grip, grip).inflate(2.6),
                    entity -> validTarget(player, entity) && !state.captured.contains(entity))) {
                    if (target.getBoundingBox().distanceToSqr(grip) > 2.6 * 2.6) continue;
                    var obstruction = player.level().clip(new ClipContext(grip, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    if (obstruction.getType() != HitResult.Type.MISS) continue;
                    state.captured.add(target);
                    ParalysisController.capture(target);
                    damage(player, target, 6F);
                }
            }
            for (Entity target : state.captured) {
                Vec3 held = grip.add(0, -target.getBbHeight() * 0.5, 0);
                target.setDeltaMovement(Vec3.ZERO);
                target.hurtMarked = true;
                target.fallDistance = 0;
                if (target instanceof ServerPlayer other) other.connection.teleport(held.x, held.y, held.z, other.getYRot(), other.getXRot());
                else target.teleportTo(held.x, held.y, held.z);
            }
            if (elapsed >= 30 && elapsed <= 70 && elapsed % 10 == 0) {
                float radius = elapsed == 70 ? 2.6F : 1.8F;
                SasukeNetwork.burst(player, grip, radius, true);
                BlackFlameController.cloud(player, grip, radius);
                for (Entity target : player.level().getEntities(player, new AABB(grip, grip).inflate(radius), entity -> validTarget(player, entity))) {
                    if (target.getBoundingBox().distanceToSqr(grip) > radius * radius) continue;
                    var obstruction = player.level().clip(new ClipContext(grip, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                    if (obstruction.getType() != HitResult.Type.MISS) continue;
                    damage(player, target, elapsed == 70 ? 16F : 8F);
                    if (target instanceof LivingEntity living) BlackFlameController.burn(player, living);
                }
                if (elapsed == 70) state.captured.clear();
            }
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
                    patch.playAnimationSynchronized(SasukeAnimations.player("idle"), 0F);
                }
                case DASH -> start(player, state, Phase.SHEATHE, "sheathe_flourish", 50);
                case SHEATHE -> clear(player, state);
                case AMATERASU_ONE -> {
                    state.phase = Phase.SECOND_READY;
                    state.until = now + READY_WINDOW;
                    SasukeNetwork.status(player, state);
                }
                default -> clear(player, state);
            }
        }
        if (player.tickCount % 10 == 0) SasukeNetwork.status(player, state);
    }

    private static void tickBasic(ServerPlayer player, State state) {
        int elapsed = (int)(player.level().getGameTime() - state.basicBegan);
        if (elapsed > 18 || state.basic.isEmpty()) return;
        int end = switch (state.basic) { case "3a" -> 6; case "4a1", "4a2" -> 11; default -> 16; };
        if (elapsed > end) return;
        if (!state.basic.equals("4a2") && elapsed > 0 && elapsed % 6 == 0) SasukeNetwork.flame(player.serverLevel(), player.position(), 1F, player.getId(), 8);
        if (state.basic.equals("4a1") && elapsed % 2 == 0) SasukeNetwork.flame(player.serverLevel(), player.position().add(0, 1, 0), 0.9F, player.getId(), 5);
        int trigger = state.basic.equals("4a2") ? 7 : 14;
        if (!state.basicTriggered && elapsed >= trigger) {
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
        return target != player && target.isAlive() && !(target instanceof SusanooEntity) && !target.isSpectator() && !player.isAlliedTo(target)
            && (!(target instanceof ServerPlayer other) || (!other.isCreative() && player.canHarmPlayer(other)));
    }

    static boolean damage(ServerPlayer player, Entity target, float amount) {
        if (!validTarget(player, target)) return false;
        return target.hurt(new SkillDamageSource(player), amount);
    }

    private static void erupt(ServerPlayer player, Vec3 position, float radius, float amount) {
        SasukeNetwork.burst(player, position, radius);
        BlackFlameController.pool(player, position, radius);
        AABB area = new AABB(position.x - radius, position.y - 0.5, position.z - radius, position.x + radius, position.y + radius * 1.6, position.z + radius);
        for (Entity target : player.level().getEntities(player, area, entity -> validTarget(player, entity))) {
            Vec3 center = target.getBoundingBox().getCenter();
            var obstruction = player.level().clip(new ClipContext(position.add(0, 0.2, 0), center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (obstruction.getType() == HitResult.Type.MISS) {
                damage(player, target, amount);
                if (target instanceof LivingEntity living) BlackFlameController.burn(player, living);
            }
        }
    }

    private static void summon(ServerPlayer player, State state) {
        state.shieldHits = 3;
        state.spirit = new SusanooEntity(SasukeMod.SUSANOO.get(), player.level());
        state.spirit.tame(player);
        state.spirit.setPos(player.position());
        start(player, state, Phase.DRAW, "draw_to_side", 12);
        player.level().addFreshEntity(state.spirit);
        summonBurst(player);
    }

    private static void summonBurst(ServerPlayer player) {
        SasukeNetwork.summon(player);
        Vec3 center = player.getBoundingBox().getCenter();
        for (Entity target : player.level().getEntities(player, player.getBoundingBox().inflate(3), entity -> validTarget(player, entity))) {
            if (target.getBoundingBox().distanceToSqr(center) > 9 || !player.hasLineOfSight(target)) continue;
            damage(player, target, 6F);
            Vec3 outward = target.position().subtract(player.position()).multiply(1, 0, 1).normalize();
            target.push(outward.x * 0.55, 0.15, outward.z * 0.55);
            target.hurtMarked = true;
        }
    }

    private static void clear(ServerPlayer player, State state) {
        var resistance = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (resistance != null) resistance.removeModifier(SUSANOO_KNOCKBACK);
        var speed = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SUSANOO_SPEED);
        state.comboInputUntil = 0;
        state.shieldHits = 0;
        state.swept.clear();
        state.specialUntil = 0;
        if (state.spirit != null) state.spirit.dissolve();
        state.spirit = null;
        state.captured.clear();
        state.queuedAttack = false;
        state.phase = Phase.NORMAL;
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
            if (state != null && state.spirit != null) state.spirit.discard();
        }
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && STATES.containsKey(player)) clear(player, STATES.get(player));
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) { STATES.clear(); }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void preventStun(yesman.epicfight.api.forgeevent.EntityStunEvent event) {
        LivingEntity target = event.getStunnedEntityPatch().getOriginal();
        if (event.getStunType() == yesman.epicfight.world.damagesource.StunType.HOLD) {
            interruptForCapture(target);
        } else if (superArmor(target) || skillBody(target) && !skillDamage(event.getDamageSource())) {
            event.setCanceled(true);
        } else if (skillBody(target) && skillDamage(event.getDamageSource())) interruptForCapture(target);
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
        if (state.phase == Phase.DASH || fourth) { event.setCanceled(true); return; }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void mitigate(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) return;
        State state = STATES.get(player);
        if (state == null) return;
        boolean skeletonProtected = state.shieldHits > 0 && state.spirit != null && state.spirit.isAlive() && !state.spirit.dissolving();
        if (!superArmor(player) && skillBody(player) && event.getSource() instanceof SkillDamageSource) {
            clear(player, state);
            var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
            if (patch != null) patch.applyStun(yesman.epicfight.world.damagesource.StunType.SHORT, 0.25F);
        }
        if (!skeletonProtected) return;
        event.setAmount(event.getAmount() * 0.1F);
        if (--state.shieldHits == 0) {
            clear(player, state);
            var patch = EpicFightCapabilities.getEntityPatch(player, ServerPlayerPatch.class);
            if (patch != null) patch.playAnimationSynchronized(SasukeAnimations.player("idle"), 0F);
        }
    }
}
