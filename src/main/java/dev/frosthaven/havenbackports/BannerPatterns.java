package dev.frosthaven.havenbackports;

import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BannerPatternItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * Field Masoned and Bordure Indented banner patterns, as 26.4's Items defines them: stacksTo(1) and
 * PROVIDES_BANNER_PATTERNS = #minecraft:pattern_item/field_masoned (bricks) or /bordure_indented (curly_border).
 * 1.21.1's loom reads the pattern from BannerPatternItem's tag, which is the same thing. 26.x names these two
 * items in full and gives them no ".desc" line (the lang has none), so the description tooltip of 1.21.1's
 * BannerPatternItem is left out.
 */
final class BannerPatterns {
    private BannerPatterns() {}

    static void register() {
        pattern("field_masoned_banner_pattern", "field_masoned");
        pattern("bordure_indented_banner_pattern", "bordure_indented");
    }

    private static void pattern(String id, String tag) {
        TagKey<BannerPattern> key = TagKey.create(Registries.BANNER_PATTERN, ResourceLocation.withDefaultNamespace("pattern_item/" + tag));
        Content.ITEM.put(id, Content.ITEMS.register(id, () -> new Named(key, new Item.Properties().stacksTo(1))));
    }

    /** A pattern item whose full name is its item name (26.x), without 1.21.1's description line. */
    static final class Named extends BannerPatternItem {
        Named(TagKey<BannerPattern> pattern, Item.Properties properties) {
            super(pattern, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        }
    }
}
