package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.golem.CopperGolem;
import dev.frosthaven.havenbackports.golem.CopperGolemState;
import dev.frosthaven.havenbackports.golem.CopperGolemStatueBlock;
import dev.frosthaven.havenbackports.golem.CopperGolemStatueBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The copper golem and its statues, as 26.4's EntityTypes, Blocks (COPPER_GOLEM_STATUE, a WeatheringCopperCollection),
 * SoundEvents, MemoryModuleType and EntityDataSerializers define them. Golems come from a carved pumpkin on a copper
 * block (CarvedPumpkinBlockMixin; the block becomes the matching copper chest) and from an unaffected statue and an
 * axe; statues come from a fully oxidized golem.
 */
public final class Golems {
    static final DeferredRegister<EntityDataSerializer<?>> SERIALIZERS = DeferredRegister.create(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS, HavenBackports.ID);
    static final DeferredRegister<MemoryModuleType<?>> MEMORIES = DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, HavenBackports.ID);

    /** EntityDataSerializers.WEATHERING_COPPER_STATE and COPPER_GOLEM_STATE (26.x; ours until 1.21.1 has them). */
    public static final EntityDataSerializer<WeatherState> WEATHER_STATE_SERIALIZER = EntityDataSerializer.forValueType(CopperGolemState.WEATHER_STREAM_CODEC);
    public static final EntityDataSerializer<CopperGolemState> STATE_SERIALIZER = EntityDataSerializer.forValueType(CopperGolemState.STREAM_CODEC);

    /** MemoryModuleType.TRANSPORT_ITEMS_COOLDOWN_TICKS (no codec), VISITED_BLOCK_POSITIONS, UNREACHABLE_TRANSPORT_BLOCK_POSITIONS. */
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> TRANSPORT_ITEMS_COOLDOWN_TICKS =
            MEMORIES.register("transport_items_cooldown_ticks", () -> new MemoryModuleType<>(Optional.empty()));
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Set<GlobalPos>>> VISITED_BLOCK_POSITIONS =
            MEMORIES.register("visited_block_positions", () -> new MemoryModuleType<>(Optional.of(GlobalPos.CODEC.listOf().xmap(HashSet::new, ArrayList::new))));
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Set<GlobalPos>>> UNREACHABLE_TRANSPORT_BLOCK_POSITIONS =
            MEMORIES.register("unreachable_transport_block_positions", () -> new MemoryModuleType<>(Optional.of(GlobalPos.CODEC.listOf().xmap(HashSet::new, ArrayList::new))));

    public static final TagKey<Block> COPPER = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("copper"));
    public static final TagKey<Block> COPPER_GOLEM_STATUES = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("copper_golem_statues"));
    public static final TagKey<Item> SHEARABLE_FROM_COPPER_GOLEM = TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("shearable_from_copper_golem"));
    public static final TagKey<EntityType<?>> ACCEPTS_IRON_GOLEM_GIFT = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("accepts_iron_golem_gift"));
    public static final TagKey<EntityType<?>> CANDIDATE_FOR_IRON_GOLEM_GIFT = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("candidate_for_iron_golem_gift"));

    public static final DeferredHolder<SoundEvent, SoundEvent> SPAWN = Content.sound("entity.copper_golem.spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHEAR = Content.sound("entity.copper_golem.shear");
    public static final DeferredHolder<SoundEvent, SoundEvent> BECOME_STATUE = Content.sound("entity.copper_golem_become_statue");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_GET = Content.sound("entity.copper_golem.no_item_get");   // SoundEvents.COPPER_GOLEM_ITEM_GET
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_NO_GET = Content.sound("entity.copper_golem.no_item_no_get");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_DROP = Content.sound("entity.copper_golem.item_drop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ITEM_NO_DROP = Content.sound("entity.copper_golem.item_no_drop");

    /** CopperGolemOxidationLevels: spin, hurt, death and step sounds and the textures per stage (exposed shares unaffected's sounds). */
    public record OxidationLevel(Supplier<SoundEvent> spin, Supplier<SoundEvent> hurt, Supplier<SoundEvent> death, Supplier<SoundEvent> step,
                                 ResourceLocation texture, ResourceLocation eyeTexture) {}

    private static final OxidationLevel[] LEVELS = new OxidationLevel[4];

    public static final DeferredHolder<EntityType<?>, EntityType<CopperGolem>> COPPER_GOLEM = Content.ENTITY_TYPES.register("copper_golem",
            () -> EntityType.Builder.of(CopperGolem::new, MobCategory.MISC).sized(0.49F, 0.98F).eyeHeight(0.8125F).clientTrackingRange(10)
                    .build(HavenBackports.ID + ":copper_golem"));

    /** Statues by stage: [unwaxed ... , waxed ...] in WeatherState order. */
    public static final List<DeferredBlock<Block>> STATUES = new ArrayList<>();
    public static final DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, BlockEntityType<CopperGolemStatueBlockEntity>> STATUE_ENTITY =
            Content.BLOCK_ENTITIES.register("copper_golem_statue", () -> BlockEntityType.Builder.of(CopperGolemStatueBlockEntity::new,
                    STATUES.stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    private Golems() {}

    public static OxidationLevel level(WeatherState state) {
        return LEVELS[state.ordinal()];
    }

    public static Block statue(WeatherState state, boolean waxed) {
        return STATUES.get(state.ordinal() + (waxed ? 4 : 0)).get();
    }

    static void register(IEventBus modBus) {
        for (WeatherState s : WeatherState.values()) {
            String stage = s == WeatherState.UNAFFECTED || s == WeatherState.EXPOSED ? "copper_golem" : "copper_golem_" + s.getSerializedName();
            String suffix = s == WeatherState.UNAFFECTED ? "" : "_" + s.getSerializedName();
            LEVELS[s.ordinal()] = new OxidationLevel(Content.soundOnce(stage + ".spin"), Content.soundOnce(stage + ".hurt"),
                    Content.soundOnce(stage + ".death"), Content.soundOnce(stage + ".step"),
                    ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/copper_golem/copper_golem" + suffix + ".png"),
                    ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "textures/entity/copper_golem/copper_golem_eyes" + suffix + ".png"));
        }
        // COPPER_GOLEM_STATUE: of().mapColor(that stage's copper block).strength(3, 6).sound(COPPER_GOLEM_STATUE).pushReaction(POPPED).noOcclusion()
        DeferredSoundType sound = new DeferredSoundType(1.0F, 1.0F, Content.sound("block.copper_golem_statue.break"), Content.sound("block.copper_golem_statue.step"),
                Content.sound("block.copper_golem_statue.place"), Content.sound("block.copper_golem_statue.hit"), Content.sound("block.copper_golem_statue.fall"));
        for (boolean waxed : new boolean[]{false, true}) {
            for (WeatherState s : WeatherState.values()) {
                String name = (waxed ? "waxed_" : "") + (s == WeatherState.UNAFFECTED ? "" : s.getSerializedName() + "_") + "copper_golem_statue";
                ResourceLocation copper = ResourceLocation.withDefaultNamespace(s == WeatherState.UNAFFECTED ? "copper_block" : s.getSerializedName() + "_copper");
                Supplier<Properties> props = () -> Properties.of().mapColor(st -> BuiltInRegistries.BLOCK.get(copper).defaultMapColor())
                        .strength(3.0F, 6.0F).sound(sound).pushReaction(PushReaction.DESTROY).noOcclusion();
                DeferredBlock<Block> statue = Content.BLOCKS.register(name, () -> waxed ? new CopperGolemStatueBlock(s, props.get())
                        : new CopperGolemStatueBlock.Weathering(s, props.get()));
                Content.ITEM.put(name, Content.ITEMS.register(name, () -> new BlockItem(statue.get(), new Item.Properties())));
                STATUES.add(statue);
            }
        }
        Content.ITEM.put("copper_golem_spawn_egg", Content.ITEMS.register("copper_golem_spawn_egg",
                () -> new DeferredSpawnEggItem(COPPER_GOLEM, 0xFFFFFF, 0xFFFFFF, new Item.Properties())));
        SERIALIZERS.register("weathering_copper_state", () -> WEATHER_STATE_SERIALIZER);
        SERIALIZERS.register("copper_golem_state", () -> STATE_SERIALIZER);
        SERIALIZERS.register(modBus);
        MEMORIES.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(COPPER_GOLEM.get(), CopperGolem.createAttributes().build()));
    }
}
