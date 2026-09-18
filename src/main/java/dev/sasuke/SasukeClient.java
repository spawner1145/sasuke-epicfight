package dev.sasuke;

import com.merlin204.avalon.entity.client.renderer.EmptyRenderer;
import com.merlin204.avalon.entity.client.renderer.patch.entity.AvalonVFXRendererPatch;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
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
    private static Object lastLevel;
    private static float comboYaw;
    private static float comboPitch;

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

}
