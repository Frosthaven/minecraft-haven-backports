package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.decoration.Cushion;
import dev.frosthaven.havenbackports.decoration.CushionItem;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Cushions (26.4's EntityTypes.CUSHION and Items.CUSHION: 16 colors, stacks of 16, crafted from 3 wool slabs of a
 * color, re-dyed with any dye; furnace fuel as wool slabs, from the generated furnace_fuels map). A different thing
 * from Handcrafted's cushion blocks: kept apart, no merge.
 */
public final class Cushions {
    public static final TagKey<Block> CUSHION_USES_COLLISION_SHAPE = TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("cushion_uses_collision_shape"));
    public static final DeferredHolder<SoundEvent, SoundEvent> BREAK = Content.sound("entity.cushion.break");
    public static final DeferredHolder<SoundEvent, SoundEvent> GET_UP = Content.sound("entity.cushion.get_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLACE = Content.sound("entity.cushion.place");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIT = Content.sound("entity.cushion.sit");

    /** EntityTypes.CUSHION: misc, 1 x 0.25, tracking range 10, never re-sent (updateInterval max, no deltas), no loot table. */
    public static final DeferredHolder<EntityType<?>, EntityType<Cushion>> CUSHION = Content.ENTITY_TYPES.register("cushion",
            () -> EntityType.Builder.<Cushion>of(Cushion::new, MobCategory.MISC).sized(1.0F, 0.25F).clientTrackingRange(10)
                    .updateInterval(Integer.MAX_VALUE).setShouldReceiveVelocityUpdates(false).build(HavenBackports.ID + ":cushion"));

    private static final Map<DyeColor, DeferredItem<Item>> ITEMS = new EnumMap<>(DyeColor.class);

    private Cushions() {}

    public static Item item(DyeColor color) {
        return ITEMS.get(color).get();
    }

    static void register() {
        for (DyeColor color : DyeColor.values()) {
            String name = color.getName() + "_cushion";
            DeferredItem<Item> item = Content.ITEMS.register(name, () -> new CushionItem(color, new Item.Properties().stacksTo(16)));
            Content.ITEM.put(name, item);
            ITEMS.put(color, item);
        }
    }
}
