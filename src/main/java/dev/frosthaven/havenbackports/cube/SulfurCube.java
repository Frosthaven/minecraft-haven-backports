package dev.frosthaven.havenbackports.cube;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x SulfurCube: a cube that swallows a block (its BODY slot) and takes on that block's archetype (attributes,
 * buoyancy, explosion, contact damage, knockback, sounds, location effects). Interaction, knockback and fuse logic:
 * SulfurCubeActions; goals: SulfurCubeGoals. 1.21.1 forms: the archetype change runs from NeoForge's
 * LivingEquipmentChangeEvent (26.x collectEquipmentChanges, which 1.21.1 keeps private; the event fires there, for
 * the same before and after stacks), CompoundTag save data, hurt (26.x hurtServer).
 */
public class SulfurCube extends AbstractCubeMob implements Bucketable, net.minecraft.world.entity.Shearable {
    public static final int PICKUP_TIMER_DURATION = 100;
    static final TagKey<net.minecraft.world.item.Item> SWALLOWABLE = ItemTags.create(ResourceLocation.withDefaultNamespace("sulfur_cube_swallowable"));
    static final TagKey<net.minecraft.world.item.Item> FOOD = ItemTags.create(ResourceLocation.withDefaultNamespace("sulfur_cube_food"));
    static final TagKey<DamageType> IMMUNE_WITH_BLOCK = TagKey.create(net.minecraft.core.registries.Registries.DAMAGE_TYPE,
            ResourceLocation.withDefaultNamespace("sulfur_cube_with_block_immune_to"));
    static final Predicate<ItemEntity> ALLOWED_ITEMS = e -> !e.hasPickUpDelay() && e.isAlive() && isSwallowableItem(e.getItem());
    private static final EntityDataAccessor<Integer> MAX_FUSE = SynchedEntityData.defineId(SulfurCube.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FROM_BUCKET = SynchedEntityData.defineId(SulfurCube.class, EntityDataSerializers.BOOLEAN);

    int pickupTimer = 0;
    int pushSoundCooldown = 0;
    boolean floatsInLiquids = false;
    Optional<SulfurCubeArchetype.ExplosionData> explosionData = Optional.empty();
    SulfurCubeArchetype.KnockbackModifiers knockbackModifier = SulfurCubeArchetype.DEFAULT_KNOCKBACK_MODIFIERS;
    SulfurCubeArchetype.SoundSettings soundSettings = SulfurCubeArchetype.defaultSoundSettings();
    int fuse = -1;
    final List<SulfurCubeArchetype.ContactDamage> contactDamages = new ArrayList<>();
    final Set<EnchantmentLocationBasedEffect> activeLocationDependentEffects = new HashSet<>();
    /** 26.x AgeableMob age lock (1.21.1 has none; kept here so a bucketed cube keeps it). */
    boolean ageLocked = false;
    @Nullable
    DamageSource knockbackSource;
    float knockbackDamage;

    public SulfurCube(EntityType<? extends SulfurCube> type, Level level) {
        super(type, level);
        this.lookControl = new SulfurCubeGoals.Look(this);
        this.moveControl = new SulfurCubeGoals.Move(this);
    }

    public static AttributeSupplier.Builder createAttributes() {
        // 26.x: Mob.createMobAttributes() + tempt range 8 (SulfurCubeGoals), and LivingEntity's bounciness, air drag and
        // friction modifiers, which only this mob carries here
        return Mob.createMobAttributes().add(Cube.BOUNCINESS).add(Cube.AIR_DRAG_MODIFIER).add(Cube.FRICTION_MODIFIER);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(FROM_BUCKET, false);
        builder.define(MAX_FUSE, -1);
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(2, new SulfurCubeGoals.Tempt(this, s -> this.isBaby() ? s.is(FOOD) : isSwallowableItem(s), 1.0));
        this.goalSelector.addGoal(3, new SulfurCubeGoals.SearchForItems(this));
    }

    @Override
    protected void addTargetingGoals() {
    }

    @Override
    public boolean fromBucket() {
        return this.entityData.get(FROM_BUCKET);
    }

    @Override
    public void setFromBucket(boolean fromBucket) {
        this.entityData.set(FROM_BUCKET, fromBucket);
    }

    public int getFuse() {
        return this.fuse;
    }

    public boolean isPrimed() {
        return this.getFuse() >= 0;
    }

    void setFuse(int fuse) {
        this.fuse = fuse;
    }

    void syncFuse(int fuse) {
        this.setFuse(fuse);
        this.entityData.set(MAX_FUSE, fuse);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        if (MAX_FUSE.equals(accessor)) {
            this.setFuse(this.entityData.get(MAX_FUSE));
        }
        super.onSyncedDataUpdated(accessor);
    }

    @Override
    public SoundEvent getPickupSound() {
        return CubeSounds.BUCKET_FILL.get();
    }

    @Override
    public void saveToBucketTag(ItemStack bucket) {
        Bucketable.saveDefaultDataToBucketTag(this, bucket);
        ItemStack body = this.getItemBySlot(EquipmentSlot.BODY);
        if (!body.isEmpty()) {
            bucket.set(Cube.SULFUR_CUBE_CONTENT.get(), SulfurCubeContent.ofNonEmpty(body));
        }
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.BUCKET_ENTITY_DATA, bucket, tag -> {
            tag.putInt("age", this.getAge());
            tag.putBoolean("age_locked", this.ageLocked);
        });
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        this.setAge(tag.getInt("age"));
        this.ageLocked = tag.getBoolean("age_locked");
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(Cube.SULFUR_CUBE_BUCKET.get());
    }

    /** 26.x canBreatheUnderwater (with a block inside it does not drown); NeoForge's hook for 1.21.1's final method. */
    @Override
    public boolean canDrownInFluidType(net.neoforged.neoforge.fluids.FluidType type) {
        return !this.hasBodyItem() && super.canDrownInFluidType(type);
    }

    @Override
    public void travel(Vec3 input) {
        boolean fluid = this.isControlledByLocalInstance() && this.travelsInFluid();
        super.travel(input);
        if (fluid) {
            SulfurCubeActions.floatInFluid(this);
        }
    }

    @Override
    public double getFluidJumpThreshold() {
        return this.getBbHeight() * 0.2;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0F;
    }

    @Override
    protected boolean canDealDamage() {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.hasBodyItem() || this.fromBucket();
    }

    @Override
    public boolean canBeLeashed() {
        return this.hasBodyItem();
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (SulfurCubeActions.hurtWithBlock(this, source, damage)) {
            return true;
        }
        this.knockbackSource = source;
        this.knockbackDamage = damage;
        try {
            return super.hurt(source, damage);
        } finally {
            this.knockbackSource = null;
        }
    }

    @Override
    public void knockback(double power, double xd, double zd) {
        if (!SulfurCubeActions.knockback(this, xd, zd)) {
            super.knockback(power, xd, zd);
        }
    }

    public boolean hasBodyItem() {
        return !this.getItemBySlot(EquipmentSlot.BODY).isEmpty();
    }

    public boolean canExplode() {
        return this.explosionData.isPresent() && this.isAlive() && !this.isPrimed();
    }

    List<SulfurCubeArchetype> matchingArchetypes(ItemStack stack) {
        return this.level().registryAccess().registry(Cube.ARCHETYPE).map(r -> r.stream().filter(a -> stack.is(a.items()))
                .collect(Collectors.toCollection(ArrayList::new))).orElseGet(ArrayList::new);
    }

    @Override
    public void tick() {
        SulfurCubeActions.tickFuse(this);
        SulfurCubeActions.primeWhenOnPoweredPosition(this);
        super.tick();
    }

    @Override
    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        super.onChangedBlock(level, pos);
        SulfurCubeArchetypes.onChangedBlock(this, level);
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (this.pickupTimer > 0) {
            this.pickupTimer--;
        }
        if (this.pushSoundCooldown > 0) {
            this.pushSoundCooldown--;
        }
    }

    /** 26.x collectEquipmentChanges, BODY slot part (server; from NeoForge's LivingEquipmentChangeEvent). */
    void onBodyChanged(ItemStack previous, ItemStack current) {
        SulfurCubeArchetypes.onBodyChanged(this, previous, current);
    }

    void registerCubeGoals() {
        this.registerGoals();
    }

    @Override
    public float maxUpStep() {
        return this.hasBodyItem() ? 0.0F : super.maxUpStep();
    }

    @Override
    protected boolean omnidirectionalAirMover() {
        return this.hasBodyItem();
    }

    @Override
    public boolean canFreeze() {
        return !this.hasBodyItem() && super.canFreeze();
    }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return SulfurCubeActions.mobInteract(this, player, hand);
    }

    net.minecraft.world.InteractionResult superMobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        return super.mobInteract(player, hand);
    }

    Vec3 equipmentSpawnOffset() {
        return this.getAttachments().get(EntityAttachment.PASSENGER, 0, 0.0F);   // 26.x getAverage: the one passenger point
    }

    @Override
    public EquipmentSlot getEquipmentSlotForItem(ItemStack stack) {
        return isSwallowableItem(stack) ? EquipmentSlot.BODY : super.getEquipmentSlotForItem(stack);
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isTiny() ? CubeSounds.SMALL_HURT.get() : CubeSounds.HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.isTiny() ? CubeSounds.SMALL_DEATH.get() : CubeSounds.DEATH.get();
    }

    @Override
    protected SoundEvent getSquishSound() {
        if (this.isTiny()) {
            return CubeSounds.SMALL_SQUISH.get();
        }
        return this.hasBodyItem() ? CubeSounds.BOUNCE.get() : CubeSounds.SQUISH.get();
    }

    @Override
    protected SoundEvent getJumpSound() {
        return this.isTiny() ? CubeSounds.SMALL_JUMP.get() : CubeSounds.JUMP.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (!this.hasBodyItem()) {
            super.playStepSound(pos, state);
        }
    }

    @Nullable
    @Override
    protected ParticleOptions getParticleType() {
        return Cube.SULFUR_CUBE_GOO.get();
    }

    @Override
    public void shear(SoundSource source) {
        SulfurCubeActions.shear(this);
    }

    @Override
    public boolean readyForShearing() {
        return this.hasBodyItem();
    }

    @Override
    public boolean canPickUpLoot() {
        return !this.hasBodyItem();
    }

    static boolean isSwallowableItem(ItemStack stack) {
        return stack.is(SWALLOWABLE);
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY ? super.canUseSlot(slot) : this.isAlive() && !this.isBaby();
    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        return this.getItemBySlot(EquipmentSlot.BODY).isEmpty() && isSwallowableItem(stack) && !this.isBaby();
    }

    @Override
    protected void pickUpItem(ItemEntity entity) {
        SulfurCubeActions.pickUp(this, entity);
    }

    @Override
    protected int getBaseExperienceReward() {
        return this.isBaby() ? 0 : 1 + this.random.nextInt(2);
    }

    @Override
    protected int getSplitCount() {
        return this.isPrimed() ? 0 : 2;
    }

    @Override
    protected void setSpawnSize(ServerLevelAccessor level, DifficultyInstance difficulty) {
        this.setSize(this.isBaby() ? 1 : 2, true);
    }

    @Override
    public void setSize(int size, boolean updateHealth) {
        super.setSize(size, updateHealth);
        if (updateHealth && size == 1 && !this.isBaby()) {
            this.setBaby(true);
        }
    }

    @Override
    protected void setUpSplitCube(AbstractCubeMob cube, int halfSize, float xd, float zd) {
        super.setUpSplitCube(cube, halfSize, xd, zd);
        cube.setBaby(true);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        SulfurCube cube = Cube.SULFUR_CUBE.get().create(level);
        if (cube != null) {
            cube.setSize(1, true);
        }
        return cube;
    }

    /** 26.x canAgeUp: a baby whose age is not locked. */
    boolean canAgeUp() {
        return this.isBaby() && !this.ageLocked;
    }

    @Override
    public void setAge(int age) {
        if (this.ageLocked && this.getAge() < 0 && age > this.getAge()) {
            return;   // 26.x AgeableMob.aiStep ages only when canAgeUp
        }
        super.setAge(age);
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();
        if (!this.isBaby()) {
            this.setSize(2, true);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("pickup_timer", this.pickupTimer);
        tag.putBoolean("from_bucket", this.fromBucket());
        tag.putInt("fuse", this.getFuse());
        tag.putBoolean("AgeLocked", this.ageLocked);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        this.pickupTimer = tag.getInt("pickup_timer");
        this.setFromBucket(tag.getBoolean("from_bucket"));
        this.setFuse(tag.contains("fuse") ? tag.getInt("fuse") : -1);
        this.entityData.set(MAX_FUSE, this.getFuse());
        this.ageLocked = tag.getBoolean("AgeLocked");
        super.readAdditionalSaveData(tag);
    }

    @Override
    protected void doPush(Entity entity) {
        super.doPush(entity);
        SulfurCubeActions.applyContactDamage(this, entity);
    }

    @Override
    public void playerTouch(net.minecraft.world.entity.player.Player player) {
        super.playerTouch(player);
        SulfurCubeActions.playerPush(this, player);
    }

    @Override
    public Vec3 getLeashOffset() {
        return new Vec3(0.0, this.getBbHeight() / 2.0F, 0.0);
    }

    @Override
    protected void setCubeMobHealth(int actualSize) {
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(4 * actualSize);
    }

    void markDead() {
        this.dead = true;
    }

    void deathEffects() {
        this.triggerOnDeathMobEffects(Entity.RemovalReason.KILLED);
    }

    void setBodyItem(ItemStack stack) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, stack);
    }

    void setContent(SulfurCubeContent content) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, content.absorbedBlockItemStack().copy());
    }
}
