package dev.frosthaven.havenbackports.creaking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x LivingEntity gaze helpers the creaking reads; 1.21.1 has none of them (its enderman keeps a private check).
 * Kept here, not in a mixin: only the creaking uses them.
 */
final class Gaze26 {
    /** 26.x ItemTags.GAZE_DISGUISE_EQUIPMENT (data: carved_pumpkin). */
    static final TagKey<Item> GAZE_DISGUISE_EQUIPMENT = ItemTags.create(ResourceLocation.withDefaultNamespace("gaze_disguise_equipment"));

    private Gaze26() {}

    /** 26.x LivingEntity.PLAYER_NOT_WEARING_DISGUISE_ITEM. */
    static boolean notWearingDisguise(LivingEntity entity) {
        return !(entity instanceof Player player) || !player.getItemBySlot(EquipmentSlot.HEAD).is(GAZE_DISGUISE_EQUIPMENT);
    }

    /** 26.x LivingEntity.isLookingAtMe(target, coneSize, adjustForDistance, seeThroughTransparentBlocks, gazeHeights). */
    static boolean isLookingAtMe(LivingEntity self, LivingEntity target, double coneSize, boolean adjustForDistance,
                                 boolean seeThroughTransparentBlocks, double... gazeHeights) {
        Vec3 look = target.getViewVector(1.0F).normalize();
        for (double gazeHeight : gazeHeights) {
            Vec3 dir = new Vec3(self.getX() - target.getX(), gazeHeight - target.getEyeY(), self.getZ() - target.getZ());
            double dist = dir.length();
            dir = dir.normalize();
            double dot = look.dot(dir);
            if (dot > 1.0 - coneSize / (adjustForDistance ? dist : 1.0)
                    && hasLineOfSight(target, self, seeThroughTransparentBlocks ? ClipContext.Block.VISUAL : ClipContext.Block.COLLIDER, gazeHeight)) {
                return true;
            }
        }
        return false;
    }

    /** 26.x LivingEntity.hasLineOfSight(target, block, fluid NONE, eyeHeight): from the viewer's eyes to a set height. */
    private static boolean hasLineOfSight(LivingEntity viewer, LivingEntity target, ClipContext.Block block, double eyeHeight) {
        if (target.level() != viewer.level()) {
            return false;
        }
        Vec3 from = new Vec3(viewer.getX(), viewer.getEyeY(), viewer.getZ());
        Vec3 to = new Vec3(target.getX(), eyeHeight, target.getZ());
        return to.distanceTo(from) <= 128.0
                && viewer.level().clip(new ClipContext(from, to, block, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
    }
}
