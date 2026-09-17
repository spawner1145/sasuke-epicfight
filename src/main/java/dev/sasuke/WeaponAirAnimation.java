package dev.sasuke;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import yesman.epicfight.api.animation.AnimationClip;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.Keyframe;
import yesman.epicfight.api.animation.TransformSheet;
import yesman.epicfight.api.animation.types.DirectStaticAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Armatures;

public final class WeaponAirAnimation extends StaticAnimation {
    private final String source;
    private final boolean drawn;

    public WeaponAirAnimation(String source, boolean drawn, AnimationAccessor<? extends StaticAnimation> accessor) {
        super(0.12F, !source.equals("jump"), accessor, Armatures.BIPED);
        this.source = source;
        this.drawn = drawn;
    }

    @Override
    public void loadAnimation() {
        var base = new DirectStaticAnimation(0.12F, true, new ResourceLocation("epicfight", "biped/living/" + source), Armatures.BIPED).getAnimationClip();
        var held = SasukeAnimations.player(drawn ? "idle_sword_side" : "idle").get().getRawPose(0);
        animationClip = new AnimationClip();
        base.getJointTransforms().forEach(animationClip::addJointTransform);
        for (String joint : List.of("Head", "Neck", "Torso", "Chest", "Shoulder_R", "Arm_R", "Hand_R", "Tool_R", "Elbow_R", "Shoulder_L", "Arm_L", "Hand_L", "Tool_L", "Elbow_L")) {
            animationClip.addJointTransform(joint, new TransformSheet(new Keyframe[]{new Keyframe(0, held.orElseEmpty(joint)), new Keyframe(base.getClipTime(), held.orElseEmpty(joint))}));
        }
        animationClip.setClipTime(base.getClipTime());
        animationClip.bakeKeyframes();
    }
}
