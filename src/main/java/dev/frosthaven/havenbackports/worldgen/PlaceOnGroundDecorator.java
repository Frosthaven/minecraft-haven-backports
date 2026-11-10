package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * 26.x PlaceOnGroundDecorator, line for line. 1.21.1 forms: the provider is held directly (no Holder), its getState
 * takes (random, pos); isSolidRender reads the state's cache, which 1.21.1 fills with an empty level (as 26.x).
 */
public class PlaceOnGroundDecorator extends TreeDecorator {
    public static final MapCodec<PlaceOnGroundDecorator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("tries", 128).forGetter(p -> p.tries),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("radius", 2).forGetter(p -> p.radius),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("height", 1).forGetter(p -> p.height),
                    BlockStateProvider.CODEC.fieldOf("block_state_provider").forGetter(p -> p.blockStateProvider))
            .apply(i, PlaceOnGroundDecorator::new));
    private final int tries;
    private final int radius;
    private final int height;
    private final BlockStateProvider blockStateProvider;

    public PlaceOnGroundDecorator(int tries, int radius, int height, BlockStateProvider blockStateProvider) {
        this.tries = tries;
        this.radius = radius;
        this.height = height;
        this.blockStateProvider = blockStateProvider;
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return Worldgen.PLACE_ON_GROUND.get();
    }

    @Override
    public void place(Context context) {
        List<BlockPos> blockPositions = Decorators.getLowestTrunkOrRootOfTree(context);
        if (blockPositions.isEmpty()) {
            return;
        }
        BlockPos origin = blockPositions.getFirst();
        int minY = origin.getY();
        int minX = origin.getX();
        int maxX = origin.getX();
        int minZ = origin.getZ();
        int maxZ = origin.getZ();
        for (BlockPos position : blockPositions) {
            if (position.getY() == minY) {
                minX = Math.min(minX, position.getX());
                maxX = Math.max(maxX, position.getX());
                minZ = Math.min(minZ, position.getZ());
                maxZ = Math.max(maxZ, position.getZ());
            }
        }
        RandomSource random = context.random();
        BoundingBox bb = new BoundingBox(minX, minY, minZ, maxX, minY, maxZ).inflatedBy(this.radius, this.height, this.radius);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < this.tries; i++) {
            pos.set(random.nextIntBetweenInclusive(bb.minX(), bb.maxX()), random.nextIntBetweenInclusive(bb.minY(), bb.maxY()),
                    random.nextIntBetweenInclusive(bb.minZ(), bb.maxZ()));
            this.attemptToPlaceBlockAbove(context, pos);
        }
    }

    private void attemptToPlaceBlockAbove(Context context, BlockPos pos) {
        BlockPos abovePos = pos.above();
        if (context.level().isStateAtPosition(abovePos, state -> state.isAir() || state.is(Blocks.VINE))
                && Decorators.checkBlock(context, pos, state -> state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
                && context.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos).getY() <= abovePos.getY()) {
            context.setBlock(abovePos, this.blockStateProvider.getState(context.random(), abovePos));
        }
    }
}
