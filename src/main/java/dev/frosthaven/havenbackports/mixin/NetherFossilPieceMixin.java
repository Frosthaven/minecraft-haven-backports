package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Ghasts;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilPieces;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.x NetherFossilPiece.postProcess ends with placeDriedGhast: for half the fossils (a positional random from the world
 * seed at the fossil's centre) a dried ghast lies on a random air block of the fossil's bottom layer, in a random
 * rotation, placed only inside the chunk being generated. 1.21.1's postProcess is the same without that call, so it
 * runs at its tail, verbatim. 1.21.1's RandomSource.create(seed).forkPositional() is 26.x's
 * createThreadLocalInstance(seed).forkPositional() (the same legacy generator); the box 1.21.1 encapsulates in place
 * is 26.x's chunkBB.
 */
@Mixin(NetherFossilPieces.NetherFossilPiece.class)
abstract class NetherFossilPieceMixin extends TemplateStructurePiece {
    private NetherFossilPieceMixin(StructurePieceType type, int genDepth, StructureTemplateManager manager, net.minecraft.resources.ResourceLocation location,
                                   String templateName, StructurePlaceSettings settings, BlockPos pos) {
        super(type, genDepth, manager, location, templateName, settings, pos);
    }

    @Inject(method = "postProcess", at = @At("TAIL"))
    private void haven_backports$placeDriedGhast(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                                                 BoundingBox chunkBB, ChunkPos chunkPos, BlockPos referencePos, CallbackInfo ci) {
        BoundingBox fossilBB = this.template.getBoundingBox(this.placeSettings, this.templatePosition);
        RandomSource positional = RandomSource.create(level.getSeed()).forkPositional().at(fossilBB.getCenter());
        if (positional.nextFloat() < 0.5F) {
            int x = fossilBB.minX() + positional.nextInt(fossilBB.getXSpan());
            int y = fossilBB.minY();
            int z = fossilBB.minZ() + positional.nextInt(fossilBB.getZSpan());
            BlockPos pos = new BlockPos(x, y, z);
            if (level.getBlockState(pos).isAir() && chunkBB.isInside(pos)) {
                level.setBlock(pos, Ghasts.DRIED_GHAST.get().defaultBlockState().rotate(Rotation.getRandom(positional)), 2);
            }
        }
    }
}
