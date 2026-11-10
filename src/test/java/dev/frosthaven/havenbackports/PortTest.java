package dev.frosthaven.havenbackports;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

/**
 * 1.21.1 half of the offline port test: the same inputs as tools/porttest/Dump26.java through our ports (mixins
 * applied: this runs in NeoForge's unit test environment), compared with what Mojang's 26.x code produced
 * (build/porttest/26x.json, written by tools/porttest.py). Kept in step with Dump26.
 */
class PortTest {
    static final Path FIXTURE = Path.of(System.getProperty("haven.porttest", "build/porttest/26x.json"));
    /** 26.x overworld points of biomes not ported yet, or kept at 1.21.1 on purpose (README). */
    static final Set<String> NOT_PORTED = Set.of("minecraft:pale_garden", "minecraft:ice_caves");
    /** 26.x narrowed dripstone caves to humidity -1..0.7 (making room for nothing new: lush caves already won there);
     *  kept at 1.21.1 so no existing biome moves. */
    static final Set<String> KEPT_1211 = Set.of("minecraft:dripstone_caves");

    static JsonObject fixture() throws Exception {
        return JsonParser.parseString(Files.readString(FIXTURE)).getAsJsonObject();
    }

    static String norm(String s) {
        return s.replace(HavenBackports.ID + ":", "minecraft:");
    }

    @Test
    @SuppressWarnings("unchecked")
    void climateParameters() throws Exception {
        List<String> ours = new ArrayList<>();
        var addBiomes = OverworldBiomeBuilder.class.getDeclaredMethod("addBiomes", Consumer.class);
        addBiomes.setAccessible(true);
        Consumer<Pair<Climate.ParameterPoint, ResourceKey<?>>> add = p -> ours.add(norm(point(p.getFirst()) + " " + p.getSecond().location()));
        addBiomes.invoke(new OverworldBiomeBuilder(), add);
        List<String> mojang = new ArrayList<>();
        fixture().getAsJsonArray("climate").forEach(e -> mojang.add(e.getAsString()));
        // 26.x points of biomes not ported yet: dropped from 26.x's list, and 1.21.1's biome at the same point from ours
        Set<String> notPorted = new java.util.HashSet<>();
        for (String m : mojang) {
            if (NOT_PORTED.stream().anyMatch(m::endsWith)) {
                notPorted.add(m.substring(0, m.lastIndexOf(' ')));
            }
        }
        Predicate<String> compared = x -> !notPorted.contains(x.substring(0, x.lastIndexOf(' ')))
                && NOT_PORTED.stream().noneMatch(x::endsWith) && KEPT_1211.stream().noneMatch(x::endsWith);
        List<String> a = mojang.stream().filter(compared).toList();
        List<String> b = ours.stream().filter(compared).toList();
        assertEquals(a, b, "overworld parameter list (in order) differs from 26.x outside the biomes not ported yet");
    }

    @Test
    void trees() throws Exception {
        JsonObject trees = fixture().getAsJsonObject("trees");
        for (String tree : trees.keySet()) {
            JsonElement json = JsonParser.parseReader(new InputStreamReader(PortTest.class.getResourceAsStream(
                    "/data/" + HavenBackports.ID + "/worldgen/configured_feature/" + tree + ".json")));
            ConfiguredFeature<?, ?> feature = ConfiguredFeature.DIRECT_CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            JsonArray runs = trees.getAsJsonArray(tree);
            for (int seed = 0; seed < runs.size(); seed++) {
                Map<BlockPos, BlockState> placed = new TreeMap<>();
                feature.place(flat(placed), null, new XoroshiroRandomSource(seed), new BlockPos(0, 64, 0));
                List<String> mine = new ArrayList<>();
                placed.forEach((pos, s) -> mine.add(pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + norm(state(s))));
                List<String> theirs = new ArrayList<>();
                runs.get(seed).getAsJsonArray().forEach(e -> theirs.add(e.getAsString()));
                assertEquals(theirs, mine, tree + " seed " + seed);
            }
        }
    }

    @Test
    void shapes() throws Exception {
        JsonObject shapes = fixture().getAsJsonObject("shapes");
        int n = 0;
        for (Block block : List.of(Dappled.LEAF_LITTER.get(), Dappled.SHELF_MUSHROOM.get())) {
            for (BlockState s : block.getStateDefinition().getPossibleStates()) {
                List<String> boxes = new ArrayList<>();
                for (AABB b : s.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).toAabbs()) {
                    boxes.add(String.format("%.4f %.4f %.4f %.4f %.4f %.4f", b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
                }
                String key = norm(state(s));
                assertTrue(shapes.has(key), "26.x has no state " + key);
                List<String> theirs = new ArrayList<>();
                shapes.getAsJsonArray(key).forEach(e -> theirs.add(e.getAsString()));
                assertEquals(theirs, boxes, key);
                n++;
            }
        }
        assertEquals(shapes.size(), n, "state count");
    }

    static String point(Climate.ParameterPoint p) {
        return p.temperature().min() + ".." + p.temperature().max() + " " + p.humidity().min() + ".." + p.humidity().max() + " "
                + p.continentalness().min() + ".." + p.continentalness().max() + " " + p.erosion().min() + ".." + p.erosion().max() + " "
                + p.depth().min() + ".." + p.depth().max() + " " + p.weirdness().min() + ".." + p.weirdness().max() + " " + p.offset();
    }

    static String state(BlockState s) {
        return s.toString().replaceAll("distance=\\d,?", "").replace(",]", "]");
    }

    static WorldGenLevel flat(Map<BlockPos, BlockState> placed) {
        InvocationHandler h = (proxy, m, a) -> switch (m.getName()) {
            case "getBlockState" -> get(placed, (BlockPos) a[0]);
            case "getFluidState" -> get(placed, (BlockPos) a[0]).getFluidState();
            case "setBlock" -> {
                placed.put(((BlockPos) a[0]).immutable(), (BlockState) a[1]);
                yield true;
            }
            case "isStateAtPosition" -> ((Predicate<BlockState>) a[1]).test(get(placed, (BlockPos) a[0]));
            case "isFluidAtPosition" -> ((Predicate) a[1]).test(get(placed, (BlockPos) a[0]).getFluidState());
            case "getHeightmapPos" -> {
                BlockPos p = (BlockPos) a[1];
                yield new BlockPos(p.getX(), height(placed, (Heightmap.Types) a[0], p.getX(), p.getZ()), p.getZ());
            }
            case "getHeight" -> a == null ? 384 : height(placed, (Heightmap.Types) a[0], (int) a[1], (int) a[2]);
            case "getMinBuildHeight" -> -64;
            case "getSeed" -> 0L;
            case "scheduleTick" -> null;
            case "getBlockEntity" -> null;
            case "isClientSide" -> false;
            case "getRandom" -> RandomSource.create(0);
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == a[0];
            case "toString" -> "flat";
            default -> {
                if (m.isDefault()) {
                    yield InvocationHandler.invokeDefault(proxy, m, a);
                }
                throw new UnsupportedOperationException(m.toString());
            }
        };
        return (WorldGenLevel) Proxy.newProxyInstance(PortTest.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, h);
    }

    static BlockState get(Map<BlockPos, BlockState> placed, BlockPos pos) {
        BlockState s = placed.get(pos);
        if (s != null) {
            return s;
        }
        int y = pos.getY();
        return y == 63 ? Blocks.GRASS_BLOCK.defaultBlockState() : y < 63 && y >= 56 ? Blocks.DIRT.defaultBlockState()
                : y < 56 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    static boolean solid(BlockState s) {
        return !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    static int height(Map<BlockPos, BlockState> placed, Heightmap.Types type, int x, int z) {
        for (int y = 319; y >= -64; y--) {
            BlockState s = get(placed, new BlockPos(x, y, z));
            boolean counts = switch (type) {
                case WORLD_SURFACE, WORLD_SURFACE_WG -> !s.isAir();
                case MOTION_BLOCKING_NO_LEAVES -> solid(s) && !(s.getBlock() instanceof LeavesBlock);
                default -> solid(s);
            };
            if (counts) {
                return y + 1;
            }
        }
        return -64;
    }
}
