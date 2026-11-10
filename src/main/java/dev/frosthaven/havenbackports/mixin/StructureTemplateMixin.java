package dev.frosthaven.havenbackports.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.frosthaven.havenbackports.HavenBackports;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.x StructureTemplate.placeEntities stores the placed block in a template entity's block_pos before it creates the
 * entity, so a block-attached entity (the abandoned camp's cushions) loads attached to the block it is placed on;
 * 1.21.1 keeps the position it was saved at and logs "Block-attached entity at invalid position". Here 26.x's step on
 * 1.21.1's spelling of block_pos (TileX, TileY, TileZ), for our entities only: other mods' templates keep 1.21.1's way.
 */
@Mixin(StructureTemplate.class)
abstract class StructureTemplateMixin {
    @WrapOperation(method = "addEntitiesToWorld", require = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate;createEntityIgnoreException(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/nbt/CompoundTag;)Ljava/util/Optional;"))
    private static Optional<Entity> haven_backports$blockPos(ServerLevelAccessor level, CompoundTag tag, Operation<Optional<Entity>> original,
                                                             @Local StructureTemplate.StructureEntityInfo info) {
        if (tag.contains("TileX") && tag.getString("id").startsWith(HavenBackports.ID + ":")) {
            BlockPos pos = info.blockPos;
            tag.putInt("TileX", pos.getX());
            tag.putInt("TileY", pos.getY());
            tag.putInt("TileZ", pos.getZ());
        }
        return original.call(level, tag);
    }
}
