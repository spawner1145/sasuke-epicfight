package dev.sasuke;

import com.merlin204.avalon.entity.vfx.VFXEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.model.Armature;

public class SusanooEntity extends VFXEntity {
    private static final EntityDataAccessor<String> ACTION = SynchedEntityData.defineId(SusanooEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> REVISION = SynchedEntityData.defineId(SusanooEntity.class, EntityDataSerializers.INT);
    private Armature instanceArmature;
    private int dissolveTicks;

    public boolean dissolving() { return entityData.get(DIS_SPEED) > 0; }

    public void dissolve() {
        if (dissolving()) return;
        setDisSpeed(1F / 30F);
        dissolveTicks = 32;
        if (getOwner() instanceof net.minecraft.server.level.ServerPlayer player) {
            SasukeNetwork.flame(player.serverLevel(), position().add(0, 1.5, 0), 1.5F, -1, 3);
        }
    }

    public SusanooEntity(EntityType<? extends SusanooEntity> type, Level level) {
        super(type, level);
        ARMATURE_ACCESSOR = SasukeAnimations.SUSANOO;
        TEXTURE = SasukeMod.id("textures/entity/susanoo.png");
        LIGHT_TEXTURE = TEXTURE;
        entityData.set(ARMATURE_PATH, SasukeMod.id("entity/susanoo").toString());
        entityData.set(MESH_PATH, SasukeMod.id("entity/susanoo").toString());
        entityData.set(TEXTURE_PATH, TEXTURE.toString());
        entityData.set(LIGHT_TEXTURE_PATH, TEXTURE.toString());
        setPlayAnimation(true);
        setShouldRender(false);
        setNoAi(true);
        setInvisible(true);
        noCulling = true;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ACTION, "idle_sword_side");
        entityData.define(REVISION, 0);
    }

    @Override
    public Armature getArmature() {
        if (instanceArmature == null) instanceArmature = SasukeAnimations.SUSANOO.get().deepCopy();
        return instanceArmature;
    }

    public String action() { return entityData.get(ACTION); }
    @Override
    public yesman.epicfight.api.animation.AnimationManager.AnimationAccessor<? extends yesman.epicfight.api.animation.types.StaticAnimation> getIdleAnimation() {
        return SasukeAnimations.SPIRIT.get("idle_sword_side");
    }
    public int revision() { return entityData.get(REVISION); }
    public void animate(String name) {
        entityData.set(ACTION, name);
        entityData.set(REVISION, revision() + 1);
    }

    @Override
    public void tick() {
        super.tick();
        if (dissolving() && !level().isClientSide && --dissolveTicks <= 0) { discard(); return; }
        var owner = getOwner();
        if (owner != null && owner.isAlive() && owner.level() == level()) {
            setDeltaMovement(Vec3.ZERO);
            moveToOwner(owner);
            setStartYRot(owner.yBodyRot);
            if (dissolving() && !level().isClientSide && tickCount % 3 == 0 && owner instanceof net.minecraft.server.level.ServerPlayer player) {
                SasukeNetwork.flame(player.serverLevel(), position().add(0, 1.5, 0), 0.8F, -1, 4);
            }
        } else if (!level().isClientSide && tickCount > 5) {
            discard();
        }
    }
}
