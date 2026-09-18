package dev.sasuke;

import java.util.HashMap;
import java.util.Map;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class EffectGeometry extends RenderType {
    private static final Map<String, RenderType> TYPES = new HashMap<>();

    private EffectGeometry(String name, VertexFormat format, VertexFormat.Mode mode, int size,
            boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static RenderType type(String texture) {
        return TYPES.computeIfAbsent(texture, name -> create("sasuke_" + name, DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS, 65536, false, true, CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                .setTextureState(new TextureStateShard(SasukeMod.id("textures/particle/" + name + ".png"), true, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                .setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(false)));
    }

    public static float clamp(float value) { return Math.max(0, Math.min(1, value)); }

    public static void vertex(VertexConsumer output, PoseStack stack, Vec3 point, float textureU, float textureV,
            int color, float alpha) {
        output.vertex(stack.last().pose(), (float)point.x, (float)point.y, (float)point.z)
            .color((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F, clamp(alpha))
            .uv(textureU, textureV).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880)
            .normal(stack.last().normal(), 0, 1, 0).endVertex();
    }

    public static void line(VertexConsumer output, PoseStack stack, Vec3 start, Vec3 end,
            float startWidth, float endWidth, int color, float alpha) {
        Vec3 side = end.subtract(start).cross(start.add(end).scale(0.5)).normalize();
        if (side.lengthSqr() < 0.001) side = end.subtract(start).cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
        vertex(output, stack, start.add(side.scale(startWidth)), 0, 0, color, alpha);
        vertex(output, stack, end.add(side.scale(endWidth)), 1, 0, color, alpha);
        vertex(output, stack, end.subtract(side.scale(endWidth)), 1, 1, color, alpha);
        vertex(output, stack, start.subtract(side.scale(startWidth)), 0, 1, color, alpha);
    }

    public static void bolt(VertexConsumer output, PoseStack stack, Vec3[] points, float width, float alpha) {
        for (int layer = 0; layer < 3; layer++) {
            float thickness = width * (layer == 0 ? 3.6F : layer == 1 ? 1.7F : 0.65F);
            int color = layer == 0 ? 0x148DFF : layer == 1 ? 0x53EDFF : 0xF4FFFF;
            float opacity = alpha * (layer == 0 ? 0.15F : layer == 1 ? 0.52F : 1F);
            for (int index = 1; index < points.length; index++) {
                float taper = 1F - 0.6F * index / (points.length - 1F);
                line(output, stack, points[index - 1], points[index], thickness * (taper + 0.06F), thickness * taper, color, opacity);
            }
        }
    }

    public static void billboard(VertexConsumer output, PoseStack stack, Camera camera, Vec3 center,
            float width, float height, float rotation, int color, float alpha, int frame, int frames) {
        Vector3f horizontal = new Vector3f(1, 0, 0).rotateZ(rotation).rotate(camera.rotation());
        Vector3f vertical = new Vector3f(0, 1, 0).rotateZ(rotation).rotate(camera.rotation());
        Vec3 side = new Vec3(horizontal.x, horizontal.y, horizontal.z).scale(width * 0.5);
        Vec3 up = new Vec3(vertical.x, vertical.y, vertical.z).scale(height * 0.5);
        float left = (frame + 0.002F) / frames;
        float right = (frame + 0.998F) / frames;
        vertex(output, stack, center.subtract(side).add(up), left, 0, color, alpha);
        vertex(output, stack, center.add(side).add(up), right, 0, color, alpha);
        vertex(output, stack, center.add(side).subtract(up), right, 1, color, alpha);
        vertex(output, stack, center.subtract(side).subtract(up), left, 1, color, alpha);
    }

    public static void ring(VertexConsumer output, PoseStack stack, Vec3 center, float radius, float thickness,
            int color, float alpha, float start, float sweep) {
        for (int segment = 0; segment < 72; segment++) {
            double angle = start + sweep * segment / 72;
            double nextAngle = start + sweep * (segment + 1) / 72;
            Vec3 first = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 second = new Vec3(Math.cos(nextAngle), 0, Math.sin(nextAngle));
            vertex(output, stack, center.add(first.scale(radius)), 0, 0, color, alpha);
            vertex(output, stack, center.add(second.scale(radius)), 1, 0, color, alpha);
            vertex(output, stack, center.add(second.scale(Math.max(0, radius - thickness))), 1, 1, color, 0);
            vertex(output, stack, center.add(first.scale(Math.max(0, radius - thickness))), 0, 1, color, 0);
        }
    }

    public static void fan(VertexConsumer output, PoseStack stack, Vec3 pivot, Vec3[] edge, float alpha) {
        for (int segment = 0; segment < edge.length - 1; segment++) {
            for (int band = 0; band < 6; band++) {
                float inner = band / 6F;
                float outer = (band + 1) / 6F;
                fanVertex(output, stack, pivot, edge[segment], inner, segment / (edge.length - 1F), alpha);
                fanVertex(output, stack, pivot, edge[segment + 1], inner, (segment + 1F) / (edge.length - 1), alpha);
                fanVertex(output, stack, pivot, edge[segment + 1], outer, (segment + 1F) / (edge.length - 1), alpha);
                fanVertex(output, stack, pivot, edge[segment], outer, segment / (edge.length - 1F), alpha);
            }
        }
    }

    private static void fanVertex(VertexConsumer output, PoseStack stack, Vec3 pivot, Vec3 edge,
            float radius, float progress, float alpha) {
        double angle = -2.45 + progress * 4.9;
        vertex(output, stack, pivot.lerp(edge, radius), 0.5F + 0.47F * radius * (float)Math.cos(angle),
            0.5F + 0.47F * radius * (float)Math.sin(angle), 0xFFFFFF, alpha);
    }
}
