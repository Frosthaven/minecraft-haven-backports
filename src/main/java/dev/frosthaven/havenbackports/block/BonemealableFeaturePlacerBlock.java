package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/** 26.x BonemealableFeaturePlacerBlock (the pale moss block): bonemeal places a configured feature above it.
 *  1.21.1's MossBlock hard-codes its own moss patch. */
public class BonemealableFeaturePlacerBlock extends Block implements BonemealableBlock {
    private final ResourceKey<ConfiguredFeature<?, ?>> feature;

    public BonemealableFeaturePlacerBlock(ResourceKey<ConfiguredFeature<?, ?>> feature, Properties properties) {
        super(properties);
        this.feature = feature;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.registryAccess().registry(Registries.CONFIGURED_FEATURE).flatMap(r -> r.getHolder(this.feature))
                .ifPresent(f -> f.value().place(level, level.getChunkSource().getGenerator(), random, pos.above()));
    }

    @Override
    public Type getType() {
        return Type.NEIGHBOR_SPREADER;
    }
}
