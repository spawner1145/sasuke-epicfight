package dev.sasuke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SasukeMod.ID, value = Dist.CLIENT)
public final class BlackFlameVisuals {
    private record Eruption(Vec3 position, float radius, int seed, boolean radial, long born) {}
    private record Residue(Vec3 position, float radius, int owner, int kind, long born, int seed) {}
    private static final List<Eruption> ERUPTIONS = new ArrayList<>();
    private static final List<Residue> RESIDUES = new ArrayList<>();
    private static final Map<Integer, Long> EMPOWERED = new HashMap<>();

    public static void burst(SasukeNetwork.Burst message) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(message.position()) > 4096) return;
        if (ERUPTIONS.size() >= 96) ERUPTIONS.remove(0);
        ERUPTIONS.add(new Eruption(message.position(), message.radius(), message.seed(), message.radial(), mc.level.getGameTime()));
        if (message.radius() >= 1) mc.level.playLocalSound(message.position().x, message.position().y, message.position().z,
            net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS,
            message.radial() ? 0.55F : 0.85F, message.radial() ? 1.15F : 0.7F, false);
    }

    public static void add(SasukeNetwork.Flame message) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        if (message.kind() == 2) EMPOWERED.put(message.entityId(), now + 25);
        if (message.kind() == 7 && message.entityId() >= 0) EMPOWERED.put(message.entityId(), now + 8);
        Residue existing = null;
        for (var residue : RESIDUES) {
            if (residue.kind == message.kind() && residue.owner == message.entityId()
                    && (residue.owner >= 0 && residue.kind != 7 || residue.position.distanceToSqr(message.position()) < 0.01)) {
                existing = residue;
                break;
            }
        }
        if (existing != null) RESIDUES.remove(existing);
        if (RESIDUES.size() >= 128) RESIDUES.remove(0);
        RESIDUES.add(new Residue(message.position(), message.radius(), message.entityId(), message.kind(), now,
            existing == null ? level.random.nextInt() : existing.seed));
    }

    public static boolean empowered(int entity) {
        var level = Minecraft.getInstance().level;
        return level != null && EMPOWERED.getOrDefault(entity, 0L) > level.getGameTime();
    }

    public static void clear() { ERUPTIONS.clear(); RESIDUES.clear(); EMPOWERED.clear(); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        ERUPTIONS.removeIf(effect -> now - effect.born > (effect.radius < 0 ? 10 : 26));
        RESIDUES.removeIf(effect -> now - effect.born > (effect.kind == 7 ? 22 : 28));
        EMPOWERED.entrySet().removeIf(entry -> entry.getValue() <= now);
        Vec3 camera = event.getCamera().getPosition();
        for (var residue : RESIDUES) {
            Vec3 position = residue.position;
            if (residue.kind == 1 || residue.kind == 2) {
                var entity = mc.level.getEntity(residue.owner);
                if (entity == null || !entity.isAlive()) continue;
                position = entity.getPosition(event.getPartialTick());
            }
            if (position.distanceToSqr(camera) > 4096) continue;
            float age = now - residue.born + event.getPartialTick();
            float alpha = EffectGeometry.clamp(((residue.kind == 7 ? 22 : 28) - age) / 8);
            residual(event, position.subtract(camera), residue.radius, residue.kind, residue.seed,
                now + event.getPartialTick(), alpha);
        }
        for (var eruption : ERUPTIONS) {
            if (eruption.position.distanceToSqr(camera) > 4096) continue;
            float age = now - eruption.born + event.getPartialTick();
            Vec3 center = eruption.position.subtract(camera).add(0, 0.035, 0);
            if (eruption.radius < 0) charge(event, center, -eruption.radius, age, eruption.seed);
            else eruption(event, center, eruption, age);
        }
        for (String texture : new String[]{"white", "halo", "flame_curl", "glint"}) {
            mc.renderBuffers().bufferSource().endBatch(EffectGeometry.type(texture));
        }
    }

    private static void residual(RenderLevelStageEvent event, Vec3 center, float radius, int kind,
            int seed, float time, float alpha) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var random = new Random(seed);
        boolean ground = kind == 0;
        var glow = buffers.getBuffer(EffectGeometry.type("halo"));
        EffectGeometry.billboard(glow, event.getPoseStack(), event.getCamera(), center.add(0, ground ? 0.18 : 0.6, 0),
            radius * 3.3F, ground ? 0.9F : 1.9F, 0, 0x100419, alpha * 0.9F, 0, 1);
        var flames = buffers.getBuffer(EffectGeometry.type("black_flame"));
        int count = ground ? Math.min(24, 9 + (int)(radius * 4)) : 0;
        for (int index = 0; index < count; index++) {
            double angle = index * 2.39996;
            double distance = Math.sqrt(random.nextDouble()) * radius;
            float height = ground ? 0.85F + random.nextFloat() * 0.9F : 0.7F + random.nextFloat() * 0.6F;
            float pulse = 0.9F + 0.1F * (float)Math.sin(time * 0.2 + index);
            Vec3 point = center.add(Math.cos(angle) * distance, height * 0.38 + (ground ? 0 : random.nextDouble() * 0.8), Math.sin(angle) * distance);
            int frame = Math.floorMod((int)(time * 0.6) + index * 3, 8);
            EffectGeometry.billboard(flames, event.getPoseStack(), event.getCamera(), point, height * 0.8F, height * pulse,
                (float)Math.sin(index * 3.7) * 0.18F, 0xFFFFFF, alpha, frame, 8);
        }
        var curls = buffers.getBuffer(EffectGeometry.type("flame_curl"));
        int curlsCount = ground ? 7 : kind == 7 ? 10 : 18;
        for (int index = 0; index < curlsCount; index++) {
            float phase = (time * 0.025F + index * 0.173F) % 1;
            double angle = index * 2.39996 + phase * 0.6;
            Vec3 point = center.add(Math.cos(angle) * radius * 0.9, 0.5 + phase * 1.6, Math.sin(angle) * radius * 0.9);
            float visibility = (ground ? (float)Math.sin(phase * Math.PI) : 0.7F + 0.3F * (float)Math.sin(time * 0.13 + index)) * alpha;
            EffectGeometry.billboard(curls, event.getPoseStack(), event.getCamera(), point, 0.3F + phase * 0.15F,
                0.4F + phase * 0.15F, time * 0.045F + index, 0xFFFFFF, visibility, 0, 1);
        }
    }

    private static void charge(RenderLevelStageEvent event, Vec3 center, float radius, float age, int seed) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float progress = EffectGeometry.clamp(age / 10);
        float fade = EffectGeometry.clamp((10 - age) / 3);
        var output = buffers.getBuffer(EffectGeometry.type("white"));
        EffectGeometry.ring(output, event.getPoseStack(), center, radius * (1 - progress * 0.8F), 0.12F,
            0x791CBA, fade * 0.7F, age * 0.2F, 5.6F);
        var curls = buffers.getBuffer(EffectGeometry.type("flame_curl"));
        for (int index = 0; index < 12; index++) {
            double angle = index * 2.39996 + progress * 1.8 + seed;
            double distance = radius * (1 - progress);
            Vec3 point = center.add(Math.cos(angle) * distance, 0.2 + (index % 3) * 0.35 * (1 - progress), Math.sin(angle) * distance);
            EffectGeometry.billboard(curls, event.getPoseStack(), event.getCamera(), point, 0.4F, 0.55F, age * 0.2F + index,
                0xFFFFFF, fade, 0, 1);
        }
    }

    private static void eruption(RenderLevelStageEvent event, Vec3 center, Eruption eruption, float age) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float radius = eruption.radius;
        float rise = 1 - (float)Math.pow(1 - EffectGeometry.clamp(age / 3), 3);
        float fade = EffectGeometry.clamp((26 - age) / 12);
        float flash = EffectGeometry.clamp((7 - age) / 5);
        var output = buffers.getBuffer(EffectGeometry.type("white"));
        var random = new Random(eruption.seed);
        int count = radius < 1 ? 10 : eruption.radial ? 30 : 36;
        for (int index = 0; index < count; index++) {
            double angle = index * 2.399963 + eruption.seed;
            double distance = random.nextDouble() * radius * 0.8;
            float height = radius * (1.2F + random.nextFloat() * 1.5F) * rise;
            Vec3 base = center.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Vec3 direction = new Vec3(Math.cos(angle) * (0.3 + distance / radius * 0.7), 1, Math.sin(angle) * (0.3 + distance / radius * 0.7)).normalize();
            if (eruption.radial) {
                double vertical = 1 - 2 * (index + 0.5) / count;
                double ring = Math.sqrt(1 - vertical * vertical);
                direction = new Vec3(Math.cos(angle) * ring, vertical, Math.sin(angle) * ring);
                base = center;
            }
            Vec3 bend = new Vec3(Math.sin(angle + age * 0.02), 0, -Math.cos(angle + age * 0.02)).scale(height * 0.12);
            Vec3 side = direction.cross(bend).normalize();
            float width = radius * (0.065F + random.nextFloat() * 0.085F) * rise;
            for (int slice = 0; slice < 9; slice++) {
                float first = slice / 9F;
                float second = (slice + 1) / 9F;
                Vec3 start = base.add(direction.scale(height * first)).add(bend.scale(first * first));
                Vec3 end = base.add(direction.scale(height * second)).add(bend.scale(second * second));
                float startWidth = width * (float)Math.pow(1 - first, 1.6);
                float endWidth = width * (float)Math.pow(1 - second, 1.6);
                EffectGeometry.line(output, event.getPoseStack(), start, end, startWidth, endWidth, 0x090311, fade);
                Vec3 accent = side.scale(startWidth * 0.35);
                EffectGeometry.line(output, event.getPoseStack(), start.add(accent), end.add(accent.scale(0.8)),
                    startWidth * 0.14F, endWidth * 0.07F, 0x791BBA, fade * (0.35F + flash * 0.5F));
            }
        }
        EffectGeometry.ring(output, event.getPoseStack(), center, radius * (0.4F + rise), radius * 0.35F,
            0x190424, fade * 0.8F, 0, (float)Math.PI * 2);
        if (radius >= 1 && age < 9) dome(event, center, radius * (0.6F + rise * 0.9F), flash, eruption.radial);
        var curls = buffers.getBuffer(EffectGeometry.type("flame_curl"));
        for (int index = 0; index < (radius < 1 ? 4 : 15); index++) {
            double angle = index * 2.39996 + eruption.seed;
            Vec3 point = center.add(Math.cos(angle) * radius * (0.3 + age * 0.035), radius * (0.3 + (index % 4) * 0.22) + age * 0.04,
                Math.sin(angle) * radius * (0.3 + age * 0.035));
            EffectGeometry.billboard(curls, event.getPoseStack(), event.getCamera(), point, radius * 0.19F + 0.1F,
                radius * 0.23F + 0.14F, index + age * 0.06F, 0xFFFFFF, fade, 0, 1);
        }
        if (radius >= 1) {
            var glow = buffers.getBuffer(EffectGeometry.type("halo"));
            Vec3 flashCenter = center.add(0, eruption.radial ? 0 : radius * 0.45, 0);
            EffectGeometry.billboard(glow, event.getPoseStack(), event.getCamera(), flashCenter, radius * 3.6F, radius * 3.6F,
                0, 0xA729FF, flash * 0.85F, 0, 1);
            EffectGeometry.billboard(glow, event.getPoseStack(), event.getCamera(), flashCenter, radius * 1.6F, radius * 1.9F,
                0, 0xFFF4FF, flash, 0, 1);
            EffectGeometry.billboard(buffers.getBuffer(EffectGeometry.type("glint")), event.getPoseStack(), event.getCamera(),
                flashCenter, radius * 5, radius * 2, 0, 0xDC9EFF, flash * 0.8F, 0, 1);
        }
    }

    private static void dome(RenderLevelStageEvent event, Vec3 center, float radius, float alpha, boolean sphere) {
        var output = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(EffectGeometry.type("white"));
        for (int latitude = 0; latitude < 12; latitude++) {
            for (int longitude = 0; longitude < 48; longitude++) {
                for (int corner : new int[]{0, 1, 2, 3}) {
                    double vertical = Math.PI * (sphere ? 1 : 0.5) * (latitude + (corner >= 2 ? 1 : 0)) / 12;
                    if (sphere) vertical -= Math.PI / 2;
                    double angle = Math.PI * 2 * (longitude + (corner == 1 || corner == 2 ? 1 : 0)) / 48;
                    Vec3 normal = new Vec3(Math.cos(vertical) * Math.cos(angle), Math.sin(vertical), Math.cos(vertical) * Math.sin(angle));
                    Vec3 point = center.add(normal.scale(radius));
                    float edge = (float)Math.pow(1 - Math.abs(normal.dot(point.normalize())), 2);
                    EffectGeometry.vertex(output, event.getPoseStack(), point, 0.5F, 0.5F, 0xD68EFF, alpha * (0.045F + edge * 0.28F));
                }
            }
        }
    }

    public static void spinEmbers(RenderLevelStageEvent event, Vec3 center, float time, float alpha) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var curls = buffers.getBuffer(EffectGeometry.type("flame_curl"));
        for (int index = 0; index < 14; index++) {
            double angle = index * 2.39996 + time * 3;
            double radius = 1.4 + (index % 4) * 0.5;
            Vec3 point = center.add(Math.cos(angle) * radius, Math.sin(index * 4.1 + time * 5) * 0.6, Math.sin(angle) * radius);
            EffectGeometry.billboard(curls, event.getPoseStack(), event.getCamera(), point, 0.35F, 0.5F, index + time * 2,
                0xFFFFFF, alpha, 0, 1);
        }
        var halo = buffers.getBuffer(EffectGeometry.type("halo"));
        for (int index = 0; index < 5; index++) {
            double angle = index * 2.39996 + time * 5;
            Vec3 point = center.add(Math.cos(angle) * 2.8, 0.2, Math.sin(angle) * 2.8);
            EffectGeometry.billboard(halo, event.getPoseStack(), event.getCamera(), point, 0.7F, 1.0F, 0, 0x9A13E7, alpha * 0.6F, 0, 1);
        }
    }
}
