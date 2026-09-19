package dev.sasuke;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.lowdragmc.photon.client.fx.EntityEffect;
import com.lowdragmc.photon.client.fx.FX;
import com.lowdragmc.photon.client.fx.FXEffect;
import com.lowdragmc.photon.client.gameobject.emitter.data.EmissionSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.RendererSetting;
import com.lowdragmc.photon.client.gameobject.emitter.data.material.TextureMaterial;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.Constant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.RandomConstant;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.color.Color;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.Curve;
import com.lowdragmc.photon.client.gameobject.emitter.data.number.curve.ECBCurves;
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Sphere;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class SasukeEffects {
    private static final Map<SusanooEntity, EntityEffect> AURAS = new HashMap<>();
    private static final List<FXEffect> BURSTS = new ArrayList<>();
    private static FX aura;

    private static Curve curve(float start, float end) {
        Curve curve = new Curve(0, 1, 0, 1, 1, "life", "size");
        curve.setCurves(new ECBCurves(0, start, 0.15F, start, 0.7F, end, 1, end));
        return curve;
    }

    private static ParticleEmitter emitter(String texture, int color, float size, float radius, int life, float speed, boolean glow) {
        ParticleEmitter emitter = new ParticleEmitter();
        var config = emitter.config;
        config.setDuration(1);
        config.setLooping(false);
        config.setMaxParticles(128);
        config.setStartLifetime(new RandomConstant(life * 0.7F, life, false));
        config.setStartSpeed(new Constant(speed));
        config.setStartSize(new NumberFunction3(size, size, size));
        config.setStartColor(new Color(color));
        config.emission.setEmissionRate(new Constant(0));
        Sphere sphere = new Sphere();
        sphere.setRadius(Math.max(0.01F, radius));
        sphere.setRadiusThickness(0.45F);
        config.shape.setShape(sphere);
        config.material.setMaterial(new TextureMaterial(SasukeMod.id("textures/particle/" + texture + ".png")));
        config.material.setCull(false);
        config.renderer.setBloomEffect(glow);
        config.renderer.setBloomColor(0xFF9945EB);
        config.renderer.getCull().setEnable(false);
        if (glow) config.material.getBlendMode().setDstColorFactor(GlStateManager.DestFactor.ONE);
        config.sizeOverLifetime.setEnable(true);
        config.sizeOverLifetime.setSize(new NumberFunction3(curve(1, 0), curve(1, 0), curve(1, 0)));
        return emitter;
    }

    private static void burstCount(ParticleEmitter emitter, int count) {
        var burst = new EmissionSetting.Burst();
        burst.time = 0;
        burst.setCount(new Constant(count));
        emitter.config.emission.getBursts().add(burst);
    }

    private static void add(FX fx, ParticleEmitter emitter, String name) {
        emitter.setName(name);
        fx.getMainFX().objects().add(emitter);
    }

    private static FX aura() {
        if (aura != null) return aura;
        aura = new FX();
        aura.setFxLocation(SasukeMod.id("susanoo_flame"));
        for (int layer = 0; layer < 1; layer++) {
            var flame = emitter("flame_curl", 0x906F25D8, 0.24F, 0.72F, 24, 0.08F, true);
            flame.config.setLooping(true);
            flame.config.setDuration(80);
            flame.config.emission.setEmissionRate(new Constant(1.2F));
            flame.config.shape.setScale(new NumberFunction3(0.8F, 0.55F, 0.8F));
            flame.config.setStartSize(new NumberFunction3(0.24F, 0.40F, 1));
            flame.config.velocityOverLifetime.setEnable(true);
            flame.config.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.12, 0));
            flame.config.velocityOverLifetime.setOrbital(new NumberFunction3(0, 0.75, 0));
            add(aura, flame, "purple_flame_" + layer);
        }
        return aura;
    }

    private static void destroy(FXEffect effect) {
        if (effect.getRuntime() != null) effect.getRuntime().destroy(true);
        if (effect instanceof EntityEffect attached) {
            var cached = EntityEffect.CACHE.get(attached.entity);
            if (cached != null) {
                cached.remove(attached);
                if (cached.isEmpty()) EntityEffect.CACHE.remove(attached.entity);
            }
        }
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        AURAS.entrySet().removeIf(entry -> {
            if (entry.getKey().isAlive() && !entry.getKey().dissolving() && entry.getKey().level() == mc.level) return false;
            destroy(entry.getValue());
            return true;
        });
        BURSTS.removeIf(effect -> {
            if (effect.getRuntime() != null && effect.getRuntime().isAlive()) return false;
            destroy(effect);
            return true;
        });
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof SusanooEntity spirit) || spirit.dissolving() || spirit.revision() == 0 || AURAS.containsKey(spirit)) continue;
            var effect = new EntityEffect(aura(), mc.level, spirit, EntityEffect.AutoRotate.NONE);
            effect.setOffset(new Vector3f(0, 1.65F, 0));
            effect.setForcedDeath(false);
            effect.start();
            AURAS.put(spirit, effect);
        }
    }

    public static void burst(SasukeNetwork.Burst message) { BlackFlameVisuals.burst(message); }

    public static void summon(Vec3 position) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FX fx = new FX();
        fx.setFxLocation(SasukeMod.id("susanoo_summon"));
        var ring = emitter("ring", 0xB09B58E1, 6F, 0.01F, 12, 0, true);
        ring.config.renderer.setRenderMode(RendererSetting.Particle.Mode.Horizontal);
        ring.config.sizeOverLifetime.setSize(new NumberFunction3(curve(0.03F, 1), curve(0.03F, 1), curve(0.03F, 1)));
        burstCount(ring, 1);
        add(fx, ring, "summon_shockwave");
        var pressure = emitter("flame", 0x90472D68, 0.65F, 0.3F, 13, 4F, false);
        pressure.config.shape.setScale(new NumberFunction3(1, 0.2, 1));
        burstCount(pressure, 30);
        add(fx, pressure, "outward_pressure");
        play(fx, position.add(0, 0.15, 0));
        mc.level.playLocalSound(position.x, position.y, position.z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,
            net.minecraft.sounds.SoundSource.PLAYERS, 0.65F, 0.65F, false);
    }

    private static void play(FX fx, Vec3 position) {
        var mc = Minecraft.getInstance();
        FXEffect effect = new FXEffect(fx, mc.level) {
            @Override
            public void start() {
                runtime = fx.createRuntime();
                runtime.getRoot().updatePos(position.toVector3f());
                runtime.emmit(this);
            }
        };
        if (BURSTS.size() >= 96) destroy(BURSTS.remove(0));
        effect.start();
        BURSTS.add(effect);
    }

    public static void flame(SasukeNetwork.Flame message) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(message.position()) > 4096) return;
        switch (message.kind()) {
            case 0, 1, 2, 7 -> BlackFlameVisuals.add(message);
            case 5, 6, 9 -> {
                ElectricVisuals.add(message);
            }
            case 8 -> {
                var target = mc.level.getEntity(message.entityId());
                if (target != null) mc.level.addParticle(yesman.epicfight.particle.EpicFightParticles.WHITE_AFTERIMAGE.get(),
                    target.getX(), target.getY(), target.getZ(), Double.longBitsToDouble(target.getId()), 0, 0);
            }
            case 3, 4 -> {
                // Susanoo pressure burst shares Amaterasu's expanding spherical shock shell.
                BlackFlameVisuals.burst(new SasukeNetwork.Burst(message.position(), message.radius() * 0.8F,
                    Float.floatToIntBits(message.radius()), true));
                FX fx = new FX();
                fx.setFxLocation(SasukeMod.id("susanoo_burst"));
                var motes = emitter("flame_curl", 0xB0AC6CF4, 0.18F, message.radius() * 0.45F, 18, 1.8F, true);
                motes.config.shape.setScale(new NumberFunction3(1, 1.4, 0.65));
                motes.config.velocityOverLifetime.setEnable(true);
                motes.config.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.4, 0));
                burstCount(motes, message.kind() == 3 ? 28 : 6);
                add(fx, motes, "dissolving_soul");
                play(fx, message.position());
            }
            case 10 -> {
                FX fx = new FX();
                fx.setFxLocation(SasukeMod.id("susanoo_dissolve"));
                var motes = emitter("glint", 0xD08F5CFF, 0.16F, message.radius() * 0.5F, 20, 1.4F, true);
                motes.config.shape.setScale(new NumberFunction3(1, 1.6, 1));
                motes.config.velocityOverLifetime.setEnable(true);
                motes.config.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.3, 0));
                burstCount(motes, 24);
                add(fx, motes, "purple_dissolve");
                play(fx, message.position());
            }
            default -> { }
        }
    }

    public static void clear() {
        ElectricVisuals.clear();
        BlackFlameVisuals.clear();
        AURAS.values().forEach(SasukeEffects::destroy);
        BURSTS.forEach(SasukeEffects::destroy);
        AURAS.clear();
        BURSTS.clear();
    }
}

