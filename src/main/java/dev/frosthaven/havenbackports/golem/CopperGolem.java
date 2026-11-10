package dev.frosthaven.havenbackports.golem;

import com.mojang.serialization.Dynamic;
import dev.frosthaven.havenbackports.Golems;
import dev.frosthaven.havenbackports.entity.Navigations;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x CopperGolem (net.minecraft.world.entity.animal.golem), with Mojang's numbers: ages every 504000 to
 * 552000 ticks unless waxed, a fully oxidized golem may become a statue (0.0058 per check), honeycomb waxes, an axe
 * scrapes wax or a stage, lightning removes a stage, shears take its antenna item, it sorts copper chests into chests.
 *
 * <p>1.21.1 forms: the antenna is 26.x's SADDLE equipment slot (1.21.1 has none): one synced stack saved as
 * "antenna", always dropped (only an iron golem's gift puts one there, and 26.x makes that a guaranteed drop); the two
 * synced enums use Golems' serializers; path types carry their 1.21.1 names (DANGER_FIRE, DANGER_OTHER, DAMAGE_FIRE);
 * the required path length is Navigations.Ground; ContainerUser.hasContainerOpen is read by ContainerOpenersCounterMixin.
 */
public class CopperGolem extends AbstractGolem implements Shearable {
    private static final long IGNORE_WEATHERING_TICK = -2L, UNSET_WEATHERING_TICK = -1L;
    private static final int WEATHERING_TICK_FROM = 504000, WEATHERING_TICK_TO = 552000;
    private static final float TURN_TO_STATUE_CHANCE = 0.0058F;
    private static final EntityDataAccessor<WeatherState> DATA_WEATHER_STATE = SynchedEntityData.defineId(CopperGolem.class, Golems.WEATHER_STATE_SERIALIZER);
    private static final EntityDataAccessor<CopperGolemState> COPPER_GOLEM_STATE = SynchedEntityData.defineId(CopperGolem.class, Golems.STATE_SERIALIZER);
    private static final EntityDataAccessor<ItemStack> DATA_ANTENNA = SynchedEntityData.defineId(CopperGolem.class, EntityDataSerializers.ITEM_STACK);
    @Nullable
    private BlockPos openedChestPos;
    @Nullable
    private UUID lastLightningBoltUUID;
    private long nextWeatheringTick = UNSET_WEATHERING_TICK;
    private int idleAnimationStartTick;
    public final AnimationState idleAnimationState = new AnimationState(), interactionGetItemAnimationState = new AnimationState(),
            interactionGetNoItemAnimationState = new AnimationState(), interactionDropItemAnimationState = new AnimationState(),
            interactionDropNoItemAnimationState = new AnimationState();

    public CopperGolem(EntityType<? extends AbstractGolem> type, Level level) {
        super(type, level);
        ((Navigations.Ground) this.getNavigation()).setCanOpenDoors(true);
        this.setPersistenceRequired();
        this.setState(CopperGolemState.IDLE);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 16.0F);
        this.setPathfindingMalus(PathType.DANGER_OTHER, 16.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, -1.0F);
        this.getBrain().setMemory(Golems.TRANSPORT_ITEMS_COOLDOWN_TICKS.get(), this.getRandom().nextInt(60, 100));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.2F).add(Attributes.STEP_HEIGHT, 1.0).add(Attributes.MAX_HEALTH, 12.0);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Navigations.Ground(this, level);
    }

    public CopperGolemState getState() {
        return this.entityData.get(COPPER_GOLEM_STATE);
    }

    public void setState(CopperGolemState state) {
        this.entityData.set(COPPER_GOLEM_STATE, state);
    }

    public WeatherState getWeatherState() {
        return this.entityData.get(DATA_WEATHER_STATE);
    }

    public void setWeatherState(WeatherState state) {
        this.entityData.set(DATA_WEATHER_STATE, state);
    }

    public ItemStack getAntenna() {
        return this.entityData.get(DATA_ANTENNA);
    }

    public void setAntenna(ItemStack stack) {
        this.entityData.set(DATA_ANTENNA, stack);
    }

    public boolean isWaxed() {
        return this.nextWeatheringTick == IGNORE_WEATHERING_TICK;
    }

    public void setWaxed(boolean waxed) {
        if (waxed != this.isWaxed()) {
            this.nextWeatheringTick = waxed ? IGNORE_WEATHERING_TICK : UNSET_WEATHERING_TICK;
        }
    }

    public void setOpenedChestPos(BlockPos pos) {
        this.openedChestPos = pos;
    }

    public void clearOpenedChestPos() {
        this.openedChestPos = null;
    }

    @Override
    protected Brain.Provider<CopperGolem> brainProvider() {
        return Brain.provider(CopperGolemAi.memories(), java.util.List.of(SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY));
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return CopperGolemAi.init(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<CopperGolem> getBrain() {
        return (Brain<CopperGolem>) super.getBrain();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_WEATHER_STATE, WeatherState.UNAFFECTED);
        builder.define(COPPER_GOLEM_STATE, CopperGolemState.IDLE);
        builder.define(DATA_ANTENNA, ItemStack.EMPTY);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putLong("next_weather_age", this.nextWeatheringTick);
        tag.putString("weather_state", this.getWeatherState().getSerializedName());
        if (!this.getAntenna().isEmpty()) {
            tag.put("antenna", this.getAntenna().save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.nextWeatheringTick = tag.contains("next_weather_age") ? tag.getLong("next_weather_age") : UNSET_WEATHERING_TICK;
        String stored = tag.getString("weather_state");
        this.setWeatherState(java.util.Arrays.stream(WeatherState.values()).filter(w -> w.getSerializedName().equals(stored)).findFirst().orElse(WeatherState.UNAFFECTED));
        this.setAntenna(ItemStack.parseOptional(this.registryAccess(), tag.getCompound("antenna")));
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler().push("copperGolemBrain");
        this.getBrain().tick((ServerLevel) this.level(), this);
        this.level().getProfiler().pop();
        this.level().getProfiler().push("copperGolemActivityUpdate");
        CopperGolemAi.updateActivity(this);
        this.level().getProfiler().pop();
        super.customServerAiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            if (!this.isNoAi()) {
                this.setupAnimationStates();
            }
        } else {
            this.updateWeathering((ServerLevel) this.level(), this.level().getRandom(), this.level().getGameTime());
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            ItemStack held = this.getMainHandItem();
            if (!held.isEmpty()) {
                BehaviorUtils.throwItem(this, held, player.position());
                this.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                return InteractionResult.sidedSuccess(this.level().isClientSide());
            }
        }
        Level level = this.level();
        if (stack.is(Items.SHEARS) && this.readyForShearing()) {
            if (level instanceof ServerLevel) {
                this.shear(SoundSource.PLAYERS);
                this.gameEvent(GameEvent.SHEAR, player);
                stack.hurtAndBreak(1, player, getSlotForHand(hand));
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide()) {
            return InteractionResult.PASS;
        }
        if (stack.is(Items.HONEYCOMB) && !this.isWaxed()) {
            BlockPos pos = this.blockPosition();
            level.levelEvent(null, 3003, pos, 0);
            level.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
            this.setWaxed(true);
            stack.consume(1, player);
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ItemTags.AXES) && this.isWaxed()) {
            level.playSound(null, this, SoundEvents.AXE_SCRAPE, this.getSoundSource(), 1.0F, 1.0F);
            level.levelEvent(null, 3004, this.blockPosition(), 0);
            this.setWaxed(false);
            stack.hurtAndBreak(1, player, getSlotForHand(hand));
            return InteractionResult.SUCCESS;
        }
        if (stack.is(ItemTags.AXES) && this.getWeatherState() != WeatherState.UNAFFECTED) {
            level.playSound(null, this, SoundEvents.AXE_SCRAPE, this.getSoundSource(), 1.0F, 1.0F);
            level.levelEvent(null, 3005, this.blockPosition(), 0);
            this.nextWeatheringTick = UNSET_WEATHERING_TICK;
            this.entityData.set(DATA_WEATHER_STATE, CopperGolemState.previous(this.getWeatherState()), true);
            stack.hurtAndBreak(1, player, getSlotForHand(hand));
            return InteractionResult.SUCCESS;
        }
        return super.mobInteract(player, hand);
    }

    private void updateWeathering(ServerLevel level, RandomSource random, long gameTime) {
        if (this.isWaxed()) {
            return;
        }
        if (this.nextWeatheringTick == UNSET_WEATHERING_TICK) {
            this.nextWeatheringTick = gameTime + random.nextIntBetweenInclusive(WEATHERING_TICK_FROM, WEATHERING_TICK_TO);
            return;
        }
        WeatherState state = this.entityData.get(DATA_WEATHER_STATE);
        boolean fullyOxidized = state == WeatherState.OXIDIZED;
        if (gameTime >= this.nextWeatheringTick && !fullyOxidized) {
            WeatherState next = CopperGolemState.next(state);
            this.setWeatherState(next);
            this.nextWeatheringTick = next == WeatherState.OXIDIZED ? 0L : this.nextWeatheringTick + random.nextIntBetweenInclusive(WEATHERING_TICK_FROM, WEATHERING_TICK_TO);
        }
        if (fullyOxidized && level.getBlockState(this.blockPosition()).isAir() && level.getRandom().nextFloat() <= TURN_TO_STATUE_CHANCE) {
            this.turnToStatue(level);
        }
    }

    private void turnToStatue(ServerLevel level) {
        BlockPos pos = this.blockPosition();
        CopperGolemStatueBlock.Pose[] poses = CopperGolemStatueBlock.Pose.values();
        level.setBlockAndUpdate(pos, Golems.statue(WeatherState.OXIDIZED, false).defaultBlockState()
                .setValue(CopperGolemStatueBlock.POSE, poses[this.random.nextInt(0, poses.length)])
                .setValue(CopperGolemStatueBlock.FACING, Direction.fromYRot(this.getYRot())));
        if (level.getBlockEntity(pos) instanceof CopperGolemStatueBlockEntity statue) {
            statue.createStatue(this);
            this.dropPreservedEquipment();
            this.dropAntenna();
            this.discard();
            this.playSound(Golems.BECOME_STATUE.get());
            if (this.isLeashed()) {
                this.dropLeash(true, level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS));
            }
        }
    }

    private void setupAnimationStates() {
        switch (this.getState()) {
            case IDLE -> {
                interactionGetNoItemAnimationState.stop();
                interactionGetItemAnimationState.stop();
                interactionDropItemAnimationState.stop();
                interactionDropNoItemAnimationState.stop();
                if (this.idleAnimationStartTick == this.tickCount) {
                    idleAnimationState.start(this.tickCount);
                } else if (this.idleAnimationStartTick == 0) {
                    this.idleAnimationStartTick = this.tickCount + this.random.nextInt(200, 240);
                }
                if (this.tickCount == this.idleAnimationStartTick + 10.0F) {
                    this.playHeadSpinSound();
                    this.idleAnimationStartTick = 0;
                }
            }
            case GETTING_ITEM -> only(interactionGetItemAnimationState);
            case GETTING_NO_ITEM -> only(interactionGetNoItemAnimationState);
            case DROPPING_ITEM -> only(interactionDropItemAnimationState);
            case DROPPING_NO_ITEM -> only(interactionDropNoItemAnimationState);
        }
    }

    /** One interaction animation runs; the idle and the other interactions stop (26.x setupAnimationStates). */
    private void only(AnimationState running) {
        idleAnimationState.stop();
        this.idleAnimationStartTick = 0;
        for (AnimationState s : new AnimationState[]{interactionGetItemAnimationState, interactionGetNoItemAnimationState,
                interactionDropItemAnimationState, interactionDropNoItemAnimationState}) {
            if (s != running) {
                s.stop();
            }
        }
        running.startIfStopped(this.tickCount);
    }

    public void spawn(WeatherState state) {
        this.setWeatherState(state);
        this.playSpawnSound();
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
        this.playSpawnSound();
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    public void playSpawnSound() {
        this.playSound(Golems.SPAWN.get());
    }

    private void playHeadSpinSound() {
        if (!this.isSilent()) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), Golems.level(this.getWeatherState()).spin().get(), this.getSoundSource(), 1.0F, 1.0F, false);
        }
    }

    @Override
    protected SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        return Golems.level(this.getWeatherState()).hurt().get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Golems.level(this.getWeatherState()).death().get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(Golems.level(this.getWeatherState()).step().get(), 1.0F, 1.0F);
    }

    @Override
    protected Vec3 getLeashOffset() {
        return new Vec3(0.0, 0.75F * this.getEyeHeight(), 0.0);
    }

    /** 26.x ContainerUser.hasContainerOpen: the chest at pos is the one it has open, or the other half of it. */
    public boolean hasContainerOpen(BlockPos pos) {
        if (this.openedChestPos == null) {
            return false;
        }
        BlockState state = this.level().getBlockState(this.openedChestPos);
        return this.openedChestPos.equals(pos) || state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && this.openedChestPos.relative(ChestBlock.getConnectedDirection(state)).equals(pos);
    }

    @Override
    public void shear(SoundSource source) {
        this.level().playSound(null, this, Golems.SHEAR.get(), source, 1.0F, 1.0F);
        ItemStack antenna = this.getAntenna();
        this.setAntenna(ItemStack.EMPTY);
        this.spawnAtLocation(antenna, 1.5F);
    }

    @Override
    public boolean readyForShearing() {
        return this.getAntenna().is(Golems.SHEARABLE_FROM_COPPER_GOLEM);
    }

    private void dropAntenna() {
        if (!this.getAntenna().isEmpty()) {
            this.spawnAtLocation(this.getAntenna());
            this.setAntenna(ItemStack.EMPTY);
        }
    }

    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        this.dropPreservedEquipment();
        this.dropAntenna();
    }

    @Override
    protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        this.setState(CopperGolemState.IDLE);
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        super.thunderHit(level, bolt);
        UUID id = bolt.getUUID();
        if (!id.equals(this.lastLightningBoltUUID)) {
            this.lastLightningBoltUUID = id;
            WeatherState state = this.getWeatherState();
            if (state != WeatherState.UNAFFECTED && !this.isWaxed()) {
                this.nextWeatheringTick = UNSET_WEATHERING_TICK;
                this.entityData.set(DATA_WEATHER_STATE, CopperGolemState.previous(state), true);
            }
        }
    }
}
