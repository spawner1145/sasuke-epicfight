package dev.sasuke;

import java.util.LinkedHashMap;
import java.util.Map;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackAnimationProperty;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.gameasset.Armatures;

public final class SasukeAnimations {
    public static final Armatures.ArmatureAccessor<Armature> SUSANOO = Armatures.ArmatureAccessor.create(SasukeMod.ID, "entity/susanoo", Armature::new);
    public static final Map<String, AnimationAccessor<? extends StaticAnimation>> PLAYER = new LinkedHashMap<>();
    public static final Map<String, AnimationAccessor<StaticAnimation>> SPIRIT = new LinkedHashMap<>();
    public static final Map<String, AnimationAccessor<BasicAttackAnimation>> ATTACKS = new LinkedHashMap<>();
    public static final String[] NAMES = {"1a", "2a", "3a", "4a1", "4a2", "4a3", "idle", "idle_guard", "idle_sword_side", "run_sheathed", "run_sword_side", "draw_to_side", "draw_to_guard", "dash_spin_slash", "sheathe_flourish", "amaterasu_1", "amaterasu_2", "amaterasu_combo", "perfect_parry"};

    public static void register(AnimationManager.AnimationRegistryEvent event) {
        event.newBuilder(SasukeMod.ID, builder -> {
            PLAYER.clear();
            SPIRIT.clear();
            ATTACKS.clear();
            for (String source : new String[]{"walk", "jump", "fall", "fly", "creative_idle", "creative_fly_forward", "kneel", "sneak"}) {
                for (boolean drawn : new boolean[]{false, true}) {
                    String name = source + (drawn ? "_drawn" : "_sheathed");
                    PLAYER.put(name, builder.nextAccessor("player/" + name, accessor -> new WeaponAirAnimation(source, drawn, accessor)));
                }
            }
            for (String name : NAMES) {
                boolean loop = name.startsWith("idle") || name.startsWith("run");
                if (name.matches("[123]a|4a[123]") || name.equals("dash_spin_slash")) {
                    float[] phase = phase(name);
                    AnimationAccessor<BasicAttackAnimation> attack = builder.nextAccessor("player/" + name, accessor ->
                        (name.equals("dash_spin_slash")
                            ? new SpinSlashAnimation(phase[0] / 60F, phase[1] / 60F, phase[2] / 60F, accessor)
                            : new RecoveryAttackAnimation(phase[0] / 60F, phase[1] / 60F, duration(name) / 60F, accessor))
                            .<BasicAttackAnimation, Float>addProperty(AttackAnimationProperty.ATTACK_SPEED_FACTOR, 0F)
                            .addProperty(yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty.DAMAGE_MODIFIER,
                                yesman.epicfight.api.utils.math.ValueModifier.multiplier(
                                    (name.equals("dash_spin_slash") ? 26F : name.startsWith("4a") ? 12F : CombatController.BASE_ATTACK_DAMAGE)
                                        / CombatController.BASE_ATTACK_DAMAGE)));
                    ATTACKS.put(name, attack);
                } else if (name.equals("sheathe_flourish")) {
                    PLAYER.put(name, builder.<ActionAnimation>nextAccessor("player/" + name, accessor -> new SheatheAnimation(accessor)));
                } else if (name.startsWith("run")) {
                    PLAYER.put(name, builder.<yesman.epicfight.api.animation.types.MovementAnimation>nextAccessor("player/" + name, accessor -> new NinjaRunAnimation(accessor)));
                } else if (loop) {
                    PLAYER.put(name, builder.nextAccessor("player/" + name, accessor -> new StaticAnimation(0.12F, true, accessor, Armatures.BIPED)));
                } else {
                    PLAYER.put(name, builder.<ActionAnimation>nextAccessor("player/" + name, accessor -> new ActionAnimation(0.08F, accessor, Armatures.BIPED)));
                }
                SPIRIT.put(name, builder.nextAccessor("susanoo/" + name, accessor -> new StaticAnimation(0.06F, loop, accessor, SUSANOO)));
            }
        });
    }

    public static AnimationAccessor<? extends StaticAnimation> player(String name) {
        return ATTACKS.containsKey(name) ? ATTACKS.get(name) : PLAYER.get(name);
    }

    private static float[] phase(String name) {
        return switch (name) {
            case "1a" -> new float[]{5, 12, 16};
            case "2a" -> new float[]{9, 21, 27};
            case "3a" -> new float[]{4, 12, 16};
            case "4a1" -> new float[]{10, 25, 32};
            case "4a2" -> new float[]{7, 20, 24};
            case "4a3" -> new float[]{15, 40, 49};
            default -> new float[]{18, 58, 74};
        };
    }

    static int duration(String name) {
        return switch (name) {
            case "1a" -> 22;
            case "2a" -> 31;
            case "3a" -> 22;
            case "4a1" -> 36;
            case "4a2" -> 31;
            case "4a3" -> 51;
            default -> throw new IllegalArgumentException(name);
        };
    }
}
