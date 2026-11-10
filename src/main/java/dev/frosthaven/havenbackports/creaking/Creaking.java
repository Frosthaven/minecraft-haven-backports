package dev.frosthaven.havenbackports.creaking;

import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x Creaking, on 1.21.1 APIs. Its controls, navigation and HomeNodeEvaluator are in CreakingControls; the gaze
 * helpers 1.21.1 lacks are in Gaze26. Forms 1.21.1 forced:
 * - hurt (1.21.1) in place of hurtServer; the damage blame is 1.21.1's own inline code (26.x moved it to methods).
 * - 26.x Mob.doHurtTarget plays the attack sound on a hit; 1.21.1's does not, so this class does.
 * - 26.x path types DAMAGING, FIRE, FIRE_IN_NEIGHBOR are 1.21.1's DAMAGE_OTHER, DAMAGE_FIRE, DANGER_FIRE.
 * - 26.x tag cannot_be_pushed_onto_boats (1.21.1 has no tag; its boat seats any small living entity): canRide
 *   refuses boats.
 * - home_pos is the same int array 26.x's BlockPos codec writes.
 */
public class Creaking extends Monster {
    private static final EntityDataAccessor<Boolean> CAN_MOVE = SynchedEntityData.defineId(Creaking.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_ACTIVE = SynchedEntityData.defineId(Creaking.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_TEARING_DOWN = SynchedEntityData.defineId(Creaking.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<BlockPos>> HOME_POS = SynchedEntityData.defineId(Creaking.class,
            EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final int ATTACK_ANIMATION_DURATION = 15;
    private static final float ACTIVATION_RANGE_SQ = 144.0F;
    public static final int ATTACK_INTERVAL = 40;
    public static final float SPEED_MULTIPLIER_WHEN_IDLING = 0.3F;
    public static final int CREAKING_ORANGE = 16545810;
    public static final int CREAKING_GRAY = 6250335;
    public static final int INVULNERABILITY_ANIMATION_DURATION = 8;
    public static final int TWITCH_DEATH_DURATION = 45;
    private static final int MAX_PLAYER_STUCK_COUNTER = 4;
    private int attackAnimationRemainingTicks;
    public final AnimationState attackAnimationState = new AnimationState();
    public final AnimationState invulnerabilityAnimationState = new AnimationState();
    public final AnimationState deathAnimationState = new AnimationState();
    private int invulnerabilityAnimationRemainingTicks;
    private boolean eyesGlowing;
    private int nextFlickerTime;
    private int playerStuckCounter;

    public Creaking(EntityType<? extends Creaking> type, Level level) {
        super(type, level);
        this.lookControl = new CreakingControls.Look(this);
        this.moveControl = new CreakingControls.Move(this);
        this.jumpControl = new CreakingControls.Jump(this);
        ((GroundPathNavigation) this.getNavigation()).setCanFloat(true);
        this.xpReward = 0;
    }

    public void setTransient(BlockPos pos) {
        this.setHomePos(pos);
        this.setPathfindingMalus(PathType.DAMAGE_OTHER, 8.0F);
        this.setPathfindingMalus(PathType.POWDER_SNOW, 8.0F);
        this.setPathfindingMalus(PathType.LAVA, 8.0F);
        this.setPathfindingMalus(PathType.DAMAGE_FIRE, 0.0F);
        this.setPathfindingMalus(PathType.DANGER_FIRE, 0.0F);
    }

    public boolean isHeartBound() {
        return this.getHomePos() != null;
    }

    @Override
    protected BodyRotationControl createBodyControl() {
        return new CreakingControls.Body(this);
    }

    @Override
    protected Brain.Provider<Creaking> brainProvider() {
        return CreakingAi.provider();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return CreakingAi.makeBrain(this, CreakingAi.provider().makeBrain(dynamic));
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<Creaking> getBrain() {
        return (Brain<Creaking>) super.getBrain();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CAN_MOVE, true);
        builder.define(IS_ACTIVE, false);
        builder.define(IS_TEARING_DOWN, false);
        builder.define(HOME_POS, Optional.empty());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.MOVEMENT_SPEED, 0.4F)
                .add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.STEP_HEIGHT, 1.0625);
    }

    public boolean canMove() {
        return this.entityData.get(CAN_MOVE);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!(target instanceof LivingEntity)) {
            return false;
        }
        this.attackAnimationRemainingTicks = ATTACK_ANIMATION_DURATION;
        this.level().broadcastEntityEvent(this, (byte) 4);
        boolean hurt = super.doHurtTarget(target);
        if (hurt) {
            this.playAttackSound();
        }
        return hurt;
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        BlockPos homePos = this.getHomePos();
        if (!(this.level() instanceof ServerLevel) || homePos == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, damage);
        }
        if (this.isInvulnerableTo(source) || this.invulnerabilityAnimationRemainingTicks > 0 || this.isDeadOrDying()) {
            return false;
        }
        Player responsiblePlayer = this.blameSourceForDamage(source);
        Entity directEntity = source.getDirectEntity();
        if (!(directEntity instanceof LivingEntity) && !(directEntity instanceof Projectile) && responsiblePlayer == null) {
            return false;
        }
        this.invulnerabilityAnimationRemainingTicks = INVULNERABILITY_ANIMATION_DURATION;
        this.level().broadcastEntityEvent(this, (byte) 66);
        this.gameEvent(GameEvent.ENTITY_ACTION);
        if (this.level().getBlockEntity(homePos) instanceof CreakingHeartBlockEntity heart && heart.isProtector(this)) {
            if (responsiblePlayer != null) {
                heart.creakingHurt();
            }
            this.playHurtSound(source);
        }
        return true;
    }

    /** 26.x resolveMobResponsibleForDamage + resolvePlayerResponsibleForDamage, as 1.21.1's LivingEntity.hurt does them. */
    @Nullable
    public Player blameSourceForDamage(DamageSource source) {
        Entity entity = source.getEntity();
        if (entity instanceof LivingEntity living && !source.is(DamageTypeTags.NO_ANGER)
                && (!source.is(net.minecraft.world.damagesource.DamageTypes.WIND_CHARGE)
                || !this.getType().is(net.minecraft.tags.EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE))) {
            this.setLastHurtByMob(living);
        }
        if (entity instanceof Player player) {
            this.lastHurtByPlayerTime = 100;
            this.lastHurtByPlayer = player;
        } else if (entity instanceof TamableAnimal tamable && tamable.isTame()) {
            this.lastHurtByPlayerTime = 100;
            this.lastHurtByPlayer = tamable.getOwner() instanceof Player owner ? owner : null;
        }
        return this.lastHurtByPlayer;
    }

    @Override
    public boolean isPushable() {
        return super.isPushable() && this.canMove();
    }

    @Override
    public void push(double x, double y, double z) {
        if (this.canMove()) {
            super.push(x, y, z);
        }
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return !(vehicle instanceof Boat) && super.canRide(vehicle);
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler().push("creakingBrain");
        this.getBrain().tick((ServerLevel) this.level(), this);
        this.level().getProfiler().pop();
        CreakingAi.updateActivity(this);
    }

    @Override
    public void aiStep() {
        if (this.invulnerabilityAnimationRemainingTicks > 0) {
            this.invulnerabilityAnimationRemainingTicks--;
        }
        if (this.attackAnimationRemainingTicks > 0) {
            this.attackAnimationRemainingTicks--;
        }
        if (!this.level().isClientSide()) {
            boolean canMove = this.entityData.get(CAN_MOVE);
            boolean nowCanMove = this.checkCanMove();
            if (nowCanMove != canMove) {
                this.gameEvent(GameEvent.ENTITY_ACTION);
                if (nowCanMove) {
                    this.makeSound(CreakingContent.CREAKING_UNFREEZE.get());
                } else {
                    this.stopInPlace();
                    this.makeSound(CreakingContent.CREAKING_FREEZE.get());
                }
            }
            this.entityData.set(CAN_MOVE, nowCanMove);
        }
        super.aiStep();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            BlockPos homePos = this.getHomePos();
            if (homePos != null && !(this.level().getBlockEntity(homePos) instanceof CreakingHeartBlockEntity heart && heart.isProtector(this))) {
                this.setHealth(0.0F);
            }
        }
        super.tick();
        if (this.level().isClientSide()) {
            this.setupAnimationStates();
            this.checkEyeBlink();
        }
    }

    @Override
    protected void tickDeath() {
        if (this.isHeartBound() && this.isTearingDown()) {
            this.deathTime++;
            if (!this.level().isClientSide() && this.deathTime > TWITCH_DEATH_DURATION && !this.isRemoved()) {
                this.tearDown();
            }
        } else {
            super.tickDeath();
        }
    }

    @Override
    protected void updateWalkAnimation(float distance) {
        this.walkAnimation.update(Math.min(distance * 25.0F, 3.0F), 0.4F);
    }

    private void setupAnimationStates() {
        this.attackAnimationState.animateWhen(this.attackAnimationRemainingTicks > 0, this.tickCount);
        this.invulnerabilityAnimationState.animateWhen(this.invulnerabilityAnimationRemainingTicks > 0, this.tickCount);
        this.deathAnimationState.animateWhen(this.isTearingDown(), this.tickCount);
    }

    /** Crumble particles of pale oak wood (100) and of an awake heart (10), the death sound, then gone: no drops. */
    public void tearDown() {
        if (this.level() instanceof ServerLevel serverLevel) {
            AABB box = this.getBoundingBox();
            Vec3 center = box.getCenter();
            double xSpread = box.getXsize() * 0.3;
            double ySpread = box.getYsize() * 0.3;
            double zSpread = box.getZsize() * 0.3;
            BlockState wood = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("haven_backports", "pale_oak_wood")).defaultBlockState();
            serverLevel.sendParticles(new BlockParticleOption(CreakingContent.BLOCK_CRUMBLE.get(), wood), center.x, center.y, center.z, 100,
                    xSpread, ySpread, zSpread, 0.0);
            BlockState heart = CreakingContent.CREAKING_HEART.get().defaultBlockState().setValue(CreakingHeartBlock.STATE, CreakingHeartState.AWAKE);
            serverLevel.sendParticles(new BlockParticleOption(CreakingContent.BLOCK_CRUMBLE.get(), heart), center.x, center.y, center.z, 10,
                    xSpread, ySpread, zSpread, 0.0);
        }
        this.makeSound(this.getDeathSound());
        this.remove(Entity.RemovalReason.DISCARDED);
    }

    public void creakingDeathEffects(DamageSource source) {
        this.blameSourceForDamage(source);
        this.die(source);
        this.makeSound(CreakingContent.CREAKING_TWITCH.get());
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 66) {
            this.invulnerabilityAnimationRemainingTicks = INVULNERABILITY_ANIMATION_DURATION;
            this.playHurtSound(this.damageSources().generic());
        } else if (id == 4) {
            this.attackAnimationRemainingTicks = ATTACK_ANIMATION_DURATION;
            this.playAttackSound();
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public boolean fireImmune() {
        return this.isHeartBound() || super.fireImmune();
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return !this.isHeartBound() && super.canUsePortal(allowPassengers);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new CreakingControls.Navigation(this, level);
    }

    public boolean playerIsStuckInYou() {
        List<Player> players = this.getBrain().getMemory(MemoryModuleType.NEAREST_PLAYERS).orElse(List.of());
        if (players.isEmpty()) {
            this.playerStuckCounter = 0;
            return false;
        }
        AABB ownBox = this.getBoundingBox();
        for (Player player : players) {
            if (ownBox.contains(player.getEyePosition())) {
                this.playerStuckCounter++;
                return this.playerStuckCounter > MAX_PLAYER_STUCK_COUNTER;
            }
        }
        this.playerStuckCounter = 0;
        return false;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        NbtUtils.readBlockPos(tag, "home_pos").ifPresent(this::setTransient);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        BlockPos homePos = this.getHomePos();
        if (homePos != null) {
            tag.put("home_pos", NbtUtils.writeBlockPos(homePos));
        }
    }

    public void setHomePos(BlockPos pos) {
        this.entityData.set(HOME_POS, Optional.of(pos));
    }

    @Nullable
    public BlockPos getHomePos() {
        return this.entityData.get(HOME_POS).orElse(null);
    }

    public void setTearingDown() {
        this.entityData.set(IS_TEARING_DOWN, true);
    }

    public boolean isTearingDown() {
        return this.entityData.get(IS_TEARING_DOWN);
    }

    public boolean hasGlowingEyes() {
        return this.eyesGlowing;
    }

    public void checkEyeBlink() {
        if (this.deathTime > this.nextFlickerTime) {
            this.nextFlickerTime = this.deathTime + this.getRandom().nextIntBetweenInclusive(this.eyesGlowing ? 2 : this.deathTime / 4,
                    this.eyesGlowing ? 8 : this.deathTime / 2);
            this.eyesGlowing = !this.eyesGlowing;
        }
    }

    public void playAttackSound() {
        this.makeSound(CreakingContent.CREAKING_ATTACK.get());
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isActive() ? null : CreakingContent.CREAKING_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isHeartBound() ? CreakingContent.CREAKING_SWAY.get() : super.getHurtSound(source);
    }

    @Override
    protected SoundEvent getDeathSound() {
        return CreakingContent.CREAKING_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(CreakingContent.CREAKING_STEP.get(), 0.15F, 1.0F);
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.getTargetFromBrain();
    }

    @Override
    public void knockback(double strength, double x, double z) {
        if (this.canMove()) {
            super.knockback(strength, x, z);
        }
    }

    public boolean checkCanMove() {
        List<Player> players = this.getBrain().getMemory(MemoryModuleType.NEAREST_PLAYERS).orElse(List.of());
        boolean active = this.isActive();
        if (players.isEmpty()) {
            if (active) {
                this.deactivate();
            }
            return true;
        }
        boolean hasPotentialTarget = false;
        for (Player player : players) {
            if (this.canAttack(player) && !this.isAlliedTo(player)) {
                hasPotentialTarget = true;
                if ((!active || Gaze26.notWearingDisguise(player)) && Gaze26.isLookingAtMe(this, player, 0.5, false, true,
                        this.getEyeY(), this.getY() + 0.5 * this.getScale(), (this.getEyeY() + this.getY()) / 2.0)) {
                    if (active) {
                        return false;
                    }
                    if (player.distanceToSqr(this) < ACTIVATION_RANGE_SQ) {
                        this.activate(player);
                        return false;
                    }
                }
            }
        }
        if (!hasPotentialTarget && active) {
            this.deactivate();
        }
        return true;
    }

    public void activate(Player player) {
        this.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, player);
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.makeSound(CreakingContent.CREAKING_ACTIVATE.get());
        this.setIsActive(true);
    }

    public void deactivate() {
        this.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.makeSound(CreakingContent.CREAKING_DEACTIVATE.get());
        this.setIsActive(false);
    }

    public void setIsActive(boolean active) {
        this.entityData.set(IS_ACTIVE, active);
    }

    public boolean isActive() {
        return this.entityData.get(IS_ACTIVE);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }
}
