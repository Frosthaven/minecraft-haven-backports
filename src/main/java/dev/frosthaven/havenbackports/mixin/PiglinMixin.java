package dev.frosthaven.havenbackports.mixin;

import com.google.common.collect.ImmutableList;
import dev.frosthaven.havenbackports.Spears;
import java.util.Collection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x Piglin: the spawn weapon {@code nextFloat() < 0.5 ? crossbow : (nextInt(10) == 0 ? golden spear : golden
 * sword)}, and the five spear memories in the brain. 26.x's Brain registers a behavior's required memories
 * (spear_status) itself; 1.21.1's ignores memories missing from the provider list, so all five are listed.
 */
@Mixin(Piglin.class)
abstract class PiglinMixin extends AbstractPiglin {
    private PiglinMixin(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
    }

    @Inject(method = "createSpawnWeapon", at = @At("HEAD"), cancellable = true)
    private void haven_backports$weapon(CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(this.random.nextFloat() < 0.5 ? new ItemStack(Items.CROSSBOW) : new ItemStack(this.random.nextInt(10) == 0
                ? BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("haven_backports", "golden_spear")) : Items.GOLDEN_SWORD));
    }

    @ModifyArg(method = "brainProvider", index = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/Brain;provider(Ljava/util/Collection;Ljava/util/Collection;)Lnet/minecraft/world/entity/ai/Brain$Provider;"))
    private Collection<? extends MemoryModuleType<?>> haven_backports$spearMemories(Collection<? extends MemoryModuleType<?>> memories) {
        return ImmutableList.<MemoryModuleType<?>>builder().addAll(memories).add(Spears.SPEAR_FLEEING_TIME.get(), Spears.SPEAR_FLEEING_POSITION.get(),
                Spears.SPEAR_CHARGE_POSITION.get(), Spears.SPEAR_ENGAGE_TIME.get(), Spears.SPEAR_STATUS.get()).build();
    }
}
