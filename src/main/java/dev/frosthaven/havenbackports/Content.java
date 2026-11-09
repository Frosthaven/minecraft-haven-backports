package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.Access;
import dev.frosthaven.havenbackports.block.ShelfBlock;
import dev.frosthaven.havenbackports.block.ShelfBlockEntity;
import dev.frosthaven.havenbackports.block.WeatheringCopperBarsBlock;
import dev.frosthaven.havenbackports.block.WeatheringCopperChainBlock;
import dev.frosthaven.havenbackports.block.WeatheringLanternBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.util.DeferredSoundType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every block and item of the backport, each defined as Mojang's 26.4 snapshot 7 {@code Blocks} defines it (read from
 * the unobfuscated jar). Helpers match Mojang's: registerStair = ofFullCopy(base), registerSlab = ofLegacyCopy(base),
 * registerWall = ofLegacyCopy(base).forceSolidOn(). 26.x's PushReaction.POPPED is 1.21.1's DESTROY.
 */
public final class Content {
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HavenBackports.ID);
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HavenBackports.ID);
    static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, HavenBackports.ID);
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HavenBackports.ID);
    static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HavenBackports.ID);

    /** Block items by id, in registration order (Tabs places them the way Mojang's CreativeModeTabs does). */
    static final Map<String, DeferredItem<? extends Item>> ITEM = new LinkedHashMap<>();
    static final List<Runnable> FLAMMABLE = new ArrayList<>();

    /** Built up front: blocks register before particle types, and the copper torch takes its flame when built
     *  (vanilla's torches take ParticleTypes.FLAME, which exists before blocks). The same instance is registered. */
    private static final SimpleParticleType COPPER_FIRE_FLAME_TYPE = new SimpleParticleType(false);
    public static final DeferredHolder<net.minecraft.core.particles.ParticleType<?>, SimpleParticleType> COPPER_FIRE_FLAME =
            PARTICLES.register("copper_fire_flame", () -> COPPER_FIRE_FLAME_TYPE);

    // ShelfBlock's SoundEvents.SHELF_* (sounds.json from Mojang's asset store)
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELF_ACTIVATE = sound("block.shelf.activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELF_DEACTIVATE = sound("block.shelf.deactivate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELF_TAKE_ITEM = sound("block.shelf.take_item");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELF_SINGLE_SWAP = sound("block.shelf.single_swap");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELF_PLACE_ITEM = sound("block.shelf.place_item");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHELF_MULTI_SWAP = sound("block.shelf.multi_swap");

    private static final List<DeferredBlock<Block>> SHELVES = new ArrayList<>();
    /** BlockEntityTypes.SHELF ("shelf"), for every shelf. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShelfBlockEntity>> SHELF_ENTITY = BLOCK_ENTITIES.register("shelf",
            () -> BlockEntityType.Builder.of(ShelfBlockEntity::new, SHELVES.stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));

    private Content() {}

    static void register(IEventBus modBus) {
        // Calcite (Blocks.CALCITE_*): 1.21.1's calcite has the snapshot's definition.
        Supplier<Block> calcite = () -> Blocks.CALCITE;
        block("calcite_pillar", RotatedPillarBlock::new, () -> Properties.ofFullCopy(Blocks.CALCITE));
        family("calcite", calcite);
        DeferredBlock<Block> polished = block("polished_calcite", Block::new, () -> Properties.ofFullCopy(Blocks.CALCITE));
        family("polished_calcite", polished);
        DeferredBlock<Block> bricks = block("polished_calcite_bricks", Block::new, () -> Properties.ofFullCopy(polished.get()));
        family("polished_calcite_brick", bricks);

        // Packed ice bricks: of().mapColor(ICE).instrument(CHIME).requiresCorrectToolForDrops().friction(0.6)
        // .strength(0.5).sound(PACKED_ICE) -- PACKED_ICE is a 26.x sound set with new sound events (sounds.json).
        DeferredSoundType packedIce = new DeferredSoundType(1.0F, 1.0F, sound("block.packed_ice.break"),
                sound("block.packed_ice.step"), sound("block.packed_ice.place"), sound("block.packed_ice.hit"),
                sound("block.packed_ice.fall"));
        DeferredBlock<Block> iceBricks = block("packed_ice_bricks", Block::new, () -> Properties.of().mapColor(MapColor.ICE)
                .instrument(NoteBlockInstrument.CHIME).requiresCorrectToolForDrops().friction(0.6F).strength(0.5F).sound(packedIce));
        family("packed_ice_brick", iceBricks);
        block("chiseled_packed_ice", Block::new, () -> Properties.ofFullCopy(iceBricks.get()));

        // Snow bricks: of().mapColor(SNOW).friction(0.6).requiresCorrectToolForDrops().strength(0.6).sound(SNOW).
        DeferredBlock<Block> snowBricks = block("snow_bricks", Block::new, () -> Properties.of().mapColor(MapColor.SNOW)
                .friction(0.6F).requiresCorrectToolForDrops().strength(0.6F).sound(SoundType.SNOW));
        family("snow_brick", snowBricks);

        // Wool and concrete stairs and slabs: ColorCollection.zipMap over WOOL / CONCRETE with registerStair / registerSlab.
        // Wool ones are flammable 30/60 (FireBlock.bootStrap, as wool).
        for (DyeColor c : DyeColor.values()) {
            Supplier<Block> wool = () -> BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(c.getName() + "_wool"));
            Supplier<Block> concrete = () -> BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(c.getName() + "_concrete"));
            DeferredBlock<Block> ws = stairs(c.getName() + "_wool_stairs", wool);
            DeferredBlock<Block> wl = slab(c.getName() + "_wool_slab", wool);
            FLAMMABLE.add(() -> fire().setFlammable(ws.get(), 30, 60));
            FLAMMABLE.add(() -> fire().setFlammable(wl.get(), 30, 60));
            stairs(c.getName() + "_concrete_stairs", concrete);
            slab(c.getName() + "_concrete_slab", concrete);
        }

        // Copper (WeatheringCopperCollection: four stages that oxidize, each with a waxed twin of the plain class).
        for (WeatherState stage : WeatherState.values()) {
            String p = stage == WeatherState.UNAFFECTED ? "" : stage.getSerializedName() + "_";
            // bars: of().requiresCorrectToolForDrops().strength(5, 6).sound(COPPER).noOcclusion()
            Supplier<Properties> bars = () -> Properties.of().requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.COPPER).noOcclusion();
            block(p + "copper_bars", props -> new WeatheringCopperBarsBlock(stage, props), bars);
            block("waxed_" + p + "copper_bars", Access.Bars::new, bars);
            // chain: of().forceSolidOn().requiresCorrectToolForDrops().strength(5, 6).sound(CHAIN).noOcclusion()
            Supplier<Properties> chain = () -> Properties.of().forceSolidOn().requiresCorrectToolForDrops().strength(5.0F, 6.0F).sound(SoundType.CHAIN).noOcclusion();
            block(p + "copper_chain", props -> new WeatheringCopperChainBlock(stage, props), chain);
            block("waxed_" + p + "copper_chain", ChainBlock::new, chain);
            // lantern: of().mapColor(METAL).forceSolidOn().strength(3.5).sound(LANTERN).lightLevel(15).noOcclusion()
            // .pushReaction(POPPED)
            Supplier<Properties> lantern = () -> Properties.of().mapColor(MapColor.METAL).forceSolidOn().strength(3.5F)
                    .sound(SoundType.LANTERN).lightLevel(s -> 15).noOcclusion().pushReaction(PushReaction.DESTROY);
            block(p + "copper_lantern", props -> new WeatheringLanternBlock(stage, props), lantern);
            block("waxed_" + p + "copper_lantern", LanternBlock::new, lantern);
        }
        // copper torch: of().noCollision().instabreak().lightLevel(14).sound(WOOD).pushReaction(POPPED), copper fire flame;
        // copper wall torch: wallVariant(COPPER_TORCH, true) = the torch's properties, drops like the torch, no item.
        Supplier<Properties> torch = () -> Properties.of().noCollission().instabreak().lightLevel(s -> 14).sound(SoundType.WOOD)
                .pushReaction(PushReaction.DESTROY);
        DeferredBlock<Block> copperTorch = BLOCKS.register("copper_torch", () -> new Access.Torch(COPPER_FIRE_FLAME_TYPE, torch.get()));
        DeferredBlock<Block> copperWallTorch = BLOCKS.register("copper_wall_torch",
                () -> new Access.WallTorch(COPPER_FIRE_FLAME_TYPE, torch.get().dropsLike(copperTorch.get())));
        ITEM.put("copper_torch", ITEMS.register("copper_torch",
                () -> new net.minecraft.world.item.StandingAndWallBlockItem(copperTorch.get(), copperWallTorch.get(),
                        new Item.Properties(), net.minecraft.core.Direction.DOWN)));

        ITEM.put("copper_nugget", ITEMS.registerSimpleItem("copper_nugget"));

        // Shelves: of().mapColor(<wood>).instrument(BASS).sound(SHELF).ignitedByLava().strength(2, 3); the nether woods
        // without ignitedByLava. FireBlock: 30/20 for the overworld woods. Map colors as Mojang takes them.
        DeferredSoundType shelfSound = new DeferredSoundType(1.0F, 1.0F, sound("block.shelf.break"), sound("block.shelf.step"),
                sound("block.shelf.place"), sound("block.shelf.hit"), sound("block.shelf.fall"));
        Map<String, Supplier<MapColor>> shelfColors = new LinkedHashMap<>();
        shelfColors.put("oak", () -> Blocks.OAK_PLANKS.defaultMapColor());
        shelfColors.put("spruce", () -> Blocks.SPRUCE_LOG.defaultMapColor());
        shelfColors.put("birch", () -> Blocks.BIRCH_PLANKS.defaultMapColor());
        shelfColors.put("jungle", () -> Blocks.JUNGLE_LOG.defaultMapColor());
        shelfColors.put("acacia", () -> Blocks.ACACIA_PLANKS.defaultMapColor());
        shelfColors.put("dark_oak", () -> Blocks.DARK_OAK_LOG.defaultMapColor());
        shelfColors.put("mangrove", () -> Blocks.MANGROVE_LOG.defaultMapColor());
        shelfColors.put("cherry", () -> Blocks.CHERRY_PLANKS.defaultMapColor());
        shelfColors.put("bamboo", () -> Blocks.BAMBOO_PLANKS.defaultMapColor());
        shelfColors.put("crimson", () -> MapColor.CRIMSON_STEM);
        shelfColors.put("warped", () -> MapColor.WARPED_STEM);
        shelfColors.forEach((wood, color) -> {
            boolean nether = wood.equals("crimson") || wood.equals("warped");
            DeferredBlock<Block> shelf = block(wood + "_shelf", ShelfBlock::new, () -> {
                Properties p = Properties.of().mapColor(color.get()).instrument(NoteBlockInstrument.BASS).sound(shelfSound);
                return (nether ? p : p.ignitedByLava()).strength(2.0F, 3.0F);
            });
            SHELVES.add(shelf);
            if (!nether) {
                FLAMMABLE.add(() -> fire().setFlammable(shelf.get(), 30, 20));
            }
        });
        // Modded woods (tools/modshelves.py): Mojang's shelf properties; map color from the wood's planks when its mod
        // is loaded (MapColor.WOOD otherwise); nether woods as crimson and warped. They load without the wood's mod.
        for (ModdedShelves.Wood wood : ModdedShelves.WOODS) {
            ResourceLocation planks = ResourceLocation.parse(wood.planks());
            DeferredBlock<Block> shelf = block(wood.id(), ShelfBlock::new, () -> {
                Properties p = Properties.of().mapColor(state -> BuiltInRegistries.BLOCK.getOptional(planks)
                                .map(Block::defaultMapColor).orElse(MapColor.WOOD))
                        .instrument(NoteBlockInstrument.BASS).sound(shelfSound);
                return (wood.nether() ? p : p.ignitedByLava()).strength(2.0F, 3.0F);
            });
            SHELVES.add(shelf);
            if (!wood.nether()) {
                FLAMMABLE.add(() -> fire().setFlammable(shelf.get(), 30, 20));
            }
        }

        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        PARTICLES.register(modBus);
        SOUNDS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(FMLCommonSetupEvent.class, e -> e.enqueueWork(() -> FLAMMABLE.forEach(Runnable::run)));
    }

    private static FireBlock fire() {
        return (FireBlock) Blocks.FIRE;
    }

    static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, name)));
    }

    /** Stairs, slab and wall of a base block (Mojang's registerStair / registerSlab / registerWall). */
    private static void family(String prefix, Supplier<? extends Block> base) {
        stairs(prefix + "_stairs", base);
        slab(prefix + "_slab", base);
        block(prefix + "_wall", WallBlock::new, () -> Properties.ofLegacyCopy(base.get()).forceSolidOn());
    }

    private static DeferredBlock<Block> stairs(String name, Supplier<? extends Block> base) {
        return block(name, p -> new StairBlock(base.get().defaultBlockState(), p), () -> Properties.ofFullCopy(base.get()));
    }

    private static DeferredBlock<Block> slab(String name, Supplier<? extends Block> base) {
        return block(name, SlabBlock::new, () -> Properties.ofLegacyCopy(base.get()));
    }

    private static DeferredBlock<Block> block(String name, Function<Properties, ? extends Block> make, Supplier<Properties> properties) {
        DeferredBlock<Block> b = BLOCKS.register(name, () -> make.apply(properties.get()));
        ITEM.put(name, ITEMS.registerSimpleBlockItem(b));
        return b;
    }
}
