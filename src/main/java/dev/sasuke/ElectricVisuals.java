package dev.sasuke;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SasukeMod.ID, value = Dist.CLIENT)
public final class ElectricVisuals {
    private record Arc(Vec3 origin, Vec3 end, float radius, int kind, long born, int seed, int owner) {}
    private record Position(Vec3 point, long tick) {}
    private static final java.util.Map<Integer, Position> PREVIOUS = new java.util.HashMap<>();
    private static final List<Arc> ARCS = new ArrayList<>();
    public static void add(SasukeNetwork.Flame message) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (ARCS.size() >= 96) ARCS.remove(0);
        Vec3 start = message.position();
        Vec3 end = start;
        if (message.kind() == 6) {
            for (var previous : ARCS) {
                if (previous.kind == 6 && previous.owner == message.entityId() && level.getGameTime() - previous.born <= 3) start = previous.end;
            }
        }
        if (message.kind() == 6 || message.kind() == 9) ARCS.removeIf(arc -> arc.kind == 6 && arc.owner == message.entityId());
        if (message.kind() == 5) {
            Position previous = PREVIOUS.put(message.entityId(), new Position(start, level.getGameTime()));
            if (previous == null || level.getGameTime() - previous.tick > 4 || previous.point.distanceToSqr(start) > 25) return;
            start = previous.point;
            if (start.distanceToSqr(end) < 0.0025) return;
        } else if (message.kind() == 9) start = start.add(0, 5, 0);
        else if (message.kind() != 6) start = start.add(0, 0.5, 0);
        ARCS.add(new Arc(start, end, message.radius(), message.kind(), level.getGameTime(), level.random.nextInt(), message.entityId()));
    }
    public static void clear() { ARCS.clear(); PREVIOUS.clear(); }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long now = mc.level.getGameTime();
        PREVIOUS.entrySet().removeIf(entry -> now - entry.getValue().tick > 20);
        ARCS.removeIf(arc -> now - arc.born > (arc.kind == 9 ? 16 : arc.kind == 6 ? 1 : 5));
        var buffers = mc.renderBuffers().bufferSource();
        var type = RenderType.entityTranslucentEmissive(SasukeMod.id("textures/particle/white.png"));
        var vertices = buffers.getBuffer(type);
        var stack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        for (var arc : ARCS) {
            var random = new Random(arc.seed + (now - arc.born) / 2);
            float alpha = 1F - (now - arc.born + event.getPartialTick()) / (arc.kind == 9 ? 17F : 6F);
            if (arc.kind == 6) {
                Vec3 center = arc.origin.lerp(arc.end, Math.min(1, now - arc.born + event.getPartialTick())).subtract(camera);
                float time = (now % 10000) + event.getPartialTick();
                float pulse = 1F + 0.045F * (float)Math.sin(time * 0.8);
                sphere(vertices, stack, center, arc.radius * 0.42F * pulse, 0.96F, 0.8F, 0.95F);
                sphere(vertices, stack, center, arc.radius * 0.73F * pulse, 0.22F, 0.12F, 0.5F);
                sphere(vertices, stack, center, arc.radius * 1.1F * pulse, 0.065F, 0.05F, 0.3F);
                for (int band = 0; band < 5; band++) {
                    Vec3 previous = null;
                    for (int segment = 0; segment <= 14; segment++) {
                        double angle = segment * Math.PI * 1.65 / 14 + time * (band % 2 == 0 ? 0.17 : -0.13) + band * 1.7;
                        double radius = arc.radius * (0.85 + 0.09 * Math.sin(segment * 2.4 + Math.floor(time / 2) + band));
                        Vec3 point = new Vec3(Math.cos(angle) * radius, Math.sin(angle) * radius, 0)
                            .xRot(band * 0.83F).yRot(band * 1.31F).add(center);
                        if (previous != null) {
                            line(vertices, stack, previous, point, 0.047F, 0.08F, 0.35F, 1F, 0.3F);
                            line(vertices, stack, previous, point, 0.012F, 0.65F, 0.9F, 1F, 0.95F);
                        }
                        previous = point;
                    }
                }
                continue;
            }
            Vec3 axis = arc.end.subtract(arc.origin).normalize();
            Vec3 side = axis.cross(new Vec3(0, 1, 0)).normalize();
            if (side.lengthSqr() < 0.01) side = new Vec3(1, 0, 0);
            Vec3 up = side.cross(axis).normalize();
            for (int strand = 0; strand < (arc.kind == 5 ? 4 : 1); strand++) {
                double angle = strand * Math.PI / 2;
                Vec3 offset = arc.kind == 5 ? side.scale(Math.cos(angle) * 0.45).add(up.scale(Math.sin(angle) * 0.65)) : Vec3.ZERO;
                Vec3 previous = arc.origin.add(offset);
                for (int segment = 1; segment <= 3; segment++) {
                    double fraction = segment / 3.0;
                    Vec3 next = arc.origin.lerp(arc.end, fraction).add(offset);
                    double bend = arc.kind == 5 ? 0.3 : 0.08;
                    if (segment < 3) next = next.add(side.scale((random.nextDouble() - 0.5) * bend)).add(up.scale((random.nextDouble() - 0.5) * bend));
                    line(vertices, stack, previous.subtract(camera), next.subtract(camera), 0.055F, 0.1F, 0.4F, 1F, alpha * 0.4F);
                    line(vertices, stack, previous.subtract(camera), next.subtract(camera), 0.015F, 0.65F, 0.85F, 1F, alpha);
                    previous = next;
                }
            }
        }
        buffers.endBatch(type);
    }

    private static void sphere(VertexConsumer vertices, PoseStack stack, Vec3 center, float radius, float alpha, float red, float green) {
        for (int latitude = 0; latitude < 10; latitude++) {
            for (int longitude = 0; longitude < 16; longitude++) {
                for (int corner : new int[]{0, 3, 2, 1}) {
                    double vertical = Math.PI * ((latitude + (corner >= 2 ? 1 : 0)) / 10.0 - 0.5);
                    double horizontal = Math.PI * 2 * (longitude + (corner == 1 || corner == 2 ? 1 : 0)) / 16.0;
                    Vec3 normal = new Vec3(Math.cos(vertical) * Math.cos(horizontal), Math.sin(vertical), Math.cos(vertical) * Math.sin(horizontal));
                    Vec3 point = center.add(normal.scale(radius));
                    float shine = (float)(0.5 + 0.5 * normal.y);
                    vertices.vertex(stack.last().pose(), (float)point.x, (float)point.y, (float)point.z)
                        .color(red + shine * (1F - red) * 0.15F, green + shine * (1F - green) * 0.15F, 1F, alpha).uv(0, 0)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880)
                        .normal(stack.last().normal(), (float)normal.x, (float)normal.y, (float)normal.z).endVertex();
                }
            }
        }
    }

    private static void line(VertexConsumer vertices, PoseStack stack, Vec3 start, Vec3 end, float width, float red, float green, float blue, float alpha) {
        Vec3 side = end.subtract(start).cross(start.add(end).scale(0.5)).normalize().scale(width);
        if (side.lengthSqr() < 0.000001) side = new Vec3(width, 0, 0);
        for (Vec3 point : new Vec3[]{start.add(side), end.add(side), end.subtract(side), start.subtract(side)}) {
            vertices.vertex(stack.last().pose(), (float)point.x, (float)point.y, (float)point.z).color(red, green, blue, alpha).uv(0, 0)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(stack.last().normal(), 0, 1, 0).endVertex();
        }
    }
}
