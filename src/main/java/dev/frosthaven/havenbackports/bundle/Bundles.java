package dev.frosthaven.havenbackports.bundle;

import dev.frosthaven.havenbackports.HavenBackports;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Bundles as 26.4 has them. 1.21.1's minecraft:bundle item carries no feature flag (Items.BUNDLE has no
 * requiredFeatures); only its creative tab entry and its recipe sit behind the experimental "bundle" pack. So the
 * item works in every world as it is, on a dedicated server too, and nothing per world changes: the mod adds 26.x's
 * recipe (string over leather), its creative tab place and village loot, and gives every bundle 26.x's behaviour
 * (BundleLogic, through BundleItemMixin): the selected item, dropping one item at a time, the new tooltip.
 * The 16 dyed bundles are Items.DYED_BUNDLE: BundleItem, stacksTo(1), empty BUNDLE_CONTENTS.
 */
public final class Bundles {
    /** 26.x ItemTags.BUNDLES: the bundle and the 16 dyed ones (the dye recipes' input, the GUI scroll's match). */
    public static final TagKey<Item> BUNDLES = ItemTags.create(ResourceLocation.withDefaultNamespace("bundles"));
    public static final List<String> DYED = new ArrayList<>();

    static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HavenBackports.ID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HavenBackports.ID);
    static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, HavenBackports.ID);

    /**
     * 26.x BundleContents.selectedItem: which item a bundle gives out next. 26.x keeps it in the contents but never
     * saves it (the codec writes the items only); 1.21.1's BundleContents is a final vanilla record, so it lives
     * here, sent to clients and never saved, as in 26.x.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SELECTED =
            COMPONENTS.registerComponentType("bundle_selected_item", b -> b.networkSynchronized(ByteBufCodecs.VAR_INT));
    /** SoundEvents.BUNDLE_INSERT_FAIL (26.x, item.bundle.insert_fail). */
    public static final DeferredHolder<SoundEvent, SoundEvent> INSERT_FAIL = SOUNDS.register("item.bundle.insert_fail",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "item.bundle.insert_fail")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TransmuteRecipe>> TRANSMUTE =
            SERIALIZERS.register("crafting_transmute", () -> TransmuteRecipe.SERIALIZER);

    private Bundles() {}

    static {
        for (DyeColor c : DyeColor.values()) {
            DYED.add(c.getName() + "_bundle");
        }
    }

    public static Item.Properties properties() {
        return new Item.Properties().stacksTo(1).component(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
    }

    public static Item make(Item.Properties properties) {
        return new BundleItem(properties);
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
        SERIALIZERS.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1").playToServer(
                SelectBundleItemPayload.TYPE, SelectBundleItemPayload.STREAM_CODEC, SelectBundleItemPayload::handle));
    }
}
