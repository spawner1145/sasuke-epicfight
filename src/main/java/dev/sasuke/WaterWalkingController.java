package dev.sasuke;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SasukeMod.ID)
public final class WaterWalkingController {
    private static boolean equipped(Player player) {
        return player.isAlive() && !player.isSpectator() && player.getMainHandItem().is(SasukeMod.KUSANAGI.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void fall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && equipped(player)) {
            event.setDamageMultiplier(0);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void tick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        if (!equipped(player)) return;
        player.fallDistance = 0;
        if (player.isShiftKeyDown() || player.isPassenger() || player.isFallFlying() || player.getAbilities().flying
            || player.getDeltaMovement().y > 0.05) return;
        if (!player.level().noCollision(player, player.getBoundingBox().move(0, -0.06, 0))) return;
        double surface = Double.NEGATIVE_INFINITY;
        double halfWidth = player.getBbWidth() * 0.45;
        for (double offsetX : new double[]{-halfWidth, halfWidth}) {
            for (double offsetZ : new double[]{-halfWidth, halfWidth}) {
                for (int layer = -1; layer <= 0; layer++) {
                    BlockPos position = BlockPos.containing(player.getX() + offsetX, player.getY() + layer, player.getZ() + offsetZ);
                    var fluid = player.level().getFluidState(position);
                    if (!fluid.is(FluidTags.WATER) || player.level().getFluidState(position.above()).is(FluidTags.WATER)) continue;
                    double height = position.getY() + fluid.getHeight(player.level(), position);
                    if (player.getY() >= height - 0.22 && player.getY() <= height + 0.12) surface = Math.max(surface, height);
                }
            }
        }
        if (!Double.isFinite(surface)) return;
        double lift = surface + 0.002 - player.getY();
        if (!player.level().noCollision(player, player.getBoundingBox().move(0, lift, 0))) return;
        player.setPos(player.getX(), surface + 0.002, player.getZ());
        Vec3 movement = player.getDeltaMovement();
        player.setDeltaMovement(movement.x, Math.max(0, movement.y), movement.z);
        player.setOnGround(true);
        player.setSwimming(false);
    }
}
