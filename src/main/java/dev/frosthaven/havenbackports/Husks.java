package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.entity.SpawnWeightModifier;
import dev.frosthaven.havenbackports.husk.CamelHusk;
import dev.frosthaven.havenbackports.husk.Parched;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The camel husk and the parched, the desert mobs of newer Minecraft, as 26.4's EntityTypes, Items, SoundEvents,
 * DefaultAttributes and SpawnPlacements define them. The parched spawns in deserts (26.x desert biome: weight 50,
 * groups of 4; the desert skeleton goes from 100 to 50 with it), data written by the generator (tools/spawns.py).
 * A natural husk may arrive on a camel husk with a parched behind it (HuskMixin, 26.x Husk.finalizeSpawn).
 */
public final class Husks {
    static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, HavenBackports.ID);

    public static final TagKey<Item> CAMEL_HUSK_FOOD = TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("camel_husk_food"));
    public static final TagKey<EntityType<?>> IMMUNE_TO_WEAKNESS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("immune_to_weakness"));

    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_AMBIENT = Content.sound("entity.camel_husk.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_DASH = Content.sound("entity.camel_husk.dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_DASH_READY = Content.sound("entity.camel_husk.dash_ready");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_DEATH = Content.sound("entity.camel_husk.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_EAT = Content.sound("entity.camel_husk.eat");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_HURT = Content.sound("entity.camel_husk.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_SADDLE = Content.sound("entity.camel_husk.saddle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_SIT = Content.sound("entity.camel_husk.sit");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_STAND = Content.sound("entity.camel_husk.stand");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_STEP = Content.sound("entity.camel_husk.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAMEL_HUSK_STEP_SAND = Content.sound("entity.camel_husk.step_sand");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARCHED_AMBIENT = Content.sound("entity.parched.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARCHED_DEATH = Content.sound("entity.parched.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARCHED_HURT = Content.sound("entity.parched.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARCHED_STEP = Content.sound("entity.parched.step");
    // Parrot imitations (NeoForge's parrot_imitations data map names them; 26.x Parrot.MOB_SOUND_MAP)
    public static final DeferredHolder<SoundEvent, SoundEvent> PARROT_IMITATE_CAMEL_HUSK = Content.sound("entity.parrot.imitate.camel_husk");
    public static final DeferredHolder<SoundEvent, SoundEvent> PARROT_IMITATE_PARCHED = Content.sound("entity.parrot.imitate.parched");

    /** EntityTypes.CAMEL_HUSK: monster, 1.7 x 2.375, eye 2.275, tracking range 10 (the camel's size). */
    public static final DeferredHolder<EntityType<?>, EntityType<CamelHusk>> CAMEL_HUSK = Content.ENTITY_TYPES.register("camel_husk",
            () -> EntityType.Builder.of(CamelHusk::new, MobCategory.MONSTER).sized(1.7F, 2.375F).eyeHeight(2.275F).clientTrackingRange(10)
                    .build(HavenBackports.ID + ":camel_husk"));
    /** EntityTypes.PARCHED: monster, 0.6 x 1.99, eye 1.74, riding offset -0.7, tracking range 8 (the bogged's). 1.21.1's
     *  notInPeaceful is the Monster class itself. */
    public static final DeferredHolder<EntityType<?>, EntityType<Parched>> PARCHED = Content.ENTITY_TYPES.register("parched",
            () -> EntityType.Builder.of(Parched::new, MobCategory.MONSTER).sized(0.6F, 1.99F).eyeHeight(1.74F).ridingOffset(-0.7F)
                    .clientTrackingRange(8).build(HavenBackports.ID + ":parched"));

    private Husks() {}

    /** 26.x Monster.checkSurfaceMonstersSpawnRules: checkMonsterSpawnRules (written out: 1.21.1's takes only Monster
     *  types, and the camel husk is a camel) and sky access unless from a spawner. */
    static boolean surface(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL
                && (MobSpawnType.ignoresLightRequirements(reason) || Monster.isDarkEnoughToSpawn(level, pos, random))
                && Mob.checkMobSpawnRules(type, level, reason, pos, random)
                && (MobSpawnType.isSpawner(reason) || level.canSeeSky(pos));
    }

    static void register(IEventBus modBus) {
        // 26.x's spawn eggs have their own textures; white tints leave them as drawn.
        Content.ITEM.put("camel_husk_spawn_egg", Content.ITEMS.register("camel_husk_spawn_egg",
                () -> new DeferredSpawnEggItem(CAMEL_HUSK, 0xFFFFFF, 0xFFFFFF, new Item.Properties())));
        Content.ITEM.put("parched_spawn_egg", Content.ITEMS.register("parched_spawn_egg",
                () -> new DeferredSpawnEggItem(PARCHED, 0xFFFFFF, 0xFFFFFF, new Item.Properties())));
        BIOME_MODIFIERS.register("spawn_weight", () -> SpawnWeightModifier.CODEC);
        BIOME_MODIFIERS.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> {
            e.put(CAMEL_HUSK.get(), Camel.createAttributes().build());
            e.put(PARCHED.get(), Parched.createAttributes().build());
        });
        // SpawnPlacements: both ON_GROUND, MOTION_BLOCKING_NO_LEAVES, Monster::checkSurfaceMonstersSpawnRules (monster
        // rules plus sky access unless from a spawner: 1.21.1 has it as Husk.checkHuskSpawnRules).
        modBus.addListener(RegisterSpawnPlacementsEvent.class, e -> {
            e.register(CAMEL_HUSK.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Husks::surface,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
            e.register(PARCHED.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Husks::surface,
                    RegisterSpawnPlacementsEvent.Operation.REPLACE);
        });
    }
}
