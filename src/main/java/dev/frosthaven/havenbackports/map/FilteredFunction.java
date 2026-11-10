package dev.frosthaven.havenbackports.map;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctions;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * 26.x's FilteredFunction (minecraft:filtered with on_pass / on_fail) as haven_backports:filtered; 1.21.1's
 * minecraft:filtered has one modifier, run on a match only. Mojang's explorer maps use on_fail to discard a map
 * that found no structure.
 */
public final class FilteredFunction extends LootItemConditionalFunction {
    public static final MapCodec<FilteredFunction> CODEC = RecordCodecBuilder.mapCodec(i -> commonFields(i).and(i.group(
            ItemPredicate.CODEC.fieldOf("item_filter").forGetter(f -> f.filter),
            LootItemFunctions.ROOT_CODEC.optionalFieldOf("on_pass").forGetter(f -> f.onPass),
            LootItemFunctions.ROOT_CODEC.optionalFieldOf("on_fail").forGetter(f -> f.onFail)
    )).apply(i, FilteredFunction::new));

    private final ItemPredicate filter;
    private final Optional<LootItemFunction> onPass;
    private final Optional<LootItemFunction> onFail;

    private FilteredFunction(List<LootItemCondition> conditions, ItemPredicate filter, Optional<LootItemFunction> onPass,
                             Optional<LootItemFunction> onFail) {
        super(conditions);
        this.filter = filter;
        this.onPass = onPass;
        this.onFail = onFail;
    }

    @Override
    public LootItemFunctionType<FilteredFunction> getType() {
        return ExplorerMaps.FILTERED.get();
    }

    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        Optional<LootItemFunction> function = this.filter.test(stack) ? this.onPass : this.onFail;
        return function.isPresent() ? function.get().apply(stack, context) : stack;
    }

    @Override
    public void validate(ValidationContext context) {
        super.validate(context);
        this.onPass.ifPresent(f -> f.validate(context.forChild(".on_pass")));
        this.onFail.ifPresent(f -> f.validate(context.forChild(".on_fail")));
    }
}
