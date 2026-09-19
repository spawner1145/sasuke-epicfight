package dev.sasuke;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

final class CombatAudio {
    static int next(ServerPlayer player, String key, int count) {
        var data = player.getPersistentData();
        int value = Math.floorMod(data.getInt("sasukeAudio_" + key), count);
        data.putInt("sasukeAudio_" + key, (value + 1) % count);
        return value + 1;
    }
    static void play(ServerPlayer player, String sound) {
        playAt(player, sound, player.position());
    }
    static void playAt(ServerPlayer player, String sound, net.minecraft.world.phys.Vec3 position) {
        player.serverLevel().playSound(null, position.x, position.y, position.z,
            SoundEvent.createVariableRangeEvent(SasukeMod.id(sound)), SoundSource.PLAYERS, 1F, 1F);
    }
    static void action(ServerPlayer player, String action) {
        String sound = switch (action) {
            case "1a" -> "attack_1";
            case "2a" -> "attack_2";
            case "3a" -> "attack_3";
            case "4a1" -> "attack_41";
            case "4a2" -> "attack_42";
            case "4a3" -> "attack_43_" + next(player, "fourth", 3);
            case "dash_spin_slash" -> "spin_" + next(player, "spin", 2);
            case "basic_sheathe" -> "basic_sheathe";
            case "sheathe_flourish" -> "spin_sheathe";
            default -> null;
        };
        if (sound != null) play(player, sound);
    }
}
