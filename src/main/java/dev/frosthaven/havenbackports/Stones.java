package dev.frosthaven.havenbackports;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Cinnabar and sulfur stone sets, as 26.4's Blocks defines them: of().sound(CINNABAR|SULFUR).mapColor(COLOR_RED|
 * COLOR_YELLOW).instrument(BASEDRUM).requiresCorrectToolForDrops().strength(1.5, 6); polished, bricks and chiseled are
 * ofLegacyCopy of the base; stairs, slabs and walls through Mojang's helpers (Content.family). They come from the
 * Sulfur Caves biome, ported with it.
 */
final class Stones {
    private Stones() {}

    static void register() {
        set("cinnabar", MapColor.COLOR_RED);
        set("sulfur", MapColor.COLOR_YELLOW);
    }

    private static void set(String name, MapColor color) {
        DeferredSoundType sound = new DeferredSoundType(1.0F, 1.0F, Content.sound("block." + name + ".break"),
                Content.sound("block." + name + ".step"), Content.sound("block." + name + ".place"),
                Content.sound("block." + name + ".hit"), Content.sound("block." + name + ".fall"));
        DeferredBlock<Block> base = Content.block(name, Block::new, () -> Properties.of().sound(sound).mapColor(color)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.5F, 6.0F));
        Content.family(name, base);
        DeferredBlock<Block> polished = Content.block("polished_" + name, Block::new, () -> Properties.ofLegacyCopy(base.get()));
        Content.family("polished_" + name, polished);
        DeferredBlock<Block> bricks = Content.block(name + "_bricks", Block::new, () -> Properties.ofLegacyCopy(base.get()));
        Content.family(name + "_brick", bricks);
        Content.block("chiseled_" + name, Block::new, () -> Properties.ofLegacyCopy(base.get()));
    }
}
