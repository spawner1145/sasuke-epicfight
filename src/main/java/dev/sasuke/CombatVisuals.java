package dev.sasuke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = SasukeMod.ID, value = Dist.CLIENT)
public final class CombatVisuals {
    private static final Vec3 BLADE_TIP = new Vec3(-0.005, 0.1474, -1.70);
    private static final Map<Integer, Boolean> WAS_SHEATHING = new HashMap<>();

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var buffers = mc.renderBuffers().bufferSource();
        for (var entity : mc.level.players()) {
            if (!entity.isAlive() || entity.isSpectator() || !entity.getMainHandItem().is(SasukeMod.KUSANAGI.get())
                    || entity.distanceToSqr(mc.player) > 4096) continue;
            var patch = EpicFightCapabilities.getEntityPatch(entity, LivingEntityPatch.class);
            if (patch == null) continue;
            var player = patch.getAnimator().getPlayerFor(null);
            var accessor = player.getRealAnimation();
            var currentAnimation = player.getAnimation().get();
            String action = null;
            for (String name : new String[]{"1a", "2a", "3a", "4a1", "4a2", "4a3", "draw_to_side", "dash_spin_slash", "sheathe_flourish", "perfect_parry"}) {
                if (accessor.equals(SasukeAnimations.player(name))) { action = name; break; }
            }
            if (action == null && currentAnimation.getRegistryName() != null) {
                String path = currentAnimation.getRegistryName().getPath();
                for (String name : new String[]{"1a", "2a", "3a", "4a1", "4a2", "4a3", "draw_to_side", "dash_spin_slash", "sheathe_flourish", "perfect_parry"}) {
                    if (path.endsWith("player/" + name)) { action = name; break; }
                }
            }
            float time = player.getPrevElapsedTime() + (player.getElapsedTime() - player.getPrevElapsedTime()) * event.getPartialTick();
            Vec3 origin = entity.getPosition(event.getPartialTick()).subtract(event.getCamera().getPosition());
            OpenMatrix4f model = OpenMatrix4f.createTranslation((float)origin.x, (float)origin.y, (float)origin.z)
                .rotateDeg(180, Vec3f.Y_AXIS).mulBack(patch.getModelMatrix(event.getPartialTick()));
            StaticAnimation animation = accessor.get();
            int entityId = entity.getId();
            boolean wasSheathing = WAS_SHEATHING.getOrDefault(entityId, false);
            if ("sheathe_flourish".equals(action)) {
                WAS_SHEATHING.put(entityId, true);
            } else if (wasSheathing) {
                WAS_SHEATHING.put(entityId, false);
            }
            if (action == null) {
                continue;
            }
            switch (action) {
                case "2a" -> kick(event, buffers, patch, animation, model, time, origin, entity.yBodyRot);
                case "3a" -> thrust(event, buffers, patch, animation, model, time, 0.065F, 0.205F, false);
                case "4a1" -> thrust(event, buffers, patch, animation, model, time, 0.15F, 0.425F, true);
                case "4a3" -> {
                    float alpha = window(time, 0.20F, 0.66F, 0.10F);
                    if (alpha > 0) ElectricVisuals.body(event, origin.add(0, 0.9, 0), entity.getId(), time * 20, alpha);
                }
                case "dash_spin_slash" -> spin(event, buffers, patch, animation, model, time, origin, BlackFlameVisuals.empowered(entity.getId()));
                case "draw_to_side" -> bladeTrail(event, buffers, patch, animation, model, time, 0.0F, 0.85F,
                    window(time, 0.0F, 0.85F, 0.10F), 0.28F, false);
                case "sheathe_flourish" -> sheathe(event, buffers, patch, animation, model, time);
                case "perfect_parry" -> {
                    float alpha = window(time, 0.02F, 0.10F, 0.15F);
                    if (alpha > 0) glint(event, buffers, joint(patch, animation, model, "Tool_R", new Vec3(0, 0, -0.4), time), 1.7F, alpha);
                }
                default -> { }
            }
        }
        for (String texture : new String[]{"slash_trail", "black_slash_trail", "kick_air", "white", "glint", "ring", "halo", "dust"}) {
            buffers.endBatch(EffectGeometry.type(texture));
        }
    }

    private static float window(float time, float start, float end, float fade) {
        if (time < start || time >= end + fade) return 0;
        return EffectGeometry.clamp((time - start) / 0.025F) * EffectGeometry.clamp((end + fade - time) / fade);
    }

    private static Vec3 joint(LivingEntityPatch<?> patch, StaticAnimation animation, OpenMatrix4f model,
            String name, Vec3 offset, float time) {
        var pose = animation.getPoseByTime(patch, time, 1);
        var transform = patch.getArmature().getBoundTransformFor(pose, patch.getArmature().searchJointByName(name)).mulFront(model);
        return OpenMatrix4f.transform(transform, offset);
    }

    private static void kick(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers,
            LivingEntityPatch<?> patch, StaticAnimation animation, OpenMatrix4f model, float time, Vec3 origin, float yaw) {
        float alpha = window(time, 0.15F, 0.34F, 0.085F);
        if (alpha <= 0) return;
        float head = Math.min(time, 0.34F);
        Vec3 rightFoot = joint(patch, animation, model, "Leg_R", new Vec3(0, 0.38, 0), head);
        Vec3 leftFoot = joint(patch, animation, model, "Leg_L", new Vec3(0, 0.38, 0), head);
        String leg = rightFoot.y > leftFoot.y ? "Leg_R" : "Leg_L";
        Vec3 foot = leg.equals("Leg_R") ? rightFoot : leftFoot;
        Vec3 hip = joint(patch, animation, model, "Root", Vec3.ZERO, head);
        Vec3 direction = foot.subtract(hip).normalize();
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 side = direction.cross(up).normalize();
        if (side.lengthSqr() < 0.1) side = new Vec3(1, 0, 0).yRot((float)Math.toRadians(-yaw));
        Vec3 vertical = side.cross(direction).normalize();
        Vec3 center = foot.add(direction.scale(0.14));
        Vec3[] edge = new Vec3[41];
        float growth = EffectGeometry.clamp((time - 0.15F) / 0.10F);
        for (int sample = 0; sample < edge.length; sample++) {
            double angle = -2.15 + 4.3 * sample / (edge.length - 1);
            edge[sample] = center.add(vertical.scale(Math.cos(angle) * (0.5 + growth * 0.4)))
                .add(side.scale(Math.sin(angle) * (0.35 + growth * 0.3)));
        }
        EffectGeometry.fan(buffers.getBuffer(EffectGeometry.type("kick_air")), event.getPoseStack(), center, edge, alpha * 1.25F);
        var output = buffers.getBuffer(EffectGeometry.type("white"));
        for (int streak = 0; streak < 11; streak++) {
            Vec3 offset = vertical.scale((streak - 2) * 0.15);
            EffectGeometry.line(output, event.getPoseStack(), foot.subtract(direction.scale(0.75)).add(offset),
                foot.add(direction.scale(0.5)).add(offset.scale(0.3)), 0.025F + (streak % 3) * 0.018F, 0, 0xEDF6FF, alpha * 0.72F);
        }
        EffectGeometry.billboard(buffers.getBuffer(EffectGeometry.type("glint")), event.getPoseStack(), event.getCamera(),
            foot.add(direction.scale(0.25)), 1.7F, 0.85F, 0, 0xDDF8FF, alpha * 0.9F, 0, 1);
        dust(event, buffers, origin, time - 0.15F, alpha, 0.8F);
    }

    private static void thrust(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers,
            LivingEntityPatch<?> patch, StaticAnimation animation, OpenMatrix4f model, float time,
            float start, float end, boolean electric) {
        float alpha = window(time, start, end, 0.065F);
        if (alpha <= 0) return;
        float sample = Math.min(time, end);
        Vec3 grip = joint(patch, animation, model, "Tool_R", Vec3.ZERO, sample);
        Vec3 tip = joint(patch, animation, model, "Tool_R", BLADE_TIP, sample);
        Vec3 direction = tip.subtract(grip).normalize();
        var output = buffers.getBuffer(EffectGeometry.type("white"));
        Vec3 point = tip.add(direction.scale(electric ? 1.5 : 0.9));
        EffectGeometry.line(output, event.getPoseStack(), grip.subtract(direction.scale(0.7)), point, electric ? 0.29F : 0.18F, 0, 0x4BDFFF, alpha * 0.25F);
        EffectGeometry.line(output, event.getPoseStack(), grip, point, electric ? 0.18F : 0.105F, 0, 0xEDFFFF, alpha);
        EffectGeometry.line(output, event.getPoseStack(), grip.subtract(direction.scale(1.8)), point, 0.015F, 0, 0xFFFFFF, alpha);
        if (electric) ElectricVisuals.lance(event, grip, point, time * 20, alpha);
        var glow = buffers.getBuffer(EffectGeometry.type("halo"));
        EffectGeometry.billboard(glow, event.getPoseStack(), event.getCamera(), tip, electric ? 2.4F : 1.4F,
            electric ? 2.4F : 1.4F, 0, 0x44DFFF, alpha * 0.6F, 0, 1);
    }

    private static void spin(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers,
            LivingEntityPatch<?> patch, StaticAnimation animation, OpenMatrix4f model, float time, Vec3 origin, boolean black) {
        float alpha = window(time, 0.30F, 0.94F, 0.13F);
        if (alpha <= 0) return;
        bladeTrail(event, buffers, patch, animation, model, time, 0.30F, 0.94F, alpha, 0.28F, black);
        Vec3 pivot = joint(patch, animation, model, "Chest", Vec3.ZERO, Math.min(time, 0.94F));
        dust(event, buffers, origin, time - 0.3F, alpha, 2.7F);
        if (black) BlackFlameVisuals.spinEmbers(event, pivot, time, alpha);
    }

    private static void bladeTrail(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers,
            LivingEntityPatch<?> patch, StaticAnimation animation, OpenMatrix4f model, float time,
            float start, float end, float alpha, float history, boolean black) {
        float head = Math.min(time, end);
        float tail = Math.max(start, head - history);
        Vec3[] starts = new Vec3[28];
        Vec3[] ends = new Vec3[28];
        for (int index = 0; index < starts.length; index++) {
            float sample = tail + (head - tail) * index / (starts.length - 1F);
            starts[index] = joint(patch, animation, model, "Tool_R", new Vec3(-0.005, -0.0177, 0.0898), sample);
            ends[index] = joint(patch, animation, model, "Tool_R", BLADE_TIP, sample);
        }
        var trail = buffers.getBuffer(EffectGeometry.type(black ? "black_slash_trail" : "slash_trail"));
        for (int index = 1; index < starts.length; index++) {
            float from = (index - 1F) / (starts.length - 1F);
            float to = index / (starts.length - 1F);
            float fade = alpha * (0.22F + 0.78F * to);
            EffectGeometry.vertex(trail, event.getPoseStack(), starts[index - 1], from, 1, 0xB8F7FF, fade);
            EffectGeometry.vertex(trail, event.getPoseStack(), ends[index - 1], from, 0, 0xFFFFFF, fade);
            EffectGeometry.vertex(trail, event.getPoseStack(), ends[index], to, 0, 0xFFFFFF, fade);
            EffectGeometry.vertex(trail, event.getPoseStack(), starts[index], to, 1, 0xB8F7FF, fade);
        }
    }

    private static void sheathe(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers,
            LivingEntityPatch<?> patch, StaticAnimation animation, OpenMatrix4f model, float time) {
        float trailAlpha = window(time, 0.0F, 97F / 60F, 0.08F);
        if (trailAlpha > 0) bladeTrail(event, buffers, patch, animation, model, time, 0.0F, 97F / 60F, trailAlpha, 0.34F, false);
        float flick = window(time, 0.37F, 0.54F, 0.08F);
        if (flick > 0) {
            Vec3 grip = joint(patch, animation, model, "Tool_R", Vec3.ZERO, time);
            Vec3 tip = joint(patch, animation, model, "Tool_R", BLADE_TIP, time);
            var output = buffers.getBuffer(EffectGeometry.type("white"));
            Vec3 side = tip.subtract(grip).cross(new Vec3(0, 1, 0)).normalize();
            for (int index = 0; index < 3; index++) {
                Vec3 start = tip.add(side.scale((index - 1) * 0.10));
                EffectGeometry.line(output, event.getPoseStack(), start, start.add(0, 0.32 + index * 0.09, 0), 0.015F, 0, 0xF4F6FF, flick);
            }
        }
        // The sheath flash belongs to the final settling frames, immediately before idle.
        float settleStart = 97F / 60F;
        float settleEnd = 105F / 60F;
        float settle = time < settleStart || time >= settleEnd ? 0
            : EffectGeometry.clamp((time - settleStart) / 0.04F)
                * EffectGeometry.clamp((settleEnd - time) / (settleEnd - settleStart));
        if (settle > 0) {
            Vec3 mouth = joint(patch, animation, model, "Root", new Vec3(0.36, 0.58, 0.18), time);
            glint(event, buffers, mouth, 1.8F + settle * 0.35F, settle);
            EffectGeometry.billboard(buffers.getBuffer(EffectGeometry.type("ring")), event.getPoseStack(), event.getCamera(), mouth,
                0.55F + settle * 0.22F, 0.55F + settle * 0.22F, 0, 0xF5E9FF, settle * 0.72F, 0, 1);
        }
    }

    private static void glint(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers, Vec3 point, float size, float alpha) {
        EffectGeometry.billboard(buffers.getBuffer(EffectGeometry.type("glint")), event.getPoseStack(), event.getCamera(),
            point, size, size, 0, 0xFFFFFF, alpha, 0, 1);
    }

    private static void dust(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers,
            Vec3 origin, float age, float alpha, float radius) {
        var output = buffers.getBuffer(EffectGeometry.type("dust"));
        for (int index = 0; index < 7; index++) {
            double angle = index * 2.39996;
            Vec3 point = origin.add(Math.cos(angle) * radius * (0.6 + age), 0.12 + age * 0.2, Math.sin(angle) * radius * (0.6 + age));
            EffectGeometry.billboard(output, event.getPoseStack(), event.getCamera(), point, 0.6F + age, 0.35F + age * 0.5F,
                index, 0xECE4DC, alpha * 0.55F, 0, 1);
        }
    }
}
