package dev.frosthaven.havenbackports;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.util.DeferredSoundType;

/**
 * Chiseled Soulshale, as 26.4's Blocks defines it: of().mapColor(WARPED_STEM).strength(2, 6)
 * .sound(CHISELED_SOULSHALE), a 26.x sound set (block_sound_set/chiseled_soulshale, volume and pitch 1). No tool
 * requirement; mineable with a pickaxe (tag). Its only source is the ancient city's chests; 26.x lists it in no
 * creative tab, so neither does this.
 */
final class Soulshale {
    private Soulshale() {}

    static void register() {
        DeferredSoundType sound = new DeferredSoundType(1.0F, 1.0F, Content.sound("block.chiseled_soulshale.break"),
                Content.sound("block.chiseled_soulshale.step"), Content.sound("block.chiseled_soulshale.place"),
                Content.sound("block.chiseled_soulshale.hit"), Content.sound("block.chiseled_soulshale.fall"));
        Content.block("chiseled_soulshale", Block::new, () -> Properties.of().mapColor(MapColor.WARPED_STEM).strength(2.0F, 6.0F).sound(sound));
    }
}
