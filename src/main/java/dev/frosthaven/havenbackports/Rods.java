package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.WeatheringLightningRodBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.poi.ExtendPoiTypesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Lightning rods in every copper stage, as 26.4's Blocks.LIGHTNING_ROD (a WeatheringCopperCollection) defines them:
 * of().mapColor(that stage's copper block).forceSolidOn().requiresCorrectToolForDrops().strength(3, 6)
 * .sound(COPPER).noOcclusion(); WeatheringLightningRodBlock oxidizes, the waxed ones are plain LightningRodBlocks.
 * The unaffected stage stays minecraft:lightning_rod (its 1.21.1 definition is the same; it ages through
 * LightningRodBlockMixin and the data maps). 26.x's lightning_rod POI holds every stage, so lightning finds ours.
 */
final class Rods {
    private static final List<DeferredBlock<Block>> ALL = new ArrayList<>();

    private Rods() {}

    static void register(IEventBus modBus) {
        for (boolean waxed : new boolean[]{false, true}) {
            for (WeatherState stage : WeatherState.values()) {
                if (!waxed && stage == WeatherState.UNAFFECTED) {
                    continue;
                }
                String id = (waxed ? "waxed_" : "") + (stage == WeatherState.UNAFFECTED ? "" : stage.getSerializedName() + "_") + "lightning_rod";
                Supplier<Block> copper = switch (stage) {
                    case UNAFFECTED -> () -> Blocks.COPPER_BLOCK;
                    case EXPOSED -> () -> Blocks.EXPOSED_COPPER;
                    case WEATHERED -> () -> Blocks.WEATHERED_COPPER;
                    case OXIDIZED -> () -> Blocks.OXIDIZED_COPPER;
                };
                Supplier<Properties> props = () -> Properties.of().mapColor(s -> copper.get().defaultMapColor()).forceSolidOn()
                        .requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.COPPER).noOcclusion();
                ALL.add(Content.block(id, waxed ? LightningRodBlock::new : p -> new WeatheringLightningRodBlock(stage, p), props));
            }
        }
        modBus.addListener(ExtendPoiTypesEvent.class, e -> ALL.forEach(b -> e.addBlockToPoi(PoiTypes.LIGHTNING_ROD, b.get())));
    }
}
