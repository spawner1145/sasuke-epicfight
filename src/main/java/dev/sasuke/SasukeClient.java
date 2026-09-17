package dev.sasuke;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import com.merlin204.avalon.entity.client.renderer.EmptyRenderer;
import com.merlin204.avalon.entity.client.renderer.patch.entity.AvalonVFXRendererPatch;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;

@Mod.EventBusSubscriber(modid = SasukeMod.ID, value = Dist.CLIENT)
public final class SasukeClient {
    public static final KeyMapping FIRST = new KeyMapping("key.sasuke_epicfight.first", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.sasuke_epicfight");
    public static final KeyMapping SECOND = new KeyMapping("key.sasuke_epicfight.second", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.sasuke_epicfight");
    private static SasukeNetwork.Status status = new SasukeNetwork.Status(0, 0, 0);
    private static final List<Spike> SPIKES = new ArrayList<>();
    private static Object lastLevel;
    private static float comboYaw;
    private static float comboPitch;
    private record Spike(Vec3 position, float radius, int seed, long born, boolean radial) {}

    @Mod.EventBusSubscriber(modid = SasukeMod.ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(FIRST); event.register(SECOND); }
        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(SasukeMod.SUSANOO.get(), EmptyRenderer::new);
            event.registerEntityRenderer(SasukeMod.PLANTED_SWORD.get(), PlantedSwordRenderer::new);
        }
        @SubscribeEvent
        public static void patches(PatchedRenderersEvent.Add event) {
            event.addPatchedEntityRenderer(SasukeMod.SUSANOO.get(), type -> new AvalonVFXRendererPatch(event.getContext(), type));
        }
    }

    public static void status(SasukeNetwork.Status message) {
        var player = Minecraft.getInstance().player;
        if (message.phase() == 8 && status.phase() != 8 && player != null) { comboYaw = player.getYRot(); comboPitch = player.getXRot(); }
        status = message;
    }

    @SubscribeEvent
    public static void lockFacing(TickEvent.RenderTickEvent event) {
        var player = Minecraft.getInstance().player;
        if ((status.phase() == 8 || status.phase() == 9) && player != null) { player.setYRot(comboYaw); player.setXRot(comboPitch); player.setYHeadRot(comboYaw); player.setYBodyRot(comboYaw); }
    }
    public static void burst(SasukeNetwork.Burst message) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            if (message.radius() > 0) SPIKES.add(new Spike(message.position(), message.radius(), message.seed(), level.getGameTime(), message.radial()));
            SasukeEffects.burst(message);
        }
    }

    private static boolean equipped() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem().is(SasukeMod.KUSANAGI.get());
    }

    private static void send(int key) {
        var mc = Minecraft.getInstance();
        int forward = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        int left = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        SasukeNetwork.CHANNEL.sendToServer(new SasukeNetwork.Input(key, forward, left));
    }

    @SubscribeEvent
    public static void input(InputEvent.InteractionKeyMappingTriggered event) {
        if (equipped() && status.phase() == 4) send(4);
        if (equipped() && event.isAttack() && (status.phase() == 1 || status.phase() == 2)) {
            event.setCanceled(true);
            event.setSwingHand(false);
            send(3);
        }
    }

    @SubscribeEvent
    public static void movement(MovementInputUpdateEvent event) {
        if (equipped() && status.phase() == 4 && (event.getInput().forwardImpulse != 0 || event.getInput().leftImpulse != 0 || event.getInput().jumping || event.getInput().shiftKeyDown)) send(4);
        if (equipped() && status.phase() >= 5 && status.phase() != 6) {
            var input = event.getInput();
            input.forwardImpulse = 0;
            input.leftImpulse = 0;
            input.jumping = false;
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (lastLevel != mc.level) {
            lastLevel = mc.level;
            SPIKES.clear();
            SasukeEffects.clear();
            status = new SasukeNetwork.Status(0, 0, 0);
        }
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        SasukeEffects.tick();
        var attackKey = mc.options.keyAttack.getKey();
        boolean attackHeld = attackKey.getType() == InputConstants.Type.MOUSE
            ? GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), attackKey.getValue()) == GLFW.GLFW_PRESS
            : InputConstants.isKeyDown(mc.getWindow().getWindow(), attackKey.getValue());
        if (equipped() && mc.screen == null && mc.isWindowActive() && attackHeld && (status.phase() == 0 || status.phase() == 6) && mc.player.tickCount % 2 == 0) send(5);
        while (FIRST.consumeClick()) if (equipped() && mc.screen == null) send(1);
        while (SECOND.consumeClick()) if (equipped() && mc.screen == null) send(2);
        status = new SasukeNetwork.Status(status.phase(), Math.max(0, status.firstCooldown() - 1), Math.max(0, status.secondCooldown() - 1));
        SPIKES.removeIf(spike -> mc.level.getGameTime() - spike.born > 25);
    }

    @SubscribeEvent
    public static void hud(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().toString().equals("minecraft:hotbar") || !equipped()) return;
        var mc = Minecraft.getInstance();
        String first = status.firstCooldown() == 0 ? "就绪" : String.format(java.util.Locale.ROOT, "%.1fs", status.firstCooldown() / 20F);
        String second = status.phase() == 6 ? "二段就绪 · 方向键 + " + SECOND.getTranslatedKeyMessage().getString() : status.secondCooldown() == 0 ? "就绪" : String.format(java.util.Locale.ROOT, "%.1fs", status.secondCooldown() / 20F);
        String text = FIRST.getTranslatedKeyMessage().getString() + " 须佐 " + first + "    " + SECOND.getTranslatedKeyMessage().getString() + " 天照 " + second;
        event.getGuiGraphics().drawCenteredString(mc.font, text, event.getWindow().getGuiScaledWidth() / 2, event.getWindow().getGuiScaledHeight() - 66, 0xD9B5FF);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || SPIKES.isEmpty()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        PoseStack stack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucent(SasukeMod.id("textures/particle/white.png"));
        VertexConsumer vertices = buffers.getBuffer(type);
        for (Spike spike : SPIKES) {
            float age = mc.level.getGameTime() - spike.born + event.getPartialTick();
            float rise = (float)(1 - Math.pow(1 - Math.min(1F, age / 4F), 3)) * Math.min(1F, (26F - age) / 12F);
            if (rise <= 0) continue;
            Random random = new Random(spike.seed);
            stack.pushPose();
            stack.translate(spike.position.x - camera.x, spike.position.y - camera.y, spike.position.z - camera.z);
            int count = spike.radial ? 32 : spike.radius < 1 ? 7 : 27;
            for (int index = 0; index < count; index++) {
                stack.pushPose();
                double angle = random.nextDouble() * Math.PI * 2;
                float distance = index == 0 ? 0 : random.nextFloat() * spike.radius;
                float centerX = (float)Math.cos(angle) * distance;
                float centerZ = (float)Math.sin(angle) * distance;
                if (spike.radial) {
                    float vertical = 1F - 2F * (index + 0.5F) / count;
                    float ring = (float)Math.sqrt(1 - vertical * vertical);
                    float azimuth = index * 2.399963F + spike.seed * 0.01F;
                    stack.mulPose(new org.joml.Quaternionf().rotationTo(new org.joml.Vector3f(0, 1, 0), new org.joml.Vector3f(ring * (float)Math.cos(azimuth), vertical, ring * (float)Math.sin(azimuth))));
                    centerX = centerZ = 0;
                } else {
                    stack.mulPose(new org.joml.Quaternionf().rotateY((float)angle).rotateZ(distance / spike.radius * 0.45F));
                }
                float height = spike.radius * (0.65F + random.nextFloat() * 1.4F) * rise;
                float width = (0.14F + random.nextFloat() * 0.19F) * spike.radius;
                float fade = Math.min(1F, (26F - age) / 10F);
                cone(vertices, stack, centerX, centerZ, height * 1.012F, width * 1.035F, 0.17F, 0.015F, 0.24F, fade * 0.4F);
                cone(vertices, stack, centerX, centerZ, height, width, 0.018F, 0.006F, 0.028F, fade * 0.98F);
                stack.popPose();
            }
            stack.popPose();
        }
        buffers.endBatch(type);
    }

    private static void cone(VertexConsumer vertices, PoseStack stack, float centerX, float centerZ, float height, float width, float red, float green, float blue, float alpha) {
        for (int side = 0; side < 5; side++) {
            double first = side * Math.PI * 2 / 5;
            double second = (side + 1) * Math.PI * 2 / 5;
            vertex(vertices, stack, centerX + (float)Math.cos(first) * width, 0, centerZ + (float)Math.sin(first) * width, red, green, blue, alpha);
            vertex(vertices, stack, centerX + (float)Math.cos(second) * width, 0, centerZ + (float)Math.sin(second) * width, red, green, blue, alpha);
            vertex(vertices, stack, centerX + width * 0.45F, height, centerZ, red, green, blue, alpha);
            vertex(vertices, stack, centerX + width * 0.45F, height, centerZ, red, green, blue, alpha);
        }
    }

    private static void vertex(VertexConsumer vertices, PoseStack stack, float posX, float posY, float posZ, float red, float green, float blue, float alpha) {
        vertices.vertex(stack.last().pose(), posX, posY, posZ).color(red, green, blue, alpha).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(stack.last().normal(), 0, 1, 0).endVertex();
    }
}
