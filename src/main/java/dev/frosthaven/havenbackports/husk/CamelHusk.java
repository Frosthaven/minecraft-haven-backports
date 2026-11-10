package dev.frosthaven.havenbackports.husk;

import dev.frosthaven.havenbackports.Husks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x CamelHusk, method for method, on 1.21.1's Camel. Where 1.21.1's Camel names its sounds inline (26.x
 * moved them into getDashingSound, getSitDownSound, getStandUpSound), the 1.21.1 method is overridden with Mojang's
 * body and the husk's sound: handleStartJump, sitDown, standUp. The dash-ready sound plays inside Camel.tick, so
 * CamelMixin swaps it. 26.x canBeABaby() = false is isBaby() = false and setBaby doing nothing (1.21.1 has no such
 * switch). 26.x isMobControlled (a mob riding in front): CamelPanicMixin keeps a husk-ridden camel husk from
 * panicking, as 26.x CamelAi.CamelPanic does; chargeSpeedModifier 4.0 is read by SpearUseGoal.
 */
public class CamelHusk extends Camel {
    public CamelHusk(EntityType<? extends Camel> type, Level level) {
        super(type, level);
    }

    @Override
    public boolean removeWhenFarAway(double distSqr) {
        return true;
    }

    public boolean isMobControlled() {
        return this.getFirstPassenger() instanceof Mob;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        this.setPersistenceRequired();
        return super.mobInteract(player, hand);
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isMobControlled();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(Husks.CAMEL_HUSK_FOOD);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return Husks.CAMEL_HUSK_AMBIENT.get();
    }

    @Override
    public boolean canMate(Animal partner) {
        return false;
    }

    @Nullable
    @Override
    public Camel getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
    }

    @Override
    protected SoundEvent getDeathSound() {
        return Husks.CAMEL_HUSK_DEATH.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return Husks.CAMEL_HUSK_HURT.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (state.is(BlockTags.CAMEL_SAND_STEP_SOUND_BLOCKS)) {
            this.playSound(Husks.CAMEL_HUSK_STEP_SAND.get(), 0.4F, 1.0F);
        } else {
            this.playSound(Husks.CAMEL_HUSK_STEP.get(), 0.4F, 1.0F);
        }
    }

    /** 26.x Camel.handleStartJump with getDashingSound() = CAMEL_HUSK_DASH. */
    @Override
    public void handleStartJump(int jumpPower) {
        this.makeSound(Husks.CAMEL_HUSK_DASH.get());
        this.gameEvent(GameEvent.ENTITY_ACTION);
        this.setDashing(true);
    }

    @Override
    protected SoundEvent getEatingSound() {
        return Husks.CAMEL_HUSK_EAT.get();
    }

    /** 26.x Camel.sitDown with getSitDownSound() = CAMEL_HUSK_SIT. */
    @Override
    public void sitDown() {
        if (!this.isCamelSitting()) {
            this.makeSound(Husks.CAMEL_HUSK_SIT.get());
            this.setPose(Pose.SITTING);
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.resetLastPoseChangeTick(-this.level().getGameTime());
        }
    }

    /** 26.x Camel.standUp with getStandUpSound() = CAMEL_HUSK_STAND. */
    @Override
    public void standUp() {
        if (this.isCamelSitting()) {
            this.makeSound(Husks.CAMEL_HUSK_STAND.get());
            this.setPose(Pose.STANDING);
            this.gameEvent(GameEvent.ENTITY_ACTION);
            this.resetLastPoseChangeTick(this.level().getGameTime());
        }
    }

    @Override
    public SoundEvent getSaddleSoundEvent() {
        return Husks.CAMEL_HUSK_SADDLE.get();
    }

    public float chargeSpeedModifier() {
        return 4.0F;
    }
}
