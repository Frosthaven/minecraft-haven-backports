package dev.frosthaven.havenbackports.ghast;

import dev.frosthaven.havenbackports.Ghasts;
import com.mojang.serialization.Dynamic;
import dev.frosthaven.havenbackports.entity.Leash26;
import dev.frosthaven.havenbackports.entity.QuadLeash;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x HappyGhast (net.minecraft.world.entity.animal.happyghast), with Mojang's numbers. Interaction is in
 * {@link HappyGhastInteraction}, the controls in {@link GhastControls} and the ghastling's brain in {@link HappyGhastAi}.
 *
 * <p>1.21.1 forms: home = restriction; TEMPT_RANGE and CAMERA_DISTANCE are not attributes in 1.21.1 (the tempt goal and
 * sensor use 16, the camera handler 8); travelFlying, isInClouds, Level.precipitationAt and healNaturally are copied
 * here; the precise position sync 26.x sends while the ghast holds still (setRequiresPrecisePosition) is a teleport
 * packet to tracking players every tick; 26.x's canBeCollidedWith(other) is called from EntityMixin; the culling box
 * 26.x's renderer extends down by half the height is the entity's culling box here.
 */
public class HappyGhast extends Animal implements Leash26.Holder, Leash26.Rules {
    public static final float BABY_SCALE = 0.2375F;
    public static final double CAMERA_DISTANCE = 8.0;
    private static final EntityDataAccessor<Boolean> IS_LEASH_HOLDER = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> STAYS_STILL = SynchedEntityData.defineId(HappyGhast.class, EntityDataSerializers.BOOLEAN);
    private int leashHolderTime;
    private int serverStillTimeout;

    public HappyGhast(EntityType<? extends HappyGhast> type, Level level) {
        super(type, level);
        this.moveControl = new GhastControls.GhastMoveControl(this, true, this::isOnStillTimeout);
        this.lookControl = new GhastControls.HappyGhastLookControl(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0).add(Attributes.FLYING_SPEED, 0.05)
                .add(Attributes.MOVEMENT_SPEED, 0.05).add(Attributes.FOLLOW_RANGE, 16.0);
    }

    private void setServerStillTimeout(int timeout) {
        if (this.serverStillTimeout <= 0 && timeout > 0 && this.level() instanceof ServerLevel level) {
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
            level.getChunkSource().broadcast(this, new ClientboundTeleportEntityPacket(this));
        }
        this.serverStillTimeout = timeout;
        this.entityData.set(STAYS_STILL, this.serverStillTimeout > 0);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(3, new GhastControls.HappyGhastFloatGoal(this));
        this.goalSelector.addGoal(4, new GhastControls.TemptForNonPathfinders(this, 1.0,
                stack -> !this.isWearingBodyArmor() && !this.isBaby() ? stack.is(Ghasts.HAPPY_GHAST_TEMPT_ITEMS) : stack.is(Ghasts.HAPPY_GHAST_FOOD),
                7.0, HappyGhastAi.TEMPT_RANGE));
        this.goalSelector.addGoal(5, new GhastControls.RandomFloatAroundGoal(this, 16));
    }

    private void adultGhastSetup() {
        this.moveControl = new GhastControls.GhastMoveControl(this, true, this::isOnStillTimeout);
        this.lookControl = new GhastControls.HappyGhastLookControl(this);
        this.navigation = this.createNavigation(this.level());
        if (this.level() instanceof ServerLevel level) {
            this.removeAllGoals(goal -> true);
            this.registerGoals();
            this.getBrain().stopAll(level, this);
            this.brain.clearMemories();
        }
    }

    private void babyGhastSetup() {
        this.moveControl = new FlyingMoveControl(this, 180, true);
        this.lookControl = new LookControl(this);
        this.navigation = new dev.frosthaven.havenbackports.entity.Navigations.Flying(this, this.level());
        this.setServerStillTimeout(0);
        this.removeAllGoals(goal -> true);
    }

    @Override
    protected void ageBoundaryReached() {
        if (this.isBaby()) {
            this.babyGhastSetup();
        } else {
            this.adultGhastSetup();
        }
        super.ageBoundaryReached();
    }

    @Override
    protected float sanitizeScale(float scale) {
        return Math.min(scale, 1.0F);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    /** 26.x travel: LivingEntity.travelFlying(input, speed, speed) with fluid drag (water 0.8, lava 0.5) or 0.91. */
    @Override
    public void travel(Vec3 input) {
        if (!this.isControlledByLocalInstance()) {
            return;
        }
        float speed = (float) this.getAttributeValue(Attributes.FLYING_SPEED) * 5.0F / 3.0F;
        double drag = this.isInWater() ? 0.8F : this.isInLava() ? 0.5F : 0.91F;
        this.moveRelative(speed, input);
        this.move(net.minecraft.world.entity.MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().scale(drag));
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        if (!level.isEmptyBlock(pos)) {
            return 0.0F;
        }
        return level.isEmptyBlock(pos.below()) && !level.isEmptyBlock(pos.below(2)) ? 10.0F : 5.0F;
    }

    /** 26.x canBreatheUnderwater: a ghastling never drowns (NeoForge makes canBreatheUnderwater final; this is its hook). */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return !this.isBaby() && super.canDrownInFluidType(type);
    }

    @Override
    protected boolean shouldStayCloseToLeashHolder() {
        return false;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public float getVoicePitch() {
        return 1.0F;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    public int getAmbientSoundInterval() {
        int interval = super.getAmbientSoundInterval();
        return this.isVehicle() ? interval * 6 : interval;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return (this.isBaby() ? Ghasts.GHASTLING_AMBIENT : Ghasts.HAPPY_GHAST_AMBIENT).get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return (this.isBaby() ? Ghasts.GHASTLING_HURT : Ghasts.HAPPY_GHAST_HURT).get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return (this.isBaby() ? Ghasts.GHASTLING_DEATH : Ghasts.HAPPY_GHAST_DEATH).get();
    }

    @Override
    protected float getSoundVolume() {
        return this.isBaby() ? 1.0F : 4.0F;
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 1;
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return this.isBaby() ? this.getType().getDimensions().scale(BABY_SCALE).withEyeHeight(0.46875F) : super.getDefaultDimensions(pose);
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return Ghasts.HAPPY_GHAST.get().create(level);
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    public float getAgeScale() {
        return this.isBaby() ? BABY_SCALE : 1.0F;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Ghasts.HAPPY_GHAST_FOOD);
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY ? super.canUseSlot(slot) : this.isAlive() && !this.isBaby();
    }

    @Override
    public boolean isBodyArmorItem(ItemStack stack) {
        return stack.getItem() instanceof HarnessItem;
    }

    @Override
    public net.minecraft.world.InteractionResult mobInteract(Player player, net.minecraft.world.InteractionHand hand) {
        net.minecraft.world.InteractionResult result = HappyGhastInteraction.interact(this, player, hand);
        return result != null ? result : super.mobInteract(player, hand);
    }

    @Override
    protected void addPassenger(Entity passenger) {
        if (!this.isVehicle()) {
            this.playSound(Ghasts.HARNESS_GOGGLES_DOWN.get());
        }
        super.addPassenger(passenger);
        if (!this.level().isClientSide()) {
            if (!this.scanPlayerAboveGhast()) {
                this.setServerStillTimeout(0);
            } else if (this.serverStillTimeout > 10) {
                this.setServerStillTimeout(10);
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide()) {
            this.setServerStillTimeout(10);
        }
        if (!this.isVehicle()) {
            this.clearRestriction();
            this.playSound(Ghasts.HARNESS_GOGGLES_UP.get());
        }
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return this.getPassengers().size() < 4;
    }

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return this.isWearingBodyArmor() && !this.isOnStillTimeout() && this.getFirstPassenger() instanceof Player player
                ? player : super.getControllingPassenger();
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        return HappyGhastInteraction.riddenInput(this, controller);
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        Vec2 rotation = new Vec2(controller.getXRot() * 0.5F, controller.getYRot());
        float yRot = this.getYRot();
        yRot += Mth.wrapDegrees(rotation.y - yRot) * 0.08F;
        this.setRot(yRot, rotation.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = yRot;
    }

    @Override
    protected Brain.Provider<HappyGhast> brainProvider() {
        return Brain.provider(HappyGhastAi.MEMORIES, List.of(net.minecraft.world.entity.ai.sensing.SensorType.NEAREST_LIVING_ENTITIES,
                net.minecraft.world.entity.ai.sensing.SensorType.HURT_BY, Ghasts.FOOD_TEMPTATIONS.get(), Ghasts.NEAREST_ADULT_ANY_TYPE.get(),
                net.minecraft.world.entity.ai.sensing.SensorType.NEAREST_PLAYERS));
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return HappyGhastAi.init(this.brainProvider().makeBrain(dynamic));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Brain<HappyGhast> getBrain() {
        return (Brain<HappyGhast>) super.getBrain();
    }

    @Override
    protected void customServerAiStep() {
        if (this.isBaby()) {
            this.level().getProfiler().push("happyGhastBrain");
            this.getBrain().tick((ServerLevel) this.level(), this);
            this.level().getProfiler().pop();
            this.level().getProfiler().push("happyGhastActivityUpdate");
            HappyGhastAi.updateActivity(this);
            this.level().getProfiler().pop();
        }
        this.checkRestriction();
        super.customServerAiStep();
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            if (this.leashHolderTime > 0) {
                this.leashHolderTime--;
            }
            this.entityData.set(IS_LEASH_HOLDER, this.leashHolderTime > 0);
            if (this.serverStillTimeout > 0) {
                if (this.tickCount > 60) {
                    this.serverStillTimeout--;
                }
                this.setServerStillTimeout(this.serverStillTimeout);
            }
            if (this.scanPlayerAboveGhast()) {
                this.setServerStillTimeout(10);
            }
            if (this.isOnStillTimeout() && this.level() instanceof ServerLevel level) {
                level.getChunkSource().broadcast(this, new ClientboundTeleportEntityPacket(this));   // setRequiresPrecisePosition
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.continuousHeal();
    }

    private void checkRestriction() {
        if (!this.isLeashed() && !this.isVehicle()) {
            int radius = !this.isBaby() && this.getItemBySlot(EquipmentSlot.BODY).isEmpty() ? 64 : 32;
            if (!this.hasRestriction() || !this.getRestrictCenter().closerThan(this.blockPosition(), radius + 16) || radius != (int) this.getRestrictRadius()) {
                this.restrictTo(this.blockPosition(), radius);
            }
        }
    }

    private void continuousHeal() {
        if (this.level() instanceof ServerLevel level && this.isAlive() && this.deathTime == 0 && this.getMaxHealth() != this.getHealth()) {
            boolean fast = HappyGhastInteraction.isInClouds(this) || HappyGhastInteraction.precipitationAt(level, this.blockPosition()) != Biome.Precipitation.NONE;
            if (this.tickCount % (fast ? 20 : 600) == 0 && this.getHealth() > 0.0F && this.getHealth() < this.getMaxHealth()) {
                this.setHealth(Math.min(this.getHealth() + 1.0F, this.getMaxHealth()));   // 26.x healNaturally(1)
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(IS_LEASH_HOLDER, false);
        builder.define(STAYS_STILL, false);
    }

    public boolean isLeashHolder() {
        return this.entityData.get(IS_LEASH_HOLDER);
    }

    public boolean staysStill() {
        return this.entityData.get(STAYS_STILL);
    }

    // ---- leash (26.x Leashable on the happy ghast; Leash26 runs the tick) ----
    @Override
    public boolean supportQuadLeashAsHolder() {
        return true;
    }

    @Override
    public void notifyLeashHolder(Entity leashee) {
        if (QuadLeash.supports(leashee)) {
            this.leashHolderTime = 5;
        }
    }

    /** 26.x getQuadLeashHolderOffsets. */
    public Vec3[] getQuadLeashHolderOffsets() {
        return QuadLeash.create(this, -0.03125, 0.4375, 0.46875, 0.03125);
    }

    @Override
    public double elasticDistance() {
        return 10.0;
    }

    @Override
    public double snapDistance() {
        return 16.0;
    }

    @Override
    public void onElasticPull() {
        ((GhastControls.GhastMoveControl) this.getMoveControl()).setWait();
    }

    @Override
    public boolean handleLeashAtDistance(Entity holder, float distance) {
        if (this.level() instanceof ServerLevel level) {
            Leash26.tick(level, this, this);
        }
        return false;   // 1.21.1's legacy pull does not run: the 26.x tick above replaces it
    }

    @Override
    protected Vec3 getLeashOffset() {
        return Vec3.ZERO;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("still_timeout", this.serverStillTimeout);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setServerStillTimeout(tag.getInt("still_timeout"));
    }

    public boolean isOnStillTimeout() {
        return this.staysStill() || this.serverStillTimeout > 0;
    }

    private boolean scanPlayerAboveGhast() {
        return HappyGhastInteraction.playerAbove(this);
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new GhastControls.HappyGhastBodyRotationControl(this);
    }

    /** 26.x Entity.canBeCollidedWith(other), read by EntityMixin for Entity.canCollideWith. */
    public boolean canBeCollidedWith(@Nullable Entity other) {
        if (this.isBaby() || !this.isAlive()) {
            return false;
        }
        if (this.level().isClientSide() && other instanceof Player && other.position().y >= this.getBoundingBox().maxY) {
            return true;
        }
        return this.isVehicle() && other instanceof HappyGhast || this.isOnStillTimeout();
    }

    public boolean isFlyingVehicle() {
        return !this.isBaby();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        return box.setMinY(box.minY - this.getBbHeight() / 2.0F);
    }
}
