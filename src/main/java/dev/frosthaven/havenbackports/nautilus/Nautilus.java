package dev.frosthaven.havenbackports.nautilus;

import com.mojang.serialization.Dynamic;
import dev.frosthaven.havenbackports.Nautiluses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityAttachments;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x Nautilus: breeds (a tame parent's baby is tame to the same owner), half-size babies with their own
 * sounds, dries out on land (300 air, 2 damage a second at -20, 26.x handleAirSupply after baseTick), sounds by
 * water or land. The brain is NautilusAi's (26.x Brain.Provider with the memories 1.21.1 needs listed).
 */
public class Nautilus extends AbstractNautilus {
    private EntityDimensions babyDimensions;

    public Nautilus(EntityType<? extends Nautilus> type, Level level) {
        super(type, level);
    }

    @Override
    protected Brain.Provider<Nautilus> brainProvider() {
        return Brain.provider(NautilusAi.MEMORIES, NautilusAi.SENSORS);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return NautilusAi.makeBrain(this.brainProvider().makeBrain(dynamic), false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<Nautilus> getBrain() {
        return (Brain<Nautilus>) super.getBrain();
    }

    @Nullable
    @Override
    public Nautilus getBreedOffspring(ServerLevel level, AgeableMob partner) {
        Nautilus baby = Nautiluses.NAUTILUS.get().create(level);
        if (baby != null && this.isTame()) {
            baby.setOwnerUUID(this.getOwnerUUID());
            baby.setTame(true, true);
        }
        return baby;
    }

    /** 26.x BABY_DIMENSIONS: the type's size x 0.5, passenger attachment 0.5 up. */
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        if (!this.isBaby()) {
            return super.getDefaultDimensions(pose);
        }
        if (this.babyDimensions == null) {
            this.babyDimensions = this.getType().getDimensions().scale(0.5F)
                    .withAttachments(EntityAttachments.builder().attach(EntityAttachment.PASSENGER, 0.0F, 0.5F, 0.0F));
        }
        return this.babyDimensions;
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler().push("nautilusBrain");
        this.getBrain().tick((ServerLevel) this.level(), this);
        this.level().getProfiler().pop();
        this.level().getProfiler().push("nautilusActivityUpdate");
        NautilusAi.updateActivity(this);
        this.level().getProfiler().pop();
        super.customServerAiStep();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        if (this.isBaby()) {
            return this.isUnderWater() ? Nautiluses.BABY_AMBIENT.get() : Nautiluses.BABY_AMBIENT_LAND.get();
        }
        return this.isUnderWater() ? Nautiluses.NAUTILUS_AMBIENT.get() : Nautiluses.NAUTILUS_AMBIENT_LAND.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (this.isBaby()) {
            return this.isUnderWater() ? Nautiluses.BABY_HURT.get() : Nautiluses.BABY_HURT_LAND.get();
        }
        return this.isUnderWater() ? Nautiluses.NAUTILUS_HURT.get() : Nautiluses.NAUTILUS_HURT_LAND.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        if (this.isBaby()) {
            return this.isUnderWater() ? Nautiluses.BABY_DEATH.get() : Nautiluses.BABY_DEATH_LAND.get();
        }
        return this.isUnderWater() ? Nautiluses.NAUTILUS_DEATH.get() : Nautiluses.NAUTILUS_DEATH_LAND.get();
    }

    @Override
    protected SoundEvent getDashSound() {
        return this.isUnderWater() ? Nautiluses.NAUTILUS_DASH.get() : Nautiluses.NAUTILUS_DASH_LAND.get();
    }

    @Override
    protected SoundEvent getDashReadySound() {
        return this.isUnderWater() ? Nautiluses.NAUTILUS_DASH_READY.get() : Nautiluses.NAUTILUS_DASH_READY_LAND.get();
    }

    @Override
    protected void playEatingSound() {
        this.makeSound(this.isBaby() ? Nautiluses.BABY_EAT.get() : Nautiluses.NAUTILUS_EAT.get());
    }

    @Override
    protected SoundEvent getSwimSound() {
        return this.isBaby() ? Nautiluses.BABY_SWIM.get() : Nautiluses.NAUTILUS_SWIM.get();
    }

    @Override
    public int getMaxAirSupply() {
        return 300;
    }

    protected void handleAirSupply(ServerLevel level, int preTickAirSupply) {
        if (this.isAlive() && !this.isInWater()) {
            this.setAirSupply(preTickAirSupply - 1);
            if (this.getAirSupply() <= -20) {
                this.setAirSupply(0);
                this.hurt(this.damageSources().dryOut(), 2.0F);
            }
        } else {
            this.setAirSupply(300);
        }
    }

    @Override
    public void baseTick() {
        int airSupply = this.getAirSupply();
        super.baseTick();
        if (!this.isNoAi() && this.level() instanceof ServerLevel serverLevel) {
            this.handleAirSupply(serverLevel, airSupply);
        }
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isAggravated();
    }
}
