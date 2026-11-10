import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * 26.x half of the offline port test (tools/porttest.py runs it; src/test/.../PortTest.java is the 1.21.1 half, kept
 * in step with this file). Mojang's code, Mojang's data: what each produces is written to the JSON file given.
 */
public final class Dump26 {
    static final String[] TREES = {"red_poplar_leaf_litter", "orange_poplar_leaf_litter", "yellow_poplar_leaf_litter", "red_poplar",
            "orange_poplar", "yellow_poplar", "fallen_poplar_tree"};
    static final int SEEDS = 50;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        JsonObject out = new JsonObject();
        JsonArray climate = new JsonArray();
        var addBiomes = OverworldBiomeBuilder.class.getDeclaredMethod("addBiomes", java.util.function.Consumer.class);
        addBiomes.setAccessible(true);
        java.util.function.Consumer<com.mojang.datafixers.util.Pair<Climate.ParameterPoint, net.minecraft.resources.ResourceKey<?>>> add =
                pair -> climate.add(point(pair.getFirst()) + " " + pair.getSecond().identifier());
        addBiomes.invoke(new OverworldBiomeBuilder(), add);
        out.add("climate", climate);
        JsonObject trees = new JsonObject();
        for (String tree : TREES) {
            JsonElement json = JsonParser.parseReader(new InputStreamReader(
                    Dump26.class.getResourceAsStream("/data/minecraft/worldgen/feature/" + tree + ".json")));
            Feature feature = Feature.DIRECT_CODEC.parse(JsonOps.INSTANCE, inlineProviders(json)).getOrThrow();
            JsonArray runs = new JsonArray();
            for (int seed = 0; seed < SEEDS; seed++) {
                Map<BlockPos, BlockState> placed = new TreeMap<>();
                WorldGenLevel level = flat(placed);
                RandomSource random = new XoroshiroRandomSource(seed);
                feature.place(level, null, random, new BlockPos(0, 64, 0));
                runs.add(blocks(placed));
            }
            trees.add(tree, runs);
        }
        out.add("trees", trees);
        JsonObject shapes = new JsonObject();
        for (var block : new net.minecraft.world.level.block.Block[]{Blocks.LEAF_LITTER, Blocks.SHELF_MUSHROOM}) {
            for (BlockState s : block.getStateDefinition().getPossibleStates()) {
                JsonArray boxes = new JsonArray();
                for (AABB b : s.getShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).toAabbs()) {
                    boxes.add(String.format("%.4f %.4f %.4f %.4f %.4f %.4f", b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
                }
                shapes.add(state(s), boxes);
            }
        }
        out.add("shapes", shapes);
        Files.writeString(Path.of(args[0]), new GsonBuilder().setPrettyPrinting().create().toJson(out));
    }

    /** Named block state providers (data/minecraft/worldgen/block_state_provider) inlined from the jar: the codec
     *  resolves names only through a registry, which this test has not loaded. */
    static JsonElement inlineProviders(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = new JsonObject();
            for (var en : e.getAsJsonObject().entrySet()) {
                JsonElement v = en.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && en.getKey().endsWith("_provider")) {
                    var in = Dump26.class.getResourceAsStream("/data/minecraft/worldgen/block_state_provider/" + v.getAsString().split(":")[1] + ".json");
                    if (in != null) {
                        v = JsonParser.parseReader(new InputStreamReader(in));
                    }
                }
                o.add(en.getKey(), inlineProviders(v));
            }
            return o;
        }
        if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            e.getAsJsonArray().forEach(x -> a.add(inlineProviders(x)));
            return a;
        }
        return e;
    }

    static String point(Climate.ParameterPoint p) {
        return p.temperature().min() + ".." + p.temperature().max() + " " + p.humidity().min() + ".." + p.humidity().max() + " "
                + p.continentalness().min() + ".." + p.continentalness().max() + " " + p.erosion().min() + ".." + p.erosion().max() + " "
                + p.depth().min() + ".." + p.depth().max() + " " + p.weirdness().min() + ".." + p.weirdness().max() + " " + p.offset();
    }

    /** "x y z state" per placed block; leaves' distance is left out (both versions recompute it from tags). */
    static JsonArray blocks(Map<BlockPos, BlockState> placed) {
        JsonArray a = new JsonArray();
        placed.forEach((pos, s) -> a.add(pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + state(s)));
        return a;
    }

    static String state(BlockState s) {
        return s.toString().replaceAll("distance=\\d,?", "").replace(",]", "]");
    }

    /** Grass at y 63 (dirt below to y 56), air above; heightmaps from the blocks as listed in the comment below. */
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
            case "getMinY" -> -64;
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
        return (WorldGenLevel) Proxy.newProxyInstance(Dump26.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, h);
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
        return !s.getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    /** One rule for both versions: a block counts for every heightmap if it has a collision shape (leaves too, except for
     *  the *_NO_LEAVES maps); WORLD_SURFACE counts any non-air block. */
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
