package dev.sasuke;

import net.minecraft.world.effect.MobEffectCategory;
import yesman.epicfight.world.effect.VisibleMobEffect;

/**
 * Separately registered combat-body effect, sharing Epic Fight's effect base and icon.
 * Epic Fight checks its own registry instance, not subclasses; CombatController's
 * EntityStunEvent handler implements our immunity and skill/grab exceptions.
 */
public final class BodyStunImmunityEffect extends VisibleMobEffect {
    public BodyStunImmunityEffect(String name) {
        super(MobEffectCategory.BENEFICIAL, 16758016,
            SasukeMod.id("textures/mob_effect/" + name + ".png"));
        addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE,
            name.equals("hard_body") ? "83618d21-f58e-4265-8051-2ddc99cabfa6" : "5405d6de-13bd-4a64-b51b-8eb2b0f5edb0",
            1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION);
    }
}
