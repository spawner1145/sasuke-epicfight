package dev.sasuke;

import net.minecraft.server.level.ServerPlayer;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.ActionAnimation;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

public final class SheatheAnimation extends ActionAnimation {
    public SheatheAnimation(AnimationAccessor<? extends ActionAnimation> accessor) {
        super(0.08F, accessor, Armatures.BIPED);
        stateSpectrumBlueprint.clear();
    }

    @Override
    public void end(LivingEntityPatch<?> patch, AssetAccessor<? extends DynamicAnimation> next, boolean completed) {
        super.end(patch, next, completed);
        if (patch.getOriginal() instanceof ServerPlayer player) CombatController.sheatheEnded(player, completed);
    }
}
