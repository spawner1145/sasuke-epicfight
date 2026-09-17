package dev.sasuke;

import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public final class SpinSlashAnimation extends BasicAttackAnimation {
    public SpinSlashAnimation(float antic, float contact, float recovery, AnimationAccessor<? extends BasicAttackAnimation> accessor) {
        super(0.06F, antic, contact, recovery, null, Armatures.BIPED.get().toolR, accessor, Armatures.BIPED);
    }

    @Override
    protected Vec3 getCoordVector(LivingEntityPatch<?> patch, AssetAccessor<? extends DynamicAnimation> animation) {
        return Vec3.ZERO;
    }
}
