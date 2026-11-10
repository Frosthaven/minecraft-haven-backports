package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.block.SaplingBlock26;
import dev.frosthaven.havenbackports.block.TreeGrower26;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.HangingSignItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 26.x wood sets (Blocks.java: POPLAR_*, PALE_OAK_*), each block as Mojang defines it. 26.x's BlockSetType.DEFAULT_WOOD
 * is 1.21.1's BlockSetType.OAK (same flags and sounds), BlockSoundSets.WOOD is SoundType.WOOD, POPPED is DESTROY.
 * Stairs are registerLegacyStair (ofLegacyCopy of the planks). Wall signs are wallVariant(standing, true): the
 * standing sign's properties, loot and name. Fuel, compost and stripping are NeoForge data maps (tools/woods.py).
 */
public final class Woods {
    /** A leaves block of the set: id, its block, its map color. */
    public record Leaves(String id, Function<Properties, Block> make, MapColor color) {}

    /** The blocks other classes reach (signs for the block entity types, saplings for pots and trades, boats). */
    public record Wood(String name, WoodType type, DeferredBlock<Block> planks, DeferredBlock<Block> sapling, DeferredBlock<Block> log,
                       List<DeferredBlock<Block>> signs, List<DeferredBlock<Block>> hangingSigns, DeferredBlock<Block> pottedSapling,
                       DeferredItem<Item> boat, DeferredItem<Item> chestBoat, Supplier<Boat.Type> boatType) {}

    static final List<Wood> ALL = new ArrayList<>();
    public static Wood POPLAR;

    private Woods() {}

    public static List<Wood> all() {
        return ALL;
    }

    static void register() {
        // POPLAR_PLANKS LIGHT_GRAY; POPLAR_LOG logProperties(LIGHT_GRAY top, PODZOL side); POPLAR_WOOD PODZOL; leaves
        // UntintedParticleLeavesBlock(0.01, {color}_POPLAR_LEAVES particle, ambient POPLAR_LEAVES_AMBIENT)
        // POPLAR_SAPLING of().mapColor(METAL) (Blocks.java 329, as Mojang has it)
        POPLAR = wood("poplar", MapColor.COLOR_LIGHT_GRAY, MapColor.COLOR_LIGHT_GRAY, MapColor.PODZOL, MapColor.PODZOL, MapColor.METAL,
                Dappled.POPLAR_LEAVES, () -> Dappled.POPLAR, BoatTypes::poplar);
    }

    static Wood wood(String w, MapColor planksColor, MapColor logTop, MapColor logSide, MapColor woodColor, MapColor saplingColor, List<Leaves> leaves,
            Supplier<TreeGrower26> grower, Supplier<Boat.Type> boatType) {
        WoodType type = WoodType.register(new WoodType(HavenBackports.ID + ":" + w, BlockSetType.OAK));
        DeferredBlock<Block> planks = Content.block(w + "_planks", Block::new, () -> Properties.of().mapColor(planksColor)
                .instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava());
        DeferredBlock<Block> sapling = Content.block(w + "_sapling", p -> new SaplingBlock26(grower, HavenBackports.ID + ":" + w, p),
                () -> Properties.of().mapColor(saplingColor).noCollission().randomTicks()
                        .instabreak().sound(SoundType.GRASS).pushReaction(PushReaction.DESTROY));
        DeferredBlock<Block> log = Content.block(w + "_log", RotatedPillarBlock::new, () -> log(logTop, logSide));
        DeferredBlock<Block> strippedLog = Content.block("stripped_" + w + "_log", RotatedPillarBlock::new,
                () -> log(planksColor, planksColor));
        DeferredBlock<Block> wood = Content.block(w + "_wood", RotatedPillarBlock::new, () -> Properties.of().mapColor(woodColor)
                .instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
        DeferredBlock<Block> strippedWood = Content.block("stripped_" + w + "_wood", RotatedPillarBlock::new, () -> Properties.of()
                .mapColor(planksColor).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava());
        for (Leaves l : leaves) {
            DeferredBlock<Block> b = Content.block(l.id(), l.make(), () -> Dappled.leavesProperties(l.id()).mapColor(l.color()));
            Content.FLAMMABLE.add(() -> Content.fire().setFlammable(b.get(), 30, 60));
        }
        Supplier<Properties> sign = () -> Properties.of().mapColor(planksColor).forceSolidOn().instrument(NoteBlockInstrument.BASS)
                .noCollission().strength(1.0F).ignitedByLava();
        DeferredBlock<Block> standing = Content.BLOCKS.register(w + "_sign", () -> new StandingSignBlock(type, sign.get()));
        DeferredBlock<Block> wall = Content.BLOCKS.register(w + "_wall_sign", () -> new WallSignBlock(type,
                Properties.ofFullCopy(standing.get()).dropsLike(standing.get())) {
            @Override
            public String getDescriptionId() {
                return standing.get().getDescriptionId();
            }
        });
        DeferredBlock<Block> hanging = Content.BLOCKS.register(w + "_hanging_sign", () -> new CeilingHangingSignBlock(type, sign.get()));
        DeferredBlock<Block> wallHanging = Content.BLOCKS.register(w + "_wall_hanging_sign", () -> new WallHangingSignBlock(type,
                Properties.ofFullCopy(hanging.get()).dropsLike(hanging.get())) {
            @Override
            public String getDescriptionId() {
                return hanging.get().getDescriptionId();
            }
        });
        Content.ITEM.put(w + "_sign", Content.ITEMS.register(w + "_sign",
                () -> new SignItem(new Item.Properties().stacksTo(16), standing.get(), wall.get())));
        Content.ITEM.put(w + "_hanging_sign", Content.ITEMS.register(w + "_hanging_sign",
                () -> new HangingSignItem(hanging.get(), wallHanging.get(), new Item.Properties().stacksTo(16))));
        Content.block(w + "_pressure_plate", p -> new PressurePlateBlock(BlockSetType.OAK, p), () -> Properties.of().mapColor(planksColor)
                .forceSolidOn().instrument(NoteBlockInstrument.BASS).noCollission().strength(0.5F).ignitedByLava()
                .pushReaction(PushReaction.DESTROY));
        Content.block(w + "_trapdoor", p -> new TrapDoorBlock(BlockSetType.OAK, p), () -> Properties.of().mapColor(planksColor)
                .instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().isValidSpawn((s, l, pos, e) -> false).ignitedByLava());
        DeferredBlock<Block> potted = Content.BLOCKS.register("potted_" + w + "_sapling", () -> new FlowerPotBlock(
                () -> (FlowerPotBlock) Blocks.FLOWER_POT, sapling, Properties.of().instabreak().noOcclusion().pushReaction(PushReaction.DESTROY)));
        Content.block(w + "_button", p -> new ButtonBlock(BlockSetType.OAK, 30, p),
                () -> Properties.of().noCollission().strength(0.5F).pushReaction(PushReaction.DESTROY));
        DeferredBlock<Block> stairs = Content.block(w + "_stairs", p -> new StairBlock(planks.get().defaultBlockState(), p),
                () -> Properties.ofLegacyCopy(planks.get()));
        DeferredBlock<Block> slab = Content.block(w + "_slab", SlabBlock::new, () -> Properties.ofLegacyCopy(planks.get()));
        DeferredBlock<Block> gate = Content.block(w + "_fence_gate", p -> new FenceGateBlock(type, p), () -> Properties.of()
                .mapColor(planksColor).forceSolidOn().instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava());
        DeferredBlock<Block> fence = Content.block(w + "_fence", FenceBlock::new, () -> Properties.of().mapColor(planksColor)
                .instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).ignitedByLava().sound(SoundType.WOOD));
        Content.block(w + "_door", p -> new DoorBlock(BlockSetType.OAK, p), () -> Properties.of().mapColor(planksColor)
                .instrument(NoteBlockInstrument.BASS).strength(3.0F).noOcclusion().ignitedByLava().pushReaction(PushReaction.DESTROY));
        Content.shelf(w + "_shelf", () -> planksColor, false);
        DeferredItem<Item> boat = Content.ITEMS.register(w + "_boat", () -> new BoatItem(false, boatType.get(), new Item.Properties().stacksTo(1)));
        DeferredItem<Item> chestBoat = Content.ITEMS.register(w + "_chest_boat",
                () -> new BoatItem(true, boatType.get(), new Item.Properties().stacksTo(1)));
        Content.ITEM.put(w + "_boat", boat);
        Content.ITEM.put(w + "_chest_boat", chestBoat);
        // FireBlock: planks, slab, fence gate, fence, stairs 5/20; logs and woods 5/5; leaves 30/60 (above)
        for (DeferredBlock<Block> b : List.of(planks, slab, gate, fence, stairs)) {
            Content.FLAMMABLE.add(() -> Content.fire().setFlammable(b.get(), 5, 20));
        }
        for (DeferredBlock<Block> b : List.of(log, strippedLog, wood, strippedWood)) {
            Content.FLAMMABLE.add(() -> Content.fire().setFlammable(b.get(), 5, 5));
        }
        Wood out = new Wood(w, type, planks, sapling, log, List.of(standing, wall), List.of(hanging, wallHanging), potted, boat, chestBoat,
                boatType);
        ALL.add(out);
        return out;
    }

    private static Properties log(MapColor top, MapColor side) {
        return Properties.of().mapColor(state -> state.getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Y ? top : side)
                .instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.WOOD).ignitedByLava();
    }

    /** Signs join the vanilla sign block entities; saplings go in flower pots; boats dispense (1.21.1 patterns). */
    static void setup(IEventBus modBus) {
        modBus.addListener(BlockEntityTypeAddBlocksEvent.class, e -> {
            e.modify(BlockEntityType.SIGN, ALL.stream().flatMap(w -> w.signs().stream()).map(DeferredBlock::get).toArray(Block[]::new));
            e.modify(BlockEntityType.HANGING_SIGN, ALL.stream().flatMap(w -> w.hangingSigns().stream()).map(DeferredBlock::get).toArray(Block[]::new));
        });
        Content.COMMON_SETUP.add(() -> {
            for (Wood w : ALL) {
                ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(w.sapling().getId(), w.pottedSapling());
                DispenserBlock.registerBehavior(w.boat().get(),
                        new net.minecraft.core.dispenser.BoatDispenseItemBehavior(w.boatType().get(), false));
                DispenserBlock.registerBehavior(w.chestBoat().get(),
                        new net.minecraft.core.dispenser.BoatDispenseItemBehavior(w.boatType().get(), true));
            }
        });
    }
}
