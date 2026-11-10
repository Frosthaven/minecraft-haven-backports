package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Variants;
import dev.frosthaven.havenbackports.variant.VariantHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.AgeableMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The 26.x variant the pig, cow, chicken and wolf carry (VariantHolder, from their own mixins): defined with the
 * default variant, saved under 26.x's key and read back (26.x VariantUtils.writeVariant / readVariant). AgeableMob is
 * the class all four inherit these methods from (1.21.1's cow and chicken declare none of their own).
 */
@Mixin(AgeableMob.class)
abstract class AgeableMobMixin {
    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void haven_backports$defineVariant(SynchedEntityData.Builder builder, CallbackInfo ci) {
        if (this instanceof VariantHolder<?> holder) {
            define(holder, builder);
        }
    }

    private static <T> void define(VariantHolder<T> holder, SynchedEntityData.Builder builder) {
        builder.define(holder.haven_backports$accessor(), Variants.defaultOrAny(((AgeableMob) holder).level().registryAccess(),
                holder.haven_backports$registry(), holder.haven_backports$defaultName()));
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void haven_backports$saveVariant(CompoundTag tag, CallbackInfo ci) {
        if (this instanceof VariantHolder<?> holder) {
            holder.haven_backports$variant().unwrapKey().ifPresent(k -> tag.putString(holder.haven_backports$saveKey(), k.location().toString()));
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void haven_backports$readVariant(CompoundTag tag, CallbackInfo ci) {
        if (this instanceof VariantHolder<?> holder) {
            read(holder, tag);
        }
    }

    private static <T> void read(VariantHolder<T> holder, CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(holder.haven_backports$saveKey()));
        if (id != null) {
            ((AgeableMob) holder).level().registryAccess().registryOrThrow(holder.haven_backports$registry())
                    .getHolder(ResourceKey.create(holder.haven_backports$registry(), id)).ifPresent(holder::haven_backports$setVariant);
        }
    }
}
