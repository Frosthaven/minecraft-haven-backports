package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Variants;
import dev.frosthaven.havenbackports.variant.FarmVariant;
import dev.frosthaven.havenbackports.variant.VariantHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.Chicken;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 26.x Chicken's climate variant (chicken_variant: temperate, warm, cold): synced, saved, inherited; a warm chicken lays brown
 * eggs and a cold one blue eggs (26.x gameplay/chicken_lay; 1.21.1 hard-codes the egg).
 */
@Mixin(Chicken.class)
abstract class ChickenMixin implements VariantHolder<FarmVariant> {
    @Unique
    private static final EntityDataAccessor<Holder<FarmVariant>> HAVEN_BACKPORTS$VARIANT = SynchedEntityData.defineId(Chicken.class, Variants.CHICKEN_SERIALIZER);

    @Override
    public EntityDataAccessor<Holder<FarmVariant>> haven_backports$accessor() {
        return HAVEN_BACKPORTS$VARIANT;
    }

    @Override
    public ResourceKey<Registry<FarmVariant>> haven_backports$registry() {
        return Variants.CHICKEN;
    }

    @Override
    public String haven_backports$defaultName() {
        return "temperate";
    }

    @Override
    public String haven_backports$saveKey() {
        return "variant";
    }

    @Override
    public Holder<FarmVariant> haven_backports$variant() {
        return ((Chicken) (Object) this).getEntityData().get(HAVEN_BACKPORTS$VARIANT);
    }

    @Override
    public void haven_backports$setVariant(Holder<FarmVariant> variant) {
        ((Chicken) (Object) this).getEntityData().set(HAVEN_BACKPORTS$VARIANT, variant);
    }

    /** 26.x chicken_lay: egg for temperate, brown_egg for warm, blue_egg for cold. */
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "aiStep", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/animal/Chicken;spawnAtLocation(Lnet/minecraft/world/level/ItemLike;)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private net.minecraft.world.entity.item.ItemEntity haven_backports$layVariantEgg(Chicken self, net.minecraft.world.level.ItemLike egg,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<net.minecraft.world.entity.item.ItemEntity> original) {
        String variant = this.haven_backports$variant().unwrapKey().map(k -> k.location().getPath()).orElse("temperate");
        net.minecraft.world.level.ItemLike laid = switch (variant) {
            case "warm" -> Variants.BROWN_EGG.get();
            case "cold" -> Variants.BLUE_EGG.get();
            default -> egg;
        };
        return original.call(self, laid);
    }

    /** 26.x getBreedOffspring: the baby takes one parent's variant, at random. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "getBreedOffspring", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    @SuppressWarnings("unchecked")
    private void haven_backports$inheritVariant(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.AgeableMob partner,
                                                org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Chicken> cir) {
        if (cir.getReturnValue() instanceof VariantHolder<?> baby && partner instanceof VariantHolder<?> other) {
            boolean mine = ((Chicken) (Object) this).getRandom().nextBoolean();
            ((VariantHolder<FarmVariant>) baby).haven_backports$setVariant(mine ? this.haven_backports$variant() : ((VariantHolder<FarmVariant>) other).haven_backports$variant());
        }
    }
}
