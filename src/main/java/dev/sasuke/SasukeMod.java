package dev.sasuke;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import yesman.epicfight.api.forgeevent.EntityPatchRegistryEvent;

@Mod(SasukeMod.ID)
public class SasukeMod {
    public static final String ID = "sasuke_epicfight";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<net.minecraft.world.effect.MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ID);
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> HARD_BODY = EFFECTS.register("hard_body", () -> new BodyStunImmunityEffect("hard_body"));
    public static final RegistryObject<net.minecraft.world.effect.MobEffect> SUPER_ARMOR = EFFECTS.register("super_armor", () -> new BodyStunImmunityEffect("super_armor"));
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);
    public static final RegistryObject<Item> KUSANAGI = ITEMS.register("kusanagi", () -> new SwordItem(Tiers.NETHERITE, 7, -2.0F, new Item.Properties().fireResistant()) {
        @Override
        public int getMaxDamage(net.minecraft.world.item.ItemStack stack) { return 0; }

        @Override
        public boolean isDamageable(net.minecraft.world.item.ItemStack stack) { return false; }

        @Override
        public boolean isBarVisible(net.minecraft.world.item.ItemStack stack) { return false; }
    });
    public static final RegistryObject<EntityType<SusanooEntity>> SUSANOO = ENTITIES.register("susanoo", () -> EntityType.Builder.<SusanooEntity>of(SusanooEntity::new, MobCategory.MISC).sized(0.1F, 0.1F).clientTrackingRange(12).updateInterval(1).noSave().build(ID + ":susanoo"));
    public static final RegistryObject<EntityType<net.minecraft.world.entity.Display.ItemDisplay>> PLANTED_SWORD = ENTITIES.register("planted_sword", () -> EntityType.Builder.<net.minecraft.world.entity.Display.ItemDisplay>of(net.minecraft.world.entity.Display.ItemDisplay::new, MobCategory.MISC).sized(0.1F, 0.1F).clientTrackingRange(12).updateInterval(1).noSave().build(ID + ":planted_sword"));

    public SasukeMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        EFFECTS.register(bus);
        ENTITIES.register(bus);
        bus.addListener(SasukeAnimations::register);
        bus.addListener(SasukeWeapon::register);
        bus.addListener(this::attributes);
        bus.addListener(this::patches);
        SasukeNetwork.register();
    }

    private void attributes(EntityAttributeCreationEvent event) {
        event.put(SUSANOO.get(), SusanooEntity.getDefaultAttribute());
    }

    private void patches(EntityPatchRegistryEvent event) {
        event.getTypeEntry().put(SUSANOO.get(), entity -> SusanooPatch::new);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ID, path);
    }
}
