package dev.frosthaven.havenbackports.creaking;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.x SpawnUtil.trySpawnMob with its checkCollisions flag and the ON_TOP_OF_COLLIDER_NO_LEAVES strategy; 1.21.1's
 * SpawnUtil has neither. EntitySpawnReason is 1.21.1's MobSpawnType.
 */
final class SpawnUtil26 {
    private SpawnUtil26() {}

    /** 26.x SpawnUtil.Strategy.ON_TOP_OF_COLLIDER_NO_LEAVES. */
    static boolean onTopOfColliderNoLeaves(ServerLevel level, BlockPos pos, BlockState state, BlockPos abovePos, BlockState aboveState) {
        return aboveState.getCollisionShape(level, abovePos).isEmpty() && !state.is(BlockTags.LEAVES)
                && Block.isFaceFull(state.getCollisionShape(level, pos), Direction.UP);
    }

    static <T extends Mob> Optional<T> trySpawnMob(EntityType<T> type, MobSpawnType reason, ServerLevel level, BlockPos start, int attempts,
                                                   int rangeXZ, int rangeY, boolean checkCollisions) {
        BlockPos.MutableBlockPos searchPos = start.mutable();
        RandomSource random = level.getRandom();
        for (int i = 0; i < attempts; i++) {
            int dx = Mth.randomBetweenInclusive(random, -rangeXZ, rangeXZ);
            int dz = Mth.randomBetweenInclusive(random, -rangeXZ, rangeXZ);
            searchPos.setWithOffset(start, dx, rangeY, dz);
            if (level.getWorldBorder().isWithinBounds(searchPos) && moveToPossibleSpawnPosition(level, rangeY, searchPos)
                    && (!checkCollisions || level.noCollision(type.getSpawnAABB(searchPos.getX() + 0.5, searchPos.getY(), searchPos.getZ() + 0.5)))) {
                T mob = type.create(level, null, searchPos, reason, false, false);
                if (mob != null) {
                    if (mob.checkSpawnRules(level, reason) && mob.checkSpawnObstruction(level)) {
                        level.addFreshEntityWithPassengers(mob);
                        mob.playAmbientSound();
                        return Optional.of(mob);
                    }
                    mob.discard();
                }
            }
        }
        return Optional.empty();
    }

    private static boolean moveToPossibleSpawnPosition(ServerLevel level, int rangeY, BlockPos.MutableBlockPos searchPos) {
        BlockPos.MutableBlockPos abovePos = new BlockPos.MutableBlockPos().set(searchPos);
        BlockState aboveState = level.getBlockState(abovePos);
        for (int y = rangeY; y >= -rangeY; y--) {
            searchPos.move(Direction.DOWN);
            abovePos.setWithOffset(searchPos, Direction.UP);
            BlockState currentState = level.getBlockState(searchPos);
            if (onTopOfColliderNoLeaves(level, searchPos, currentState, abovePos, aboveState)) {
                searchPos.move(Direction.UP);
                return true;
            }
            aboveState = currentState;
        }
        return false;
    }
}
