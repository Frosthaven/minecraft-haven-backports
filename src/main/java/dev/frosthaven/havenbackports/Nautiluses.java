package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.nautilus.AbstractNautilus;
import dev.frosthaven.havenbackports.nautilus.Nautilus;
import dev.frosthaven.havenbackports.nautilus.NautilusAi;
import dev.frosthaven.havenbackports.nautilus.NautilusArmorItem;
import dev.frosthaven.havenbackports.nautilus.NautilusInventoryMenu;
import dev.frosthaven.havenbackports.nautilus.ZombieNautilus;
import dev.frosthaven.havenbackports.variant.FarmVariant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.sensing.TemptingSensor;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The nautilus, the zombie nautilus, nautilus armor and Breath of the Nautilus, as 26.4's EntityTypes, Items,
 * SoundEvents, MobEffects, MemoryModuleType, SensorType, DefaultAttributes and SpawnPlacements define them. Nautiluses
 * spawn in 9 ocean biomes (26.x biome files: tools/spawns.py writes the biome modifiers); zombie nautiluses arrive with
 * drowned (DrownedMixin). Breath of the Nautilus keeps a rider's air from running out under water without refilling it
 * (26.x MobEffectUtil.hasWaterBreathing / shouldEffectsRefillAirsupply: NeoForge's LivingBreatheEvent). A nautilus is
 * never pushed onto a boat (26.x #cannot_be_pushed_onto_boats: NeoForge's EntityMountEvent).
 */
public final class Nautiluses {
    static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, HavenBackports.ID);
    static final DeferredRegister<MemoryModuleType<?>> MEMORIES = DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, HavenBackports.ID);
    static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HavenBackports.ID);
    static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, HavenBackports.ID);

    public static final TagKey<Item> NAUTILUS_FOOD = item("nautilus_food"), NAUTILUS_TAMING_ITEMS = item("nautilus_taming_items"),
            NAUTILUS_BUCKET_FOOD = item("nautilus_bucket_food");
    public static final TagKey<EntityType<?>> NAUTILUS_HOSTILES = entity("nautilus_hostiles"), CAN_WEAR_NAUTILUS_ARMOR = entity("can_wear_nautilus_armor"),
            IMMUNE_TO_POISON = entity("immune_to_poison"), CANNOT_BE_PUSHED_ONTO_BOATS = entity("cannot_be_pushed_onto_boats");
    public static final TagKey<Biome> SPAWNS_CORAL_VARIANT = TagKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("spawns_coral_variant_zombie_nautilus"));

    /** zombie_nautilus_variant (26.x ZombieNautilusVariant: asset_id, model normal or warm, spawn_conditions). */
    public static final ResourceKey<Registry<FarmVariant>> VARIANTS = ResourceKey.createRegistryKey(Variants.id("zombie_nautilus_variant"));
    public static final EntityDataSerializer<Holder<FarmVariant>> VARIANT_SERIALIZER = EntityDataSerializer.forValueType(ByteBufCodecs.holderRegistry(VARIANTS));

    public static final DeferredHolder<SoundEvent, SoundEvent> NAUTILUS_AMBIENT = s("entity.nautilus.ambient"), NAUTILUS_AMBIENT_LAND = s("entity.nautilus.ambient_land"),
            NAUTILUS_DASH = s("entity.nautilus.dash"), NAUTILUS_DASH_LAND = s("entity.nautilus.dash_land"),
            NAUTILUS_DASH_READY = s("entity.nautilus.dash_ready"), NAUTILUS_DASH_READY_LAND = s("entity.nautilus.dash_ready_land"),
            NAUTILUS_DEATH = s("entity.nautilus.death"), NAUTILUS_DEATH_LAND = s("entity.nautilus.death_land"), NAUTILUS_EAT = s("entity.nautilus.eat"),
            NAUTILUS_HURT = s("entity.nautilus.hurt"), NAUTILUS_HURT_LAND = s("entity.nautilus.hurt_land"), NAUTILUS_RIDING = s("entity.nautilus.riding"),
            NAUTILUS_SWIM = s("entity.nautilus.swim"),
            BABY_AMBIENT = s("entity.baby_nautilus.ambient"), BABY_AMBIENT_LAND = s("entity.baby_nautilus.ambient_land"),
            BABY_DEATH = s("entity.baby_nautilus.death"), BABY_DEATH_LAND = s("entity.baby_nautilus.death_land"), BABY_EAT = s("entity.baby_nautilus.eat"),
            BABY_HURT = s("entity.baby_nautilus.hurt"), BABY_HURT_LAND = s("entity.baby_nautilus.hurt_land"), BABY_SWIM = s("entity.baby_nautilus.swim"),
            ZOMBIE_AMBIENT = s("entity.zombie_nautilus.ambient"), ZOMBIE_AMBIENT_LAND = s("entity.zombie_nautilus.ambient_land"),
            ZOMBIE_DASH = s("entity.zombie_nautilus.dash"), ZOMBIE_DASH_LAND = s("entity.zombie_nautilus.dash_land"),
            ZOMBIE_DASH_READY = s("entity.zombie_nautilus.dash_ready"), ZOMBIE_DASH_READY_LAND = s("entity.zombie_nautilus.dash_ready_land"),
            ZOMBIE_DEATH = s("entity.zombie_nautilus.death"), ZOMBIE_DEATH_LAND = s("entity.zombie_nautilus.death_land"), ZOMBIE_EAT = s("entity.zombie_nautilus.eat"),
            ZOMBIE_HURT = s("entity.zombie_nautilus.hurt"), ZOMBIE_HURT_LAND = s("entity.zombie_nautilus.hurt_land"),
            ZOMBIE_RIDING = s("entity.zombie_nautilus.riding"), ZOMBIE_SWIM = s("entity.zombie_nautilus.swim"),
            ARMOR_EQUIP_NAUTILUS = s("item.armor.equip_nautilus"), ARMOR_UNEQUIP_NAUTILUS = s("item.armor.unequip_nautilus"),
            SADDLE_EQUIP = s("item.nautilus_saddle_equip"), SADDLE_UNDERWATER_EQUIP = s("item.nautilus_saddle_underwater_equip"),
            SADDLE_UNEQUIP = s("item.saddle.unequip"), PARROT_IMITATE_ZOMBIE_NAUTILUS = s("entity.parrot.imitate.zombie_nautilus");

    /** MobEffects.BREATH_OF_THE_NAUTILUS: beneficial, color 65518. */
    public static final DeferredHolder<MobEffect, MobEffect> BREATH_OF_THE_NAUTILUS = EFFECTS.register("breath_of_the_nautilus",
            () -> new MobEffect(MobEffectCategory.BENEFICIAL, 65518) {});

    /** MemoryModuleType.CHARGE_COOLDOWN_TICKS and ATTACK_TARGET_COOLDOWN (26.x; ours until 1.21.1 has them). */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> CHARGE_COOLDOWN_TICKS =
            MEMORIES.register("charge_cooldown_ticks", () -> new MemoryModuleType<>(Optional.of(com.mojang.serialization.Codec.INT)));
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> ATTACK_TARGET_COOLDOWN =
            MEMORIES.register("attack_target_cooldown", () -> new MemoryModuleType<>(Optional.of(com.mojang.serialization.Codec.INT)));
    /** SensorType.NAUTILUS_TEMPTATIONS: TemptingSensor over #nautilus_food. */
    public static final DeferredHolder<SensorType<?>, SensorType<TemptingSensor>> NAUTILUS_TEMPTATIONS =
            Content.SENSOR_TYPES.register("nautilus_temptations", () -> new SensorType<>(() -> new TemptingSensor(NautilusAi.getTemptations())));

    /** EntityTypes.NAUTILUS: water creature, 0.875 x 0.95, passenger 1.1375, eye 0.2751, tracking range 10; ZOMBIE_NAUTILUS the same as a monster. */
    public static final DeferredHolder<EntityType<?>, EntityType<Nautilus>> NAUTILUS = Content.ENTITY_TYPES.register("nautilus",
            () -> EntityType.Builder.of(Nautilus::new, MobCategory.WATER_CREATURE).sized(0.875F, 0.95F).passengerAttachments(1.1375F)
                    .eyeHeight(0.2751F).clientTrackingRange(10).build(HavenBackports.ID + ":nautilus"));
    public static final DeferredHolder<EntityType<?>, EntityType<ZombieNautilus>> ZOMBIE_NAUTILUS = Content.ENTITY_TYPES.register("zombie_nautilus",
            () -> EntityType.Builder.of(ZombieNautilus::new, MobCategory.MONSTER).sized(0.875F, 0.95F).passengerAttachments(1.1375F)
                    .eyeHeight(0.2751F).clientTrackingRange(10).build(HavenBackports.ID + ":zombie_nautilus"));

    public static final DeferredHolder<MenuType<?>, MenuType<NautilusInventoryMenu>> MENU = MENUS.register("nautilus",
            () -> IMenuTypeExtension.create((id, inv, buf) -> new NautilusInventoryMenu(id, inv, (AbstractNautilus) inv.player.level().getEntity(buf.readVarInt()))));

    public static final List<DeferredItem<Item>> ARMORS = new ArrayList<>();

    private Nautiluses() {}

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(name));
    }

    private static TagKey<EntityType<?>> entity(String name) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace(name));
    }

    private static DeferredHolder<SoundEvent, SoundEvent> s(String name) {
        return Content.sound(name);
    }

    private static void armor(String id, Holder<ArmorMaterial> material, String asset, Item.Properties properties) {
        DeferredItem<Item> item = Content.ITEMS.register(id, () -> new NautilusArmorItem(material, asset, properties));
        Content.ITEM.put(id, item);
        ARMORS.add(item);
    }

    static void register(IEventBus modBus) {
        // Items.*_NAUTILUS_ARMOR: Item.Properties.nautilusArmor(ArmorMaterials.X); netherite also fireResistant. Copper and
        // netherite take Gear's materials (26.x defense: body 4 and 19), iron, gold and diamond 1.21.1's (body 5, 7, 11).
        armor("copper_nautilus_armor", Gear.COPPER, "copper", new Item.Properties());
        armor("iron_nautilus_armor", ArmorMaterials.IRON, "iron", new Item.Properties());
        armor("golden_nautilus_armor", ArmorMaterials.GOLD, "gold", new Item.Properties());
        armor("diamond_nautilus_armor", ArmorMaterials.DIAMOND, "diamond", new Item.Properties());
        armor("netherite_nautilus_armor", Gear.NETHERITE, "netherite", new Item.Properties().fireResistant());
        Content.ITEM.put("nautilus_spawn_egg", Content.ITEMS.register("nautilus_spawn_egg",
                () -> new DeferredSpawnEggItem(NAUTILUS, 0xFFFFFF, 0xFFFFFF, new Item.Properties())));
        Content.ITEM.put("zombie_nautilus_spawn_egg", Content.ITEMS.register("zombie_nautilus_spawn_egg",
                () -> new DeferredSpawnEggItem(ZOMBIE_NAUTILUS, 0xFFFFFF, 0xFFFFFF, new Item.Properties())));
        EFFECTS.register(modBus);
        MEMORIES.register(modBus);
        MENUS.register(modBus);
        SERIALIZERS.register("zombie_nautilus_variant", () -> VARIANT_SERIALIZER);
        SERIALIZERS.register(modBus);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, e -> e.dataPackRegistry(VARIANTS, FarmVariant.CODEC, FarmVariant.NETWORK_CODEC));
        modBus.addListener(EntityAttributeCreationEvent.class, e -> {
            e.put(NAUTILUS.get(), AbstractNautilus.createAttributes().build());
            e.put(ZOMBIE_NAUTILUS.get(), ZombieNautilus.createAttributes().build());
        });
        // SpawnPlacements: NAUTILUS IN_WATER, MOTION_BLOCKING_NO_LEAVES, checkNautilusSpawnRules (the zombie nautilus has none)
        modBus.addListener(RegisterSpawnPlacementsEvent.class, e -> e.register(NAUTILUS.get(), SpawnPlacementTypes.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, AbstractNautilus::checkNautilusSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() -> NautilusArmorItem.registerDispenser(ARMORS.stream().map(DeferredItem::get).toList())));
        NeoForge.EVENT_BUS.addListener(Nautiluses::breathe);
        NeoForge.EVENT_BUS.addListener(Nautiluses::mount);
    }

    /** 26.x: Breath of the Nautilus counts as water breathing (no air lost) but does not refill air, unless Water
     *  Breathing or Conduit Power is also there (NeoForge's own handling already refills nothing for those). */
    private static void breathe(LivingBreatheEvent e) {
        if (!e.canBreathe() && e.getEntity().hasEffect(BREATH_OF_THE_NAUTILUS) && !MobEffectUtil.hasWaterBreathing(e.getEntity())) {
            e.setCanBreathe(true);
            e.setRefillAirAmount(0);
        }
    }

    /** 26.x Boat (and raft) pushing skips #cannot_be_pushed_onto_boats. 1.21.1 has no tag there; a mount of such a mob
     *  onto a boat that no player starts is refused here (1.21.1's push is the only way a mob boards a boat itself). */
    private static void mount(EntityMountEvent e) {
        if (e.isMounting() && e.getEntityBeingMounted() instanceof Boat && e.getEntityMounting().getType().is(CANNOT_BE_PUSHED_ONTO_BOATS)
                && e.getEntityMounting() instanceof AbstractNautilus) {
            e.setCanceled(true);
        }
    }
}
