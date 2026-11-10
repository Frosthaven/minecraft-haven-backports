package dev.frosthaven.havenbackports.cube;

import dev.frosthaven.havenbackports.HavenBackports;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Sulfur Cube (26.x EntityTypes.SULFUR_CUBE and what comes with it): entity type, attributes, bucket and spawn
 * egg, the sulfur_cube_content component, the sulfur_cube_goo particle, the archetype registry, spawn placement,
 * dispenser behaviours and the equipment hook. Ids are Mojang's, in the haven_backports namespace.
 */
public final class Cube {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HavenBackports.ID);
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, HavenBackports.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenBackports.ID);
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HavenBackports.ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, HavenBackports.ID);

    /** 26.x registry sulfur_cube_archetype (data: data/haven_backports/haven_backports/sulfur_cube_archetype/). */
    public static final ResourceKey<Registry<SulfurCubeArchetype>> ARCHETYPE =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "sulfur_cube_archetype"));

    // 26.x Attributes: bounciness 0 (0..1), air_drag_modifier 1 (0..2048), friction_modifier 1 (0..2048), all synced.
    // Only the Sulfur Cube has them (SulfurCube.createAttributes): no other entity changes.
    public static final Holder<Attribute> BOUNCINESS = ATTRIBUTES.register("bounciness",
            () -> new RangedAttribute("attribute.name.haven_backports.bounciness", 0.0, 0.0, 1.0).setSyncable(true));
    public static final Holder<Attribute> AIR_DRAG_MODIFIER = ATTRIBUTES.register("air_drag_modifier",
            () -> new RangedAttribute("attribute.name.haven_backports.air_drag_modifier", 1.0, 0.0, 2048.0).setSyncable(true));
    public static final Holder<Attribute> FRICTION_MODIFIER = ATTRIBUTES.register("friction_modifier",
            () -> new RangedAttribute("attribute.name.haven_backports.friction_modifier", 1.0, 0.0, 2048.0).setSyncable(true));

    /** 26.x EntityType.Builder.of(SulfurCube::new, MONSTER).sized(0.49, 0.49).eyeHeight(0.175).spawnDimensionsScale(2)
     *  .clientTrackingRange(10). */
    public static final DeferredHolder<EntityType<?>, EntityType<SulfurCube>> SULFUR_CUBE = ENTITIES.register("sulfur_cube",
            () -> EntityType.Builder.of(SulfurCube::new, MobCategory.MONSTER).sized(0.49F, 0.49F).eyeHeight(0.175F).spawnDimensionsScale(2.0F)
                    .clientTrackingRange(10).build(HavenBackports.ID + ":sulfur_cube"));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SulfurCubeContent>> SULFUR_CUBE_CONTENT =
            COMPONENTS.registerComponentType("sulfur_cube_content", b -> b.persistent(SulfurCubeContent.CODEC)
                    .networkSynchronized(SulfurCubeContent.STREAM_CODEC));

    /** 26.x ParticleTypes.SULFUR_CUBE_GOO (BreakingItemParticle.SulfurCubeProvider on the client). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SULFUR_CUBE_GOO = PARTICLES.register("sulfur_cube_goo",
            () -> new SimpleParticleType(false));

    /** 26.x Items: MobBucketItem(SULFUR_CUBE, Fluids.EMPTY, BUCKET_EMPTY_SULFUR_CUBE, stacksTo(1)); registerSpawnEgg. */
    public static final DeferredItem<Item> SULFUR_CUBE_BUCKET = ITEMS.register("sulfur_cube_bucket",
            () -> new SulfurCubeBucketItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> SULFUR_CUBE_SPAWN_EGG = ITEMS.register("sulfur_cube_spawn_egg",
            () -> new DeferredSpawnEggItem(SULFUR_CUBE, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));   // 26.x: its own item model, no tints

    private Cube() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ATTRIBUTES.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        PARTICLES.register(modBus);
        CubeSounds.SOUNDS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(SULFUR_CUBE.get(), SulfurCube.createAttributes().build()));
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, e -> e.dataPackRegistry(ARCHETYPE, SulfurCubeArchetype.DIRECT_CODEC));
        // 26.x SpawnPlacements: ON_GROUND, MOTION_BLOCKING_NO_LEAVES, SulfurCube.checkSulfurCubeSpawnRules (always true)
        modBus.addListener(RegisterSpawnPlacementsEvent.class, e -> e.register(SULFUR_CUBE.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (type, level, reason, pos, random) -> true, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(CubeDispense::register));
        NeoForge.EVENT_BUS.addListener(LivingEquipmentChangeEvent.class, e -> {
            if (e.getSlot() == EquipmentSlot.BODY && e.getEntity() instanceof SulfurCube cube && !cube.level().isClientSide) {
                cube.onBodyChanged(e.getFrom(), e.getTo());
            }
        });
    }
}
