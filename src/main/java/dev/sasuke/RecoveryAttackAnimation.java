package dev.sasuke;

import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.gameasset.Armatures;

public final class RecoveryAttackAnimation extends BasicAttackAnimation {
    public static final double MOVEMENT_SCALE = 2.15;
    public static final double FOURTH_FORWARD_DISTANCE = (4.645297 - 0.000946) * MOVEMENT_SCALE;
    public RecoveryAttackAnimation(float antic, float contact, float duration, AnimationAccessor<? extends BasicAttackAnimation> accessor) {
        super(0.04F, antic, contact, duration, null, Armatures.BIPED.get().toolR, accessor, Armatures.BIPED);
        addProperty(ActionAnimationProperty.CANCELABLE_MOVE, false);
    }

    @Override
    public void end(yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch<?> patch,
            yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.DynamicAnimation> next,
            boolean completed) {
        super.end(patch, next, completed);
        if (completed && patch.getOriginal() instanceof net.minecraft.server.level.ServerPlayer player) {
            CombatController.basicAttackEnded(player);
        }
    }

    @Override
    protected net.minecraft.world.phys.Vec3 getCoordVector(yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch<?> patch,
            yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.DynamicAnimation> animation) {
        if (getAccessor().equals(SasukeAnimations.ATTACKS.get("4a2"))) return net.minecraft.world.phys.Vec3.ZERO;
        return super.getCoordVector(patch, animation).multiply(MOVEMENT_SCALE, 1, MOVEMENT_SCALE);
    }
}
