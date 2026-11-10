package dev.frosthaven.havenbackports.variant;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceKey;

/**
 * Implemented by the vanilla pig, cow, chicken (climate variant) and wolf (sound variant) through their mixins, which
 * hold the synced accessor (defined in the class's own static initializer, so server and client number it alike).
 * AgeableMobMixin defines, saves and loads it for all four.
 */
public interface VariantHolder<T> {
    EntityDataAccessor<Holder<T>> haven_backports$accessor();

    ResourceKey<Registry<T>> haven_backports$registry();

    /** The variant a new mob has (26.x: temperate, classic). */
    String haven_backports$defaultName();

    /** The save key (26.x: "variant", the wolf's "sound_variant"). */
    String haven_backports$saveKey();

    Holder<T> haven_backports$variant();

    void haven_backports$setVariant(Holder<T> variant);
}
