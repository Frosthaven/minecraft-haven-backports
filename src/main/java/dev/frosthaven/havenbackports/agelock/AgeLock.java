package dev.frosthaven.havenbackports.agelock;

import dev.frosthaven.havenbackports.Content;
import dev.frosthaven.havenbackports.HavenBackports;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 26.x's golden dandelion age lock (AgeableMob.canUseGoldenDandelion, setAgeLocked, makeAgeLockedParticle,
 * canAgeUp): a baby fed a golden dandelion stops growing up (and stays loaded: persistence required); a second one
 * lets it grow again. Either way its age goes back to a newborn's. 1.21.1 has no such lock, so the AgeableMob and
 * Tadpole mixins carry the state ({@link Lockable}: synced data and the "AgeLocked" save key, as 26.x) and the
 * AgeLock* mixins put the checks where 26.x reads isAgeLocked or canAgeUp.
 */
public final class AgeLock {
    /** The item by id: Flora registers it (no hard reference, so either class loads first). */
    public static final ResourceLocation GOLDEN_DANDELION = ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "golden_dandelion");
    /** 26.x EntityTypeTags.CANNOT_BE_AGE_LOCKED (zombie horse, skeleton horse, villager). */
    public static final TagKey<EntityType<?>> CANNOT_BE_AGE_LOCKED =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("cannot_be_age_locked"));
    /** 26.x AgeableMob.AGE_LOCK_COOLDOWN_TICKS: the particle time, and the wait before the next toggle. */
    public static final int COOLDOWN_TICKS = 40;
    /** 26.x AGE_LOCK_DOWNWARDS_MOVING_PARTICLE_Y_OFFSET. */
    private static final float LOCKED_PARTICLE_Y_OFFSET = 0.2F;

    public static final DeferredHolder<SoundEvent, SoundEvent> USE = Content.sound("item.golden_dandelion.use");
    public static final DeferredHolder<SoundEvent, SoundEvent> UNUSE = Content.sound("item.golden_dandelion.unuse");
    /** 26.x ParticleTypes.PAUSE_MOB_GROWTH and RESET_MOB_GROWTH (SimpleVerticalParticle down and up). */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> PAUSE_MOB_GROWTH =
            Content.PARTICLES.register("pause_mob_growth", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> RESET_MOB_GROWTH =
            Content.PARTICLES.register("reset_mob_growth", () -> new SimpleParticleType(false));

    /** The lock state the mixins add to AgeableMob and Tadpole (26.x fields of those classes). */
    public interface Lockable {
        boolean haven_backports$isAgeLocked();

        void haven_backports$setAgeLocked(boolean locked);

        int haven_backports$lockTimer();

        void haven_backports$setLockTimer(int ticks);
    }

    private AgeLock() {}

    /** Loads the registrations (called from the mod constructor). */
    public static void register() {}

    public static boolean isGoldenDandelion(ItemStack stack) {
        return !stack.isEmpty() && stack.getItemHolder().is(GOLDEN_DANDELION);
    }

    public static boolean isAgeLocked(Entity entity) {
        return entity instanceof Lockable lockable && lockable.haven_backports$isAgeLocked();
    }

    /** 26.x AgeableMob.canAgeUp: a baby whose age is not locked. */
    public static boolean canAgeUp(AgeableMob mob) {
        return mob.isBaby() && !isAgeLocked(mob);
    }

    /** 26.x AgeableMob.canUseGoldenDandelion. */
    public static boolean canUseGoldenDandelion(ItemStack itemInHand, boolean isBaby, int cooldown, Mob mob) {
        return isGoldenDandelion(itemInHand) && isBaby && cooldown == 0 && !mob.getType().is(CANNOT_BE_AGE_LOCKED);
    }

    /** 26.x AgeableMob.setAgeLocked(mob, isAgeLocked, player, item, setAgeLockData). */
    public static void setAgeLocked(Mob mob, BooleanSupplier isAgeLocked, Player player, ItemStack itemInHand, Consumer<Mob> setAgeLockData) {
        setAgeLockData.accept(mob);
        itemInHand.consume(1, player);
        boolean locked = isAgeLocked.getAsBoolean();
        if (locked) {
            mob.setPersistenceRequired();
        }
        mob.level().playSound(null, mob.blockPosition(), locked ? USE.get() : UNUSE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** 26.x AgeableMob.makeAgeLockedParticle: one particle every 2 ticks while the timer runs, on the server. */
    public static int makeAgeLockedParticle(Level level, Mob mob, int timer, boolean isAgeLocked) {
        if (timer > 0) {
            if (timer % 2 == 0 && level instanceof ServerLevel server) {
                float yOffset = isAgeLocked ? LOCKED_PARTICLE_Y_OFFSET : 0.0F;
                double x = mob.getRandomX(1.0);
                // 26.x getRandomY(0.2): getY((2 * random - 1) * 0.2); 1.21.1 has getY(scale) only
                double y = mob.getY((2.0 * mob.getRandom().nextDouble() - 1.0) * 0.2) + mob.getBbHeight() + yOffset;
                double z = mob.getRandomZ(1.0);
                server.sendParticles(isAgeLocked ? PAUSE_MOB_GROWTH.get() : RESET_MOB_GROWTH.get(), x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            timer--;
        }
        return timer;
    }

    /**
     * 26.x AgeableMob.mobInteract: a golden dandelion on a lockable baby toggles its lock; otherwise null (the caller
     * goes on as 1.21.1). 26.x setAgeLockedData: toggle, age back to a newborn's, particles for 40 ticks.
     */
    public static InteractionResult interact(AgeableMob mob, Player player, InteractionHand hand) {
        ItemStack itemInHand = player.getItemInHand(hand);
        Lockable lock = (Lockable) mob;
        if (!canUseGoldenDandelion(itemInHand, mob.isBaby(), lock.haven_backports$lockTimer(), mob)) {
            return null;
        }
        setAgeLocked(mob, lock::haven_backports$isAgeLocked, player, itemInHand, m -> {
            lock.haven_backports$setAgeLocked(!lock.haven_backports$isAgeLocked());
            mob.setAge(AgeableMob.BABY_START_AGE);   // 26.x getBabyStartAge (1.21.1: the constant; no mob overrides it)
            lock.haven_backports$setLockTimer(COOLDOWN_TICKS);
        });
        return InteractionResult.SUCCESS;
    }
}
