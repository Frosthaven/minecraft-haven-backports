package dev.frosthaven.havenbackports.mixin;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x's Mob.populateDefaultEquipmentSlots and getEquipmentForSlot: armor tiers leather, COPPER, gold, chainmail, iron,
 * diamond; the tier rolls nextInt(3) and three 0.1087 upgrade chances (1.21.1: nextInt(2), 0.095); slots filled head,
 * chest, legs, feet. Subclasses that call super (zombies, skeletons, piglins ...) get it as in 26.x.
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
}
