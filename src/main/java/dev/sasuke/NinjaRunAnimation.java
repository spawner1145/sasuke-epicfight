package dev.sasuke;

import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.animation.types.MovementAnimation;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public final class NinjaRunAnimation extends MovementAnimation {
    public NinjaRunAnimation(AnimationAccessor<? extends MovementAnimation> accessor) {
        super(0.12F, true, accessor, Armatures.BIPED);
    }

    @Override
    public Pose getPoseByTime(LivingEntityPatch<?> patch, float time, float partialTicks) {
        var entity = patch.getOriginal();
        double motionX = entity.getX() - entity.xo;
        double motionZ = entity.getZ() - entity.zo;
        float angle = 0;
        if (motionX * motionX + motionZ * motionZ > 0.00001) {
            angle = net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-motionX, motionZ)) - entity.yBodyRot);
        }
        Pose pose = super.getPoseByTime(patch, time, partialTicks);
        pose.orElseEmpty("Root").rotation().premul(new org.joml.Quaternionf().rotationY((float)Math.toRadians(-angle)));
        return pose;
    }
}
