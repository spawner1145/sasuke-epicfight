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
public final class ElectricVisuals {
    private record Arc(Vec3 origin, Vec3 end, float radius, int kind, long born, int seed, int owner) {}
    private record Position(Vec3 point, long tick) {}
    private static final Map<Integer, Position> PREVIOUS = new HashMap<>();
    private static final List<Arc> ARCS = new ArrayList<>();

    public static void add(SasukeNetwork.Flame message) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = level.getGameTime();
        Vec3 start = message.position();
        if (message.kind() == 6) {
            for (var previous : ARCS) {
                if (previous.kind == 6 && previous.owner == message.entityId() && now - previous.born <= 4) start = previous.end;
            }
        }
        if (message.kind() == 6 || message.kind() == 9) ARCS.removeIf(arc -> arc.kind == 6 && arc.owner == message.entityId());
        if (message.kind() == 5) {
            Position previous = PREVIOUS.put(message.entityId(), new Position(start, now));
            if (previous != null && now - previous.tick <= 4 && previous.point.distanceToSqr(start) < 100) start = previous.point;
        }
        if (ARCS.size() >= 128) ARCS.remove(0);
        ARCS.add(new Arc(start, message.position(), message.radius(), message.kind(), now, level.random.nextInt(), message.entityId()));
    }

    public static void clear() { ARCS.clear(); PREVIOUS.clear(); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        PREVIOUS.entrySet().removeIf(entry -> now - entry.getValue().tick > 20);
        ARCS.removeIf(arc -> now - arc.born > (arc.kind == 9 ? 18 : arc.kind == 6 ? 4 : 5));
        Vec3 camera = event.getCamera().getPosition();
        for (var arc : ARCS) {
            float age = now - arc.born + event.getPartialTick();
            Vec3 center = arc.end.subtract(camera);
            if (arc.kind == 9) strike(event, center, arc.radius, age, arc.seed);
            else if (arc.kind == 6) orb(event, arc.origin.lerp(arc.end, Math.min(1, age)).subtract(camera), arc.radius,
                now + event.getPartialTick(), EffectGeometry.clamp((4 - age) / 2));
            else {
                float alpha = EffectGeometry.clamp(1 - age / 5);
                body(event, center, arc.seed, age, alpha);
                if (arc.origin.distanceToSqr(arc.end) > 0.01) lance(event, arc.origin.subtract(camera), center, now, alpha * 0.55F);
            }
        }
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity.getType() != SasukeMod.PLANTED_SWORD.get() || entity.distanceToSqr(camera) > 4096) continue;
            Vec3 base = entity.getPosition(event.getPartialTick()).subtract(camera).add(0, -0.74, 0);
            var buffers = mc.renderBuffers().bufferSource();
            var output = buffers.getBuffer(EffectGeometry.type("white"));
            float time = now + event.getPartialTick();
            EffectGeometry.ring(output, event.getPoseStack(), base, 0.62F, 0.18F, 0x70EAFF, 0.7F, time * 0.06F, 5.8F);
            Random swordRandom = new Random(entity.getId() * 991L + (long)(time * 2));
            for (int strand = 0; strand < 5; strand++) {
                double angle = strand * Math.PI * 2 / 5 + time * 0.12;
                Vec3 start = base.add(Math.cos(angle) * 0.18, 0.05, Math.sin(angle) * 0.18);
                Vec3 end = base.add(Math.cos(angle + 1.8) * 0.75, 0.95 + 0.25 * Math.sin(time + strand), Math.sin(angle + 1.8) * 0.75);
                EffectGeometry.bolt(output, event.getPoseStack(), jagged(start, end, 7, 0.12, swordRandom), 0.028F, 0.85F);
            }
            Random debrisRandom = new Random(entity.getId() * 1777L);
            for (int shard = 0; shard < 12; shard++) {
                double angle = shard * Math.PI * 2 / 12;
                float distance = 0.35F + debrisRandom.nextFloat() * 0.55F;
                Vec3 shardBase = base.add(Math.cos(angle) * distance, 0.02, Math.sin(angle) * distance);
                Vec3 shardTip = shardBase.add(Math.cos(angle) * 0.12, 0.16 + debrisRandom.nextFloat() * 0.18, Math.sin(angle) * 0.12);
                EffectGeometry.line(output, event.getPoseStack(), shardBase, shardTip, 0.07F, 0.025F, 0x68747C, 0.8F);
            }
            EffectGeometry.billboard(buffers.getBuffer(EffectGeometry.type("halo")), event.getPoseStack(), event.getCamera(),
                base, 1.4F, 0.45F, 0, 0x22B8FF, 0.8F, 0, 1);
        }
        for (String texture : new String[]{"white", "halo", "ring", "glint", "dust"}) mc.renderBuffers().bufferSource().endBatch(EffectGeometry.type(texture));
    }

    private static Vec3[] jagged(Vec3 start, Vec3 end, int segments, double amplitude, Random random) {
        Vec3 direction = end.subtract(start).normalize();
        Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.01) side = new Vec3(1, 0, 0);
        Vec3 up = side.cross(direction).normalize();
        Vec3[] points = new Vec3[segments + 1];
        for (int segment = 0; segment <= segments; segment++) {
            double fraction = segment / (double)segments;
            double envelope = segment == 0 || segment == segments ? 0 : 1;
            points[segment] = start.lerp(end, fraction).add(side.scale((random.nextDouble() - 0.5) * amplitude * envelope))
                .add(up.scale((random.nextDouble() - 0.5) * amplitude * envelope));
        }
        return points;
    }

    public static void lance(RenderLevelStageEvent event, Vec3 start, Vec3 end, float time, float alpha) {
        var random = new Random(731L + (long)(time / 2));
        var output = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(EffectGeometry.type("white"));
        Vec3 direction = end.subtract(start).normalize();
        Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.01) side = new Vec3(1, 0, 0);
        Vec3 up = side.cross(direction).normalize();
        for (int strand = 0; strand < 3; strand++) {
            Vec3 offset = side.scale((strand - 1) * 0.2);
            Vec3[] points = jagged(start.add(offset), end.add(offset), 9, 0.8, random);
            EffectGeometry.bolt(output, event.getPoseStack(), points, strand == 0 ? 0.055F : 0.025F, alpha);
            Vec3 fork = points[4].add(side.scale((random.nextDouble() - 0.5) * 0.8)).add(up.scale(0.7));
            EffectGeometry.bolt(output, event.getPoseStack(), jagged(points[4], fork, 4, 0.2, random), 0.018F, alpha * 0.8F);
        }
    }

    public static void body(RenderLevelStageEvent event, Vec3 center, int seed, float time, float alpha) {
        var random = new Random(seed + (long)(time / 2) * 31);
        var output = Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(EffectGeometry.type("white"));
        for (int strand = 0; strand < 5; strand++) {
            double angle = strand * Math.PI * 2 / 5 + time * 0.17;
            Vec3 start = center.add(Math.cos(angle) * 0.72, -0.05, Math.sin(angle) * 0.72);
            Vec3 end = center.add(Math.cos(angle + 1.6) * 1.05, 0.10, Math.sin(angle + 1.6) * 1.05);
            EffectGeometry.bolt(output, event.getPoseStack(), jagged(start, end, 7, 0.35, random), 0.024F, alpha);
        }
    }

    private static void orb(RenderLevelStageEvent event, Vec3 center, float radius, float time, float alpha) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var halo = buffers.getBuffer(EffectGeometry.type("halo"));
        EffectGeometry.billboard(halo, event.getPoseStack(), event.getCamera(), center, radius * 2.5F, radius * 2.5F, 0, 0x0C9AFF, alpha * 0.45F, 0, 1);
        EffectGeometry.billboard(halo, event.getPoseStack(), event.getCamera(), center, radius * 1.45F, radius * 1.45F, 0, 0x55F5FF, alpha, 0, 1);
        EffectGeometry.billboard(halo, event.getPoseStack(), event.getCamera(), center, radius * 0.95F, radius * 0.95F, 0, 0xFFFFFF, alpha, 0, 1);
        var output = buffers.getBuffer(EffectGeometry.type("white"));
        for (int band = 0; band < 4; band++) {
            Vec3[] points = new Vec3[25];
            for (int segment = 0; segment < points.length; segment++) {
                double angle = segment * Math.PI * 1.8 / (points.length - 1) + time * (band % 2 == 0 ? 0.15 : -0.13);
                double size = radius * (0.75 + 0.08 * Math.sin(segment * 2.3 + Math.floor(time / 2) + band));
                points[segment] = new Vec3(Math.cos(angle) * size, Math.sin(angle) * size, 0).xRot(band * 0.9F).yRot(band * 1.7F).add(center);
            }
            EffectGeometry.bolt(output, event.getPoseStack(), points, 0.024F, alpha);
        }
    }

    private static void strike(RenderLevelStageEvent event, Vec3 center, float radius, float age, int seed) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float fade = EffectGeometry.clamp((17 - age) / 9);
        float flash = EffectGeometry.clamp((5 - age) / 4);
        float growth = 1 - (float)Math.pow(1 - EffectGeometry.clamp(age / 5), 3);
        var output = buffers.getBuffer(EffectGeometry.type("white"));
        var random = new Random(seed + (long)(age / 2) * 17);
        if (age < 11) {
            Vec3 top = center.add(0, 8.5, 0);
            Vec3[] trunk = jagged(top, center, 13, 0.65, random);
            EffectGeometry.bolt(output, event.getPoseStack(), trunk, 0.19F, fade);
            for (int branch = 0; branch < 7; branch++) {
                int fork = 2 + random.nextInt(9);
                double angle = branch * 2.39996;
                Vec3 end = trunk[fork].add(Math.cos(angle) * 1.6, -1.4 - random.nextDouble(), Math.sin(angle) * 1.6);
                EffectGeometry.bolt(output, event.getPoseStack(), jagged(trunk[fork], end, 5, 0.45, random), 0.04F, fade * 0.9F);
            }
        }
        for (int spoke = 0; spoke < 16; spoke++) {
            double angle = spoke * Math.PI * 2 / 16;
            Vec3 end = center.add(Math.cos(angle) * radius * growth, 0.07 + random.nextDouble() * 0.25, Math.sin(angle) * radius * growth);
            EffectGeometry.bolt(output, event.getPoseStack(), jagged(center.add(0, 0.06, 0), end, 6, 0.5, random), 0.034F, fade);
        }
        EffectGeometry.ring(output, event.getPoseStack(), center.add(0, 0.05, 0), radius * (0.15F + growth), 0.36F,
            0xCAFFFF, fade * 0.7F, 0, (float)Math.PI * 2);
        random = new Random(seed);
        for (int shard = 0; shard < 20; shard++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double reach = radius * (0.45 + random.nextDouble() * 0.5) * growth;
            Vec3 end = center.add(Math.cos(angle) * reach, 0.4 + random.nextDouble() * 2.3 * growth, Math.sin(angle) * reach);
            EffectGeometry.line(output, event.getPoseStack(), center.add(0, 0.05, 0), end, 0.12F + random.nextFloat() * 0.13F, 0,
                shard % 3 == 0 ? 0xFFFFFF : 0x89FAFF, flash * 0.9F);
        }
        debris(event, center, radius, age, seed, fade);
        var halo = buffers.getBuffer(EffectGeometry.type("halo"));
        EffectGeometry.billboard(halo, event.getPoseStack(), event.getCamera(), center.add(0, 0.45, 0), radius * 2.6F,
            radius * 2.1F, 0, 0x19B7FF, fade * 0.65F, 0, 1);
        EffectGeometry.billboard(halo, event.getPoseStack(), event.getCamera(), center.add(0, 0.6, 0), radius * 1.2F,
            radius * 1.3F, 0, 0xFFFFFF, flash, 0, 1);
    }

    private static void debris(RenderLevelStageEvent event, Vec3 center, float radius, float age, int seed, float alpha) {
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var output = buffers.getBuffer(EffectGeometry.type("white"));
        var random = new Random(seed ^ 4871);
        for (int index = 0; index < 17; index++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.045 + random.nextDouble() * 0.08;
            double lift = Math.max(0.04, age * (0.18 + random.nextDouble() * 0.13) - age * age * 0.015);
            Vec3 point = center.add(Math.cos(angle) * age * speed * radius * 0.4, lift, Math.sin(angle) * age * speed * radius * 0.4);
            float size = 0.035F + random.nextFloat() * 0.075F;
            EffectGeometry.line(output, event.getPoseStack(), point, point.add(size * 0.7, size, size * 0.4), size, size * 0.45F,
                index % 3 == 0 ? 0x847C6D : 0x252A30, alpha);
        }
        var dust = buffers.getBuffer(EffectGeometry.type("dust"));
        for (int index = 0; index < 8; index++) {
            double angle = index * Math.PI / 4;
            Vec3 point = center.add(Math.cos(angle) * age * 0.09, 0.15, Math.sin(angle) * age * 0.09);
            EffectGeometry.billboard(dust, event.getPoseStack(), event.getCamera(), point, 0.8F + age * 0.09F,
                0.4F + age * 0.025F, index, 0xD5D4CE, alpha * 0.65F, 0, 1);
        }
    }
}
