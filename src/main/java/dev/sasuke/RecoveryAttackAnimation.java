package dev.sasuke;

import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.property.AnimationProperty.ActionAnimationProperty;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.gameasset.Armatures;

public final class RecoveryAttackAnimation extends BasicAttackAnimation {
    public RecoveryAttackAnimation(float antic, float contact, float duration, AnimationAccessor<? extends BasicAttackAnimation> accessor) {
        super(0.04F, antic, contact, duration, null, Armatures.BIPED.get().toolR, accessor, Armatures.BIPED);
        addProperty(ActionAnimationProperty.CANCELABLE_MOVE, false);
    }

    @Override
    protected net.minecraft.world.phys.Vec3 getCoordVector(yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch<?> patch,
            yesman.epicfight.api.asset.AssetAccessor<? extends yesman.epicfight.api.animation.types.DynamicAnimation> animation) {
        if (getAccessor().equals(SasukeAnimations.ATTACKS.get("4a2"))) return net.minecraft.world.phys.Vec3.ZERO;
        return super.getCoordVector(patch, animation).multiply(2.15, 1, 2.15);
    }
}
