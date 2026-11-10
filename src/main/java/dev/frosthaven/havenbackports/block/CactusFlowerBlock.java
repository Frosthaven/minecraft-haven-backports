package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Flora;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 26.x CactusFlowerBlock on 1.21.1's BushBlock: stands on #support_override_cactus_flower (cactus, farmland) or on
 *  any block whose top is sturdy at the center. Cacti grow it (FloraCactusMixin). */
public class CactusFlowerBlock extends BushBlock {
    private static final VoxelShape SHAPE = Shapes26.column(14.0, 14.0, 0.0, 12.0);

    public CactusFlowerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        BlockState below = level.getBlockState(pos);
        return below.is(Flora.SUPPORT_OVERRIDE_CACTUS_FLOWER) || below.isFaceSturdy(level, pos, Direction.UP, SupportType.CENTER);
    }
}
