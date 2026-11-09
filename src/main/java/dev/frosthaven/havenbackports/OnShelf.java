package dev.frosthaven.havenbackports;

import net.minecraft.world.item.ItemDisplayContext;

/**
 * 26.x's {@code ItemDisplayContext.ON_SHELF}, added to 1.21.1's enum by META-INF/enumextensions.json as
 * {@code haven_backports:on_shelf} with no fallback: a model without that display entry draws untransformed, as in 26.x.
 */
public final class OnShelf {
    private static ItemDisplayContext context;

    private OnShelf() {}

    public static ItemDisplayContext context() {
        if (context == null) {
            context = ItemDisplayContext.valueOf("HAVEN_BACKPORTS_ON_SHELF");
        }
        return context;
    }
}
