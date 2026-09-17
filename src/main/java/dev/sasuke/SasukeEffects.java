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
        config.setMaxParticles(160);
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
        config.renderer.setBloomColor(0xFF8734D9);
        config.renderer.getCull().setEnable(false);
        if (glow) config.material.getBlendMode().setDstColorFactor(GlStateManager.DestFactor.ONE);
        config.sizeOverLifetime.setEnable(true);
        config.sizeOverLifetime.setSize(new NumberFunction3(curve(1, 0), curve(1, 0), curve(1, 0)));
        return emitter;
    }

    private static void burstCount(ParticleEmitter emitter, int count, int delay) {
        var burst = new EmissionSetting.Burst();
        burst.time = delay;
        burst.setCount(new Constant(count));
        emitter.config.setDuration(delay + 1);
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
        for (int layer = 0; layer < 2; layer++) {
            var flame = emitter("flame", layer == 0 ? 0x755421B0 : 0x508B49ED, layer == 0 ? 0.95F : 0.5F, 1.25F, 22, 0.15F, layer == 1);
            flame.config.setLooping(true);
            flame.config.setDuration(80);
            flame.config.emission.setEmissionRate(new Constant(layer == 0 ? 2F : 1F));
            flame.config.shape.setScale(new NumberFunction3(1, 1.2, 0.8));
            flame.config.setStartSize(new NumberFunction3(layer == 0 ? 0.65 : 0.35, layer == 0 ? 1.7 : 0.9, 1));
            flame.config.velocityOverLifetime.setEnable(true);
            flame.config.velocityOverLifetime.setLinear(new NumberFunction3(0, 1.1, 0));
            flame.config.velocityOverLifetime.setOrbital(new NumberFunction3(0, 0.65, 0));
            add(aura, flame, "purple_flame_" + layer);
        }
        return aura;
    }

    public static void tick() {
        var mc = Minecraft.getInstance();
        AURAS.entrySet().removeIf(entry -> {
            if (entry.getKey().isAlive() && !entry.getKey().dissolving() && entry.getKey().level() == mc.level) return false;
            if (entry.getValue().getRuntime() != null) entry.getValue().getRuntime().destroy(false);
            var cached = EntityEffect.CACHE.get(entry.getKey());
            if (cached != null) {
                cached.remove(entry.getValue());
                if (cached.isEmpty()) EntityEffect.CACHE.remove(entry.getKey());
            }
            return true;
        });
        BURSTS.removeIf(effect -> {
            if (effect.getRuntime() != null && effect.getRuntime().isAlive()) return false;
            if (effect instanceof EntityEffect attached) {
                var cached = EntityEffect.CACHE.get(attached.entity);
                if (cached != null) {
                    cached.remove(attached);
                    if (cached.isEmpty()) EntityEffect.CACHE.remove(attached.entity);
                }
            }
            return true;
        });
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof SusanooEntity spirit) || spirit.dissolving() || spirit.revision() == 0 || spirit.tickCount < 3 || AURAS.containsKey(spirit)) continue;
            var effect = new EntityEffect(aura(), mc.level, spirit, EntityEffect.AutoRotate.NONE);
            effect.setOffset(new Vector3f(0, 1.65F, 0));
            effect.setForcedDeath(false);
            effect.start();
            AURAS.put(spirit, effect);
        }
    }

    public static void burst(SasukeNetwork.Burst message) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(message.position()) > 4096) return;
        boolean charge = message.radius() < 0;
        float radius = Math.abs(message.radius());
        if (!charge && radius >= 1) mc.level.playLocalSound(message.position().x, message.position().y, message.position().z,
            net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS, message.radial() ? 0.55F : 0.85F, message.radial() ? 1.15F : 0.7F, false);
        FX fx = new FX();
        fx.setFxLocation(SasukeMod.id(charge ? "amaterasu_charge" : message.radial() ? "amaterasu_hand" : "amaterasu_ground"));
        if (charge) {
            var inward = emitter("flame", 0xDC07040A, 0.55F, radius, 9, -radius * 2, false);
            burstCount(inward, 26, 0);
            add(fx, inward, "converging_embers");
        } else {
            var black = emitter("flame", 0xFC040305, radius * 0.85F, radius * 0.5F, 27, message.radial() ? 1.5F : 0.4F, false);
            black.config.setStartSize(new NumberFunction3(radius * 0.6F, radius * 1.4F, radius));
            black.config.velocityOverLifetime.setEnable(true);
            black.config.velocityOverLifetime.setLinear(new NumberFunction3(0, message.radial() ? 0 : 1.8, 0));
            burstCount(black, radius < 1 ? 12 : 42, 0);
            add(fx, black, "black_flame");
            var sparks = emitter("spark", 0x7864318C, radius * 0.06F, radius * 0.25F, 13, radius * 2.2F, true);
            burstCount(sparks, radius < 1 ? 2 : 8, 0);
            add(fx, sparks, "violet_shrapnel");
            var wisps = emitter("flame", 0xB01C1028, radius * 0.35F, radius * 0.7F, 20, 0.6F, false);
            burstCount(wisps, radius < 1 ? 1 : 4, 3);
            add(fx, wisps, "violet_afterburn");
        }
        if (radius >= 1) {
            var ring = emitter("ring", charge ? 0xB0191020 : 0xA00A070E, radius * (charge ? 2.2F : 4.5F), 0.01F, charge ? 9 : 14, 0, false);
            ring.config.renderer.setRenderMode(message.radial() ? RendererSetting.Particle.Mode.Billboard : RendererSetting.Particle.Mode.Horizontal);
            if (!charge) ring.config.sizeOverLifetime.setSize(new NumberFunction3(curve(0.08F, 1), curve(0.08F, 1), curve(0.08F, 1)));
            burstCount(ring, 1, 0);
            add(fx, ring, "pressure_ring");
        }
        play(fx, message.position().add(0, message.radial() ? 0 : 0.1, 0));
    }

    public static void summon(Vec3 position) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FX fx = new FX();
        fx.setFxLocation(SasukeMod.id("susanoo_summon"));
        var ring = emitter("ring", 0xB08952C8, 6F, 0.01F, 12, 0, true);
        ring.config.renderer.setRenderMode(RendererSetting.Particle.Mode.Horizontal);
        ring.config.sizeOverLifetime.setSize(new NumberFunction3(curve(0.03F, 1), curve(0.03F, 1), curve(0.03F, 1)));
        burstCount(ring, 1, 0);
        add(fx, ring, "summon_shockwave");
        var pressure = emitter("flame", 0x90472D68, 0.65F, 0.3F, 13, 4F, false);
        pressure.config.shape.setScale(new NumberFunction3(1, 0.2, 1));
        burstCount(pressure, 38, 0);
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
        track(effect);
    }

    private static void track(FXEffect effect) {
        if (BURSTS.size() >= 256) {
            var oldest = BURSTS.remove(0);
            if (oldest.getRuntime() != null) oldest.getRuntime().destroy(true);
            if (oldest instanceof EntityEffect attached) {
                var cached = EntityEffect.CACHE.get(attached.entity);
                if (cached != null) {
                    cached.remove(attached);
                    if (cached.isEmpty()) EntityEffect.CACHE.remove(attached.entity);
                }
            }
        }
        effect.start();
        BURSTS.add(effect);
    }

    public static void flame(SasukeNetwork.Flame message) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.player.distanceToSqr(message.position()) > 4096) return;
        FX fx = new FX();
        fx.setFxLocation(SasukeMod.id("residual_" + message.kind()));
        if (message.kind() == 8) {
            var target = mc.level.getEntity(message.entityId());
            if (target != null) mc.level.addParticle(yesman.epicfight.particle.EpicFightParticles.WHITE_AFTERIMAGE.get(), target.getX(), target.getY(), target.getZ(), Double.longBitsToDouble(target.getId()), 0, 0);
            return;
        }
        if (message.kind() == 5 || message.kind() == 6 || message.kind() == 9) {
            ElectricVisuals.add(message);
            var sparks = emitter("spark", 0xDA499FFF, message.kind() == 6 ? 0.09F : 0.08F, message.radius() * 0.3F, message.kind() == 6 ? 7 : 12, message.kind() == 9 ? 5F : 0.5F, true);
            sparks.config.renderer.setBloomColor(0xFF409AFF);
            burstCount(sparks, message.kind() == 9 ? 48 : message.kind() == 6 ? 3 : 7, 0);
            add(fx, sparks, "blue_discharge");
            if (message.kind() == 9) {
                var ring = emitter("ring", 0xB0378FFF, message.radius() * 2.3F, 0.01F, 16, 0, true);
                ring.config.renderer.setBloomColor(0xFF409AFF);
                ring.config.renderer.setRenderMode(RendererSetting.Particle.Mode.Horizontal);
                ring.config.sizeOverLifetime.setSize(new NumberFunction3(curve(0.05F, 1), curve(0.05F, 1), curve(0.05F, 1)));
                burstCount(ring, 1, 0);
                add(fx, ring, "electric_shockwave");
                mc.level.playLocalSound(message.position().x, message.position().y, message.position().z, net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.3F, false);
            }
            play(fx, message.position());
            return;
        }
        if (message.kind() == 7) {
            var flame = emitter("flame", 0xF5040306, 0.6F, message.radius(), 16, 0.2F, false);
            burstCount(flame, 6, 0);
            add(fx, flame, "black_slash");
            play(fx, message.position());
            return;
        }
        if (message.kind() >= 3) {
            var motes = emitter("spark", 0xB09C69EA, 0.095F, message.radius(), 48, 0.3F, true);
            motes.config.shape.setScale(new NumberFunction3(1, 1.4, 0.65));
            motes.config.velocityOverLifetime.setEnable(true);
            motes.config.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.4, 0));
            burstCount(motes, message.kind() == 3 ? 64 : 10, 0);
            add(fx, motes, "dissolving_soul");
            play(fx, message.position());
            return;
        }
        boolean ground = message.kind() == 0;
        for (int layer = 0; layer < 2; layer++) {
            var flame = emitter("flame", layer == 0 ? 0xF5040306 : 0x90281839,
                ground ? 0.65F : 0.5F, message.radius(), 18, 0.12F, false);
            flame.config.setDuration(20);
            flame.config.emission.setEmissionRate(new Constant(layer == 0 ? (ground ? Math.max(1F, message.radius() * 1.5F) : 1.5F) : 0.2F));
            flame.config.shape.setScale(new NumberFunction3(1, ground ? 0.08 : 1.3, 1));
            flame.config.setStartSize(new NumberFunction3(0.45, ground ? 0.95 : 0.8, 0.45));
            flame.config.velocityOverLifetime.setEnable(true);
            flame.config.velocityOverLifetime.setLinear(new NumberFunction3(0, 0.7, 0));
            add(fx, flame, "residual_flame_" + layer);
        }
        if (message.kind() == 2) {
            var sparks = emitter("spark", 0x88703CA9, 0.07F, message.radius(), 20, 0.2F, true);
            sparks.config.setDuration(20);
            sparks.config.emission.setEmissionRate(new Constant(0.25F));
            add(fx, sparks, "aura_embers");
        }
        var entity = mc.level.getEntity(message.entityId());
        if (!ground && entity != null) {
            var effect = new EntityEffect(fx, mc.level, entity, EntityEffect.AutoRotate.NONE);
            effect.setOffset(new Vector3f(0, entity.getBbHeight() * 0.5F, 0));
            effect.setAllowMulti(true);
            effect.setForcedDeath(false);
            track(effect);
        } else if (ground) play(fx, message.position().add(0, 0.1, 0));
    }

    public static void clear() {
        ElectricVisuals.clear();
        for (var effect : AURAS.values()) {
            if (effect.getRuntime() != null) effect.getRuntime().destroy(true);
            EntityEffect.CACHE.remove(effect.entity);
        }
        for (var effect : BURSTS) {
            if (effect.getRuntime() != null) effect.getRuntime().destroy(true);
            if (effect instanceof EntityEffect attached) {
                var cached = EntityEffect.CACHE.get(attached.entity);
                if (cached != null) {
                    cached.remove(attached);
                    if (cached.isEmpty()) EntityEffect.CACHE.remove(attached.entity);
                }
            }
        }
        AURAS.clear();
        BURSTS.clear();
    }
}
