package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Column;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.phys.Vec3;

/** 26.x LargeSpeleothemFeature (1.21.1's large dripstone feature made general: any base block, replaceable set), line
 *  for line; the debug markers are left out. */
public class LargeSpeleothemFeature extends Feature<LargeSpeleothemFeature.Config> {
    public record Config(BlockState baseBlock, HolderSet<Block> replaceableBlocks, int floorToCeilingSearchRange, IntProvider columnRadius,
                         FloatProvider heightScale, float maxColumnRadiusToCaveHeightRatio, FloatProvider stalactiteBluntness,
                         FloatProvider stalagmiteBluntness, FloatProvider windSpeed, int minRadiusForWind, float minBluntnessForWind)
            implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        BlockState.CODEC.fieldOf("base_block").forGetter(Config::baseBlock),
                        RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("replaceable_blocks").forGetter(Config::replaceableBlocks),
                        Codec.intRange(1, 512).optionalFieldOf("floor_to_ceiling_search_range", 30).forGetter(Config::floorToCeilingSearchRange),
                        IntProvider.codec(1, 16).fieldOf("column_radius").forGetter(Config::columnRadius),
                        FloatProvider.codec(0.0F, 20.0F).fieldOf("height_scale").forGetter(Config::heightScale),
                        Codec.floatRange(0.1F, 1.0F).fieldOf("max_column_radius_to_cave_height_ratio").forGetter(Config::maxColumnRadiusToCaveHeightRatio),
                        FloatProvider.codec(0.1F, 10.0F).fieldOf("stalactite_bluntness").forGetter(Config::stalactiteBluntness),
                        FloatProvider.codec(0.1F, 10.0F).fieldOf("stalagmite_bluntness").forGetter(Config::stalagmiteBluntness),
                        FloatProvider.codec(0.0F, 2.0F).fieldOf("wind_speed").forGetter(Config::windSpeed),
                        Codec.intRange(0, 100).fieldOf("min_radius_for_wind").forGetter(Config::minRadiusForWind),
                        Codec.floatRange(0.0F, 5.0F).fieldOf("min_bluntness_for_wind").forGetter(Config::minBluntnessForWind))
                .apply(i, Config::new));
    }

    public LargeSpeleothemFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        Config c = context.config();
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        if (!SpeleothemUtils.isEmptyOrWater(level, origin)) {
            return false;
        }
        Optional<Column> column = Column.scan(level, origin, c.floorToCeilingSearchRange(), SpeleothemUtils::isEmptyOrWater,
                state -> SpeleothemUtils.isBaseOrLava(state, c.baseBlock().getBlock(), c.replaceableBlocks()));
        if (column.isEmpty() || !(column.get() instanceof Column.Range range) || range.height() < 4) {
            return false;
        }
        int maxRadiusByHeight = (int) (range.height() * c.maxColumnRadiusToCaveHeightRatio());
        int maxRadius = Math.clamp((long) maxRadiusByHeight, c.columnRadius().getMinValue(), c.columnRadius().getMaxValue());
        int radius = Mth.randomBetweenInclusive(random, c.columnRadius().getMinValue(), maxRadius);
        LargeSpeleothem stalactite = make(c.baseBlock(), origin.atY(range.ceiling() - 1), false, random, radius, c.stalactiteBluntness(), c.heightScale());
        LargeSpeleothem stalagmite = make(c.baseBlock(), origin.atY(range.floor() + 1), true, random, radius, c.stalagmiteBluntness(), c.heightScale());
        WindOffsetter wind = stalactite.isSuitableForWind(c.minRadiusForWind(), c.minBluntnessForWind())
                && stalagmite.isSuitableForWind(c.minRadiusForWind(), c.minBluntnessForWind())
                ? new WindOffsetter(origin.getY(), random, c.windSpeed(), 16 - radius) : WindOffsetter.NONE;
        boolean stalactiteEmbedded = stalactite.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(level, wind);
        boolean stalagmiteEmbedded = stalagmite.moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(level, wind);
        if (stalactiteEmbedded) {
            stalactite.placeBlocks(level, random, wind);
        }
        if (stalagmiteEmbedded) {
            stalagmite.placeBlocks(level, random, wind);
        }
        return true;
    }

    private static LargeSpeleothem make(BlockState baseBlock, BlockPos root, boolean pointingUp, RandomSource random, int radius,
                                        FloatProvider bluntness, FloatProvider heightScale) {
        return new LargeSpeleothem(baseBlock, root, pointingUp, radius, bluntness.sample(random), heightScale.sample(random));
    }

    private static final class LargeSpeleothem {
        private final BlockState baseBlock;
        private BlockPos root;
        private final boolean pointingUp;
        private int radius;
        private final double bluntness;
        private final double scale;

        LargeSpeleothem(BlockState baseBlock, BlockPos root, boolean pointingUp, int radius, double bluntness, double scale) {
            this.baseBlock = baseBlock;
            this.root = root;
            this.pointingUp = pointingUp;
            this.radius = radius;
            this.bluntness = bluntness;
            this.scale = scale;
        }

        private int getHeight() {
            return this.getHeightAtRadius(0.0F);
        }

        boolean moveBackUntilBaseIsInsideStoneAndShrinkRadiusIfNecessary(WorldGenLevel level, WindOffsetter wind) {
            while (this.radius > 1) {
                BlockPos.MutableBlockPos newRoot = this.root.mutable();
                int maxTries = Math.min(10, this.getHeight());
                for (int i = 0; i < maxTries; i++) {
                    if (level.getBlockState(newRoot).is(Blocks.LAVA)) {
                        return false;
                    }
                    if (SpeleothemUtils.isCircleMostlyEmbeddedInStone(level, wind.offset(newRoot), this.radius)) {
                        this.root = newRoot;
                        return true;
                    }
                    newRoot.move(this.pointingUp ? Direction.DOWN : Direction.UP);
                }
                this.radius /= 2;
            }
            return false;
        }

        private int getHeightAtRadius(float checkRadius) {
            return (int) SpeleothemUtils.getSpeleothemHeight(checkRadius, this.radius, this.scale, this.bluntness);
        }

        void placeBlocks(WorldGenLevel level, RandomSource random, WindOffsetter wind) {
            for (int dx = -this.radius; dx <= this.radius; dx++) {
                for (int dz = -this.radius; dz <= this.radius; dz++) {
                    float currentRadius = Mth.sqrt(dx * dx + dz * dz);
                    if (currentRadius > this.radius) {
                        continue;
                    }
                    int height = this.getHeightAtRadius(currentRadius);
                    if (height <= 0) {
                        continue;
                    }
                    if (random.nextFloat() < 0.2) {
                        height = (int) (height * Mth.randomBetween(random, 0.8F, 1.0F));
                    }
                    BlockPos.MutableBlockPos pos = this.root.offset(dx, 0, dz).mutable();
                    boolean hasBeenOutOfStone = false;
                    int maxY = this.pointingUp ? level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ()) : Integer.MAX_VALUE;
                    for (int i = 0; i < height && pos.getY() < maxY; i++) {
                        BlockPos adjusted = wind.offset(pos);
                        if (SpeleothemUtils.isEmptyOrWaterOrLava(level, adjusted)) {
                            hasBeenOutOfStone = true;
                            level.setBlock(adjusted, this.baseBlock.getBlock().defaultBlockState(), 2);
                        } else if (hasBeenOutOfStone && level.getBlockState(adjusted).is(BlockTags.BASE_STONE_OVERWORLD)) {
                            break;
                        }
                        pos.move(this.pointingUp ? Direction.UP : Direction.DOWN);
                    }
                }
            }
        }

        boolean isSuitableForWind(int minRadiusForWind, float minBluntnessForWind) {
            return this.radius >= minRadiusForWind && this.bluntness >= minBluntnessForWind;
        }
    }

    private static final class WindOffsetter {
        static final WindOffsetter NONE = new WindOffsetter();
        private final int originY;
        private final Vec3 windSpeed;
        private final int maxOffset;

        WindOffsetter(int originY, RandomSource random, FloatProvider windSpeedRange, int maxOffset) {
            this.originY = originY;
            this.maxOffset = maxOffset;
            float speed = windSpeedRange.sample(random);
            float direction = Mth.randomBetween(random, 0.0F, (float) Math.PI);
            this.windSpeed = new Vec3(Mth.cos(direction) * speed, 0.0, Mth.sin(direction) * speed);
        }

        private WindOffsetter() {
            this.originY = 0;
            this.windSpeed = null;
            this.maxOffset = 0;
        }

        BlockPos offset(BlockPos pos) {
            if (this.windSpeed == null) {
                return pos;
            }
            Vec3 total = this.windSpeed.scale(this.originY - pos.getY());
            int dx = Math.clamp((long) Mth.floor(total.x), -this.maxOffset, this.maxOffset);
            int dz = Math.clamp((long) Mth.floor(total.z), -this.maxOffset, this.maxOffset);
            return pos.offset(dx, 0, dz);
        }
    }
}
