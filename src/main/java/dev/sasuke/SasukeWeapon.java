package dev.sasuke;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.LivingMotion;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.entity.eventlistener.ComboCounterHandleEvent;

public final class SasukeWeapon {
    private static class Builder extends WeaponCapability.Builder {
        Builder() { constructor(DrawnCapability::new); }
    }

    private static class DrawnCapability extends WeaponCapability {
        DrawnCapability(CapabilityItem.Builder builder) { super(builder); }

        @Override
        public Map<LivingMotion, AnimationAccessor<? extends StaticAnimation>> getLivingMotionModifier(LivingEntityPatch<?> patch, InteractionHand hand) {
            var motions = new HashMap<>(super.getLivingMotionModifier(patch, hand));
            if (hand == InteractionHand.MAIN_HAND) {
                String suffix = patch.getOriginal() instanceof ServerPlayer player && CombatController.isDrawn(player) ? "_drawn" : "_sheathed";
                motions.put(LivingMotions.JUMP, SasukeAnimations.player("jump" + suffix));
                motions.put(LivingMotions.WALK, SasukeAnimations.player("walk" + suffix));
                motions.put(LivingMotions.FALL, SasukeAnimations.player("fall" + suffix));
                motions.put(LivingMotions.FLY, SasukeAnimations.player("fly" + suffix));
                motions.put(LivingMotions.CREATIVE_IDLE, SasukeAnimations.player("creative_idle" + suffix));
                motions.put(LivingMotions.CREATIVE_FLY, SasukeAnimations.player("creative_fly_forward" + suffix));
                motions.put(LivingMotions.KNEEL, SasukeAnimations.player("kneel" + suffix));
                motions.put(LivingMotions.SNEAK, SasukeAnimations.player("sneak" + suffix));
            }
            if (hand == InteractionHand.MAIN_HAND && patch.getOriginal() instanceof ServerPlayer player && CombatController.isDrawn(player)) {
                motions.put(LivingMotions.IDLE, SasukeAnimations.player("idle_sword_side"));
                motions.put(LivingMotions.WALK, SasukeAnimations.player("walk_drawn"));
                motions.put(LivingMotions.RUN, SasukeAnimations.player("run_sword_side"));
            }
            return motions;
        }
    }

    public static void register(WeaponCapabilityPresetRegistryEvent event) {
        event.getTypeEntry().put(SasukeMod.id("kusanagi"), item -> new Builder()
            .category(CapabilityItem.WeaponCategories.UCHIGATANA)
            .styleProvider(patch -> CapabilityItem.Styles.TWO_HAND)
            .collider(new yesman.epicfight.api.collider.MultiOBBCollider(9, 0.8D, 0.7D, 1.4D, 0D, 0D, -1.3D))
            .canBePlacedOffhand(false)
            .comboCounterHandler((cap, cause, patch, next, counter) -> {
                var data = patch.getOriginal().getPersistentData();
                int stage = Math.floorMod(data.getInt("sasukeComboStage"), 3);
                if (patch.getOriginal() instanceof ServerPlayer player && CombatController.specialAttack(player)) return counter;
                if (cause == ComboCounterHandleEvent.Causal.TIME_EXPIRED) {
                    if (patch.getOriginal() instanceof ServerPlayer player && !CombatController.comboExpired(player)) {
                        return patch.getSkill(yesman.epicfight.skill.SkillSlots.BASIC_ATTACK).getDataManager()
                            .getDataValue(yesman.epicfight.skill.SkillDataKeys.COMBO_COUNTER.get());
                    }
                    return stage * 4;
                }
                for (int index = 1; index <= 3; index++) {
                    if (SasukeAnimations.ATTACKS.get("4a" + index).equals(next)) {
                        data.putInt("sasukeComboStage", index % 3);
                        return counter;
                    }
                }
                return cap.getAutoAttackMotion(patch).contains(next) ? counter : stage * 4;
            })
            .newStyleCombo(CapabilityItem.Styles.TWO_HAND,
                SasukeAnimations.ATTACKS.get("1a"), SasukeAnimations.ATTACKS.get("2a"), SasukeAnimations.ATTACKS.get("3a"), SasukeAnimations.ATTACKS.get("4a1"),
                SasukeAnimations.ATTACKS.get("1a"), SasukeAnimations.ATTACKS.get("2a"), SasukeAnimations.ATTACKS.get("3a"), SasukeAnimations.ATTACKS.get("4a2"),
                SasukeAnimations.ATTACKS.get("1a"), SasukeAnimations.ATTACKS.get("2a"), SasukeAnimations.ATTACKS.get("3a"), SasukeAnimations.ATTACKS.get("4a3"),
                SasukeAnimations.ATTACKS.get("1a"), Animations.UCHIGATANA_AIR_SLASH)
            .livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.IDLE, SasukeAnimations.player("idle"))
            .livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.WALK, SasukeAnimations.player("walk_sheathed"))
            .livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.RUN, SasukeAnimations.player("run_sheathed"))
            .livingMotionModifier(CapabilityItem.Styles.TWO_HAND, LivingMotions.BLOCK, Animations.UCHIGATANA_GUARD));
    }
}
