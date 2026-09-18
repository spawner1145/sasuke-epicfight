package dev.sasuke;

import com.merlin204.avalon.entity.vfx.VFXEntityPatch;
import net.minecraftforge.event.entity.living.LivingEvent;
import yesman.epicfight.api.animation.types.DynamicAnimation;
import yesman.epicfight.api.animation.Pose;

public class SusanooPatch extends VFXEntityPatch<SusanooEntity> {
    private int revision = -1;

    @Override
    public void tick(LivingEvent.LivingTickEvent event) {
        if (original.revision() == 0) return;
        if (revision != original.revision()) {
            revision = original.revision();
            boolean retract = original.action().equals("combo_retract");
            var animation = SasukeAnimations.SPIRIT.get(retract ? "amaterasu_combo" : original.action());
            if (animation != null) {
                if (retract) animator.playAnimation(animation, -3.4F);
                else animator.playAnimationInstantly(animation);
            }
        }
        animator.tick();
        if (isLogicalClient()) original.setShouldRender(true);
        if (isLogicalClient()) clientTick(event);
        else serverTick(event);
    }

    @Override
    public void poseTick(DynamicAnimation animation, Pose pose, float elapsedTime, float partialTick) {
        setYRot(original.getStartYRot());
        if (original.action().equals("draw_to_side") || original.action().equals("idle_sword_side")) {
            for (String joint : new String[]{"clavicle.L", "clavicle.R"}) {
                var transform = pose.orElseEmpty(joint).copy();
                transform.scale().set(0, 0, 0);
                pose.putJointData(joint, transform);
            }
        }
    }
}
