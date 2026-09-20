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
    public boolean dissolving() { return isRemoved(); }

    public void dissolve() {
        if (level().isClientSide || isRemoved()) return;
        if (getOwner() instanceof net.minecraft.server.level.ServerPlayer player) {
            SasukeNetwork.flame(player.serverLevel(), position().add(0, 1.5, 0), 1.2F, -1, 10);
        }
        discard();
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
        setShouldRender(true);
        setNoAi(true);
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
        if (isRemoved()) return;
        var owner = getOwner();
        if (owner != null && owner.isAlive() && owner.level() == level()) {
            setDeltaMovement(Vec3.ZERO);
            moveToOwner(owner);
            setStartYRot(owner.yBodyRot);
            if (!level().isClientSide && !dissolving() && (action().equals("idle_sword_side") || action().equals("run_sword_side"))) {
                boolean moving = owner.isSprinting();
                String nextAction = moving ? "run_sword_side" : "idle_sword_side";
                if (!action().equals(nextAction)) animate(nextAction);
            }
        } else if (!level().isClientSide && tickCount > 5) {
            discard();
        }
    }
}
