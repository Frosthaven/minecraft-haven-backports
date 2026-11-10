package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.spear.SpearUseGoal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x ZombifiedPiglin: SpearUseGoal(this, 1.0, 1.0, 10, 2) at priority 1 (ZombieAttackGoal stays at 2), and the
 *  spawn weapon {@code nextInt(20) == 0 ? golden spear : golden sword} (1.21.1: always the sword). */
@Mixin(ZombifiedPiglin.class)
abstract class ZombifiedPiglinMixin extends Monster {
    private ZombifiedPiglinMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = "addBehaviourGoals", at = @At("HEAD"))
    private void haven_backports$spearGoal(CallbackInfo ci) {
        this.goalSelector.addGoal(1, new SpearUseGoal<>((ZombifiedPiglin) (Object) this, 1.0, 1.0, 10.0F, 2.0F));
    }

    @Inject(method = "populateDefaultEquipmentSlots", at = @At("HEAD"), cancellable = true)
    private void haven_backports$weapon(RandomSource random, DifficultyInstance difficulty, CallbackInfo ci) {
        ci.cancel();
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(random.nextInt(20) == 0
                ? BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("haven_backports", "golden_spear")) : Items.GOLDEN_SWORD));
    }
}
