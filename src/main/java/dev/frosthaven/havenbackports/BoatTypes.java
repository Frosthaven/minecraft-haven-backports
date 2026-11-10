package dev.frosthaven.havenbackports;

import java.util.function.Supplier;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;

/**
 * 26.x gives each wood its own boat entity types; 1.21.1 has one boat entity with a Boat.Type enum, which NeoForge
 * makes extensible (META-INF/enumextensions.json names these proxies). Textures resolve to
 * haven_backports:textures/entity/{boat,chest_boat}/&lt;wood&gt;.png and model layers to haven_backports:boat/&lt;wood&gt;
 * (BoatRenderer, ModelLayers), both from the type's name. Arguments are suppliers: they resolve after registration.
 */
public final class BoatTypes {
    public static final EnumProxy<Boat.Type> POPLAR_PROXY = new EnumProxy<>(Boat.Type.class,
            (Supplier<Block>) () -> Woods.POPLAR.planks().get(), HavenBackports.ID + ":poplar",
            (Supplier<Item>) () -> Woods.POPLAR.boat().get(), (Supplier<Item>) () -> Woods.POPLAR.chestBoat().get(),
            (Supplier<Item>) () -> Items.STICK, false);

    private BoatTypes() {}

    public static Boat.Type poplar() {
        return POPLAR_PROXY.getValue();
    }
}
