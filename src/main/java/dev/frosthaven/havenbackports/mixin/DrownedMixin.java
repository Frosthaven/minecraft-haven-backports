package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Nautiluses;
import dev.frosthaven.havenbackports.nautilus.ZombieNautilus;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x Drowned.finalizeSpawn, the zombie nautilus jockey (after 1.21.1's nautilus shell roll, as in 26.x): a natural or
 * structure drowned holding a trident, not a baby and not in #more_frequent_drowned_spawns (rivers), rides a new zombie
 * nautilus half of the time (a structure one is persistent).
 */
@Mixin(Drowned.class)
abstract class DrownedMixin extends Zombie {
    private DrownedMixin(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void haven_backports$zombieNautilusJockey(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                                     SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> cir) {
        if ((reason == MobSpawnType.NATURAL || reason == MobSpawnType.STRUCTURE) && this.getMainHandItem().is(Items.TRIDENT)
                && level.getRandom().nextFloat() < 0.5F && !this.isBaby() && !level.getBiome(this.blockPosition()).is(BiomeTags.MORE_FREQUENT_DROWNED_SPAWNS)) {
            ZombieNautilus nautilus = Nautiluses.ZOMBIE_NAUTILUS.get().create(this.level());
            if (nautilus != null) {
                if (reason == MobSpawnType.STRUCTURE) {
                    nautilus.setPersistenceRequired();
                }
                nautilus.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                nautilus.finalizeSpawn(level, difficulty, reason, null);
                this.startRiding(nautilus, false);
                level.addFreshEntity(nautilus);
            }
        }
    }
}
