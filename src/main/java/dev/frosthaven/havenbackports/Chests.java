package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.CopperChestBlock;
import dev.frosthaven.havenbackports.block.WeatheringCopperChestBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Copper chests (26.4's Blocks.COPPER_CHEST, a WeatheringCopperCollection): of().mapColor(that stage's copper block)
 * .strength(3, 6).sound(COPPER).requiresCorrectToolForDrops(); hinge sounds per CopperChestBlock.getHingeSound. They
 * share the vanilla chest block entity, as in 26.x.
 */
public final class Chests {
    public static final List<DeferredBlock<Block>> ALL = new ArrayList<>();
    static final DeferredHolder<SoundEvent, SoundEvent> OPEN = Content.sound("block.copper_chest.open");
    static final DeferredHolder<SoundEvent, SoundEvent> CLOSE = Content.sound("block.copper_chest.close");
    static final DeferredHolder<SoundEvent, SoundEvent> WEATHERED_OPEN = Content.sound("block.copper_chest_weathered.open");
    static final DeferredHolder<SoundEvent, SoundEvent> WEATHERED_CLOSE = Content.sound("block.copper_chest_weathered.close");
    static final DeferredHolder<SoundEvent, SoundEvent> OXIDIZED_OPEN = Content.sound("block.copper_chest_oxidized.open");
    static final DeferredHolder<SoundEvent, SoundEvent> OXIDIZED_CLOSE = Content.sound("block.copper_chest_oxidized.close");

    private Chests() {}

    static Supplier<SoundEvent> hinge(WeatherState state, boolean open) {
        return switch (state) {
            case WEATHERED -> open ? WEATHERED_OPEN : WEATHERED_CLOSE;
            case OXIDIZED -> open ? OXIDIZED_OPEN : OXIDIZED_CLOSE;
            default -> open ? OPEN : CLOSE;
        };
    }

    static void register(IEventBus modBus) {
        for (boolean waxed : new boolean[]{false, true}) {
            for (WeatherState stage : WeatherState.values()) {
                String p = (waxed ? "waxed_" : "") + (stage == WeatherState.UNAFFECTED ? "" : stage.getSerializedName() + "_");
                String copper = (stage == WeatherState.UNAFFECTED ? "" : stage.getSerializedName() + "_") + "copper";
                ResourceLocation copperBlock = ResourceLocation.withDefaultNamespace(stage == WeatherState.UNAFFECTED ? "copper_block" : copper);
                Supplier<Properties> props = () -> Properties.of()
                        .mapColor(s -> BuiltInRegistries.BLOCK.get(copperBlock).defaultMapColor())
                        .strength(3.0F, 6.0F).sound(SoundType.COPPER).requiresCorrectToolForDrops();
                DeferredBlock<Block> chest = Content.BLOCKS.register(p + "copper_chest", () -> waxed
                        ? new CopperChestBlock(stage, hinge(stage, true), hinge(stage, false), props.get())
                        : new WeatheringCopperChestBlock(stage, hinge(stage, true), hinge(stage, false), props.get()));
                Content.ITEM.put(p + "copper_chest", Content.ITEMS.registerSimpleBlockItem(chest));
                ALL.add(chest);
            }
        }
        modBus.addListener(BlockEntityTypeAddBlocksEvent.class,
                e -> e.modify(BlockEntityType.CHEST, ALL.stream().map(DeferredBlock::get).toArray(Block[]::new)));
    }
}
