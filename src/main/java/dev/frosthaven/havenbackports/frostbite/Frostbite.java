package dev.frosthaven.havenbackports.frostbite;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x Frostbite with its 26.x base RangedZombie folded in (1.21.1 has no ranged zombie base). A zombie of the cold:
 * throws ice balls when it holds one (a quarter spawn with one) and its target is out of melee reach, melee hits give
 * 60 ticks of Freezing, walks in powder snow without sinking or slowing, drowns into a zombie (not a drowned) and never
 * turns into a frostbite itself (FreezingConversion). Health 24, speed 0.21; zombie attributes otherwise.
 */
public class Frostbite extends Zombie implements RangedAttackMob {
    private static final int MELEE_ATTACK_TICKS = 30;
    private static final int FREEZING_TICKS_ON_MELEE = 60;
    private static final float MELEE_MODE_DISTANCE_SQR = 9.0F;
    private static final float RANGED_MODE_DISTANCE_SQR = 25.0F;
    boolean isRanged;

    public Frostbite(EntityType<? extends Frostbite> type, Level level) {
        super(type, level);
        this.setPathfindingMalus(PathType.POWDER_SNOW, 0.0F);
        // 26.x also zeroes ON_TOP_OF_POWDER_SNOW, a type 1.21.1 lacks (its DANGER_POWDER_SNOW is 0 already)
        this.setPathfindingMalus(PathType.DANGER_POWDER_SNOW, 0.0F);
    }

    @Override
    protected void addBehaviourGoals() {
        this.goalSelector.addGoal(1, new FrostbiteGoals.Ranged(this, 1.0, this::getRangedAttackIntervalMin, this::getRangedAttackIntervalMax, 16.0F));
        this.goalSelector.addGoal(2, new FrostbiteGoals.Melee(this, 1.0, false, MELEE_ATTACK_TICKS));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 1.0));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(ZombifiedPiglin.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true, false));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, AbstractVillager.class, false));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Turtle.class, 10, true, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 24.0).add(Attributes.MOVEMENT_SPEED, 0.21F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return FrostbiteContent.FROSTBITE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FrostbiteContent.FROSTBITE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FrostbiteContent.FROSTBITE_DEATH.get();
    }

    @Override
    protected SoundEvent getStepSound() {
        return FrostbiteContent.FROSTBITE_STEP.get();
    }

    /** 26.x replaces the zombie's iron sword or shovel: only an ice ball, one spawn in four. */
    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() > 0.75) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(FrostbiteContent.ICE_BALL_ITEM.get()));
        }
    }

    public Item getRangedWeaponType() {
        return FrostbiteContent.ICE_BALL_ITEM.get();
    }

    /** 26.x Mob.compareWeapons with getPreferredWeaponType() = #frostbite_preferred_weapons: a held-item candidate in
     *  the tag always wins over one outside it, and never loses to one. Armor keeps 1.21.1's rules. */
    @Override
    protected boolean canReplaceCurrentItem(ItemStack candidate, ItemStack existing) {
        if (this.getEquipmentSlotForItem(candidate) == EquipmentSlot.MAINHAND && !existing.isEmpty()) {
            boolean newPreferred = candidate.is(FrostbiteContent.FROSTBITE_PREFERRED_WEAPONS);
            boolean oldPreferred = existing.is(FrostbiteContent.FROSTBITE_PREFERRED_WEAPONS);
            if (oldPreferred != newPreferred) {
                return newPreferred;
            }
        }
        return super.canReplaceCurrentItem(candidate, existing);
    }

    /** Drowning: a zombie, with the frostbite's conversion sound (26.x level event 1057). */
    @Override
    protected void doUnderWaterConversion() {
        if (!net.neoforged.neoforge.event.EventHooks.canLivingConvert(this, EntityType.ZOMBIE, timer -> this.conversionTime = timer)) {
            return;
        }
        this.convertToZombieType(EntityType.ZOMBIE);
        if (!this.isSilent()) {
            this.level().playSound(null, this.blockPosition(), FrostbiteContent.FROSTBITE_CONVERTED_TO_ZOMBIE.get(), SoundSource.HOSTILE, 2.0F,
                    this.random.nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result && target instanceof LivingEntity livingEntity) {
            livingEntity.addEffect(new MobEffectInstance(FrostbiteContent.FREEZING, FREEZING_TICKS_ON_MELEE), this);
        }
        return result;
    }

    private int getRangedAttackIntervalMin() {
        return (int) (20.0F * switch (this.level().getDifficulty()) {
            case PEACEFUL, NORMAL -> 1.0F;
            case EASY -> 3.0F;
            case HARD -> 0.75F;
        });
    }

    private int getRangedAttackIntervalMax() {
        return (int) (20.0F * switch (this.level().getDifficulty()) {
            case PEACEFUL, NORMAL -> 2.0F;
            case EASY -> 3.0F;
            case HARD -> 1.5F;
        });
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 speedMultiplier) {
        if (!state.is(Blocks.POWDER_SNOW)) {
            super.makeStuckInBlock(state, speedMultiplier);
        }
    }

    /** RangedZombie.performRangedAttack: the held ice ball (or a new one) at the target's lower third, power 1.6,
     *  RangedAttackMob.rangedAttackUncertainty (14 - 4 x difficulty), then the arm swing. */
    @Override
    public void performRangedAttack(LivingEntity target, float power) {
        ItemStack mainHandItem = this.getMainHandItem();
        ItemStack itemStack = mainHandItem.is(this.getRangedWeaponType()) ? mainHandItem : new ItemStack(this.getRangedWeaponType());
        Projectile projectile = new IceBall(this.level(), this, itemStack);
        double xd = target.getX() - this.getX();
        double yd = target.getY(0.3333333333333333) - projectile.getY();
        double zd = target.getZ() - this.getZ();
        double distanceToTarget = Math.sqrt(xd * xd + zd * zd);
        if (!this.level().isClientSide()) {   // Projectile.spawnProjectileUsingShoot; getTrajectoryCorrectionFactor = gravity x 4
            projectile.shoot(xd, yd + distanceToTarget * projectile.getGravity() * 4.0, zd, 1.6F, 14 - this.level().getDifficulty().getId() * 4);
            this.level().addFreshEntity(projectile);
        }
        this.playSound(FrostbiteContent.ICE_BALL_THROW.get(), 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.swing(InteractionHand.MAIN_HAND);
    }

    /** RangedZombie.shouldDoRangedAttack: holding the weapon, the target 5 blocks off (3 once throwing) or out of melee
     *  reach with no path that reaches it. */
    boolean shouldDoRangedAttack() {
        if (!this.getMainHandItem().is(this.getRangedWeaponType())) {
            return false;
        }
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return false;
        }
        float rangeThreshold = this.isRanged ? MELEE_MODE_DISTANCE_SQR : RANGED_MODE_DISTANCE_SQR;
        if (this.distanceToSqr(target) >= rangeThreshold) {
            return true;
        } else if (this.isWithinMeleeAttackRange(target)) {
            return false;
        }
        Path path = this.navigation.getPath() != null ? this.navigation.getPath() : this.navigation.createPath(target, 0);
        return path != null && path.getTarget().equals(target.blockPosition()) && !path.canReach();
    }
}
