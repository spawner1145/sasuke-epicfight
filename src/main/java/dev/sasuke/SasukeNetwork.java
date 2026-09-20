package dev.sasuke;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class SasukeNetwork {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(SasukeMod.id("combat"), () -> "8", "8"::equals, "8"::equals);
    public record ComboMovie(double startTick, int casterId, boolean showVideo) {}
    public record Input(int key, int forward, int left) {}
    public record Status(int phase, int firstCooldown, int secondCooldown) {}
    public record Burst(Vec3 position, float radius, int seed, boolean radial) {}
    public record Summon(Vec3 position) {}
    public record Flame(Vec3 position, float radius, int entityId, int kind) {}

    public static void register() {
        CHANNEL.registerMessage(5, ComboMovie.class, (message, buffer) -> { buffer.writeDouble(message.startTick()); buffer.writeVarInt(message.casterId()); buffer.writeBoolean(message.showVideo()); }, buffer -> new ComboMovie(buffer.readDouble(), buffer.readVarInt(), buffer.readBoolean()),
            (message, supplier) -> {
                supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ComboCg.play(message)));
                supplier.get().setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(4, Flame.class,
            (message, buffer) -> { buffer.writeDouble(message.position.x); buffer.writeDouble(message.position.y); buffer.writeDouble(message.position.z); buffer.writeFloat(message.radius); buffer.writeInt(message.entityId); buffer.writeByte(message.kind); },
            buffer -> new Flame(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readFloat(), buffer.readInt(), buffer.readByte()),
            (message, supplier) -> {
                supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SasukeEffects.flame(message)));
                supplier.get().setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(0, Input.class,
            (message, buffer) -> { buffer.writeByte(message.key); buffer.writeByte(message.forward); buffer.writeByte(message.left); },
            buffer -> new Input(buffer.readByte(), buffer.readByte(), buffer.readByte()),
            (message, supplier) -> {
                var context = supplier.get();
                context.enqueueWork(() -> {
                    ServerPlayer player = context.getSender();
                    if (player != null && message.key >= 1 && message.key <= 5 && Math.abs(message.forward) <= 1 && Math.abs(message.left) <= 1) CombatController.input(player, message);
                });
                context.setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Status.class,
            (message, buffer) -> { buffer.writeVarInt(message.phase); buffer.writeVarInt(message.firstCooldown); buffer.writeVarInt(message.secondCooldown); },
            buffer -> new Status(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()),
            (message, supplier) -> {
                supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SasukeClient.status(message)));
                supplier.get().setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(2, Burst.class,
            (message, buffer) -> { buffer.writeDouble(message.position.x); buffer.writeDouble(message.position.y); buffer.writeDouble(message.position.z); buffer.writeFloat(message.radius); buffer.writeInt(message.seed); buffer.writeBoolean(message.radial); },
            buffer -> new Burst(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()), buffer.readFloat(), buffer.readInt(), buffer.readBoolean()),
            (message, supplier) -> {
                supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SasukeClient.burst(message)));
                supplier.get().setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(3, Summon.class,
            (message, buffer) -> { buffer.writeDouble(message.position.x); buffer.writeDouble(message.position.y); buffer.writeDouble(message.position.z); },
            buffer -> new Summon(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())),
            (message, supplier) -> {
                supplier.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SasukeEffects.summon(message.position)));
                supplier.get().setPacketHandled(true);
            }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void comboCg(ServerPlayer caster, net.minecraft.world.entity.Entity target, long castTick) {
        double start = castTick + (0.08 + 60.0 / 60.0) * 20.0;
        for (ServerPlayer listener : caster.serverLevel().players()) {
            boolean video = listener == caster || listener == target;
            if (video || listener.distanceToSqr(caster) <= 4096) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> listener), new ComboMovie(start, caster.getId(), video));
            }
        }
    }

    public static void status(ServerPlayer player, CombatController.State state) {
        long now = player.level().getGameTime();
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Status(state.phase == CombatController.Phase.NORMAL && CombatController.flameSecondReady(player, state)
            ? CombatController.Phase.SECOND_READY.ordinal() : state.phase.ordinal(), player.isCreative() ? 0 : (int)Math.max(0, state.firstReady - now), player.isCreative() ? 0 : (int)Math.max(0, state.secondReady - now)));
    }

    public static void burst(ServerPlayer player, Vec3 position, float radius) {
        burst(player, position, radius, false);
    }

    public static void burst(ServerPlayer player, Vec3 position, float radius, boolean radial) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(position.x, position.y, position.z, 64, player.level().dimension())), new Burst(position, radius, player.getRandom().nextInt(), radial));
    }

    public static void summon(ServerPlayer player) {
        Vec3 position = player.position();
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(position.x, position.y, position.z, 64, player.level().dimension())), new Summon(position));
    }

    public static void flame(net.minecraft.server.level.ServerLevel level, Vec3 position, float radius, int entityId, int kind) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(position.x, position.y, position.z, 64, level.dimension())), new Flame(position, radius, entityId, kind));
    }
}
