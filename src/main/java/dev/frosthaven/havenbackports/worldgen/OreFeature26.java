package dev.frosthaven.havenbackports.worldgen;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.BulkSectionAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

/**
 * 26.x OreFeature (with AbstractOreFeature's air exposure check), for ores whose targets use 26.x's position-aware rule
 * tests (the Ice Caves' ores: all_of, any_of, height_match). 1.21.1's RuleTest.test has no position, so its OreFeature
 * cannot run them; ores without these tests stay 1.21.1's minecraft:ore. 26.x walks each touched column bottom to top
 * (1.21.1 walks spheres), so it is ported as it is, not 1.21.1's loop with a position added.
 */
public class OreFeature26 extends Feature<OreFeature26.Config> {
    /** 26.x RuleTest: test(state, pos, random). 1.21.1's rule tests are taken as they are (they ignore the position). */
    public interface Rule {
        boolean test(BlockState state, BlockPos pos, RandomSource random);

        Codec<Rule> SELF = Codec.lazyInitialized(() -> Rule.CODEC);
        MapCodec<AllOf> ALL_OF = SELF.listOf().fieldOf("rules").xmap(AllOf::new, AllOf::rules);
        MapCodec<AnyOf> ANY_OF = SELF.listOf().fieldOf("rules").xmap(AnyOf::new, AnyOf::rules);
        Map<String, MapCodec<? extends Rule>> TYPES = Map.<String, MapCodec<? extends Rule>>of(
                "haven_backports:all_of", ALL_OF, "haven_backports:any_of", ANY_OF, "haven_backports:height_match", HeightMatch.CODEC);
        Codec<Rule> OURS = Codec.STRING.partialDispatch("predicate_type", r -> DataResult.success(r.type()),
                t -> TYPES.containsKey(t) ? DataResult.success(TYPES.get(t)) : DataResult.error(() -> "not a 26.x rule test: " + t));
        Codec<Rule> CODEC = Codec.either(Codec.lazyInitialized(() -> OURS), RuleTest.CODEC)
                .xmap(e -> e.map(r -> r, Vanilla::new), r -> r instanceof Vanilla v ? Either.right(v.rule()) : Either.left(r));

        default String type() {
            throw new IllegalStateException("no type");
        }
    }

    public record Vanilla(RuleTest rule) implements Rule {
        @Override
        public boolean test(BlockState state, BlockPos pos, RandomSource random) {
            return this.rule.test(state, random);
        }
    }

    /** 26.x AllOfRuleTest. */
    public record AllOf(List<Rule> rules) implements Rule {
        @Override
        public boolean test(BlockState state, BlockPos pos, RandomSource random) {
            for (Rule rule : this.rules) {
                if (!rule.test(state, pos, random)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public String type() {
            return "haven_backports:all_of";
        }
    }

    /** 26.x AnyOfRuleTest. */
    public record AnyOf(List<Rule> rules) implements Rule {
        @Override
        public boolean test(BlockState state, BlockPos pos, RandomSource random) {
            for (Rule rule : this.rules) {
                if (rule.test(state, pos, random)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public String type() {
            return "haven_backports:any_of";
        }
    }

    /** 26.x HeightMatchTest. */
    public record HeightMatch(int minInclusive, int maxInclusive) implements Rule {
        static final MapCodec<HeightMatch> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.INT.fieldOf("min_inclusive").forGetter(HeightMatch::minInclusive),
                Codec.INT.fieldOf("max_inclusive").forGetter(HeightMatch::maxInclusive)).apply(i, HeightMatch::new));

        @Override
        public boolean test(BlockState state, BlockPos pos, RandomSource random) {
            return this.minInclusive <= pos.getY() && pos.getY() <= this.maxInclusive;
        }

        @Override
        public String type() {
            return "haven_backports:height_match";
        }
    }

    /** 26.x BlockReplacement. */
    public record Target(Rule target, BlockState state) {
        static final Codec<Target> CODEC = RecordCodecBuilder.create(i -> i.group(
                Rule.CODEC.fieldOf("target").forGetter(Target::target), BlockState.CODEC.fieldOf("state").forGetter(Target::state)).apply(i, Target::new));
    }

    public record Config(List<Target> targets, int size, float discardChanceOnAirExposure) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.list(Target.CODEC).fieldOf("targets").forGetter(Config::targets),
                Codec.intRange(0, 64).fieldOf("size").forGetter(Config::size),
                Codec.floatRange(0.0F, 1.0F).fieldOf("discard_chance_on_air_exposure").forGetter(Config::discardChanceOnAirExposure))
                .apply(i, Config::new));
    }

    public OreFeature26() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        int size = context.config().size();
        float dir = random.nextFloat() * (float) Math.PI;
        float spreadXY = size / 8.0F;
        int maxRadius = Mth.ceil((size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double x0 = origin.getX() + Math.sin(dir) * spreadXY;
        double x1 = origin.getX() - Math.sin(dir) * spreadXY;
        double z0 = origin.getZ() + Math.cos(dir) * spreadXY;
        double z1 = origin.getZ() - Math.cos(dir) * spreadXY;
        double y0 = origin.getY() + random.nextInt(3) - 2;
        double y1 = origin.getY() + random.nextInt(3) - 2;
        int xStart = origin.getX() - Mth.ceil(spreadXY) - maxRadius;
        int yStart = origin.getY() - 2 - maxRadius;
        int zStart = origin.getZ() - Mth.ceil(spreadXY) - maxRadius;
        int sizeXZ = 2 * (Mth.ceil(spreadXY) + maxRadius);
        // 26.x LevelReader.anyHeightMatches(OCEAN_FLOOR_WG, xStart, zStart, xStart + sizeXZ - 1, zStart + sizeXZ - 1, yStart, MAX)
        for (int x = xStart; x <= xStart + sizeXZ - 1; x++) {
            for (int z = zStart; z <= zStart + sizeXZ - 1; z++) {
                if (level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) >= yStart) {
                    return this.doPlace(level, random, context.config(), x0, x1, z0, z1, y0, y1, xStart, zStart, sizeXZ);
                }
            }
        }
        return false;
    }

    private boolean doPlace(WorldGenLevel level, RandomSource random, Config config, double x0, double x1, double z0, double z1, double y0,
                            double y1, int xStart, int zStart, int sizeXZ) {
        int size = config.size();
        int placed = 0;
        BlockPos.MutableBlockPos orePos = new BlockPos.MutableBlockPos();
        double[] data = new double[size * 4];
        double maxRadius = 0.0;
        for (int i = 0; i < size; i++) {
            float step = (float) i / size;
            double ss = random.nextDouble() * size / 16.0;
            double r = ((Mth.sin((float) Math.PI * step) + 1.0F) * ss + 1.0) / 2.0;
            data[i * 4] = Mth.lerp(step, x0, x1);
            data[i * 4 + 1] = Mth.lerp(step, y0, y1);
            data[i * 4 + 2] = Mth.lerp(step, z0, z1);
            data[i * 4 + 3] = r;
            maxRadius = Math.max(maxRadius, r);
        }
        double stepDist = Mth.length(x1 - x0, y1 - y0, z1 - z0) / size;
        for (int i = 0; i < size - 1; i++) {
            if (!(data[i * 4 + 3] <= 0.0)) {
                double radius = data[i * 4 + 3];
                for (int j = i + 1; j < size; j++) {
                    double otherRadius = data[j * 4 + 3];
                    if (!(otherRadius <= 0.0)) {
                        double dist = (j - i) * stepDist;
                        double dr = radius - otherRadius;
                        if (dr * dr > dist * dist) {
                            if (!(dr > 0.0)) {
                                data[i * 4 + 3] = -1.0;
                                break;
                            }
                            data[j * 4 + 3] = -1.0;
                        }
                        if (dist > maxRadius) {
                            break;
                        }
                    }
                }
            }
        }
        int lowestAllowedY = level.getMinBuildHeight();
        int highestAllowedY = level.getMaxBuildHeight() - 1;   // 26.x getMaxY: the top block's y
        int gridMaxX = xStart + sizeXZ - 1;
        int gridMaxZ = zStart + sizeXZ - 1;
        int[] columnMinY = new int[sizeXZ * sizeXZ];
        int[] columnMaxY = new int[sizeXZ * sizeXZ];
        Arrays.fill(columnMinY, Integer.MAX_VALUE);
        Arrays.fill(columnMaxY, Integer.MIN_VALUE);
        int touchedMinX = sizeXZ;
        int touchedMaxX = -1;
        int touchedMinZ = sizeXZ;
        int touchedMaxZ = -1;
        for (int ix = 0; ix < size; ix++) {
            double r = data[ix * 4 + 3];
            if (!(r < 0.0)) {
                double rSq = Mth.square(r);
                double xx = data[ix * 4];
                double yy = data[ix * 4 + 1];
                double zz = data[ix * 4 + 2];
                int xMin = Math.max(Mth.floor(xx - r), xStart);
                int zMin = Math.max(Mth.floor(zz - r), zStart);
                int xMax = Math.clamp((long) Mth.floor(xx + r), xMin, gridMaxX);
                int zMax = Math.clamp((long) Mth.floor(zz + r), zMin, gridMaxZ);
                touchedMinX = Math.min(touchedMinX, xMin - xStart);
                touchedMaxX = Math.max(touchedMaxX, xMax - xStart);
                touchedMinZ = Math.min(touchedMinZ, zMin - zStart);
                touchedMaxZ = Math.max(touchedMaxZ, zMax - zStart);
                for (int x = xMin; x <= xMax; x++) {
                    double dx = x + 0.5 - xx;
                    double remainingAfterXSqr = rSq - dx * dx;
                    if (!(remainingAfterXSqr <= 0.0)) {
                        int rowOffset = (x - xStart) * sizeXZ - zStart;
                        for (int z = zMin; z <= zMax; z++) {
                            double dz = z + 0.5 - zz;
                            double remainingSqr = remainingAfterXSqr - dz * dz;
                            if (!(remainingSqr <= 0.0)) {
                                double halfSpan = Math.sqrt(remainingSqr);
                                int yLow = Math.max(Mth.floor(yy - 0.5 - halfSpan) + 1, lowestAllowedY);
                                int yHigh = Math.min(Mth.ceil(yy - 0.5 + halfSpan) - 1, highestAllowedY);
                                if (yLow <= yHigh) {
                                    int index = rowOffset + z;
                                    columnMinY[index] = Math.min(columnMinY[index], yLow);
                                    columnMaxY[index] = Math.max(columnMaxY[index], yHigh);
                                }
                            }
                        }
                    }
                }
            }
        }
        try (BulkSectionAccess sectionGetter = new BulkSectionAccess(level)) {
            Function<BlockPos, BlockState> getBlockState = sectionGetter::getBlockState;
            SectionPos activeSection = null;
            LevelChunkSection section = null;
            for (int gridX = touchedMinX; gridX <= touchedMaxX; gridX++) {
                int xx = xStart + gridX;
                int sectionRelativeX = SectionPos.sectionRelative(xx);
                int sectionX = SectionPos.blockToSectionCoord(xx);
                int rowOffset = gridX * sizeXZ;
                for (int gridZ = touchedMinZ; gridZ <= touchedMaxZ; gridZ++) {
                    int index = rowOffset + gridZ;
                    int yMin = columnMinY[index];
                    if (yMin != Integer.MAX_VALUE) {
                        int yMax = columnMaxY[index];
                        int zx = zStart + gridZ;
                        int sectionRelativeZ = SectionPos.sectionRelative(zx);
                        int sectionZ = SectionPos.blockToSectionCoord(zx);
                        boolean canWriteWholeColumn = level.ensureCanWrite(orePos.set(xx, yMin, zx))
                                && (yMax == yMin || level.ensureCanWrite(orePos.set(xx, yMax, zx)));
                        for (int y = yMin; y <= yMax; y++) {
                            orePos.setY(y);
                            if (canWriteWholeColumn || level.ensureCanWrite(orePos)) {
                                int sectionY = SectionPos.blockToSectionCoord(y);
                                if (activeSection == null || activeSection.getX() != sectionX || activeSection.getY() != sectionY
                                        || activeSection.getZ() != sectionZ) {
                                    section = sectionGetter.getSection(orePos);
                                    activeSection = SectionPos.of(sectionX, sectionY, sectionZ);
                                }
                                if (section != null) {
                                    BlockState blockState = section.getBlockState(sectionRelativeX, SectionPos.sectionRelative(y), sectionRelativeZ);
                                    for (Target target : config.targets()) {
                                        if (target.target().test(blockState, orePos, random)) {
                                            if (checkAirExposure(getBlockState, random, orePos, config.discardChanceOnAirExposure())) {
                                                section.setBlockState(sectionRelativeX, SectionPos.sectionRelative(y), sectionRelativeZ, target.state(), false);
                                                placed++;
                                            }
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return placed > 0;
    }

    /** 26.x AbstractOreFeature.checkAirExposure. */
    private static boolean checkAirExposure(Function<BlockPos, BlockState> getter, RandomSource random, BlockPos pos, float chance) {
        if (chance <= 0.0F || chance < 1.0F && random.nextFloat() >= chance) {
            return true;
        }
        BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
        for (Direction direction : Direction.values()) {
            neighbor.setWithOffset(pos, direction);
            if (getter.apply(neighbor).isAir()) {
                return false;
            }
        }
        return true;
    }
}
