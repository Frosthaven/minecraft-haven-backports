package dev.frosthaven.havenbackports.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

/**
 * 26.x DarkOakTrunkPlacer, line for line. 26.x puts the side branches' leaves over the branches (the trunk's foot x and
 * z); 1.21.1's puts them over the leaned top. The port test found the difference (pale oak trees from 26.x's own data);
 * our pale oak trees use this one, vanilla dark oaks keep 1.21.1's.
 */
public class DarkOakTrunkPlacer extends TrunkPlacer {
    public static final MapCodec<DarkOakTrunkPlacer> CODEC = RecordCodecBuilder.mapCodec(i -> trunkPlacerParts(i).apply(i, DarkOakTrunkPlacer::new));

    public DarkOakTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    protected TrunkPlacerType<?> type() {
        return Worldgen.DARK_OAK_TRUNK_PLACER.get();
    }

    @Override
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> setter, RandomSource random,
            int treeHeight, BlockPos origin, TreeConfiguration tree) {
        List<FoliagePlacer.FoliageAttachment> attachments = new ArrayList<>();
        BlockPos below = origin.below();
        setDirtAt(level, setter, random, below, tree);
        setDirtAt(level, setter, random, below.east(), tree);
        setDirtAt(level, setter, random, below.south(), tree);
        setDirtAt(level, setter, random, below.south().east(), tree);
        Direction lean = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        int leanHeight = treeHeight - random.nextInt(4);
        int leanSteps = 2 - random.nextInt(3);
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        int tx = x;
        int tz = z;
        int ey = y + treeHeight - 1;
        for (int dy = 0; dy < treeHeight; dy++) {
            if (dy >= leanHeight && leanSteps > 0) {
                tx += lean.getStepX();
                tz += lean.getStepZ();
                leanSteps--;
            }
            BlockPos p = new BlockPos(tx, y + dy, tz);
            if (TreeFeature.isAirOrLeaves(level, p)) {
                this.placeLog(level, setter, random, p, tree);
                this.placeLog(level, setter, random, p.east(), tree);
                this.placeLog(level, setter, random, p.south(), tree);
                this.placeLog(level, setter, random, p.east().south(), tree);
            }
        }
        attachments.add(new FoliagePlacer.FoliageAttachment(new BlockPos(tx, ey, tz), 0, true));
        for (int ox = -1; ox <= 2; ox++) {
            for (int oz = -1; oz <= 2; oz++) {
                if ((ox < 0 || ox > 1 || oz < 0 || oz > 1) && random.nextInt(3) <= 0) {
                    int length = random.nextInt(3) + 2;
                    for (int b = 0; b < length; b++) {
                        this.placeLog(level, setter, random, new BlockPos(x + ox, ey - b - 1, z + oz), tree);
                    }
                    attachments.add(new FoliagePlacer.FoliageAttachment(new BlockPos(x + ox, ey, z + oz), 0, false));
                }
            }
        }
        return attachments;
    }
}
