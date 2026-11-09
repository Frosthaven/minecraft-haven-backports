package dev.frosthaven.havenbackports;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The calcite building set of Minecraft 26.4 snapshot 7, as Mojang's {@code Blocks} defines it (read from the
 * snapshot's unobfuscated class): pillar, polished and bricks are {@code ofFullCopy} of their parent (calcite,
 * calcite, polished calcite); stairs use Mojang's {@code registerStair} ({@code ofFullCopy(base)}), slabs
 * {@code registerSlab} ({@code ofLegacyCopy(base)}), walls {@code registerWall} ({@code ofLegacyCopy(base)
 * .forceSolidOn()}). 1.21.1's calcite has the snapshot's exact definition (terracotta white, basedrum, calcite sound,
 * correct tool, 0.75). The snapshot's stairs and slabs also set {@code isViewBlocking(NEAR_PLANE_INTERSECTS_OUTLINE)},
 * an engine-wide camera change 1.21.1 lacks for all stairs and slabs; ours behave as 1.21.1's vanilla ones.
 */
public final class Calcite {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HavenBackports.ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenBackports.ID);
    /** Creative tab order, inserted after vanilla calcite (the snapshot's order). */
    private static final List<DeferredItem<BlockItem>> ORDER = new ArrayList<>();

    private Calcite() {}

    public static void register(IEventBus modBus) {
        Supplier<Block> calcite = () -> Blocks.CALCITE;
        family("calcite", calcite);
        block("calcite_pillar", RotatedPillarBlock::new, () -> Properties.ofFullCopy(Blocks.CALCITE));
        DeferredBlock<Block> polished = block("polished_calcite", Block::new, () -> Properties.ofFullCopy(Blocks.CALCITE));
        family("polished_calcite", polished);
        DeferredBlock<Block> bricks = block("polished_calcite_bricks", Block::new, () -> Properties.ofFullCopy(polished.get()));
        family("polished_calcite_brick", bricks);

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(BuildCreativeModeTabContentsEvent.class, Calcite::tab);
    }

    /** Stairs, slab and wall of a base block, built the way Mojang's registerStair / registerSlab / registerWall do. */
    private static void family(String prefix, Supplier<? extends Block> base) {
        block(prefix + "_stairs", p -> new StairBlock(base.get().defaultBlockState(), p), () -> Properties.ofFullCopy(base.get()));
        block(prefix + "_slab", SlabBlock::new, () -> Properties.ofLegacyCopy(base.get()));
        block(prefix + "_wall", WallBlock::new, () -> Properties.ofLegacyCopy(base.get()).forceSolidOn());
    }

    private static DeferredBlock<Block> block(String name, Function<Properties, ? extends Block> make, Supplier<Properties> properties) {
        DeferredBlock<Block> b = BLOCKS.register(name, () -> make.apply(properties.get()));
        ORDER.add(ITEMS.registerSimpleBlockItem(b));
        return b;
    }

    private static void tab(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != CreativeModeTabs.BUILDING_BLOCKS) {
            return;
        }
        ItemStack after = new ItemStack(Items.CALCITE);
        boolean anchored = e.getParentEntries().contains(after);
        for (DeferredItem<BlockItem> item : ORDER) {
            ItemStack stack = new ItemStack(item.get());
            if (anchored) {
                e.insertAfter(after, stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                after = stack;
            } else {
                e.accept(stack);
            }
        }
    }
}
