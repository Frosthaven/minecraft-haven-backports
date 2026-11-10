package dev.frosthaven.havenbackports.map;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** 26.x's DiscardItem (minecraft:discard) as haven_backports:discard: the item becomes empty. */
public final class DiscardItem extends LootItemConditionalFunction {
    public static final MapCodec<DiscardItem> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).apply(i, DiscardItem::new));

    private DiscardItem(List<LootItemCondition> conditions) {
        super(conditions);
    }

    @Override
    public LootItemFunctionType<DiscardItem> getType() {
        return ExplorerMaps.DISCARD.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        return ItemStack.EMPTY;
    }
}
