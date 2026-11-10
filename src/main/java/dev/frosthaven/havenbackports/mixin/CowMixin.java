package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Variants;
import dev.frosthaven.havenbackports.variant.FarmVariant;
import dev.frosthaven.havenbackports.variant.VariantHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.animal.Cow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 26.x Cow's climate variant (cow_variant: temperate, warm, cold): synced, saved, inherited from a parent. 1.21.1's mooshroom
 * extends Cow and carries the value unused (26.x's mooshroom is no Cow); spawning skips it.
 */
@Mixin(Cow.class)
abstract class CowMixin implements VariantHolder<FarmVariant> {
    @Unique
    private static final EntityDataAccessor<Holder<FarmVariant>> HAVEN_BACKPORTS$VARIANT = SynchedEntityData.defineId(Cow.class, Variants.COW_SERIALIZER);

    @Override
    public EntityDataAccessor<Holder<FarmVariant>> haven_backports$accessor() {
        return HAVEN_BACKPORTS$VARIANT;
    }

    @Override
    public ResourceKey<Registry<FarmVariant>> haven_backports$registry() {
        return Variants.COW;
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
        return ((Cow) (Object) this).getEntityData().get(HAVEN_BACKPORTS$VARIANT);
    }

    @Override
    public void haven_backports$setVariant(Holder<FarmVariant> variant) {
        ((Cow) (Object) this).getEntityData().set(HAVEN_BACKPORTS$VARIANT, variant);
    }

    /** 26.x getBreedOffspring: the baby takes one parent's variant, at random. */
    @org.spongepowered.asm.mixin.injection.Inject(method = "getBreedOffspring", at = @org.spongepowered.asm.mixin.injection.At("RETURN"))
    @SuppressWarnings("unchecked")
    private void haven_backports$inheritVariant(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.AgeableMob partner,
                                                org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Cow> cir) {
        if (cir.getReturnValue() instanceof VariantHolder<?> baby && partner instanceof VariantHolder<?> other) {
            boolean mine = ((Cow) (Object) this).getRandom().nextBoolean();
            ((VariantHolder<FarmVariant>) baby).haven_backports$setVariant(mine ? this.haven_backports$variant() : ((VariantHolder<FarmVariant>) other).haven_backports$variant());
        }
    }
}
