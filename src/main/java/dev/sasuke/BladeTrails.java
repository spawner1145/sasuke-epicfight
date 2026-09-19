package dev.sasuke;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/** Shared rendered-pose trail for slashes, drawing, spinning and sheathing. */
final class BladeTrails {
    private static final Vec3 ROOT = new Vec3(-0.005, -0.0177, 0.0898);
    private static final Vec3 TIP = new Vec3(-0.005, 0.1474, -1.70);
    private static final Map<Integer, State> STATES = new HashMap<>();
    private static Object level;

    private record Window(float start, float end, float history, float fade) { }
    private record Edge(Vec3 root, Vec3 tip) { }
    private static final class State {
        final Object owner;
        final String action;
        final BladeTrailHistory<Edge> history = new BladeTrailHistory<>();
        double clock = Double.NaN;
        float animationTime;
        boolean link;

        State(Object owner, String action) { this.owner = owner; this.action = action; }
    }

    static void beginFrame(Object currentLevel) {
        if (level != currentLevel || currentLevel == null) {
            STATES.clear();
            level = currentLevel;
        }
    }

    static void retainPlayers(java.util.Set<Integer> visiblePlayers) {
        STATES.keySet().retainAll(visiblePlayers);
    }

    private static Window window(String action) {
        if (action == null) return null;
        return switch (action) {
            case "1a", "3a", "4a1" -> new Window(0, SasukeAnimations.duration(action) / 60F, 0.20F, 0.10F);
            case "draw_to_side" -> new Window(0, 0.85F, 0.28F, 0.10F);
            case "dash_spin_slash" -> new Window(0.30F, 0.94F, 0.28F, 0.13F);
            case "sheathe_flourish" -> new Window(0, 97F / 60F, 0.34F, 0.08F);
            case "basic_sheathe" -> new Window(0, (97F - BasicSheatheAnimation.SOURCE_START_FRAME) / 60F, 0.34F, 0.08F);
            default -> null;
        };
    }

    static void render(RenderLevelStageEvent event, MultiBufferSource.BufferSource buffers,
            LivingEntityPatch<?> patch, String action, float time, boolean black) {
        var entity = patch.getOriginal();
        int id = entity.getId();
        Window window = window(action);
        if (window == null) {
            STATES.remove(id);
            return;
        }
        var player = patch.getAnimator().getPlayerFor(null);
        boolean link = player.getAnimation().get().isLinkAnimation();
        double clock = entity.tickCount + (double)event.getPartialTick();
        State state = STATES.get(id);
        if (state == null || state.owner != entity || !state.action.equals(action)
                || clock < state.clock || clock - state.clock > 1.5
                || (time + 0.00001F < state.animationTime && !(state.link && !link))
                || (link && !state.link)) {
            state = new State(entity, action);
            STATES.put(id, state);
        }

        // Only sample the current tick: old poses cannot be reconstructed using
        // the current model transform. Sub-tick poses match Avalon RenderMeshItem.
        double first = Math.max(entity.tickCount, Double.isNaN(state.clock) ? entity.tickCount : state.clock);
        int steps = Math.max(1, (int)Math.ceil((clock - first) * 12));
        for (int i = 0; i <= steps; i++) {
            double sampleClock = first + (clock - first) * i / steps;
            float partial = (float)(sampleClock - entity.tickCount);
            float sampleTime = player.getPrevElapsedTime()
                + (player.getElapsedTime() - player.getPrevElapsedTime()) * partial;
            if (sampleTime < window.start || sampleTime > window.end) continue;
            state.history.add(sampleClock, sample(patch, partial));
        }
        state.clock = clock;
        state.animationTime = time;
        state.link = link;
        state.history.trim(clock - window.history * 20);
        var edges = state.history.samples();
        if (edges.size() < 2) return;
        float alpha = time <= window.end ? 1 : EffectGeometry.clamp((window.end + window.fade - time) / window.fade);
        if (alpha <= 0) return;
        double firstTick = edges.get(0).tick();
        double span = edges.get(edges.size() - 1).tick() - firstTick;
        if (span <= 0) return;
        Vec3 camera = event.getCamera().getPosition();
        var output = buffers.getBuffer(EffectGeometry.type(black ? "black_slash_trail" : "slash_trail"));
        for (int i = 1; i < edges.size(); i++) {
            var before = edges.get(i - 1);
            var after = edges.get(i);
            float from = (float)((before.tick() - firstTick) / span);
            float to = (float)((after.tick() - firstTick) / span);
            EffectGeometry.vertex(output, event.getPoseStack(), before.edge().root.subtract(camera), from, 1, 0xB8F7FF, alpha * from);
            EffectGeometry.vertex(output, event.getPoseStack(), before.edge().tip.subtract(camera), from, 0, 0xFFFFFF, alpha * from);
            EffectGeometry.vertex(output, event.getPoseStack(), after.edge().tip.subtract(camera), to, 0, 0xFFFFFF, alpha * to);
            EffectGeometry.vertex(output, event.getPoseStack(), after.edge().root.subtract(camera), to, 1, 0xB8F7FF, alpha * to);
        }
    }

    private static Edge sample(LivingEntityPatch<?> patch, float partial) {
        var pose = patch.getAnimator().getPose(partial);
        // Keep world translation in doubles rather than baking large coordinates
        // into a float matrix (which makes distant trails visibly jitter).
        var model = new OpenMatrix4f().rotateDeg(180, Vec3f.Y_AXIS).mulBack(patch.getModelMatrix(partial));
        var transform = patch.getArmature().getBoundTransformFor(pose,
            patch.getArmature().searchJointByName("Tool_R")).mulFront(model);
        Vec3 origin = patch.getOriginal().getPosition(partial);
        return new Edge(OpenMatrix4f.transform(transform, ROOT).add(origin),
            OpenMatrix4f.transform(transform, TIP).add(origin));
    }

    static Vec3 bladeRoot(LivingEntityPatch<?> patch, float partial) {
        return sample(patch, partial).root;
    }

    static Vec3 bladeTip(LivingEntityPatch<?> patch, float partial) {
        return sample(patch, partial).tip;
    }

    static Vec3 sheathMouth(LivingEntityPatch<?> patch, float partial) {
        // Kusanagi's scabbard is rigidly weighted to Torso (joint 7).
        // Center of its open +X rim, converted from mesh bind space to Torso local.
        Vec3 mouth = new Vec3(0.408, -0.021, 0.161);
        var pose = patch.getAnimator().getPose(partial);
        var model = new OpenMatrix4f().rotateDeg(180, Vec3f.Y_AXIS).mulBack(patch.getModelMatrix(partial));
        var transform = patch.getArmature().getBoundTransformFor(pose,
            patch.getArmature().searchJointByName("Torso")).mulFront(model);
        return OpenMatrix4f.transform(transform, mouth).add(patch.getOriginal().getPosition(partial));
    }
}
