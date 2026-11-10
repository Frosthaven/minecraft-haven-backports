package dev.frosthaven.havenbackports.creaking;

import com.mojang.datafixers.util.Either;
import dev.frosthaven.havenbackports.Night26;
import dev.frosthaven.havenbackports.PaleGarden;
import dev.frosthaven.havenbackports.particle.TrailParticleOption;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x CreakingHeartBlockEntity on 1.21.1 APIs. Forms 1.21.1 forced:
 * - gameplay/creaking_active is Night26.active; with no value (no day cycle) the heart keeps awake or dormant, and
 *   does not send its creaking away for the time of day.
 * - 26.x ServerLevel.isSpawningMonsters (game rules spawn_mobs and spawn_monsters) is 1.21.1's doMobSpawning rule and
 *   the server's spawn-monsters setting.
 * - 26.x BlockPos.breadthFirstTraversal with ACCEPT/SKIP/STOP is ported below (1.21.1's takes a plain predicate).
 * - 26.x sendParticles(force, alwaysShow) is 1.21.1's per-player long-distance send (it forces the particle too).
 * - "creaking" is the same int-array UUID 26.x's UUIDUtil codec writes.
 * - The resin place sound is the resin clump's sound type (26.x SoundEvents.RESIN.placeSound()).
 */
public class CreakingHeartBlockEntity extends BlockEntity {
    private static final int PLAYER_DETECTION_RANGE = 32;
    public static final int CREAKING_ROAMING_RADIUS = 32;
    private static final int DISTANCE_CREAKING_TOO_FAR = 34;
    private static final int SPAWN_RANGE_XZ = 16;
    private static final int SPAWN_RANGE_Y = 8;
    private static final int ATTEMPTS_PER_SPAWN = 5;
    private static final int UPDATE_TICKS = 20;
    private static final int UPDATE_TICKS_VARIANCE = 5;
    private static final int HURT_CALL_TOTAL_TICKS = 100;
    private static final int HURT_CALL_INTERVAL = 10;
    private static final int HURT_CALL_PARTICLE_TICKS = 50;
    private static final int MAX_DEPTH = 2;
    private static final int MAX_COUNT = 64;
    private static final int TICKS_GRACE_PERIOD = 30;
    private static final Optional<Creaking> NO_CREAKING = Optional.empty();
    @Nullable
    private Either<Creaking, UUID> creakingInfo;
    private long ticksExisted;
    private int ticker;
    private int emitter;
    @Nullable
    private Vec3 emitterTarget;
    private int outputSignal;

    public CreakingHeartBlockEntity(BlockPos pos, BlockState state) {
        super(CreakingContent.CREAKING_HEART_ENTITY.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CreakingHeartBlockEntity entity) {
        entity.ticksExisted++;
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int computedOutputSignal = entity.computeAnalogOutputSignal();
        if (entity.outputSignal != computedOutputSignal) {
            entity.outputSignal = computedOutputSignal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        if (entity.emitter > 0) {
            if (entity.emitter > HURT_CALL_PARTICLE_TICKS) {
                entity.emitParticles(serverLevel, 1, true);
                entity.emitParticles(serverLevel, 1, false);
            }
            if (entity.emitter % HURT_CALL_INTERVAL == 0 && entity.emitterTarget != null) {
                entity.getCreakingProtector().ifPresent(c -> entity.emitterTarget = c.getBoundingBox().getCenter());
                Vec3 heartPosition = Vec3.atCenterOf(pos);
                float progress = 0.2F + 0.8F * (HURT_CALL_TOTAL_TICKS - entity.emitter) / 100.0F;
                Vec3 soundLocation = heartPosition.subtract(entity.emitterTarget).scale(progress).add(entity.emitterTarget);
                float volume = entity.emitter / 2.0F / 100.0F + 0.5F;
                serverLevel.playSound(null, BlockPos.containing(soundLocation), CreakingContent.CREAKING_HEART_HURT.get(), SoundSource.BLOCKS, volume, 1.0F);
            }
            entity.emitter--;
        }
        if (entity.ticker-- >= 0) {
            return;
        }
        entity.ticker = entity.level == null ? UPDATE_TICKS : entity.level.getRandom().nextInt(UPDATE_TICKS_VARIANCE) + UPDATE_TICKS;
        BlockState updatedState = updateCreakingState(level, state, pos, entity);
        if (updatedState != state) {
            level.setBlockAndUpdate(pos, updatedState);
            if (updatedState.getValue(CreakingHeartBlock.STATE) == CreakingHeartState.UPROOTED) {
                return;
            }
        }
        if (entity.creakingInfo == null) {
            if (updatedState.getValue(CreakingHeartBlock.STATE) == CreakingHeartState.AWAKE && spawningMonsters(serverLevel)
                    && serverLevel.getLevelData().getDifficulty() != Difficulty.PEACEFUL) {
                Player player = level.getNearestPlayer(pos.getX(), pos.getY(), pos.getZ(), PLAYER_DETECTION_RANGE, false);
                if (player != null) {
                    Creaking creaking = spawnProtector(serverLevel, entity);
                    if (creaking != null) {
                        entity.setCreakingInfo(creaking);
                        creaking.makeSound(CreakingContent.CREAKING_SPAWN.get());
                        level.playSound(null, entity.getBlockPos(), CreakingContent.CREAKING_HEART_SPAWN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    }
                }
            }
        } else {
            Optional<Creaking> optionalCreaking = entity.getCreakingProtector();
            if (optionalCreaking.isPresent()) {
                Creaking creaking = optionalCreaking.get();
                boolean day = !Night26.active(level).orElse(true);
                if (day && !creaking.isPersistenceRequired() || entity.distanceToCreaking() > DISTANCE_CREAKING_TOO_FAR || creaking.playerIsStuckInYou()) {
                    entity.removeProtector(null);
                }
            }
        }
    }

    private static boolean spawningMonsters(ServerLevel level) {
        return level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) && level.getServer().isSpawningMonsters();
    }

    private static BlockState updateCreakingState(Level level, BlockState state, BlockPos pos, CreakingHeartBlockEntity entity) {
        if (!CreakingHeartBlock.hasRequiredLogs(state, level, pos) && entity.creakingInfo == null) {
            return state.setValue(CreakingHeartBlock.STATE, CreakingHeartState.UPROOTED);
        }
        return Night26.active(level).map(active -> state.setValue(CreakingHeartBlock.STATE, active ? CreakingHeartState.AWAKE : CreakingHeartState.DORMANT))
                .orElse(state);
    }

    private double distanceToCreaking() {
        return this.getCreakingProtector().map(c -> Math.sqrt(c.distanceToSqr(Vec3.atBottomCenterOf(this.getBlockPos())))).orElse(0.0);
    }

    private void clearCreakingInfo() {
        this.creakingInfo = null;
        this.setChanged();
    }

    public void setCreakingInfo(Creaking creaking) {
        this.creakingInfo = Either.left(creaking);
        this.setChanged();
    }

    public void setCreakingInfo(UUID uuid) {
        this.creakingInfo = Either.right(uuid);
        this.ticksExisted = 0L;
        this.setChanged();
    }

    private Optional<Creaking> getCreakingProtector() {
        if (this.creakingInfo == null) {
            return NO_CREAKING;
        }
        if (this.creakingInfo.left().isPresent()) {
            Creaking creaking = this.creakingInfo.left().get();
            if (!creaking.isRemoved()) {
                return Optional.of(creaking);
            }
            this.setCreakingInfo(creaking.getUUID());
        }
        if (this.level instanceof ServerLevel serverLevel && this.creakingInfo.right().isPresent()) {
            if (serverLevel.getEntity(this.creakingInfo.right().get()) instanceof Creaking resolved) {
                this.setCreakingInfo(resolved);
                return Optional.of(resolved);
            }
            if (this.ticksExisted >= TICKS_GRACE_PERIOD) {
                this.clearCreakingInfo();
            }
        }
        return NO_CREAKING;
    }

    @Nullable
    private static Creaking spawnProtector(ServerLevel level, CreakingHeartBlockEntity entity) {
        BlockPos pos = entity.getBlockPos();
        Optional<Creaking> spawned = SpawnUtil26.trySpawnMob(CreakingContent.CREAKING.get(), MobSpawnType.SPAWNER, level, pos, ATTEMPTS_PER_SPAWN,
                SPAWN_RANGE_XZ, SPAWN_RANGE_Y, true);
        if (spawned.isEmpty()) {
            return null;
        }
        Creaking creaking = spawned.get();
        level.gameEvent(creaking, GameEvent.ENTITY_PLACE, creaking.position());
        level.broadcastEntityEvent(creaking, (byte) 60);
        creaking.setTransient(pos);
        return creaking;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public void creakingHurt() {
        Optional<Creaking> creaking = this.getCreakingProtector();
        if (creaking.isEmpty() || !(this.level instanceof ServerLevel serverLevel) || this.emitter > 0) {
            return;
        }
        this.emitParticles(serverLevel, 20, false);
        if (this.getBlockState().getValue(CreakingHeartBlock.STATE) == CreakingHeartState.AWAKE) {
            int clumps = serverLevel.getRandom().nextIntBetweenInclusive(2, 3);
            for (int i = 0; i < clumps; i++) {
                this.spreadResin(serverLevel).ifPresent(pos -> {
                    serverLevel.playSound(null, pos, PaleGarden.RESIN_CLUMP.get().defaultBlockState().getSoundType().getPlaceSound(),
                            SoundSource.BLOCKS, 1.0F, 1.0F);
                    serverLevel.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(this.getBlockState()));
                });
            }
        }
        this.emitter = HURT_CALL_TOTAL_TICKS;
        this.emitterTarget = creaking.get().getBoundingBox().getCenter();
    }

    /** 26.x BlockPos.TraversalNodeStatus. */
    private enum Status { ACCEPT, SKIP, STOP }

    /** One resin clump face on a pale oak log within 2 logs of the heart; air or a water source becomes a clump. */
    private Optional<BlockPos> spreadResin(ServerLevel level) {
        RandomSource random = level.getRandom();
        BlockPos[] placed = new BlockPos[1];
        breadthFirstTraversal(this.worldPosition, MAX_DEPTH, MAX_COUNT, (pos, acceptor) -> {
            for (Direction dir : Util.shuffledCopy(Direction.values(), random)) {
                BlockPos neighbourPos = pos.relative(dir);
                if (level.getBlockState(neighbourPos).is(PaleGarden.PALE_OAK_LOGS)) {
                    acceptor.accept(neighbourPos);
                }
            }
        }, pos -> {
            if (!level.getBlockState(pos).is(PaleGarden.PALE_OAK_LOGS)) {
                return Status.ACCEPT;
            }
            BlockState clump = PaleGarden.RESIN_CLUMP.get().defaultBlockState();
            for (Direction dir : Util.shuffledCopy(Direction.values(), random)) {
                BlockPos neighbourPos = pos.relative(dir);
                BlockState neighbourState = level.getBlockState(neighbourPos);
                Direction opposite = dir.getOpposite();
                if (neighbourState.isAir()) {
                    neighbourState = clump;
                } else if (neighbourState.is(Blocks.WATER) && neighbourState.getFluidState().isSource()
                        && clump.hasProperty(BlockStateProperties.WATERLOGGED)) {
                    neighbourState = clump.setValue(BlockStateProperties.WATERLOGGED, true);
                }
                if (neighbourState.is(clump.getBlock()) && !MultifaceBlock.hasFace(neighbourState, opposite)) {
                    level.setBlockAndUpdate(neighbourPos, neighbourState.setValue(MultifaceBlock.getFaceProperty(opposite), true));
                    placed[0] = neighbourPos;
                    return Status.STOP;
                }
            }
            return Status.ACCEPT;
        });
        return Optional.ofNullable(placed[0]);
    }

    /** 26.x BlockPos.breadthFirstTraversal. */
    private static int breadthFirstTraversal(BlockPos startPos, int maxDepth, int maxCount, BiConsumer<BlockPos, Consumer<BlockPos>> neighbourProvider,
                                             Function<BlockPos, Status> nodeProcessor) {
        record Node(BlockPos pos, int depth) {}
        Queue<Node> nodes = new ArrayDeque<>();
        LongSet visited = new LongOpenHashSet();
        nodes.add(new Node(startPos, 0));
        int count = 0;
        while (!nodes.isEmpty()) {
            Node node = nodes.poll();
            if (visited.add(node.pos.asLong())) {
                Status next = nodeProcessor.apply(node.pos);
                if (next != Status.SKIP) {
                    if (next == Status.STOP) {
                        break;
                    }
                    if (++count >= maxCount) {
                        return count;
                    }
                    if (node.depth < maxDepth) {
                        neighbourProvider.accept(node.pos, pos -> nodes.add(new Node(pos, node.depth + 1)));
                    }
                }
            }
        }
        return count;
    }

    private void emitParticles(ServerLevel serverLevel, int count, boolean towardsCreaking) {
        Optional<Creaking> creaking = this.getCreakingProtector();
        if (creaking.isEmpty()) {
            return;
        }
        int color = towardsCreaking ? Creaking.CREAKING_ORANGE : Creaking.CREAKING_GRAY;
        RandomSource random = serverLevel.getRandom();
        for (double i = 0.0; i < count; i++) {
            AABB box = creaking.get().getBoundingBox();
            Vec3 source = box.getMinPosition().add(random.nextDouble() * box.getXsize(), random.nextDouble() * box.getYsize(),
                    random.nextDouble() * box.getZsize());
            Vec3 destination = Vec3.atLowerCornerOf(this.getBlockPos()).add(random.nextDouble(), random.nextDouble(), random.nextDouble());
            if (towardsCreaking) {
                Vec3 swap = source;
                source = destination;
                destination = swap;
            }
            TrailParticleOption option = new TrailParticleOption(destination, color, random.nextInt(40) + 10);
            for (ServerPlayer player : serverLevel.players()) {
                serverLevel.sendParticles(player, option, true, source.x, source.y, source.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    public void removeProtector(@Nullable DamageSource damageSource) {
        Optional<Creaking> protector = this.getCreakingProtector();
        if (protector.isPresent()) {
            Creaking creaking = protector.get();
            if (damageSource == null) {
                creaking.tearDown();
            } else {
                creaking.creakingDeathEffects(damageSource);
                creaking.setTearingDown();
                creaking.setHealth(0.0F);
            }
            this.clearCreakingInfo();
        }
    }

    public boolean isProtector(Creaking creaking) {
        return this.getCreakingProtector().map(c -> c == creaking).orElse(false);
    }

    public int getAnalogOutputSignal() {
        return this.outputSignal;
    }

    public int computeAnalogOutputSignal() {
        if (this.creakingInfo == null || this.getCreakingProtector().isEmpty()) {
            return 0;
        }
        double scaledDistance = Math.clamp(this.distanceToCreaking(), 0.0, 32.0) / 32.0;
        return 15 - (int) Math.floor(scaledDistance * 15.0);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("creaking")) {
            this.setCreakingInfo(tag.getUUID("creaking"));
        } else {
            this.clearCreakingInfo();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (this.creakingInfo != null) {
            tag.putUUID("creaking", this.creakingInfo.map(Creaking::getUUID, uuid -> uuid));
        }
    }
}
