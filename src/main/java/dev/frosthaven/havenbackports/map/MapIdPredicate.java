package dev.frosthaven.havenbackports.map;

import com.mojang.serialization.Codec;
import net.minecraft.advancements.critereon.ItemSubPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

/** 26.x's minecraft:map_id item predicate (DataComponentPredicates AnyValue of MAP_ID) as haven_backports:map_id:
 *  the item has a map id. */
public record MapIdPredicate() implements ItemSubPredicate {
    public static final MapIdPredicate INSTANCE = new MapIdPredicate();
    public static final Codec<MapIdPredicate> CODEC = Codec.unit(INSTANCE);

    @Override
    public boolean matches(ItemStack stack) {
        return stack.has(DataComponents.MAP_ID);
    }
}
