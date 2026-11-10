package dev.frosthaven.havenbackports.nautilus;

import dev.frosthaven.havenbackports.Nautiluses;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x AbstractNautilus, method for method, on 1.21.1 APIs: a tamable swimmer that a player rides with a
 * saddle (steering by look, the jump key charging a dash along the look, 40 ticks cooldown), wears nautilus armor,
 * opens its inventory screen on sneak-use, gives its rider Breath of the Nautilus, and stays near its home (16 or 32
 * blocks) when tame. As in 26.x its face (mouth and tentacles, model +z) is at its back: it looks and swims shell first,
 * and spawnBubbles puts the mouth at minus the view vector, so at yaw 0 (south) its face points north. Interactions are in
 * {@link NautilusInteraction}. 1.21.1 forced these forms:
 * <ul>
 * <li>No SADDLE equipment slot: the saddle is the nautilus's own synced stack (saved as "saddle"), a 1.21.1 Saddleable,
 * dropped on death; body armor is 1.21.1's BODY slot.</li>
 * <li>26.x travelInWater is the water branch of 1.21.1's travel; home = 1.21.1's restriction; needsSync = hasImpulse.</li>
 * <li>Mob.interact is final in 1.21.1: interact's setPersistenceRequired happens in mobInteract.</li>
 * </ul>
 */
public abstract class AbstractNautilus extends TamableAnimal implements PlayerRideableJumping, HasCustomInventoryScreen, Saddleable {
    private static final EntityDataAccessor<Boolean> DASH = SynchedEntityData.defineId(AbstractNautilus.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<ItemStack> SADDLE = SynchedEntityData.defineId(AbstractNautilus.class, EntityDataSerializers.ITEM_STACK);
    private int dashCooldown = 0;
    protected float playerJumpPendingScale;
    protected SimpleContainer inventory;

    protected AbstractNautilus(EntityType<? extends AbstractNautilus> type, Level level) {
        super(type, level);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.011F, 0.0F, true);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.setPathfindingMalus(PathType.WATER, 0.0F);
        this.inventory = new SimpleContainer(0);   // 26.x getInventorySize(): 0 columns, 3 rows
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return !this.isTame() && !this.isBaby() ? stack.is(Nautiluses.NAUTILUS_TAMING_ITEMS) : stack.is(Nautiluses.NAUTILUS_FOOD);
    }

    @Override
    protected void usePlayerItem(Player player, InteractionHand hand, ItemStack stack) {
        if (stack.is(Nautiluses.NAUTILUS_BUCKET_FOOD)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.WATER_BUCKET)));
        } else {
            super.usePlayerItem(player, hand, stack);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 15.0).add(Attributes.MOVEMENT_SPEED, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.3F);
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return 0.0F;
    }

    public static boolean checkNautilusSpawnRules(EntityType<? extends AbstractNautilus> type, LevelAccessor level, MobSpawnType reason,
                                                  BlockPos pos, RandomSource random) {
        int seaLevel = level.getSeaLevel();
        return pos.getY() >= seaLevel - 25 && pos.getY() <= seaLevel - 5 && level.getFluidState(pos.below()).is(FluidTags.WATER)
                && level.getBlockState(pos.above()).is(Blocks.WATER);
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader level) {
        return level.isUnobstructed(this);
    }

    /** 26.x canUseSlot(SADDLE or BODY): alive, adult and tame. */
    public boolean canUseMountSlots() {
        return this.isAlive() && !this.isBaby() && this.isTame();
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY ? super.canUseSlot(slot) : this.canUseMountSlots();
    }

    @Override
    public boolean isBodyArmorItem(ItemStack stack) {
        return stack.getItem() instanceof NautilusArmorItem;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return !this.isVehicle();
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.isSaddled() && this.getFirstPassenger() instanceof Player player ? player : super.getControllingPassenger();
    }

    @Override
    protected Vec3 getRiddenInput(Player controller, Vec3 selfInput) {
        float strafe = controller.xxa;
        float forward = 0.0F;
        float up = 0.0F;
        if (controller.zza != 0.0F) {
            float forwardLook = Mth.cos(controller.getXRot() * (float) (Math.PI / 180.0));
            float upLook = -Mth.sin(controller.getXRot() * (float) (Math.PI / 180.0));
            if (controller.zza < 0.0F) {
                forwardLook *= -0.5F;
                upLook *= -0.5F;
            }
            up = upLook;
            forward = forwardLook;
        }
        return new Vec3(strafe, up, forward);
    }

    protected Vec2 getRiddenRotation(LivingEntity controller) {
        return new Vec2(controller.getXRot() * 0.5F, controller.getYRot());
    }

    @Override
    protected void tickRidden(Player controller, Vec3 riddenInput) {
        super.tickRidden(controller, riddenInput);
        Vec2 rotation = this.getRiddenRotation(controller);
        float yRot = this.getYRot();
        float diff = Mth.wrapDegrees(rotation.y - yRot);
        yRot += diff * 0.5F;
        this.setRot(yRot, rotation.x);
        this.yRotO = this.yBodyRot = this.yHeadRot = yRot;
        if (this.isControlledByLocalInstance()) {
            if (this.playerJumpPendingScale > 0.0F && !this.jumping) {
                this.executeRidersJump(this.playerJumpPendingScale, controller);
            }
            this.playerJumpPendingScale = 0.0F;
        }
    }

    /** 26.x travelInWater (no gravity, 0.9 drag) for the water branch of 1.21.1's travel. */
    @Override
    public void travel(Vec3 input) {
        if (this.isControlledByLocalInstance() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), input);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9));
        } else {
            super.travel(input);
        }
    }

    @Override
    protected float getRiddenSpeed(Player controller) {
        return this.isInWater() ? 0.0325F * (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED)
                : 0.02F * (float) this.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    protected void doPlayerRide(Player player) {
        if (!this.level().isClientSide()) {
            player.startRiding(this);
            if (!this.isVehicle()) {
                this.clearRestriction();
            }
        }
    }

    private int getNautilusRestrictionRadius() {
        return !this.isBaby() && !this.isSaddled() ? 32 : 16;
    }

    protected void checkRestriction() {
        if (!this.isLeashed() && !this.isVehicle() && this.isTame()) {
            int radius = this.getNautilusRestrictionRadius();
            if (!this.hasRestriction() || !this.getRestrictCenter().closerThan(this.blockPosition(), radius + 8) || radius != (int) this.getRestrictRadius()) {
                this.restrictTo(this.blockPosition(), radius);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        this.checkRestriction();
        super.customServerAiStep();
    }

    private void applyEffects() {
        if (this.getFirstPassenger() instanceof Player player) {
            boolean hasEffect = player.hasEffect(Nautiluses.BREATH_OF_THE_NAUTILUS);
            if (!hasEffect || this.level().getGameTime() % 40L == 0L) {
                player.addEffect(new MobEffectInstance(Nautiluses.BREATH_OF_THE_NAUTILUS, 60, 0, true, true, true));
            }
        }
    }

    private void spawnBubbles() {
        double speed = this.getDeltaMovement().length();
        double bubbleProbability = Math.clamp(speed * 2.0, 0.15F, 1.0);
        if (this.random.nextFloat() < bubbleProbability) {
            float yRot = this.getYRot();
            float xRot = Math.clamp(this.getXRot(), -10.0F, 10.0F);
            Vec3 mouth = this.calculateViewVector(xRot, yRot);
            double spread = this.random.nextDouble() * 0.8 * (1.0 + speed);
            double dx = (this.random.nextFloat() - 0.5) * spread;
            double dy = (this.random.nextFloat() - 0.5) * spread;
            double dz = (this.random.nextFloat() - 0.5) * spread;
            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() - mouth.x * 1.1, this.getY() - mouth.y + 0.25, this.getZ() - mouth.z * 1.1, dx, dy, dz);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide()) {
            this.applyEffects();
        }
        if (this.isDashing() && this.dashCooldown < 35) {
            this.setDashing(false);
        }
        if (this.dashCooldown > 0) {
            this.dashCooldown--;
            if (this.dashCooldown == 0) {
                this.makeSound(this.getDashReadySound());
            }
        }
        if (this.isInWater()) {
            this.spawnBubbles();
        }
    }

    @Override
    public boolean canJump() {
        return this.isSaddled();
    }

    @Override
    public void onPlayerJump(int jumpAmount) {
        if (this.isSaddled() && this.dashCooldown <= 0) {
            this.playerJumpPendingScale = jumpAmount >= 90 ? 1.0F : 0.4F + 0.4F * jumpAmount / 90.0F;   // 26.x getPlayerJumpPendingScale
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DASH, false);
        builder.define(SADDLE, ItemStack.EMPTY);
    }

    public boolean isDashing() {
        return this.entityData.get(DASH);
    }

    public void setDashing(boolean dashing) {
        this.entityData.set(DASH, dashing);
    }

    protected void executeRidersJump(float amount, Player controller) {
        this.addDeltaMovement(controller.getLookAngle()
                .scale((this.isInWater() ? 1.2F : 0.5F) * amount * this.getAttributeValue(Attributes.MOVEMENT_SPEED) * this.getBlockSpeedFactor()));
        this.dashCooldown = 40;
        this.setDashing(true);
        this.hasImpulse = true;
    }

    @Override
    public void handleStartJump(int jumpScale) {
        this.makeSound(this.getDashSound());
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.setDashing(true);
    }

    @Override
    public int getJumpCooldown() {
        return this.dashCooldown;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (!this.firstTick && DASH.equals(accessor)) {
            this.dashCooldown = this.dashCooldown == 0 ? 40 : this.dashCooldown;
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public void handleStopJump() {
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Nullable
    protected SoundEvent getDashSound() {
        return null;
    }

    @Nullable
    protected SoundEvent getDashReadySound() {
        return null;
    }

    public SoundEvent getRidingSound() {
        return Nautiluses.NAUTILUS_RIDING.get();
    }

    protected void playEatingSound() {
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return NautilusInteraction.mobInteract(this, player, hand);
    }

    void usePlayerItemForTaming(Player player, InteractionHand hand, ItemStack stack) {
        this.usePlayerItem(player, hand, stack);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        boolean wasHurt = super.hurt(source, damage);
        if (wasHurt && this.level() instanceof ServerLevel && source.getEntity() instanceof LivingEntity attacker) {
            NautilusAi.setAngerTarget(this, attacker);
        }
        return wasHurt;
    }

    /** 26.x #immune_to_poison (1.21.1 checks only its own #ignores_poison_and_regen). */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return (!effect.is(MobEffects.POISON) || !this.getType().is(Nautiluses.IMMUNE_TO_POISON)) && super.canBeAffected(effect);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData groupData) {
        NautilusAi.initMemories(this, level.getRandom());
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    // ---- saddle (26.x EquipmentSlot.SADDLE) ----
    public ItemStack getSaddle() {
        return this.entityData.get(SADDLE);
    }

    public void setSaddle(ItemStack stack) {
        this.entityData.set(SADDLE, stack);
    }

    @Override
    public boolean isSaddleable() {
        return this.canUseMountSlots();
    }

    @Override
    public boolean isSaddled() {
        return !this.getSaddle().isEmpty();
    }

    @Override
    public void equipSaddle(ItemStack stack, @Nullable SoundSource source) {
        this.setSaddle(stack);
        if (source != null) {
            this.level().playSound(null, this, this.getSaddleSoundEvent(), source, 1.0F, 1.0F);
        }
    }

    /** 26.x getSaddleEquipSound: the underwater one while under water. */
    @Override
    public SoundEvent getSaddleSoundEvent() {
        return this.isUnderWater() ? Nautiluses.SADDLE_UNDERWATER_EQUIP.get() : Nautiluses.SADDLE_EQUIP.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!this.getSaddle().isEmpty()) {
            tag.put("saddle", this.getSaddle().save(this.registryAccess()));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setSaddle(tag.contains("saddle") ? ItemStack.parseOptional(this.registryAccess(), tag.getCompound("saddle")) : ItemStack.EMPTY);
    }

    /** 26.x drops the SADDLE slot as guaranteed equipment. */
    @Override
    protected void dropEquipment() {
        super.dropEquipment();
        if (!this.getSaddle().isEmpty()) {
            this.spawnAtLocation(this.getSaddle());
            this.setSaddle(ItemStack.EMPTY);
        }
    }

    @Override
    public void openCustomInventoryScreen(Player player) {
        if (!this.level().isClientSide() && (!this.isVehicle() || this.hasPassenger(player)) && this.isTame()) {
            NautilusInventoryMenu.open(player, this);
        }
    }

    public int getInventoryColumns() {
        return 0;
    }

    public SimpleContainer inventory() {
        return this.inventory;
    }

    public boolean isMobControlled() {
        return this.getFirstPassenger() instanceof Mob;
    }

    protected boolean isAggravated() {
        return this.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT) || this.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.isTame();
    }
}
