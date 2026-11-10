package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.BonemealableFeaturePlacerBlock;
import dev.frosthaven.havenbackports.block.EyeblossomBlock;
import dev.frosthaven.havenbackports.block.HangingMossBlock;
import dev.frosthaven.havenbackports.block.MossyCarpetBlock;
import dev.frosthaven.havenbackports.block.ParticleLeavesBlock;
import dev.frosthaven.havenbackports.block.PottedEyeblossomBlock;
import dev.frosthaven.havenbackports.block.ResinClumpBlock;
import dev.frosthaven.havenbackports.block.TreeGrower26;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.random.SimpleWeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The Pale Garden's blocks besides the pale oak wood (Woods) and the creaking (creaking/), as 26.4 defines them. */
public final class PaleGarden {
    public static final TagKey<Block> PALE_OAK_LOGS = BlockTags.create(ResourceLocation.withDefaultNamespace("pale_oak_logs"));
    public static DeferredBlock<Block> RESIN_CLUMP;
    public static DeferredBlock<Block> PALE_OAK_LEAVES;
    public static DeferredBlock<Block> PALE_MOSS_CARPET;
    public static DeferredBlock<Block> PALE_MOSS_BLOCK;
    public static DeferredBlock<Block> PALE_HANGING_MOSS;
    public static DeferredBlock<Block> POTTED_OPEN_EYEBLOSSOM;
    public static DeferredBlock<Block> POTTED_CLOSED_EYEBLOSSOM;
    /** The creaking heart block (creaking/ registers it). */
    public static java.util.function.Supplier<Block> CREAKING_HEART;
    public static DeferredBlock<Block> OPEN_EYEBLOSSOM;
    public static DeferredBlock<Block> CLOSED_EYEBLOSSOM;
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent>
            EYEBLOSSOM_OPEN_LONG = Content.sound("block.eyeblossom.open_long"), EYEBLOSSOM_OPEN = Content.sound("block.eyeblossom.open"),
            EYEBLOSSOM_CLOSE_LONG = Content.sound("block.eyeblossom.close_long"), EYEBLOSSOM_CLOSE = Content.sound("block.eyeblossom.close"),
            EYEBLOSSOM_IDLE = Content.sound("block.eyeblossom.idle");
    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.sounds.SoundEvent, net.minecraft.sounds.SoundEvent>
            PALE_HANGING_MOSS_IDLE = Content.sound("block.pale_hanging_moss.idle");

    public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.core.particles.ParticleType<?>,
            net.minecraft.core.particles.ParticleType<dev.frosthaven.havenbackports.particle.TrailParticleOption>> TRAIL =
            Content.PARTICLES.register("trail", () -> new net.minecraft.core.particles.ParticleType<>(false) {
                @Override
                public com.mojang.serialization.MapCodec<dev.frosthaven.havenbackports.particle.TrailParticleOption> codec() {
                    return dev.frosthaven.havenbackports.particle.TrailParticleOption.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf,
                        dev.frosthaven.havenbackports.particle.TrailParticleOption> streamCodec() {
                    return dev.frosthaven.havenbackports.particle.TrailParticleOption.STREAM_CODEC;
                }
            });

    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> PALE_OAK_LEAVES_PARTICLE =
            Content.PARTICLES.register("pale_oak_leaves", () -> new SimpleParticleType(false));
    /** PALE_OAK_LEAVES: UntintedParticleLeavesBlock(0.02, PALE_OAK_LEAVES, no ambient sound), of().mapColor(METAL)
     *  .strength(0.2).randomTicks().sound(GRASS).noOcclusion().isValidSpawn(ocelotOrParrot).isSuffocating(never)
     *  .ignitedByLava().pushReaction(POPPED).isRedstoneConductor(never) (1.21.1 leaves also never block view). */
    static final List<Woods.Leaves> LEAVES = List.of(new Woods.Leaves("pale_oak_leaves",
            p -> new ParticleLeavesBlock(0.02F, PALE_OAK_LEAVES_PARTICLE, Optional.empty(), p), () -> Properties.of().mapColor(MapColor.METAL)
            .strength(0.2F).randomTicks().sound(SoundType.GRASS).noOcclusion()
            .isValidSpawn((s, l, p, e) -> e == EntityType.OCELOT || e == EntityType.PARROT).isSuffocating((s, l, p) -> false)
            .isViewBlocking((s, l, p) -> false).ignitedByLava().pushReaction(PushReaction.DESTROY).isRedstoneConductor((s, l, p) -> false)));
    /** TreeGrower.PALE_OAK: only the 2x2 pale_oak_bonemeal tree; no shortest tree. */
    static final TreeGrower26 PALE_OAK_GROWER = new TreeGrower26(SimpleWeightedRandomList.empty(),
            SimpleWeightedRandomList.<ResourceKey<ConfiguredFeature<?, ?>>>builder().add(feature("pale_oak_bonemeal"), 1).build(),
            SimpleWeightedRandomList.empty(), null);

    private PaleGarden() {}

    static void register() {
        // PALE_MOSS_BLOCK: BonemealableFeaturePlacerBlock(PALE_MOSS_PATCH_BONEMEAL), of().ignitedByLava()
        // .mapColor(LIGHT_GRAY).strength(0.1).sound(MOSS).pushReaction(POPPED); fire 5/100
        PALE_MOSS_BLOCK = Content.block("pale_moss_block", p -> new BonemealableFeaturePlacerBlock(feature("pale_moss_patch_bonemeal"), p),
                () -> Properties.of().ignitedByLava().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.1F).sound(SoundType.MOSS)
                        .pushReaction(PushReaction.DESTROY));
        // PALE_MOSS_CARPET: MossyCarpetBlock, of().ignitedByLava().mapColor(moss).strength(0.1).sound(MOSS_CARPET)
        // .pushReaction(POPPED).noOcclusion(); PALE_HANGING_MOSS: of().ignitedByLava().mapColor(moss).noCollision()
        // .sound(MOSS_CARPET).pushReaction(POPPED)
        PALE_MOSS_CARPET = Content.block("pale_moss_carpet", MossyCarpetBlock::new, () -> Properties.of().ignitedByLava()
                .mapColor(MapColor.COLOR_LIGHT_GRAY).strength(0.1F).sound(SoundType.MOSS_CARPET).pushReaction(PushReaction.DESTROY).noOcclusion());
        PALE_HANGING_MOSS = Content.block("pale_hanging_moss", HangingMossBlock::new, () -> Properties.of().ignitedByLava()
                .mapColor(MapColor.COLOR_LIGHT_GRAY).noCollission().sound(SoundType.MOSS_CARPET).pushReaction(PushReaction.DESTROY));
        // OPEN/CLOSED_EYEBLOSSOM: of().mapColor(CREAKING_HEART (orange) / PALE_OAK_LEAVES (metal)).noCollision()
        // .instabreak().sound(GRASS).offsetType(XZ).pushReaction(POPPED).randomTicks(); fire 60/100
        OPEN_EYEBLOSSOM = Content.block("open_eyeblossom", p -> new EyeblossomBlock(EyeblossomBlock.Type.OPEN, p), () -> eyeblossom(MapColor.COLOR_ORANGE));
        CLOSED_EYEBLOSSOM = Content.block("closed_eyeblossom", p -> new EyeblossomBlock(EyeblossomBlock.Type.CLOSED, p), () -> eyeblossom(MapColor.METAL));
        // POTTED_*_EYEBLOSSOM: FlowerPotBlock(eyeblossom, flowerPotProperties().randomTicks()); they swap by time (26.x)
        DeferredBlock<Block> pottedOpen = Content.BLOCKS.register("potted_open_eyeblossom",
                () -> new PottedEyeblossomBlock(true, OPEN_EYEBLOSSOM, pot()));
        DeferredBlock<Block> pottedClosed = Content.BLOCKS.register("potted_closed_eyeblossom",
                () -> new PottedEyeblossomBlock(false, CLOSED_EYEBLOSSOM, pot()));
        POTTED_OPEN_EYEBLOSSOM = pottedOpen;
        POTTED_CLOSED_EYEBLOSSOM = pottedClosed;
        // RESIN_CLUMP: MultifaceBlock, of().mapColor(TERRACOTTA_ORANGE).replaceable().noCollision().sound(RESIN)
        // .ignitedByLava().pushReaction(POPPED); RESIN_BLOCK: of().mapColor(TERRACOTTA_ORANGE).instrument(BASEDRUM)
        // .sound(RESIN); RESIN_BRICKS and CHISELED: + requiresCorrectToolForDrops().sound(RESIN_BRICKS).strength(1.5, 6);
        // stairs registerLegacyStair (ofLegacyCopy), slab, wall
        DeferredSoundType resin = Dappled.soundSet("resin", true, false, true);
        DeferredSoundType resinBricks = Dappled.soundSet("resin_bricks", true, true, true);
        RESIN_CLUMP = Content.block("resin_clump", ResinClumpBlock::new, () -> Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).replaceable()
                .noCollission().sound(resin).ignitedByLava().pushReaction(PushReaction.DESTROY));
        Content.block("resin_block", Block::new, () -> Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).instrument(NoteBlockInstrument.BASEDRUM)
                .sound(resin));
        DeferredBlock<Block> bricks = Content.block("resin_bricks", Block::new, () -> Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().sound(resinBricks).strength(1.5F, 6.0F));
        Content.block("resin_brick_stairs", p -> new StairBlock(bricks.get().defaultBlockState(), p), () -> Properties.ofLegacyCopy(bricks.get()));
        Content.block("resin_brick_slab", SlabBlock::new, () -> Properties.ofLegacyCopy(bricks.get()));
        Content.block("resin_brick_wall", WallBlock::new, () -> Properties.ofLegacyCopy(bricks.get()).forceSolidOn());
        Content.block("chiseled_resin_bricks", Block::new, () -> Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE)
                .instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().sound(resinBricks).strength(1.5F, 6.0F));
        Content.ITEM.put("resin_brick", Content.ITEMS.registerSimpleItem("resin_brick"));
        for (DeferredBlock<Block> b : List.of(PALE_MOSS_BLOCK, PALE_MOSS_CARPET, PALE_HANGING_MOSS)) {
            Content.FLAMMABLE.add(() -> Content.fire().setFlammable(b.get(), 5, 100));
        }
        for (DeferredBlock<Block> b : List.of(OPEN_EYEBLOSSOM, CLOSED_EYEBLOSSOM)) {
            Content.FLAMMABLE.add(() -> Content.fire().setFlammable(b.get(), 60, 100));
        }
        Content.COMMON_SETUP.add(() -> {
            ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(OPEN_EYEBLOSSOM.getId(), pottedOpen);
            ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(CLOSED_EYEBLOSSOM.getId(), pottedClosed);
        });
    }

    private static Properties eyeblossom(MapColor color) {
        return Properties.of().mapColor(color).noCollission().instabreak().sound(SoundType.GRASS).offsetType(BlockBehaviour.OffsetType.XZ)
                .pushReaction(PushReaction.DESTROY).randomTicks();
    }

    private static Properties pot() {
        return Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY).randomTicks();
    }

    static ResourceKey<ConfiguredFeature<?, ?>> feature(String id) {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, id));
    }

    /** 26.x CreakingHeartDecorator's state: creaking_heart_state = dormant, natural = true. */
    public static net.minecraft.world.level.block.state.BlockState naturalDormantHeart() {
        var s = CREAKING_HEART.get().defaultBlockState();
        s = with(s, "creaking_heart_state", "dormant");
        return with(s, "natural", "true");
    }

    private static net.minecraft.world.level.block.state.BlockState with(net.minecraft.world.level.block.state.BlockState s, String name, String value) {
        var p = s.getBlock().getStateDefinition().getProperty(name);
        return p == null ? s : set(s, p, value);
    }

    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState set(
            net.minecraft.world.level.block.state.BlockState s, net.minecraft.world.level.block.state.properties.Property<T> p, String value) {
        return p.getValue(value).map(v -> s.setValue(p, v)).orElse(s);
    }

}
