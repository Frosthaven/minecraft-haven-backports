package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.AmbientLeavesSound;
import dev.frosthaven.havenbackports.block.BushBlock26;
import dev.frosthaven.havenbackports.block.LeafLitterBlock;
import dev.frosthaven.havenbackports.block.ParticleLeavesBlock;
import dev.frosthaven.havenbackports.block.ShelfMushroomBlock;
import dev.frosthaven.havenbackports.block.TreeGrower26;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The Dappled Forest's own blocks besides the poplar wood (Woods): leaf litter, red shrub, shelf mushroom, the poplar
 * leaves' particles and sounds, and the poplar TreeGrower. Each as 26.4's Blocks, ParticleTypes, SoundEvents and
 * TreeGrower define it. 26.x sound sets are data (block_sound_set); a set without a hit, step or fall sound plays
 * nothing there, which 1.21.1's SoundType expresses with minecraft:intentionally_empty (SoundEngine skips it).
 */
public final class Dappled {
    /** 1.21.1 SoundManager.INTENTIONALLY_EMPTY_SOUND_LOCATION: plays nothing, logs nothing. Not registered. */
    public static final SoundEvent EMPTY = SoundEvent.createVariableRangeEvent(ResourceLocation.withDefaultNamespace("intentionally_empty"));
    static final TagKey<Block> REQUIRED_FOR_POPLAR_LEAF_AMBIENCE = BlockTags.create(ResourceLocation.withDefaultNamespace("required_for_poplar_leaf_ambience"));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RED_POPLAR_LEAVES = particle("red_poplar_leaves");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ORANGE_POPLAR_LEAVES = particle("orange_poplar_leaves");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> YELLOW_POPLAR_LEAVES = particle("yellow_poplar_leaves");

    public static final DeferredHolder<SoundEvent, SoundEvent> POPLAR_LEAVES_AMBIENT = Content.sound("block.poplar_leaves.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELF_MUSHROOM_BOUNCE = Content.sound("block.shelf_mushroom.bounce");
    private static final DeferredSoundType POPLAR_LEAVES_SOUND = soundSet("poplar_leaves", true, true, true);
    private static final DeferredSoundType LEAF_LITTER_SOUND = soundSet("leaf_litter", true, true, true);
    private static final DeferredSoundType RED_SHRUB_SOUND = soundSet("red_shrub", false, false, false);
    private static final DeferredSoundType SHELF_MUSHROOM_SOUND = soundSet("shelf_mushroom", true, false, true);

    /** RED/ORANGE/YELLOW_POPLAR_LEAVES: UntintedParticleLeavesBlock(0.01, its particle, ambient POPLAR_LEAVES_AMBIENT
     *  with #required_for_poplar_leaf_ambience), leavesProperties(POPLAR_LEAVES).mapColor(RED/ORANGE/YELLOW). */
    static final List<Woods.Leaves> POPLAR_LEAVES = List.of(poplarLeaves("red", RED_POPLAR_LEAVES, MapColor.COLOR_RED),
            poplarLeaves("orange", ORANGE_POPLAR_LEAVES, MapColor.COLOR_ORANGE), poplarLeaves("yellow", YELLOW_POPLAR_LEAVES, MapColor.COLOR_YELLOW));

    /** TreeGrower.POPLAR: red, orange and yellow poplar at weight 1 each; no mega or flower trees; shortest red_poplar. */
    static final TreeGrower26 POPLAR = new TreeGrower26(
            SimpleWeightedRandomList.<ResourceKey<ConfiguredFeature<?, ?>>>builder().add(tree("red_poplar"), 1).add(tree("orange_poplar"), 1)
                    .add(tree("yellow_poplar"), 1).build(),
            SimpleWeightedRandomList.empty(), SimpleWeightedRandomList.empty(), tree("red_poplar"));

    public static DeferredBlock<Block> LEAF_LITTER;
    public static DeferredBlock<Block> RED_SHRUB;
    public static DeferredBlock<Block> SHELF_MUSHROOM;

    private Dappled() {}

    static void register() {
        // LEAF_LITTER: of().mapColor(BROWN).replaceable().noCollision().sound(LEAF_LITTER).pushReaction(POPPED); fire 60/100
        LEAF_LITTER = Content.block("leaf_litter", LeafLitterBlock::new, () -> Properties.of().mapColor(MapColor.COLOR_BROWN).replaceable()
                .noCollission().sound(LEAF_LITTER_SOUND).pushReaction(PushReaction.DESTROY));
        // RED_SHRUB: BushBlock, of().mapColor(CRIMSON_NYLIUM).replaceable().noCollision().instabreak().sound(RED_SHRUB)
        // .ignitedByLava().pushReaction(POPPED); fire 60/100
        RED_SHRUB = Content.block("red_shrub", BushBlock26::new, () -> Properties.of().mapColor(MapColor.CRIMSON_NYLIUM).replaceable()
                .noCollission().instabreak().sound(RED_SHRUB_SOUND).ignitedByLava().pushReaction(PushReaction.DESTROY));
        // SHELF_MUSHROOM: of().mapColor(TERRACOTTA_YELLOW).bounceRestitution(0.75).fallDistanceReduction(0.5)
        // .sound(SHELF_MUSHROOM).noOcclusion().pushReaction(POPPED) (the bounce and reduction live in the class)
        SHELF_MUSHROOM = Content.block("shelf_mushroom", ShelfMushroomBlock::new, () -> Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW)
                .sound(SHELF_MUSHROOM_SOUND).noOcclusion().pushReaction(PushReaction.DESTROY));
        Content.FLAMMABLE.add(() -> Content.fire().setFlammable(LEAF_LITTER.get(), 60, 100));
        Content.FLAMMABLE.add(() -> Content.fire().setFlammable(RED_SHRUB.get(), 60, 100));
    }

    /** Blocks.leavesProperties(sound): PLANT, 0.2, random ticks, no occlusion, ocelot/parrot spawns, not suffocating,
     *  lava ignites, POPPED, not a redstone conductor. 1.21.1's leaves also never block view (26.x dropped that for
     *  every leaves block; an engine-wide change, kept at 1.21.1). */
    static Properties leavesProperties(String id) {
        return Properties.of().mapColor(MapColor.PLANT).strength(0.2F).randomTicks().sound(POPLAR_LEAVES_SOUND).noOcclusion()
                .isValidSpawn((s, l, p, e) -> e == net.minecraft.world.entity.EntityType.OCELOT || e == net.minecraft.world.entity.EntityType.PARROT)
                .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false).ignitedByLava().pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor((s, l, p) -> false);
    }

    private static Woods.Leaves poplarLeaves(String color, Supplier<SimpleParticleType> particle, MapColor map) {
        return new Woods.Leaves(color + "_poplar_leaves", p -> new ParticleLeavesBlock(0.01F, particle,
                Optional.of(new AmbientLeavesSound(POPLAR_LEAVES_AMBIENT, REQUIRED_FOR_POPLAR_LEAF_AMBIENCE)), p), map);
    }

    private static ResourceKey<ConfiguredFeature<?, ?>> tree(String id) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, id));
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> particle(String id) {
        return Content.PARTICLES.register(id, () -> new SimpleParticleType(false));
    }

    /** A 26.x block sound set: break and place always; step, hit and fall when the set has them. */
    static DeferredSoundType soundSet(String set, boolean step, boolean hit, boolean fall) {
        Supplier<SoundEvent> empty = () -> EMPTY;
        return new DeferredSoundType(1.0F, 1.0F, Content.sound("block." + set + ".break"),
                step ? Content.sound("block." + set + ".step") : empty, Content.sound("block." + set + ".place"),
                hit ? Content.sound("block." + set + ".hit") : empty, fall ? Content.sound("block." + set + ".fall") : empty);
    }
}
