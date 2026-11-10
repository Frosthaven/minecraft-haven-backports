package dev.frosthaven.havenbackports.bundle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 26.x's TransmuteRecipe (minecraft:crafting_transmute) as haven_backports:crafting_transmute, for the bundle dye
 * recipes: one input and one material in any grid, the result is the input turned into the result item with every
 * component kept (a dyed bundle keeps its contents); it does not match when that would give back the same item.
 * Only the fields Mojang's bundle recipes use (group, category, input, material, result id): material_count and
 * add_material_count_to_result (26.x's map cloning) are left out, as map cloning stays 1.21.1's recipe.
 */
public final class TransmuteRecipe implements CraftingRecipe {
    private static final Codec<Item> RESULT = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(item -> item)).apply(i, item -> item));
    static final MapCodec<TransmuteRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r -> r.category),
            Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(r -> r.input),
            Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter(r -> r.material),
            RESULT.fieldOf("result").forGetter(r -> r.result)
    ).apply(i, TransmuteRecipe::new));
    static final StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, r -> r.group,
            CraftingBookCategory.STREAM_CODEC, r -> r.category,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.input,
            Ingredient.CONTENTS_STREAM_CODEC, r -> r.material,
            ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM), r -> r.result,
            TransmuteRecipe::new);
    static final RecipeSerializer<TransmuteRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public MapCodec<TransmuteRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    };

    private final String group;
    private final CraftingBookCategory category;
    private final Ingredient input;
    private final Ingredient material;
    private final Item result;

    private TransmuteRecipe(String group, CraftingBookCategory category, Ingredient input, Ingredient material, Item result) {
        this.group = group;
        this.category = category;
        this.input = input;
        this.material = material;
        this.result = result;
    }

    @Override
    public boolean matches(CraftingInput grid, Level level) {
        if (grid.ingredientCount() != 2) {
            return false;
        }
        ItemStack found = null;
        int materials = 0;
        for (int slot = 0; slot < grid.size(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (this.input.test(stack)) {
                if (found != null) {
                    return false;
                }
                found = stack;
            } else if (!this.material.test(stack) || ++materials > 1) {
                return false;
            }
        }
        return found != null && materials == 1 && !ItemStack.isSameItemSameComponents(found, found.transmuteCopy(this.result, 1));
    }

    @Override
    public ItemStack assemble(CraftingInput grid, HolderLookup.Provider registries) {
        for (int slot = 0; slot < grid.size(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (this.input.test(stack)) {
                return stack.transmuteCopy(this.result, 1);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return new ItemStack(this.result);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, this.input, this.material);
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Bundles.TRANSMUTE.get();
    }
}
