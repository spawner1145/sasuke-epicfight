package dev.sasuke;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;

public final class PlantedSwordRenderer extends EntityRenderer<Display.ItemDisplay> {
    private final float[] positions;
    private final float[] uvs;
    private final int[] indices;
    private final boolean[] blade;

    public PlantedSwordRenderer(EntityRendererProvider.Context context) {
        super(context);
        try (var reader = Minecraft.getInstance().getResourceManager().openAsReader(SasukeMod.id("animmodels/item/kusanagi.json"))) {
            var vertices = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("vertices");
            var gson = new com.google.gson.Gson();
            positions = gson.fromJson(vertices.getAsJsonObject("positions").get("array"), float[].class);
            uvs = gson.fromJson(vertices.getAsJsonObject("uvs").get("array"), float[].class);
            indices = gson.fromJson(vertices.getAsJsonObject("parts").getAsJsonObject("noGroups").get("array"), int[].class);
            int[] counts = gson.fromJson(vertices.getAsJsonObject("vcounts").get("array"), int[].class);
            int[] weights = gson.fromJson(vertices.getAsJsonObject("vindices").get("array"), int[].class);
            blade = new boolean[counts.length];
            int offset = 0;
            for (int vertex = 0; vertex < counts.length; vertex++) {
                blade[vertex] = weights[offset] == 13;
                offset += counts[vertex] * 2;
            }
        } catch (java.io.IOException exception) { throw new IllegalStateException("Cannot load planted Kusanagi mesh", exception); }
    }

    @Override
    public void render(Display.ItemDisplay entity, float yaw, float partialTick, PoseStack stack, MultiBufferSource buffers, int light) {
        stack.pushPose();
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw));
        var output = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity)));
        for (int face = 0; face < indices.length; face += 9) {
            if (!blade[indices[face]] || !blade[indices[face + 3]] || !blade[indices[face + 6]]) continue;
            for (int corner : new int[]{0, 3, 6, 6}) {
                int position = indices[face + corner] * 3;
                int uv = indices[face + corner + 1] * 2;
                output.vertex(stack.last().pose(), 0.37F - positions[position], 0.85F - positions[position + 1], positions[position + 2] - 0.81F)
                    .color(255, 255, 255, 255).uv(uvs[uv], uvs[uv + 1]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(stack.last().normal(), 0, 0, 1).endVertex();
            }
        }
        stack.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(Display.ItemDisplay entity) { return SasukeMod.id("textures/item/kusanagi.png"); }
}
