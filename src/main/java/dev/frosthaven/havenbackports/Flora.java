package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.BushBlock26;
import dev.frosthaven.havenbackports.block.CactusFlowerBlock;
import dev.frosthaven.havenbackports.block.FireflyBushBlock;
import dev.frosthaven.havenbackports.block.FlowerBedBlock;
import dev.frosthaven.havenbackports.block.ShortDryGrassBlock;
import dev.frosthaven.havenbackports.block.TallDryGrassBlock;
import java.util.List;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The newer flora of existing biomes: bush, firefly bush, wildflowers, short and tall dry grass, cactus flower, golden
 * dandelion (and potted), each as 26.4's Blocks defines it (properties in the comments; POPPED is 1.21.1's DESTROY).
 * 26.x places them in vanilla biomes; tools/biome_mods.py writes those placements.
 */
public final class Flora {
    public static final TagKey<Block> SUPPORTS_DRY_VEGETATION = BlockTags.create(mc("supports_dry_vegetation"));
    public static final TagKey<Block> TRIGGERS_DRY_VEGETATION_SOUNDS = BlockTags.create(mc("triggers_ambient_desert_dry_vegetation_block_sounds"));
    public static final TagKey<Block> SUPPORT_OVERRIDE_CACTUS_FLOWER = BlockTags.create(mc("support_override_cactus_flower"));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIREFLY = Content.PARTICLES.register("firefly",
            () -> new SimpleParticleType(false));
    public static final DeferredHolder<SoundEvent, SoundEvent> FIREFLY_BUSH_IDLE = Content.sound("block.firefly_bush.idle");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRY_GRASS_AMBIENT = Content.sound("block.dry_grass.ambient");

    public static DeferredBlock<Block> BUSH;
    public static DeferredBlock<Block> FIREFLY_BUSH;
    public static DeferredBlock<Block> WILDFLOWERS;
    public static DeferredBlock<Block> SHORT_DRY_GRASS;
    public static DeferredBlock<Block> TALL_DRY_GRASS;
    public static DeferredBlock<Block> CACTUS_FLOWER;
    public static DeferredBlock<Block> GOLDEN_DANDELION;
    public static DeferredBlock<Block> POTTED_GOLDEN_DANDELION;

    private Flora() {}

    static void register() {
        // BUSH: BushBlock, of().mapColor(PLANT).replaceable().noCollision().instabreak().sound(GRASS).ignitedByLava()
        // .pushReaction(POPPED)
        BUSH = Content.block("bush", BushBlock26::new, () -> Properties.of().mapColor(MapColor.PLANT).replaceable().noCollission()
                .instabreak().sound(SoundType.GRASS).ignitedByLava().pushReaction(PushReaction.DESTROY));
        // FIREFLY_BUSH: of().mapColor(PLANT).ignitedByLava().lightLevel(2).noCollision().instabreak().sound(SWEET_BERRY_BUSH)
        // .pushReaction(POPPED)
        FIREFLY_BUSH = Content.block("firefly_bush", FireflyBushBlock::new, () -> Properties.of().mapColor(MapColor.PLANT).ignitedByLava()
                .lightLevel(s -> 2).noCollission().instabreak().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.DESTROY));
        // WILDFLOWERS: FlowerBedBlock(p, 3), of().mapColor(PLANT).noCollision().sound(PINK_PETALS).pushReaction(POPPED)
        WILDFLOWERS = Content.block("wildflowers", p -> new FlowerBedBlock(p, 3), () -> Properties.of().mapColor(MapColor.PLANT)
                .noCollission().sound(SoundType.PINK_PETALS).pushReaction(PushReaction.DESTROY));
        // SHORT/TALL_DRY_GRASS: of().mapColor(COLOR_YELLOW).replaceable().noCollision().instabreak().sound(GRASS)
        // .ignitedByLava().offsetType(XYZ).pushReaction(POPPED)
        SHORT_DRY_GRASS = Content.block("short_dry_grass", ShortDryGrassBlock::new, Flora::dryGrass);
        TALL_DRY_GRASS = Content.block("tall_dry_grass", TallDryGrassBlock::new, Flora::dryGrass);
        // CACTUS_FLOWER: of().mapColor(COLOR_PINK).noCollision().instabreak().ignitedByLava().sound(CACTUS_FLOWER)
        // .pushReaction(POPPED)
        var cactusFlower = Dappled.soundSet("cactus_flower", false, false, false);
        CACTUS_FLOWER = Content.block("cactus_flower", CactusFlowerBlock::new, () -> Properties.of().mapColor(MapColor.COLOR_PINK)
                .noCollission().instabreak().ignitedByLava().sound(cactusFlower).pushReaction(PushReaction.DESTROY));
        // GOLDEN_DANDELION: FlowerBlock(SATURATION, 0.35), of().mapColor(PLANT).noCollision().instabreak().sound(GRASS)
        // .offsetType(XZ).pushReaction(POPPED); POTTED_GOLDEN_DANDELION: FlowerPotBlock(it, flowerPotProperties())
        GOLDEN_DANDELION = Content.block("golden_dandelion", p -> new FlowerBlock(MobEffects.SATURATION, 0.35F, p), () -> Properties.of()
                .mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY));
        DeferredBlock<Block> potted = Content.BLOCKS.register("potted_golden_dandelion", () -> new FlowerPotBlock(
                () -> (FlowerPotBlock) Blocks.FLOWER_POT, GOLDEN_DANDELION, Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY)));
        POTTED_GOLDEN_DANDELION = potted;
        // FireBlock.bootStrap: all of them 60/100
        for (DeferredBlock<Block> b : List.of(BUSH, FIREFLY_BUSH, WILDFLOWERS, SHORT_DRY_GRASS, TALL_DRY_GRASS, CACTUS_FLOWER, GOLDEN_DANDELION)) {
            Content.FLAMMABLE.add(() -> Content.fire().setFlammable(b.get(), 60, 100));
        }
        Content.COMMON_SETUP.add(() -> ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(GOLDEN_DANDELION.getId(), potted));
    }

    private static Properties dryGrass() {
        return Properties.of().mapColor(MapColor.COLOR_YELLOW).replaceable().noCollission().instabreak().sound(SoundType.GRASS)
                .ignitedByLava().offsetType(BlockBehaviour.OffsetType.XYZ).pushReaction(PushReaction.DESTROY);
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
