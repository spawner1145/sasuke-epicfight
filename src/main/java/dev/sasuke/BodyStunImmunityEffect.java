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
    }
}
