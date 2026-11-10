package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Flora;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 26.x DryVegetationBlock (the dry grasses' base; 26.x's dead bush too, kept at 1.21.1): placed on
 * #supports_dry_vegetation. 26.x's own animateTick plays the dead bush idle sound; both dry grasses override it with the
 * dry grass one (AmbientDesertBlockSoundsPlayer.playAmbientDryGrassSounds, ported as {@link #dryGrassSounds}).
 */
public class DryVegetationBlock extends BushBlock {
    private static final VoxelShape SHAPE = Shapes26.column(12.0, 12.0, 0.0, 13.0);

    public DryVegetationBlock(Properties properties) {
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
        return state.is(Flora.SUPPORTS_DRY_VEGETATION);
    }

    /** 26.x AmbientDesertBlockSoundsPlayer.playAmbientDryGrassSounds: one in 200 animate ticks, when the two blocks
     *  under the grass are #triggers_ambient_desert_dry_vegetation_block_sounds; a player sound (playPlayerSound: at the
     *  listener, not the block). */
    public static void dryGrassSounds(Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(200) == 0 && level.getBlockState(pos.below()).is(Flora.TRIGGERS_DRY_VEGETATION_SOUNDS)
                && level.getBlockState(pos.below(2)).is(Flora.TRIGGERS_DRY_VEGETATION_SOUNDS)) {
            PLAYER_SOUND.accept(Flora.DRY_GRASS_AMBIENT.get(), SoundSource.AMBIENT);
        }
    }

    /** 26.x ClientLevel.playPlayerSound (a sound bound to the local player); ClientSetup sets it (1.21.1 has none). */
    public static java.util.function.BiConsumer<net.minecraft.sounds.SoundEvent, SoundSource> PLAYER_SOUND = (sound, source) -> {};
}
