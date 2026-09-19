package dev.sasuke;

import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Cosmetic, interruptible recovery. Deliberately has no empowered-sheathe callback. */
public final class BasicSheatheAnimation extends ActionAnimation {
    public static final int SOURCE_START_FRAME = 71;

    public BasicSheatheAnimation(AnimationAccessor<? extends ActionAnimation> accessor) {
        super(0.10F, accessor, Armatures.BIPED);
        stateSpectrumBlueprint.clear();
    }

    @Override
    protected Vec3 getCoordVector(LivingEntityPatch<?> patch, AssetAccessor<? extends DynamicAnimation> animation) {
        return Vec3.ZERO;
    }
}
