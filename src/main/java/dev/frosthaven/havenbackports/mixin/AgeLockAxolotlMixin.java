package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.agelock.AgeLock;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.x Axolotl bucket data: "AgeLocked" goes into the bucket and back, so a locked baby stays locked. */
@Mixin(Axolotl.class)
abstract class AgeLockAxolotlMixin {
    @Inject(method = "saveToBucketTag", at = @At("TAIL"))
    private void haven_backports$saveBucket(ItemStack stack, CallbackInfo ci) {
        boolean locked = AgeLock.isAgeLocked((Axolotl) (Object) this);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> tag.putBoolean("AgeLocked", locked));
    }

    @Inject(method = "loadFromBucketTag", at = @At("TAIL"))
    private void haven_backports$loadBucket(CompoundTag tag, CallbackInfo ci) {
        ((AgeLock.Lockable) this).haven_backports$setAgeLocked(tag.getBoolean("AgeLocked"));
    }
}
