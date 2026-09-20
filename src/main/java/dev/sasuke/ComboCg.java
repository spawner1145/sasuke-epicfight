package dev.sasuke;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/** Predecoded frames of the supplied 0.6-second movie, rendered over the entire game. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = SasukeMod.ID, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
final class ComboCg extends Screen {
    private record Pending(SasukeNetwork.ComboMovie message, Object level) { }
    private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();
    private static final double TRANSITION_TICKS = 10.0 / 60.0 * 20.0;
    private static final double MOVIE_TICKS = 0.6 * 20.0;
    private static final java.util.List<Pending> CAMERA = new java.util.ArrayList<>();

    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void zoom(net.minecraftforge.client.event.ViewportEvent.ComputeFov event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) { CAMERA.clear(); return; }
        double now = mc.level.getGameTime() + event.getPartialTick();
        double zoom = 0;
        var iterator = CAMERA.iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            double start = pending.message().startTick();
            double end = start + MOVIE_TICKS;
            if (pending.level() != mc.level || now >= end + TRANSITION_TICKS) {
                iterator.remove();
                continue;
            }
            double progress = now < start ? (now - start + TRANSITION_TICKS) / TRANSITION_TICKS
                : now <= end ? 1.0 : 1.0 - (now - end) / TRANSITION_TICKS;
            progress = Math.max(0, Math.min(1, progress));
            zoom = Math.max(zoom, progress * progress * (3 - 2 * progress));
        }
        // Scale the live FOV instead of changing the player's saved camera settings.
        event.setFOV(event.getFOV() * (1.0 - 0.50 * zoom));
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void advance(net.minecraftforge.event.TickEvent.RenderTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        var mc = Minecraft.getInstance();
        var iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            if (mc.level == null || mc.level != pending.level()) { iterator.remove(); continue; }
            var message = pending.message();
            if (mc.level.getGameTime() + event.renderTickTime < message.startTick()) continue;
            iterator.remove();
            var caster = mc.level.getEntity(message.casterId());
            if (caster != null) {
                mc.level.playLocalSound(caster.getX(), caster.getY(), caster.getZ(),
                    SoundEvent.createVariableRangeEvent(SasukeMod.id("combo_hit")),
                    net.minecraft.sounds.SoundSource.PLAYERS, 1F, 1F, false);
            }
            if (message.showVideo()) show(message.startTick());
        }
    }

    private static final ResourceLocation[] FRAMES = java.util.stream.IntStream.range(0, 35)
        .mapToObj(i -> SasukeMod.id(String.format(java.util.Locale.ROOT, "textures/cg/combo_%03d.png", i)))
        .toArray(ResourceLocation[]::new);
    private final double startTick;
    private final Screen previous;
    private final Object level;

    private ComboCg(Screen previous, double startTick) {
        super(Component.literal("Combo"));
        this.previous = previous;
        this.startTick = startTick;
        this.level = Minecraft.getInstance().level;
    }
    static void preload() {
        var textures = Minecraft.getInstance().getTextureManager();
        for (var frame : FRAMES) textures.preload(frame, net.minecraft.Util.backgroundExecutor());
    }
    static void play(SasukeNetwork.ComboMovie message) {
        PENDING.add(new Pending(message, Minecraft.getInstance().level));
        if (message.showVideo()) CAMERA.add(new Pending(message, Minecraft.getInstance().level));
    }
    private static void show(double startTick) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var screen = new ComboCg(mc.screen instanceof ComboCg ? null : mc.screen, startTick);
        mc.setScreen(screen);

    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        if (minecraft.level != level) { minecraft.setScreen(null); return; }
        double seconds = (minecraft.level.getGameTime() + partial - startTick) / 20.0;
        if (seconds < 0) return;
        if (seconds >= 0.6 || minecraft.level != level) {
            minecraft.setScreen(minecraft.level == level ? previous : null);
            return;
        }
        graphics.fill(0, 0, width, height, 0xFF000000);
        int frame = Math.min(34, (int)(seconds * 60));
        // Cover (center crop), not letterbox: no uncovered game area on any aspect ratio.
        float scale = Math.max(width / 1280F, height / 684F);
        int w = (int)Math.ceil(1280 * scale), h = (int)Math.ceil(684 * scale);
        graphics.blit(FRAMES[frame], (width-w)/2, (height-h)/2, w, h, 0F, 0F, 1280, 684, 1280, 684);
    }
    // Let the grab voice finish naturally after the short CG closes.
}

