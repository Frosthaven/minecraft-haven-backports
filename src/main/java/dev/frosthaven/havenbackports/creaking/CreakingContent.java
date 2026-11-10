package dev.frosthaven.havenbackports.creaking;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Content;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.PaleGarden;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Illusioner;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The creaking and the creaking heart (26.x EntityTypes.CREAKING, Blocks.CREAKING_HEART, BlockEntityTypes.CREAKING_HEART,
 * Items.CREAKING_SPAWN_EGG, ParticleTypes.BLOCK_CRUMBLE, their SoundEvents and BlockSoundSets.CREAKING_HEART), and
 * what 26.x code elsewhere does for them: DefaultAttributes, SpawnPlacements, and the illagers' AvoidEntityGoal
 * (Evoker, Vindicator, Pillager, Illusioner). The parrot's imitation is data (neoforge:parrot_imitations, written by
 * tools/creaking.py). Ids are Mojang's, in the haven_backports namespace.
 */
public final class CreakingContent {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HavenBackports.ID);
    private static final DeferredRegister<SensorType<?>> SENSORS = DeferredRegister.create(Registries.SENSOR_TYPE, HavenBackports.ID);
    private static final DeferredRegister<MemoryModuleType<?>> MEMORIES = DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, HavenBackports.ID);

    // SoundEvents (26.x): the creaking, the heart, and the heart's block sound set (data/minecraft/block_sound_set)
    public static final DeferredHolder<SoundEvent, SoundEvent> CREAKING_ACTIVATE = Content.sound("entity.creaking.activate"),
            CREAKING_AMBIENT = Content.sound("entity.creaking.ambient"), CREAKING_ATTACK = Content.sound("entity.creaking.attack"),
            CREAKING_DEACTIVATE = Content.sound("entity.creaking.deactivate"), CREAKING_DEATH = Content.sound("entity.creaking.death"),
            CREAKING_FREEZE = Content.sound("entity.creaking.freeze"), CREAKING_SPAWN = Content.sound("entity.creaking.spawn"),
            CREAKING_STEP = Content.sound("entity.creaking.step"), CREAKING_SWAY = Content.sound("entity.creaking.sway"),
            CREAKING_TWITCH = Content.sound("entity.creaking.twitch"), CREAKING_UNFREEZE = Content.sound("entity.creaking.unfreeze"),
            PARROT_IMITATE_CREAKING = Content.sound("entity.parrot.imitate.creaking"),
            CREAKING_HEART_HURT = Content.sound("block.creaking_heart.hurt"), CREAKING_HEART_IDLE = Content.sound("block.creaking_heart.idle"),
            CREAKING_HEART_SPAWN = Content.sound("block.creaking_heart.spawn");
    private static final DeferredSoundType CREAKING_HEART_SOUND = new DeferredSoundType(1.0F, 1.0F, Content.sound("block.creaking_heart.break"),
            Content.sound("block.creaking_heart.step"), Content.sound("block.creaking_heart.place"), Content.sound("block.creaking_heart.hit"),
            Content.sound("block.creaking_heart.fall"));

    /** EntityType.Builder.of(Creaking::new, MONSTER).sized(0.9, 2.7).eyeHeight(2.3).clientTrackingRange(8).notInPeaceful()
     *  (1.21.1's Monster already leaves in peaceful). */
    public static final DeferredHolder<EntityType<?>, EntityType<Creaking>> CREAKING = ENTITIES.register("creaking",
            () -> EntityType.Builder.of(Creaking::new, MobCategory.MONSTER).sized(0.9F, 2.7F).eyeHeight(2.3F).clientTrackingRange(8)
                    .build(HavenBackports.ID + ":creaking"));

    /** 26.x PlayerSensor for the creaking (CreakingPlayerSensor), and the memory 1.21.1 lacks. */
    static final DeferredHolder<SensorType<?>, SensorType<CreakingPlayerSensor>> CREAKING_PLAYER_SENSOR = SENSORS.register("nearest_players",
            () -> new SensorType<>(CreakingPlayerSensor::new));
    static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<List<Player>>> NEAREST_VISIBLE_ATTACKABLE_PLAYERS =
            MEMORIES.register("nearest_visible_attackable_players", () -> new MemoryModuleType<>(Optional.empty()));

    /** 26.x ParticleTypes.BLOCK_CRUMBLE (TerrainParticle.CrumblingProvider on the client). */
    public static final DeferredHolder<ParticleType<?>, ParticleType<BlockParticleOption>> BLOCK_CRUMBLE = Content.PARTICLES.register("block_crumble",
            () -> new ParticleType<BlockParticleOption>(false) {
                @Override
                public MapCodec<BlockParticleOption> codec() {
                    return BlockParticleOption.codec(this);
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, BlockParticleOption> streamCodec() {
                    return BlockParticleOption.streamCodec(this);
                }
            });

    /** CREAKING_HEART: of().mapColor(COLOR_ORANGE).instrument(BASEDRUM).strength(10).sound(CREAKING_HEART). */
    public static final DeferredBlock<Block> CREAKING_HEART = Content.block("creaking_heart", CreakingHeartBlock::new,
            () -> Properties.of().mapColor(MapColor.COLOR_ORANGE).instrument(NoteBlockInstrument.BASEDRUM).strength(10.0F).sound(CREAKING_HEART_SOUND));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CreakingHeartBlockEntity>> CREAKING_HEART_ENTITY =
            Content.BLOCK_ENTITIES.register("creaking_heart", () -> BlockEntityType.Builder.of(CreakingHeartBlockEntity::new, CREAKING_HEART.get()).build(null));

    /** Items.CREAKING_SPAWN_EGG (registerSpawnEgg): 26.x's own item model, no tints. */
    public static final DeferredItem<Item> CREAKING_SPAWN_EGG = Content.ITEMS.register("creaking_spawn_egg",
            () -> new DeferredSpawnEggItem(CREAKING, 0xFFFFFF, 0xFFFFFF, new Item.Properties()));

    private CreakingContent() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        SENSORS.register(modBus);
        MEMORIES.register(modBus);
        Content.ITEM.put("creaking_spawn_egg", CREAKING_SPAWN_EGG);
        PaleGarden.CREAKING_HEART = CREAKING_HEART;
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(CREAKING.get(), Creaking.createAttributes().build()));
        // SpawnPlacements: ON_GROUND, MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules
        modBus.addListener(RegisterSpawnPlacementsEvent.class, e -> e.register(CREAKING.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Monster::checkMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE));
        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, CreakingContent::illagersAvoidCreaking);
    }

    /**
     * 26.x Evoker (3, 8, 0.6, 1.0), Vindicator (1, 8, 1.0, 1.2), Pillager (1, 8, 1.0, 1.2) and Illusioner (3, 8, 1.0, 1.2)
     * registerGoals add AvoidEntityGoal(Creaking). The other goals keep 1.21.1's priorities: 26.x moved the
     * vindicator's door and attack goals one step down to make room, and goals of one priority with no shared flag
     * (the door goals have none) run side by side, so the avoid goal ranks the same against each goal it competes with.
     */
    private static void illagersAvoidCreaking(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) {
            return;
        }
        if (e.getEntity() instanceof Evoker m) {
            m.goalSelector.addGoal(3, new AvoidEntityGoal<>(m, Creaking.class, 8.0F, 0.6, 1.0));
        } else if (e.getEntity() instanceof Vindicator m) {
            m.goalSelector.addGoal(1, new AvoidEntityGoal<>(m, Creaking.class, 8.0F, 1.0, 1.2));
        } else if (e.getEntity() instanceof Pillager m) {
            m.goalSelector.addGoal(1, new AvoidEntityGoal<>(m, Creaking.class, 8.0F, 1.0, 1.2));
        } else if (e.getEntity() instanceof Illusioner m) {
            m.goalSelector.addGoal(3, new AvoidEntityGoal<>(m, Creaking.class, 8.0F, 1.0, 1.2));
        }
    }
}
