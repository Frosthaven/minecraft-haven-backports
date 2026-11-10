package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Husks;
import dev.frosthaven.havenbackports.husk.CamelHusk;
import dev.frosthaven.havenbackports.husk.Parched;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.x Husk.finalizeSpawn, the camel husk jockey (1.21.1's Husk has no finalizeSpawn of its own, so this adds the
 * override): after Zombie's, a naturally spawned husk with room for a camel husk (its spawn box free of collisions)
 * has a 10% chance to get an iron spear and ride a new camel husk, with a parched behind it. As in 26.x the group data
 * is wrapped per husk (HuskGroupData's triedToSpawnCamelHusk starts false only for NATURAL), so each natural husk rolls
 * once. 26.x also sets canPickUpLoot here; 1.21.1's Zombie.finalizeSpawn already does that (same roll), so it is not
 * repeated.
 */
@Mixin(Husk.class)
abstract class HuskMixin extends Zombie {
    private HuskMixin(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData groupData) {
        RandomSource random = level.getRandom();
        groupData = super.finalizeSpawn(level, difficulty, reason, groupData);
        boolean tried = reason != MobSpawnType.NATURAL;
        if (groupData instanceof Zombie.ZombieGroupData && !tried) {
            BlockPos pos = this.blockPosition();
            if (level.noCollision(Husks.CAMEL_HUSK.get().getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))
                    && random.nextFloat() < 0.1F) {
                this.setItemSlot(EquipmentSlot.MAINHAND,
                        new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("haven_backports", "iron_spear"))));
                CamelHusk camelHusk = Husks.CAMEL_HUSK.get().create(this.level());
                if (camelHusk != null) {
                    camelHusk.setPos(this.getX(), this.getY(), this.getZ());
                    camelHusk.finalizeSpawn(level, difficulty, reason, null);
                    this.startRiding(camelHusk, true);
                    level.addFreshEntity(camelHusk);
                    Parched parched = Husks.PARCHED.get().create(this.level());
                    if (parched != null) {
                        parched.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                        parched.finalizeSpawn(level, difficulty, reason, null);
                        parched.startRiding(camelHusk, false);
                        level.addFreshEntityWithPassengers(parched);
                    }
                }
            }
        }
        return groupData;
    }
}
