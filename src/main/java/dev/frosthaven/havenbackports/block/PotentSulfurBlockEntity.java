package dev.frosthaven.havenbackports.block;

import dev.frosthaven.havenbackports.Sulfur;
import dev.frosthaven.havenbackports.particle.GeyserParticleOptions;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x PotentSulfurBlockEntity, line for line: nausea gas, the periodic geyser countdown (seeded per position with
 * salt -904011478), the plume particles and sounds, and the launch (+0.2 y per tick up to 0.3 + 0.1 per water block).
 * 1.21.1 forms: CompoundTag in place of ValueInput/Output; 26.x's Entity.canSimulateMovement (the side that moves the
 * entity: the client for its own player, the server for everything else) is {@link #movedHere}; Ticker.andThen is
 * {@link #and}. 26.x's checkFallDistanceAccumulation (cap the fall distance at 1 while not falling fast) is inlined.
 */
public class PotentSulfurBlockEntity extends BlockEntity {
    private static final Predicate<Entity> EFFECT_PREDICATE = EntitySelector.NO_SPECTATORS.and(EntitySelector.ENTITY_STILL_ALIVE);
    public static final long GEYSER_SALT = -904011478L;
    public int waitingCountdown = -1;
    public long eruptionTick = -1L;

    public static final BlockEntityTicker<PotentSulfurBlockEntity> SERVER_NAUSEA_EFFECT_TICKER = (level, pos, state, be) -> {
        if (level.getGameTime() % 10L == 0L) {
            BlockPos source = findNoxiousGasSourceBlock(level, pos);
            if (source != null) {
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(source).inflate(2.5, 0.0, 2.5), EFFECT_PREDICATE)) {
                    if (canBeReachedByNoxiousGas(level, source, entity.getEyePosition())) {
                        entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, true, true));
                    }
                }
            }
        }
    };
    public static final BlockEntityTicker<PotentSulfurBlockEntity> CLIENT_NOXIOUS_GAS_TICKER = (level, pos, state, be) -> {
        if (level.getGameTime() % 20L == 0L) {
            BlockPos source = findNoxiousGasSourceBlock(level, pos);
            if (source != null) {
                Vec3 c = Vec3.atCenterOf(source);
                level.addParticle(Sulfur.NOXIOUS_GAS_CLOUD.get(), c.x, c.y, c.z, 0.0, 0.0, 0.0);
            }
        }
    };
    public static final BlockEntityTicker<PotentSulfurBlockEntity> SERVER_WAITING_COUNTDOWN_TICKER = (level, pos, state, be) -> {
        if (level.getGameTime() % 20L == 0L) {
            BlockPos source = findNoxiousGasSourceBlock(level, pos);
            if (source != null) {
                if (be.waitingCountdown <= 0) {
                    int waterBlocks = source.getY() - pos.getY() - 1;
                    RandomSource positional = geyserPositional((ServerLevel) level, pos);
                    if (state.getValue(PotentSulfurBlock.STATE) == PotentSulfurState.DORMANT) {
                        be.waitingCountdown = 10 * (waterBlocks - 1) + positional.nextIntBetweenInclusive(15, 30);
                    } else {
                        positional.nextInt();
                        be.waitingCountdown = waterBlocks - 1 + positional.nextIntBetweenInclusive(1, 2);
                    }
                }
                if (be.waitingCountdown > 0) {
                    be.waitingCountdown--;
                }
                if (be.waitingCountdown == 0) {
                    PotentSulfurState next = state.getValue(PotentSulfurBlock.STATE) == PotentSulfurState.DORMANT
                            ? PotentSulfurState.ERUPTING : PotentSulfurState.DORMANT;
                    level.setBlockAndUpdate(pos, state.setValue(PotentSulfurBlock.STATE, next));
                    if (next == PotentSulfurState.DORMANT) {
                        level.gameEvent(GameEvent.BLOCK_DEACTIVATE, pos, GameEvent.Context.of(state));
                    }
                }
            }
        }
    };
    public static final BlockEntityTicker<PotentSulfurBlockEntity> LAUNCH_ENTITY_TICKER = (level, pos, state, be) -> {
        BlockPos source = findNoxiousGasSourceBlock(level, pos);
        if (source != null) {
            int waterBlocks = source.getY() - pos.getY() - 1;
            int forceHeight = getUnobstructedBlockCount(level, pos.above(), waterBlocks);
            AABB aabb = new AABB(pos.above()).expandTowards(0.0, forceHeight - 1, 0.0);
            for (Entity e : level.getEntitiesOfClass(Entity.class, aabb, EFFECT_PREDICATE)) {
                Vec3 velocity = e.getDeltaMovement();
                if (velocity.y() > -0.5 && e.fallDistance > 1.0F) {
                    e.fallDistance = 1.0F;   // 26.x Entity.checkFallDistanceAccumulation
                }
                if (movedHere(e) && !(e instanceof Player player && player.getAbilities().flying) && !e.isPassenger()
                        && !e.getType().is(Sulfur.NOT_AFFECTED_BY_GEYSERS) && velocity.y < 0.3F + waterBlocks * 0.1) {
                    e.addDeltaMovement(new Vec3(0.0, 0.2F, 0.0));
                }
            }
        }
    };

    public PotentSulfurBlockEntity(BlockPos pos, BlockState state) {
        super(Sulfur.POTENT_SULFUR_ENTITY.get(), pos, state);
    }

    /** The client plume ticker with the given loop sound (26.x CLIENT_GEYSER_PLUME_TICKER.apply(sound)). */
    public static BlockEntityTicker<PotentSulfurBlockEntity> plume(Supplier<SoundEvent> sound) {
        return (level, pos, state, be) -> {
            BlockPos source = findNoxiousGasSourceBlock(level, pos);
            if (source != null) {
                long eruptionTime = level.getGameTime() - be.eruptionTick;
                if (eruptionTime % 20L == 0L) {
                    int waterBlocks = source.getY() - pos.getY() - 1;
                    level.addParticle(new GeyserParticleOptions(Sulfur.GEYSER.get(), waterBlocks), source.getX() + 0.5, source.getY(),
                            source.getZ() + 0.5, 0.0, 0.0, 0.0);
                }
                if (eruptionTime % 40L == 0L) {
                    level.playLocalSound(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5, sound.get(), SoundSource.BLOCKS, 1.0F, 1.0F, false);
                }
            }
        };
    }

    public static BlockEntityTicker<PotentSulfurBlockEntity> and(BlockEntityTicker<PotentSulfurBlockEntity> a, BlockEntityTicker<PotentSulfurBlockEntity> b) {
        return (level, pos, state, be) -> {
            a.tick(level, pos, state, be);
            b.tick(level, pos, state, be);
        };
    }

    /** 26.x Entity.canSimulateMovement for 1.21.1: the client moves its own player (and what that player rides),
     *  the server moves every other entity. */
    static boolean movedHere(Entity e) {
        Entity mover = e.getControllingPassenger() instanceof Player p ? p : e;
        if (e.level().isClientSide()) {
            return mover instanceof Player p && p.isLocalPlayer();
        }
        return !(mover instanceof Player);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("countdown", this.waitingCountdown);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("countdown")) {
            this.waitingCountdown = tag.getInt("countdown");
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (this.eruptionTick == -1L) {
            this.eruptionTick = level.getGameTime();
        }
    }

    public void resetCountdown() {
        this.waitingCountdown = -1;
    }

    public static RandomSource geyserPositional(ServerLevel level, BlockPos pos) {
        return new XoroshiroRandomSource(level.getSeed() ^ GEYSER_SALT).forkPositional().at(pos);
    }

    private static int getUnobstructedBlockCount(Level level, BlockPos pos, int waterBlocks) {
        int forceHeight = 6 * waterBlocks;
        CollisionContext ctx = new PositionCollisionContext(pos.below().getY());
        for (int i = 0; i < forceHeight; i++) {
            BlockPos p = pos.above(i);
            if (!isGeyserPassableBlock(level.getBlockState(p), level, p, ctx)) {
                return i;
            }
        }
        return forceHeight;
    }

    private static boolean isGeyserPassableBlock(BlockState state, Level level, BlockPos pos, CollisionContext context) {
        return state.isAir() || state.is(Blocks.WATER) || state.getCollisionShape(level, pos, context).isEmpty();
    }

    @Nullable
    private static BlockPos findNoxiousGasSourceBlock(Level level, BlockPos origin) {
        int maxY = origin.getY() + 4 + 1;
        CollisionContext ctx = new PositionCollisionContext(origin.getY());
        BlockPos.MutableBlockPos pos = origin.above(1).mutable();
        while (pos.getY() <= maxY) {
            BlockState state = level.getBlockState(pos);
            boolean waterlogged = level.getFluidState(pos).isSourceOfType(Fluids.WATER);
            if (!waterlogged || !state.is(Blocks.WATER) && !isGeyserPassableBlock(state, level, pos, ctx)) {
                if (state.isAir() || isGeyserPassableBlock(state, level, pos, ctx)) {
                    return pos.immutable();
                }
                break;
            }
            pos.move(Direction.UP);
        }
        return null;
    }

    public static boolean canBeReachedByNoxiousGas(Level level, BlockPos source, Vec3 pos) {
        BlockPos blockPos = BlockPos.containing(pos);
        CollisionContext ctx = new PositionCollisionContext(blockPos.below().getY());
        if (!isGeyserPassableBlock(level.getBlockState(blockPos), level, blockPos, ctx)) {
            return false;
        } else if (pos.distanceToSqr(Vec3.atCenterOf(source)) > 9.0) {
            return false;
        }
        Vec3 belowSource = Vec3.atCenterOf(source.below());
        Vec3 belowPos = pos.with(Direction.Axis.Y, pos.y - 1.0);
        return level.getFluidState(BlockPos.containing(belowPos)).isSourceOfType(Fluids.WATER)
                && level.clip(new ClipContext(belowSource, belowPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()))
                .getType() != HitResult.Type.BLOCK;
    }
}
