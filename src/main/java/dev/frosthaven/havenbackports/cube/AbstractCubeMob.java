package dev.frosthaven.havenbackports.cube;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x AbstractCubeMob (26.x moved the slime's cube logic here; the Sulfur Cube is its only user in this mod), on
 * 1.21.1 APIs: CompoundTag save data (26.x ValueOutput), MobSpawnType (EntitySpawnReason), and the split on death
 * done as 1.21.1's Slime does it (26.x convertTo with SPLIT_ON_DEATH: name, no-AI, invulnerability, persistence and
 * team carried over; NeoForge's MobSplitEvent fires). Goals and move control: CubeGoals.
 */
public abstract class AbstractCubeMob extends Physics26Mob {
    protected static final EntityDataAccessor<Integer> ID_SIZE = SynchedEntityData.defineId(AbstractCubeMob.class, EntityDataSerializers.INT);
    public static final int MAX_SIZE = 127;
    public float targetSquish;
    public float squish;
    public float oSquish;
    private boolean wasOnGround = false;

    protected AbstractCubeMob(EntityType<? extends AbstractCubeMob> type, Level level) {
        super(type, level);
        this.fixupDimensions();
        this.moveControl = new CubeGoals.MoveControl(this);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new CubeGoals.FloatGoal(this));
        this.goalSelector.addGoal(4, new CubeGoals.RandomDirectionGoal(this));
        this.goalSelector.addGoal(5, new CubeGoals.KeepOnJumpingGoal(this));
        this.addBehaviourGoals();
        this.addTargetingGoals();
    }

    protected abstract void addBehaviourGoals();

    protected abstract void addTargetingGoals();

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(ID_SIZE, 1);
    }

    public void setSize(int size, boolean updateHealth) {
        int actualSize = Mth.clamp(size, 1, MAX_SIZE);
        this.entityData.set(ID_SIZE, actualSize);
        this.reapplyPosition();
        this.refreshDimensions();
        this.setCubeMobHealth(actualSize);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.2F + 0.1F * actualSize);
        if (updateHealth) {
            this.setHealth(this.getMaxHealth());
        }
    }

    protected void setCubeMobHealth(int actualSize) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(actualSize * actualSize);
    }

    public int getSize() {
        return this.entityData.get(ID_SIZE);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Size", this.getSize() - 1);
        tag.putBoolean("wasOnGround", this.wasOnGround);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.setSize(tag.getInt("Size") + 1, false);
        super.readAdditionalSaveData(tag);
        this.wasOnGround = tag.getBoolean("wasOnGround");
    }

    public boolean isTiny() {
        return this.getSize() <= 1;
    }

    @Nullable
    protected abstract ParticleOptions getParticleType();

    @Override
    public void tick() {
        this.oSquish = this.squish;
        this.squish = this.squish + (this.targetSquish - this.squish) * 0.5F;
        super.tick();
        if (this.onGround() && !this.wasOnGround) {
            float size = this.getDimensions(this.getPose()).width() * 2.0F;
            float radius = size / 2.0F;
            for (int i = 0; i < size * 16.0F; i++) {
                float dir = this.random.nextFloat() * (float) (Math.PI * 2);
                float d = this.random.nextFloat() * 0.5F + 0.5F;
                float xd = Mth.sin(dir) * radius * d;
                float zd = Mth.cos(dir) * radius * d;
                ParticleOptions particle = this.getParticleType();
                if (particle != null) {
                    this.level().addParticle(particle, this.getX() + xd, this.getY(), this.getZ() + zd, 0.0, 0.0, 0.0);
                }
            }
            this.playSound(this.getSquishSound(), this.getSoundVolume(), ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) / 0.8F);
            this.targetSquish = -0.5F;
        } else if (!this.onGround() && this.wasOnGround) {
            this.targetSquish = 1.0F;
        }
        this.wasOnGround = this.onGround();
        this.decreaseSquish();
    }

    protected void decreaseSquish() {
        this.targetSquish *= 0.6F;
    }

    protected int getJumpDelay() {
        return this.random.nextInt(20) + 10;
    }

    @Override
    public void refreshDimensions() {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        super.refreshDimensions();
        this.setPos(x, y, z);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (ID_SIZE.equals(accessor)) {
            this.refreshDimensions();
            this.setYRot(this.yHeadRot);
            this.yBodyRot = this.yHeadRot;
            if (this.isInWater() && this.random.nextInt(20) == 0) {
                this.doWaterSplashEffect();
            }
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        int size = this.getSize();
        if (!this.level().isClientSide && size > 1 && this.isDeadOrDying()) {
            float width = this.getDimensions(this.getPose()).width();
            float offset = width / 2.0F;
            int halfSize = size / 2;
            int count = this.getSplitCount();
            PlayerTeam team = this.getTeam();
            var children = new java.util.ArrayList<Mob>();
            for (int i = 0; i < count; i++) {
                float xd = (i % 2 - 0.5F) * offset;
                float zd = (i / 2 - 0.5F) * offset;
                Entity e = this.getType().create(this.level());
                if (e instanceof AbstractCubeMob cube) {
                    if (this.isPersistenceRequired()) {
                        cube.setPersistenceRequired();
                    }
                    cube.setCustomName(this.getCustomName());
                    cube.setNoAi(this.isNoAi());
                    cube.setInvulnerable(this.isInvulnerable());
                    if (team != null) {
                        this.level().getScoreboard().addPlayerToTeam(cube.getScoreboardName(), team);
                    }
                    this.setUpSplitCube(cube, halfSize, xd, zd);
                    children.add(cube);
                }
            }
            if (!net.neoforged.neoforge.event.EventHooks.onMobSplit(this, children).isCanceled()) {
                children.forEach(this.level()::addFreshEntity);
            }
        }
        super.remove(reason);
    }

    protected void setUpSplitCube(AbstractCubeMob cube, int halfSize, float xd, float zd) {
        cube.setSize(halfSize, true);
        cube.moveTo(this.getX() + xd, this.getY() + 0.5, this.getZ() + zd, this.random.nextFloat() * 360.0F, 0.0F);
    }

    protected int getSplitCount() {
        return 2 + this.random.nextInt(3);
    }

    @Override
    public void push(Entity entity) {
        super.push(entity);
        if (entity instanceof IronGolem golem && this.canDealDamage()) {
            this.dealDamage(golem);
        }
    }

    @Override
    public void playerTouch(Player player) {
        if (this.canDealDamage()) {
            this.dealDamage(player);
        }
    }

    protected void dealDamage(LivingEntity target) {
        if (this.level() instanceof ServerLevel level && this.isAlive() && this.isWithinMeleeAttackRange(target) && this.hasLineOfSight(target)) {
            DamageSource source = this.damageSources().mobAttack(this);
            if (target.hurt(source, this.getAttackDamage())) {
                this.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                EnchantmentHelper.doPostAttackEffects(level, target, source);
            }
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return new Vec3(0.0, dimensions.height() - 0.015625 * this.getSize() * scale, 0.0);
    }

    protected boolean canDealDamage() {
        return !this.isTiny() && this.isEffectiveAi();
    }

    protected float getAttackDamage() {
        return (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return this.getType().getDimensions().scale(this.getSize());
    }

    @Override
    public void jumpFromGround() {
        Vec3 movement = this.getDeltaMovement();
        this.setDeltaMovement(movement.x, this.getJumpPower(), movement.z);
        this.hasImpulse = true;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F * this.getSize();
    }

    @Override
    public int getMaxHeadXRot() {
        return 0;
    }

    protected boolean doPlayJumpSound() {
        return this.getSize() > 0;
    }

    public float getSoundPitch() {
        float adjust = this.isTiny() ? 1.4F : 0.8F;
        return ((this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F) * adjust;
    }

    protected abstract SoundEvent getJumpSound();

    protected abstract SoundEvent getSquishSound();

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, data);
        this.setSpawnSize(level, difficulty);
        return out;
    }

    protected void setSpawnSize(ServerLevelAccessor level, DifficultyInstance difficulty) {
        RandomSource random = level.getRandom();
        int sizeScale = random.nextInt(3);
        if (sizeScale < 2 && random.nextFloat() < 0.5F * difficulty.getSpecialMultiplier()) {
            sizeScale++;
        }
        this.setSize(1 << sizeScale, true);
    }
}
