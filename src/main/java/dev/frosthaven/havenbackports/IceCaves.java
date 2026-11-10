package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.BrittleIceBlock;
import dev.frosthaven.havenbackports.block.IceCrystalBlock;
import dev.frosthaven.havenbackports.block.IcicleBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;

/** The Ice Caves' blocks (icicle, brittle ice, ice crystal), their sounds and tags, as 26.4's Blocks, SoundEvents
 *  and BlockTags define them. 26.x POPPED is 1.21.1's PushReaction.DESTROY (as for the other backported blocks). */
public final class IceCaves {
    public static final TagKey<Block> MELTS_ICICLE_ABOVE = BlockTags.create(mc("melts_icicle_above"));
    public static final TagKey<Block> FACE_OCCLUDING_ICE_BLOCKS = BlockTags.create(mc("face_occluding_ice_blocks"));
    public static final TagKey<EntityType<?>> DOES_NOT_BREAK_BRITTLE_ICE = TagKey.create(Registries.ENTITY_TYPE, mc("does_not_break_brittle_ice"));

    public static final DeferredHolder<SoundEvent, SoundEvent> ICE_CRYSTAL_AMBIENT = Content.sound("block.ice_crystal.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> ICICLE_LAND = Content.sound("block.icicle.land");
    public static final DeferredHolder<SoundEvent, SoundEvent> BRITTLE_ICE_CRACK = Content.sound("block.brittle_ice.crack");

    public static DeferredBlock<Block> BRITTLE_ICE;
    public static DeferredBlock<Block> ICICLE;
    public static DeferredBlock<Block> ICE_CRYSTAL;

    private IceCaves() {}

    static void register() {
        // BRITTLE_ICE: of().mapColor(ICE).friction(0.98).strength(0.5).sound(BRITTLE_ICE).noOcclusion().instrument(CHIME)
        // .isValidSpawn(polar bear).isRedstoneConductor(never).pushReaction(POPPED)
        DeferredSoundType brittle = Dappled.soundSet("brittle_ice", true, true, true);
        BRITTLE_ICE = Content.block("brittle_ice", BrittleIceBlock::new, () -> Properties.of().mapColor(MapColor.ICE).friction(0.98F)
                .strength(0.5F).sound(brittle).noOcclusion().instrument(NoteBlockInstrument.CHIME)
                .isValidSpawn((s, l, p, e) -> e == EntityType.POLAR_BEAR).isRedstoneConductor((s, l, p) -> false)
                .pushReaction(PushReaction.DESTROY));
        // ICICLE: IcicleBlock(PACKED_ICE), of().mapColor(COLOR_LIGHT_BLUE).forceSolidOn().instrument(CHIME).noOcclusion()
        // .sound(ICICLE).randomTicks().strength(0.5, 0.5).dynamicShape().pushReaction(POPPED).isRedstoneConductor(never)
        DeferredSoundType icicle = Dappled.soundSet("icicle", true, true, true);
        ICICLE = Content.block("icicle", p -> new IcicleBlock(Blocks.PACKED_ICE.defaultBlockState(), p),
                () -> Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).forceSolidOn().instrument(NoteBlockInstrument.CHIME).noOcclusion()
                        .sound(icicle).randomTicks().strength(0.5F, 0.5F).dynamicShape().pushReaction(PushReaction.DESTROY)
                        .isRedstoneConductor((s, l, p) -> false));
        // ICE_CRYSTAL: of().mapColor(ICE).sound(ICE_CRYSTAL).instrument(CHIME).strength(1.5, 1.5).lightLevel(4)
        // .pushReaction(POPPED).isRedstoneConductor(never).noOcclusion()
        DeferredSoundType crystal = Dappled.soundSet("ice_crystal", true, true, true);
        ICE_CRYSTAL = Content.block("ice_crystal", IceCrystalBlock::new, () -> Properties.of().mapColor(MapColor.ICE).sound(crystal)
                .instrument(NoteBlockInstrument.CHIME).strength(1.5F, 1.5F).lightLevel(s -> 4).pushReaction(PushReaction.DESTROY)
                .isRedstoneConductor((s, l, p) -> false).noOcclusion());
        // 26.x LiquidBlock.shouldSpreadLiquid: water next to blue ice on a side becomes brittle ice (no fizz). NeoForge
        // routes 1.21.1's fluid interactions through FluidInteractionRegistry, so no mixin is needed.
        Content.COMMON_SETUP.add(() -> FluidInteractionRegistry.addInteraction(NeoForgeMod.WATER_TYPE.value(),
                new FluidInteractionRegistry.InteractionInformation(
                        (level, pos, neighbor, fluid) -> neighbor.getY() == pos.getY() && level.getBlockState(neighbor).is(Blocks.BLUE_ICE),
                        (level, pos, neighbor, fluid) -> level.setBlockAndUpdate(pos,
                                EventHooks.fireFluidPlaceBlockEvent(level, pos, pos, BRITTLE_ICE.get().defaultBlockState())))));
    }

    private static ResourceLocation mc(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }
}
