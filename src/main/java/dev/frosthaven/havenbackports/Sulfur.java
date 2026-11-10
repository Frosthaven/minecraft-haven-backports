package dev.frosthaven.havenbackports;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.block.PotentSulfurBlock;
import dev.frosthaven.havenbackports.block.PotentSulfurBlockEntity;
import dev.frosthaven.havenbackports.block.SulfurSpikeBlock;
import dev.frosthaven.havenbackports.particle.GeyserBaseParticleOptions;
import dev.frosthaven.havenbackports.particle.GeyserParticleOptions;
import io.netty.buffer.ByteBuf;
import java.util.function.Function;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * The Sulfur Caves' blocks besides the sulfur and cinnabar stone sets (Stones): potent sulfur (the geyser block) and
 * the sulfur spike, their block entity, particles and sounds, as 26.4's Blocks, BlockEntityTypes, ParticleTypes and
 * SoundEvents define them; and the tags their code reads (26.x BlockTags / EntityTypeTags).
 */
public final class Sulfur {
    public static final TagKey<Block> CAUSES_CONTINUOUS_GEYSER_ERUPTIONS = BlockTags.create(mc("causes_continuous_geyser_eruptions"));
    public static final TagKey<Block> CAUSES_PERIODIC_GEYSER_ERUPTIONS = BlockTags.create(mc("causes_periodic_geyser_eruptions"));
    public static final TagKey<Block> SPELEOTHEMS = BlockTags.create(mc("speleothems"));
    public static final TagKey<EntityType<?>> NOT_AFFECTED_BY_GEYSERS = TagKey.create(Registries.ENTITY_TYPE, mc("not_affected_by_geysers"));

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SULFUR_BUBBLES = simple("sulfur_bubbles");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NOXIOUS_GAS = simple("noxious_gas");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> NOXIOUS_GAS_CLOUD = simple("noxious_gas_cloud");
    public static final DeferredHolder<ParticleType<?>, ParticleType<GeyserParticleOptions>> GEYSER =
            particle("geyser", true, GeyserParticleOptions::codec, GeyserParticleOptions::streamCodec);
    public static final DeferredHolder<ParticleType<?>, ParticleType<GeyserBaseParticleOptions>> GEYSER_BASE =
            particle("geyser_base", true, GeyserBaseParticleOptions::codec, GeyserBaseParticleOptions::streamCodec);
    public static final DeferredHolder<ParticleType<?>, ParticleType<GeyserBaseParticleOptions>> GEYSER_POOF =
            particle("geyser_poof", true, GeyserBaseParticleOptions::codec, GeyserBaseParticleOptions::streamCodec);
    public static final DeferredHolder<ParticleType<?>, ParticleType<GeyserParticleOptions>> GEYSER_PLUME =
            particle("geyser_plume", true, GeyserParticleOptions::codec, GeyserParticleOptions::streamCodec);

    public static final DeferredHolder<SoundEvent, SoundEvent> SULFUR_SPIKE_LAND = Content.sound("block.sulfur_spike.land");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEYSER_ERUPTION_START = Content.sound("block.potent_sulfur.geyser_eruption");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEYSER_ERUPTION_ACTIVE = Content.sound("block.potent_sulfur.geyser_eruption_active");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEYSER_CONTINUOUS_START = Content.sound("block.potent_sulfur.geyser_continuous_eruption");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEYSER_CONTINUOUS_ACTIVE =
            Content.sound("block.potent_sulfur.geyser_continuous_eruption_active");
    public static final DeferredHolder<SoundEvent, SoundEvent> NOXIOUS_GAS_SOUND = Content.sound("block.potent_sulfur.noxious_gas");
    /** Musics: music.overworld.sulfur_caves (the Sulfur Caves' and Ice Caves' background music). */
    public static final DeferredHolder<SoundEvent, SoundEvent> MUSIC_SULFUR_CAVES = Content.sound("music.overworld.sulfur_caves");

    public static DeferredBlock<Block> POTENT_SULFUR;
    public static DeferredBlock<Block> SULFUR_SPIKE;
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PotentSulfurBlockEntity>> POTENT_SULFUR_ENTITY =
            Content.BLOCK_ENTITIES.register("potent_sulfur", () -> BlockEntityType.Builder.of(PotentSulfurBlockEntity::new, POTENT_SULFUR.get()).build(null));

    private Sulfur() {}

    static void register() {
        // POTENT_SULFUR: ofFullCopy(SULFUR).sound(POTENT_SULFUR).mapColor(GOLD)
        DeferredSoundType potent = Dappled.soundSet("potent_sulfur", true, true, true);
        POTENT_SULFUR = Content.block("potent_sulfur", PotentSulfurBlock::new,
                () -> Properties.ofFullCopy(Stones.SULFUR.get()).sound(potent).mapColor(MapColor.GOLD));
        // SULFUR_SPIKE: SulfurSpikeBlock(SULFUR), of().mapColor(YELLOW).forceSolidOn().instrument(BASEDRUM).noOcclusion()
        // .sound(SULFUR_SPIKE).randomTicks().strength(1.5, 3).dynamicShape().offsetType(XZ).pushReaction(POPPED)
        // .isRedstoneConductor(never)
        DeferredSoundType spike = Dappled.soundSet("sulfur_spike", true, true, true);
        SULFUR_SPIKE = Content.block("sulfur_spike", p -> new SulfurSpikeBlock(Stones.SULFUR.get().defaultBlockState(), p),
                () -> Properties.of().mapColor(MapColor.COLOR_YELLOW).forceSolidOn().instrument(NoteBlockInstrument.BASEDRUM).noOcclusion()
                        .sound(spike).randomTicks().strength(1.5F, 3.0F).dynamicShape().offsetType(BlockBehaviour.OffsetType.XZ)
                        .pushReaction(PushReaction.DESTROY).isRedstoneConductor((s, l, p) -> false));
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> simple(String id) {
        return Content.PARTICLES.register(id, () -> new SimpleParticleType(false));
    }

    private static <T extends net.minecraft.core.particles.ParticleOptions> DeferredHolder<ParticleType<?>, ParticleType<T>> particle(String id,
            boolean alwaysShow, Function<ParticleType<T>, MapCodec<T>> codec, Function<ParticleType<T>, StreamCodec<? super ByteBuf, T>> stream) {
        return Content.PARTICLES.register(id, () -> new ParticleType<T>(alwaysShow) {
            @Override
            public MapCodec<T> codec() {
                return codec.apply(this);
            }

            @Override
            @SuppressWarnings("unchecked")
            public StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec() {
                return (StreamCodec<? super RegistryFriendlyByteBuf, T>) (StreamCodec<?, T>) stream.apply(this);
            }
        });
    }
}
