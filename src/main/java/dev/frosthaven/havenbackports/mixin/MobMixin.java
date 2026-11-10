package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.frosthaven.havenbackports.Spears;
import dev.frosthaven.havenbackports.spear.SpearItem;
import dev.frosthaven.havenbackports.spear.Stab;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x's Mob.populateDefaultEquipmentSlots and getEquipmentForSlot: armor tiers leather, COPPER, gold, chainmail, iron,
 * diamond; the tier rolls nextInt(3) and three 0.1087 upgrade chances (1.21.1: nextInt(2), 0.095); slots filled head,
 * chest, legs, feet. Subclasses that call super (zombies, skeletons, piglins ...) get it as in 26.x.
 * <p>Spears (26.x Mob): doHurtTarget ends with postPiercingAttack (Lunge on a mob's spear); isWithinMeleeAttackRange
 * uses the active item's attack range (a spear: mob reach 1.0..2.25, the near end excluded); drowned and skeletons do
 * not pick spears up (26.x Drowned and AbstractSkeleton.wantsToPickUp; 1.21.1's Zombie override calls this one).
 */
@Mixin(Mob.class)
abstract class MobMixin {
    private static final List<EquipmentSlot> HAVEN_BACKPORTS$ORDER = List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final String[] HAVEN_BACKPORTS$TIERS = {"minecraft:leather", "haven_backports:copper", "minecraft:golden",
            "minecraft:chainmail", "minecraft:iron", "minecraft:diamond"};

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("HEAD"), cancellable = true)
    private void haven_backports$equipment(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        ci.cancel();
        Mob self = (Mob) (Object) this;
        if (random.nextFloat() < 0.15F * difficulty.getSpecialMultiplier()) {
            int armorType = random.nextInt(3);
            for (int i = 1; i <= 3.0F; i++) {
                if (random.nextFloat() < 0.1087F) {
                    armorType++;
                }
            }
            float partialChance = self.level().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
            boolean first = true;
            for (EquipmentSlot slot : HAVEN_BACKPORTS$ORDER) {
                ItemStack current = self.getItemBySlot(slot);
                if (!first && random.nextFloat() < partialChance) {
                    break;
                }
                first = false;
                if (current.isEmpty()) {
                    Item equip = haven_backports$forSlot(slot, armorType);
                    if (equip != null) {
                        self.setItemSlot(slot, new ItemStack(equip));
                    }
                }
            }
        }
    }

    private static Item haven_backports$forSlot(EquipmentSlot slot, int type) {
        if (type < 0 || type >= HAVEN_BACKPORTS$TIERS.length) {
            return null;
        }
        String piece = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
        return piece == null ? null : BuiltInRegistries.ITEM.get(ResourceLocation.parse(HAVEN_BACKPORTS$TIERS[type] + "_" + piece));
    }

    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void haven_backports$postPiercing(Entity target, CallbackInfoReturnable<Boolean> cir) {
        Stab.postPiercingAttack((Mob) (Object) this);
    }

    @Inject(method = "isWithinMeleeAttackRange", at = @At("HEAD"), cancellable = true)
    private void haven_backports$spearRange(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        ItemStack active = self.isUsingItem() ? self.getUseItem() : self.getMainHandItem();
        if (active.getItem() instanceof SpearItem) {
            double maxRange = SpearItem.ATTACK_RANGE.effectiveMaxRange(self);
            double minRange = SpearItem.ATTACK_RANGE.effectiveMinRange(self);
            AABB hitbox = target.getHitbox();
            cir.setReturnValue(haven_backports$attackBox(self, maxRange).intersects(hitbox)
                    && (minRange <= 0.0 || !haven_backports$attackBox(self, minRange).intersects(hitbox)));
        }
    }

    /** 26.x Mob.getAttackBoundingBox(horizontalExpansion); 1.21.1's fixes the expansion at the default reach. */
    private static AABB haven_backports$attackBox(Mob self, double horizontalExpansion) {
        Entity vehicle = self.getVehicle();
        AABB own = self.getBoundingBox();
        AABB aabb = own;
        if (vehicle != null) {
            AABB mount = vehicle.getBoundingBox();
            aabb = new AABB(Math.min(own.minX, mount.minX), own.minY, Math.min(own.minZ, mount.minZ),
                    Math.max(own.maxX, mount.maxX), own.maxY, Math.max(own.maxZ, mount.maxZ));
        }
        return aabb.inflate(horizontalExpansion, 0.0, horizontalExpansion);
    }

    @ModifyReturnValue(method = "wantsToPickUp", at = @At("RETURN"))
    private boolean haven_backports$noSpears(boolean wants, ItemStack stack) {
        return wants && !((((Object) this) instanceof Drowned || ((Object) this) instanceof AbstractSkeleton) && stack.is(Spears.SPEARS));
    }
}
