package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.frosthaven.havenbackports.block.MossyCarpetBlock;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * 26.x SimpleBlockFeature: 1.21.1's simple_block plus 26.x's schedule_tick (a tick right after placing: eyeblossoms
 * check the time at once) and the mossy carpet branch (placeAt, with its random topper). tools/worldgen.py uses this
 * type only where 26.x's data needs one of the two; every other simple_block stays 1.21.1's.
 */
public class SimpleBlockFeature extends Feature<SimpleBlockFeature.Config> {
    public record Config(BlockStateProvider toPlace, boolean scheduleTick) implements FeatureConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(i -> i.group(
                        BlockStateProvider.CODEC.fieldOf("to_place").forGetter(Config::toPlace),
                        Codec.BOOL.optionalFieldOf("schedule_tick", false).forGetter(Config::scheduleTick))
                .apply(i, Config::new));
    }

    public SimpleBlockFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        BlockState state = context.config().toPlace().getState(context.random(), origin);
        if (!state.canSurvive(level, origin)) {
            return false;
        }
        if (state.getBlock() instanceof DoublePlantBlock) {
            BlockState above = level.getBlockState(origin.above());
            if (!above.isAir() && (!Objects.equals(state.getFluidState(), above.getFluidState()) || !above.canBeReplaced())) {
                return false;
            }
            DoublePlantBlock.placeAt(level, state, origin, 2);
        } else if (state.getBlock() instanceof MossyCarpetBlock) {
            MossyCarpetBlock.placeAt(level, origin, level.getRandom(), 2);
        } else {
            level.setBlock(origin, state, 2);
        }
        if (context.config().scheduleTick()) {
            level.scheduleTick(origin, level.getBlockState(origin).getBlock(), 1);
        }
        return true;
    }
}
