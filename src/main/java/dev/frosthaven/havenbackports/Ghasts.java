package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.ghast.DriedGhastBlock;
import dev.frosthaven.havenbackports.ghast.HappyGhast;
import dev.frosthaven.havenbackports.ghast.HappyGhastAi;
import dev.frosthaven.havenbackports.ghast.HarnessItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The happy ghast, its harnesses and the dried ghast, as 26.4's EntityTypes, Items, Blocks and SoundEvents define them.
 * The dried ghast comes from its recipe, piglin bartering (26.x's table, data) and nether fossils (NetherFossilPieceMixin).
 */
public final class Ghasts {
    public static final TagKey<Item> HAPPY_GHAST_FOOD = item("happy_ghast_food");
    public static final TagKey<Item> HAPPY_GHAST_TEMPT_ITEMS = item("happy_ghast_tempt_items");
    public static final TagKey<Item> HARNESSES = item("harnesses");
    public static final TagKey<EntityType<?>> CAN_EQUIP_HARNESS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("can_equip_harness"));
    public static final TagKey<EntityType<?>> FOLLOWABLE_FRIENDLY_MOBS = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("followable_friendly_mobs"));
    public static final TagKey<Block> TRIGGERS_AMBIENT_DRIED_GHAST_BLOCK_SOUNDS = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("triggers_ambient_dried_ghast_block_sounds"));
    public static final TagKey<Block> HAPPY_GHAST_AVOIDS = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("happy_ghast_avoids"));

    public static final DeferredHolder<SoundEvent, SoundEvent> HAPPY_GHAST_AMBIENT = Content.sound("entity.happy_ghast.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAPPY_GHAST_DEATH = Content.sound("entity.happy_ghast.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAPPY_GHAST_HURT = Content.sound("entity.happy_ghast.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> HAPPY_GHAST_RIDING = Content.sound("entity.happy_ghast.riding");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARNESS_EQUIP = Content.sound("entity.happy_ghast.equip");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARNESS_UNEQUIP = Content.sound("entity.happy_ghast.unequip");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARNESS_GOGGLES_UP = Content.sound("entity.happy_ghast.harness_goggles_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> HARNESS_GOGGLES_DOWN = Content.sound("entity.happy_ghast.harness_goggles_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> GHASTLING_AMBIENT = Content.sound("entity.ghastling.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> GHASTLING_DEATH = Content.sound("entity.ghastling.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> GHASTLING_HURT = Content.sound("entity.ghastling.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> GHASTLING_SPAWN = Content.sound("entity.ghastling.spawn");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIED_GHAST_AMBIENT = Content.sound("block.dried_ghast.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIED_GHAST_AMBIENT_WATER = Content.sound("block.dried_ghast.ambient_water");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIED_GHAST_PLACE = Content.sound("block.dried_ghast.place");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIED_GHAST_PLACE_IN_WATER = Content.sound("block.dried_ghast.place_in_water");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRIED_GHAST_TRANSITION = Content.sound("block.dried_ghast.transition");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAD_TIED = Content.sound("item.lead.tied");
    public static final DeferredHolder<SoundEvent, SoundEvent> LEAD_BREAK = Content.sound("item.lead.break");

    /** EntityTypes.HAPPY_GHAST: creature, 4 x 4, eye 2.6, four passenger seats, riding offset 0.5, tracking range 10. */
    public static final DeferredHolder<EntityType<?>, EntityType<HappyGhast>> HAPPY_GHAST = Content.ENTITY_TYPES.register("happy_ghast",
            () -> EntityType.Builder.of(HappyGhast::new, MobCategory.CREATURE).sized(4.0F, 4.0F).eyeHeight(2.6F)
                    .passengerAttachments(new Vec3(0.0, 4.0, 1.7), new Vec3(-1.7, 4.0, 0.0), new Vec3(0.0, 4.0, -1.7), new Vec3(1.7, 4.0, 0.0))
                    .ridingOffset(0.5F).clientTrackingRange(10).build(HavenBackports.ID + ":happy_ghast"));

    /** SensorType.FOOD_TEMPTATIONS and NEAREST_ADULT_ANY_TYPE (26.x; ours until 1.21.1 has them). */
    public static final DeferredHolder<SensorType<?>, SensorType<HappyGhastAi.FoodTemptations>> FOOD_TEMPTATIONS =
            Content.SENSOR_TYPES.register("food_temptations", () -> new SensorType<>(HappyGhastAi.FoodTemptations::new));
    public static final DeferredHolder<SensorType<?>, SensorType<HappyGhastAi.AdultAnyType>> NEAREST_ADULT_ANY_TYPE =
            Content.SENSOR_TYPES.register("nearest_adult_any_type", () -> new SensorType<>(HappyGhastAi.AdultAnyType::new));

    /** Stats.HAPPY_GHAST_ONE_CM ("happy_ghast_one_cm", distance), counted by ServerPlayerMixin. */
    static final net.neoforged.neoforge.registries.DeferredRegister<ResourceLocation> STATS =
            net.neoforged.neoforge.registries.DeferredRegister.create(Registries.CUSTOM_STAT, HavenBackports.ID);
    public static final DeferredHolder<ResourceLocation, ResourceLocation> HAPPY_GHAST_ONE_CM =
            STATS.register("happy_ghast_one_cm", () -> ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "happy_ghast_one_cm"));

    public static final List<DeferredItem<Item>> HARNESS = new ArrayList<>();
    public static DeferredBlock<Block> DRIED_GHAST;

    private Ghasts() {}

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace(name));
    }

    static void register(IEventBus modBus) {
        // Blocks.DRIED_GHAST: of().mapColor(COLOR_GRAY).forceSolidOn().instabreak().sound(DRIED_GHAST).noOcclusion().randomTicks().
        // The dried_ghast sound set has break, step and fall only (BlockSoundCollection withPlace(false).withHit(false)); the
        // place sound plays from setPlacedBy. 1.21.1's SoundType needs all five: the others are intentionally empty.
        DeferredSoundType sound = new DeferredSoundType(1.0F, 1.0F, Content.sound("block.dried_ghast.break"), Content.sound("block.dried_ghast.step"),
                () -> SoundEvents.EMPTY, () -> SoundEvents.EMPTY, Content.sound("block.dried_ghast.fall"));
        DRIED_GHAST = Content.block("dried_ghast", DriedGhastBlock::new, () -> Properties.of().mapColor(MapColor.COLOR_GRAY).forceSolidOn()
                .instabreak().sound(sound).noOcclusion().randomTicks());
        for (DyeColor color : DyeColor.values()) {   // Items.HARNESS: stacksTo(1), Equippable.harness(color)
            DeferredItem<Item> harness = Content.ITEMS.register(color.getName() + "_harness", () -> new HarnessItem(color, new Item.Properties().stacksTo(1)));
            Content.ITEM.put(color.getName() + "_harness", harness);
            HARNESS.add(harness);
        }
        // 26.x's spawn egg has its own texture (item/happy_ghast_spawn_egg); white tints leave it as drawn.
        Content.ITEM.put("happy_ghast_spawn_egg", Content.ITEMS.register("happy_ghast_spawn_egg",
                () -> new DeferredSpawnEggItem(HAPPY_GHAST, 0xFFFFFF, 0xFFFFFF, new Item.Properties())));
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(HAPPY_GHAST.get(), HappyGhast.createAttributes().build()));
        STATS.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() -> {
            HarnessItem.registerDispenser(HARNESS.stream().map(DeferredItem::get).toList());
            net.minecraft.stats.Stats.CUSTOM.get(HAPPY_GHAST_ONE_CM.get(), net.minecraft.stats.StatFormatter.DISTANCE);
        }));
    }
}
