package dev.sasuke;

import java.lang.reflect.Method;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;

final class CaptureStamina {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final Method ADVANCED = optional("com.nameless.indestructible.world.capability.Utils.IAdvancedCapability", "setStamina", float.class);
    private static final Method EVOLUTION = optional("net.shelmarow.combat_evolution.ai.iml.ILivingEntityData", "combat_evolution$setStamina", float.class);
    private static boolean advancedFailed;
    private static boolean evolutionFailed;

    private static Method optional(String owner, String name, Class<?>... parameters) {
        try {
            return Class.forName(owner, false, CaptureStamina.class.getClassLoader()).getMethod(name, parameters);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (ReflectiveOperationException | LinkageError failure) {
            LOGGER.warn("Cannot initialize capture stamina compatibility for {}", owner, failure);
            return null;
        }
    }

    static void drain(LivingEntityPatch<?> patch) {
        // Capture still interrupts armor, but must not drain its stamina.
        // This also runs while captured entities' normal effect ticks are suspended.
        if (CombatController.superArmor(patch.getOriginal())) {
            if (patch instanceof PlayerPatch<?> player) player.setStamina(player.getMaxStamina());
            return;
        }
        patch.setStunShield(0F);
        if (patch instanceof PlayerPatch<?> player) player.setStamina(0F);
        if (ADVANCED != null && !advancedFailed && ADVANCED.getDeclaringClass().isInstance(patch)) {
            try {
                ADVANCED.invoke(patch, 0F);
            } catch (ReflectiveOperationException | LinkageError failure) {
                advancedFailed = true;
                LOGGER.warn("Cannot drain Indestructible stamina during capture", failure);
            }
        }
        if (EVOLUTION != null && !evolutionFailed && EVOLUTION.getDeclaringClass().isInstance(patch)) {
            try {
                EVOLUTION.invoke(patch, 0F);
            } catch (ReflectiveOperationException | LinkageError failure) {
                evolutionFailed = true;
                LOGGER.warn("Cannot drain Combat Evolution stamina during capture", failure);
            }
        }
    }
}
