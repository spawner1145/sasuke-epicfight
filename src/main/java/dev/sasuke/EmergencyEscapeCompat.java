package dev.sasuke;

import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.entity.eventlistener.PlayerEventListener.EventType;
import yesman.epicfight.skill.SkillCategories;
import yesman.epicfight.gameasset.EpicFightSkills;

@Mod.EventBusSubscriber(modid = SasukeMod.ID)
public final class EmergencyEscapeCompat {
    private static final UUID ID = UUID.fromString("47a98ea9-2ab4-4860-9626-ab178916ab48");
    private static final WeakHashMap<PlayerPatch<?>, Boolean> INSTALLED = new WeakHashMap<>();

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        PlayerPatch<?> patch = EpicFightCapabilities.getEntityPatch(event.player, PlayerPatch.class);
        if (patch == null || INSTALLED.putIfAbsent(patch, true) != null) return;
        patch.getEventListener().addEventListener(EventType.SKILL_CAST_EVENT, ID, cast -> {
            if (cast.getSkillContainer().getSkill().getCategory() != SkillCategories.DODGE
                    || !patch.getOriginal().getMainHandItem().is(SasukeMod.KUSANAGI.get())
                    || patch.getSkillContainerFor(EpicFightSkills.EMERGENCY_ESCAPE).isEmpty()) return;
            var player = patch.getAnimator().getPlayerFor(null);
            var animation = player.getRealAnimation();
            float time = player.getElapsedTime();
            for (String name : new String[]{"draw_to_side", "amaterasu_1", "amaterasu_2", "amaterasu_combo"}) {
                if (!animation.equals(SasukeAnimations.player(name))) continue;
                float recovery = switch (name) {
                    case "draw_to_side" -> 6F / 20F;
                    case "amaterasu_1" -> 20F / 20F;
                    case "amaterasu_2" -> 11F / 20F;
                    default -> 68F / 20F;
                };
                if (!player.getAnimation().get().isLinkAnimation() && time >= recovery
                        && !patch.isInAir() && !patch.getOriginal().isInWater()
                        && !patch.getOriginal().onClimbable() && !patch.getOriginal().isPassenger()) {
                    cast.setStateExecutable(true);
                }
            }
        });
    }
}
