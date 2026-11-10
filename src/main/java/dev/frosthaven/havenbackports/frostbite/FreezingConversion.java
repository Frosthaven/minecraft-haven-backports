package dev.frosthaven.havenbackports.frostbite;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;

/**
 * 26.x Zombie's freezingTracker (a ConversionTracker): 140 ticks in powder snow without freeze-proof wear start a 300
 * tick conversion (the zombie shakes), then a zombie becomes a frostbite, a husk or a drowned becomes a zombie, each
 * with its sound (26.x level events 1056, 1041 and 1058, played here as the same sounds). Leaving the snow ends it.
 * 26.x lets every Zombie subclass convert unless it opts out (frostbite, zombie villager, zombified piglin); this
 * covers the three vanilla types 26.x converts, so other mods' zombies keep their 1.21.1 behaviour.
 * The state lives on the zombie (FrostbiteZombieMixin, the synced flag and the save keys 26.x uses).
 */
public final class FreezingConversion {
    public static final int TOTAL_AFFLICTION_TIME = 140;
    public static final int TOTAL_CONVERSION_TIME = 300;

    /** What FrostbiteZombieMixin adds to Zombie. */
    public interface Host {
        int haven_backports$afflictionTime();

        void haven_backports$setAfflictionTime(int ticks);

        int haven_backports$conversionTime();

        void haven_backports$setConversionTime(int ticks);

        boolean haven_backports$isFreezingConverting();

        void haven_backports$setFreezingConverting(boolean converting);

        void haven_backports$convertTo(EntityType<? extends Zombie> type);
    }

    private FreezingConversion() {}

    /** Zombie.convertsToWhenFreezing for the types that convert (convertsWhenFreezing), else null. */
    @Nullable
    static EntityType<? extends Zombie> target(Zombie zombie) {
        EntityType<?> type = zombie.getType();
        if (type == EntityType.ZOMBIE) {
            return FrostbiteContent.FROSTBITE.get();
        }
        return type == EntityType.HUSK || type == EntityType.DROWNED ? EntityType.ZOMBIE : null;
    }

    /** LivingEntity.isWearingFreezeImmuneWearables. */
    static boolean afflicted(Zombie zombie) {
        if (!zombie.isInPowderSnow) {
            return false;
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.BODY}) {
            if (zombie.getItemBySlot(slot).is(ItemTags.FREEZE_IMMUNE_WEARABLES)) {
                return false;
            }
        }
        return true;
    }

    /** ConversionTracker.tick, after the zombie's own tick. */
    public static void tick(Zombie zombie) {
        Host host = (Host) zombie;
        EntityType<? extends Zombie> to = target(zombie);
        if (to == null || !(zombie.level() instanceof ServerLevel) || !zombie.isAlive() || zombie.isNoAi()) {
            return;
        }
        if (afflicted(zombie)) {
            if (host.haven_backports$isFreezingConverting()) {
                host.haven_backports$setConversionTime(host.haven_backports$conversionTime() - 1);
                if (host.haven_backports$conversionTime() < 0) {
                    convert(zombie, host, to);
                }
            } else {
                host.haven_backports$setAfflictionTime(host.haven_backports$afflictionTime() + 1);
                if (host.haven_backports$afflictionTime() >= TOTAL_AFFLICTION_TIME) {
                    start(host, TOTAL_CONVERSION_TIME);
                }
            }
        } else {
            host.haven_backports$setAfflictionTime(-1);
            host.haven_backports$setFreezingConverting(false);
        }
    }

    private static void start(Host host, int time) {
        host.haven_backports$setConversionTime(time);
        host.haven_backports$setFreezingConverting(true);
    }

    private static void convert(Zombie zombie, Host host, EntityType<? extends Zombie> to) {
        if (!net.neoforged.neoforge.event.EventHooks.canLivingConvert(zombie, to, host::haven_backports$setConversionTime)) {
            return;
        }
        boolean silent = zombie.isSilent();
        host.haven_backports$convertTo(to);   // 1.21.1 Zombie.convertToZombieType: equipment kept, attributes rolled
        if (!silent) {
            RandomSource random = zombie.getRandom();
            EntityType<?> from = zombie.getType();
            SoundEvent sound;
            float pitch;
            if (from == EntityType.ZOMBIE) {
                sound = FrostbiteContent.ZOMBIE_CONVERTED_TO_FROSTBITE.get();
                pitch = random.nextFloat() * 0.1F + 0.9F;
            } else {
                sound = from == EntityType.HUSK ? SoundEvents.HUSK_CONVERTED_TO_ZOMBIE : FrostbiteContent.DROWNED_CONVERTED_TO_ZOMBIE.get();
                pitch = (random.nextFloat() - random.nextFloat()) * 0.2F + 1.0F;
            }
            zombie.level().playSound(null, zombie.blockPosition(), sound, SoundSource.HOSTILE, 2.0F, pitch);
        }
    }

    /** ConversionTracker.addAdditionalSaveData. */
    public static void save(Zombie zombie, CompoundTag tag) {
        if (target(zombie) == null) {
            return;
        }
        Host host = (Host) zombie;
        tag.putInt("FrostbiteConversionTime", host.haven_backports$isFreezingConverting() ? host.haven_backports$conversionTime() : -1);
        tag.putInt("FreezingTime", afflicted(zombie) ? host.haven_backports$afflictionTime() : -1);
    }

    /** ConversionTracker.readAdditionalSaveData. */
    public static void load(Zombie zombie, CompoundTag tag) {
        if (target(zombie) == null) {
            return;
        }
        Host host = (Host) zombie;
        host.haven_backports$setAfflictionTime(tag.contains("FreezingTime") ? tag.getInt("FreezingTime") : 0);
        int conversionTime = tag.contains("FrostbiteConversionTime") ? tag.getInt("FrostbiteConversionTime") : -1;
        if (conversionTime != -1) {
            start(host, conversionTime);
        } else {
            host.haven_backports$setFreezingConverting(false);
        }
    }
}
