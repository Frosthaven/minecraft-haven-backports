package dev.frosthaven.havenbackports.nautilus;

import com.mojang.serialization.Dynamic;
import dev.frosthaven.havenbackports.Nautiluses;
import dev.frosthaven.havenbackports.Variants;
import dev.frosthaven.havenbackports.variant.FarmVariant;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x ZombieNautilus: faster (1.1), never a baby, never breeds, its variant from zombie_nautilus_variant
 * (temperate; warm with corals in #spawns_coral_variant_zombie_nautilus), burns in daylight unless its body armor
 * blocks the sun (26.x #burn_in_daylight and sunProtectionSlot BODY: 1.21.1 has no such tag, so aiStep carries Mojang's
 * Mob.burnUndead for this mob). The variant registry has the farm variants' shape (FarmVariant); 26.x's spawn-egg and
 * item component for it does not exist in 1.21.1. canBeABaby false = isBaby false.
 */
public class ZombieNautilus extends AbstractNautilus {
    private static final EntityDataAccessor<Holder<FarmVariant>> DATA_VARIANT =
            SynchedEntityData.defineId(ZombieNautilus.class, Nautiluses.VARIANT_SERIALIZER);

    public ZombieNautilus(EntityType<? extends ZombieNautilus> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractNautilus.createAttributes().add(Attributes.MOVEMENT_SPEED, 1.1F);
    }

    @Nullable
    @Override
    public ZombieNautilus getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.isSunBurnTick()) {
            ItemStack sunBlocker = this.getItemBySlot(EquipmentSlot.BODY);
            if (!sunBlocker.isEmpty()) {
                if (sunBlocker.isDamageableItem()) {
                    sunBlocker.hurtAndBreak(this.random.nextInt(2), this, EquipmentSlot.BODY);
                }
            } else {
                this.igniteForSeconds(8.0F);
            }
        }
    }

    @Override
    protected Brain.Provider<ZombieNautilus> brainProvider() {
        return Brain.provider(NautilusAi.MEMORIES, NautilusAi.SENSORS);
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return NautilusAi.makeBrain(this.brainProvider().makeBrain(dynamic), true);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Brain<ZombieNautilus> getBrain() {
        return (Brain<ZombieNautilus>) super.getBrain();
    }

    @Override
    protected void customServerAiStep() {
        this.level().getProfiler().push("zombieNautilusBrain");
        this.getBrain().tick((ServerLevel) this.level(), this);
        this.level().getProfiler().pop();
        this.level().getProfiler().push("zombieNautilusActivityUpdate");
        NautilusAi.updateActivity(this);
        this.level().getProfiler().pop();
        super.customServerAiStep();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return this.isUnderWater() ? Nautiluses.ZOMBIE_AMBIENT.get() : Nautiluses.ZOMBIE_AMBIENT_LAND.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return this.isUnderWater() ? Nautiluses.ZOMBIE_HURT.get() : Nautiluses.ZOMBIE_HURT_LAND.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return this.isUnderWater() ? Nautiluses.ZOMBIE_DEATH.get() : Nautiluses.ZOMBIE_DEATH_LAND.get();
    }

    @Override
    protected SoundEvent getDashSound() {
        return this.isUnderWater() ? Nautiluses.ZOMBIE_DASH.get() : Nautiluses.ZOMBIE_DASH_LAND.get();
    }

    @Override
    protected SoundEvent getDashReadySound() {
        return this.isUnderWater() ? Nautiluses.ZOMBIE_DASH_READY.get() : Nautiluses.ZOMBIE_DASH_READY_LAND.get();
    }

    @Override
    protected void playEatingSound() {
        this.makeSound(Nautiluses.ZOMBIE_EAT.get());
    }

    @Override
    protected SoundEvent getSwimSound() {
        return Nautiluses.ZOMBIE_SWIM.get();
    }

    @Override
    public SoundEvent getRidingSound() {
        return Nautiluses.ZOMBIE_RIDING.get();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VARIANT, Variants.defaultOrAny(this.registryAccess(), Nautiluses.VARIANTS, "temperate"));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("variant"));
        if (id != null) {
            this.registryAccess().registryOrThrow(Nautiluses.VARIANTS).getHolder(id).ifPresent(h -> this.setVariant(h));
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.getVariant().unwrapKey().ifPresent(k -> tag.putString("variant", k.location().toString()));
    }

    public void setVariant(Holder<FarmVariant> variant) {
        this.entityData.set(DATA_VARIANT, variant);
    }

    public Holder<FarmVariant> getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData groupData) {
        FarmVariant.select(level, this.blockPosition(), Nautiluses.VARIANTS, level.getRandom()).ifPresent(this::setVariant);
        return super.finalizeSpawn(level, difficulty, reason, groupData);
    }

    @Override
    public boolean canBeLeashed() {
        return !this.isAggravated() && !this.isMobControlled();
    }

    @Override
    public boolean isBaby() {
        return false;
    }

    @Override
    public void setBaby(boolean baby) {
    }
}
