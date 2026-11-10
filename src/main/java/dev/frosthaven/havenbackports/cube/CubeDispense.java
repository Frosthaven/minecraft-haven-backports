package dev.frosthaven.havenbackports.cube;

import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

/**
 * 26.x SulfurCubeBlockDispenseItemBehavior: a dispenser feeds a swallowable block to a Sulfur Cube in front of it,
 * else drops it. 26.x makes it the default for every item in #sulfur_cube_swallowable (DispenserBlock
 * .getDefaultDispenseMethod: CubeDispenserBlockMixin) and tries it first for TNT (26.x DispenseItemBehavior's TNT
 * entry, here wrapped around 1.21.1's own TNT behaviour). Filled buckets use 1.21.1's filled bucket behaviour.
 */
public final class CubeDispense extends DefaultDispenseItemBehavior {
    public static final CubeDispense INSTANCE = new CubeDispense();

    private CubeDispense() {}

    @Override
    protected ItemStack execute(BlockSource source, ItemStack dispensed) {
        return dispenseBlock(source.level(), source.pos().relative(source.state().getValue(DispenserBlock.FACING)), dispensed)
                ? dispensed : super.execute(source, dispensed);
    }

    public static boolean dispenseBlock(ServerLevel level, BlockPos pos, ItemStack dispensed) {
        for (SulfurCube cube : level.getEntitiesOfClass(SulfurCube.class, new AABB(pos))) {
            if (SulfurCubeActions.equipItem(cube, dispensed)) {
                dispensed.shrink(1);
                return true;
            }
        }
        return false;
    }

    static void register() {
        DispenseItemBehavior tnt = DispenserBlock.DISPENSER_REGISTRY.get(Items.TNT);
        DispenserBlock.registerBehavior(Items.TNT, (source, dispensed) -> {
            BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            if (dispenseBlock(source.level(), target, dispensed)) {
                source.level().levelEvent(1000, source.pos(), 0);   // the dispense sound and smoke of a success
                source.level().levelEvent(2000, source.pos(), source.state().getValue(DispenserBlock.FACING).get3DDataValue());
                return dispensed;
            }
            return tnt.dispense(source, dispensed);
        });
        DispenserBlock.registerBehavior(Cube.SULFUR_CUBE_BUCKET.get(), DispenserBlock.DISPENSER_REGISTRY.get(Items.AXOLOTL_BUCKET));
    }
}
