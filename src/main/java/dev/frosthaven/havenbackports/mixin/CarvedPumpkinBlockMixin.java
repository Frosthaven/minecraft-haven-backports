package dev.frosthaven.havenbackports.mixin;

import dev.frosthaven.havenbackports.Golems;
import dev.frosthaven.havenbackports.block.CopperChestBlock;
import dev.frosthaven.havenbackports.golem.CopperGolem;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.level.block.state.pattern.BlockPattern;
import net.minecraft.world.level.block.state.pattern.BlockPatternBuilder;
import net.neoforged.neoforge.common.DataMapHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.x CarvedPumpkinBlock's copper golem: a carved pumpkin or jack o'lantern on a #minecraft:copper block, checked after
 * the snow and iron golems (26.x trySpawnGolem's last branch; 1.21.1's method ends where it would start, and a spawned
 * snow or iron golem has already cleared the pumpkin). The golem spawns at the pumpkin, the copper block becomes the
 * matching copper chest facing the pumpkin's way (CopperChestBlock.getFromCopperBlock) and the golem takes the block's
 * age (a waxed block through NeoForge's waxables map, 26.x's HoneycombItem.WAX_OFF_BY_BLOCK). canSpawnGolem also
 * matches the base pattern, so dispensers place pumpkins for it.
 */
@Mixin(CarvedPumpkinBlock.class)
abstract class CarvedPumpkinBlockMixin {
    @Shadow
    @Final
    private static Predicate<BlockState> PUMPKINS_PREDICATE;

    @Unique
    private BlockPattern haven_backports$copperGolemBase, haven_backports$copperGolemFull;

    @Shadow
    private static void spawnGolemInWorld(Level level, BlockPattern.BlockPatternMatch match, Entity golem, BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "canSpawnGolem", at = @At("RETURN"), cancellable = true)
    private void haven_backports$canSpawnCopperGolem(LevelReader level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && haven_backports$base().find(level, pos) != null) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "trySpawnGolem", at = @At("TAIL"))
    private void haven_backports$copperGolem(Level level, BlockPos pos, CallbackInfo ci) {
        BlockPattern.BlockPatternMatch match = haven_backports$full().find(level, pos);
        if (match == null) {
            return;
        }
        CopperGolem golem = Golems.COPPER_GOLEM.get().create(level);   // 26.x: EntitySpawnReason.TRIGGERED
        if (golem != null) {
            BlockInWorld copper = match.getBlock(0, 1, 0);
            BlockInWorld pumpkin = match.getBlock(0, 0, 0);
            spawnGolemInWorld(level, match, golem, pumpkin.getPos());
            Direction facing = pumpkin.getState().getValue(CarvedPumpkinBlock.FACING);
            level.setBlock(copper.getPos(), CopperChestBlock.getFromCopperBlock(copper.getState().getBlock(), facing, level, copper.getPos()), 2);
            golem.spawn(haven_backports$age(copper.getState()));
        }
    }

    @Unique
    private static WeatherState haven_backports$age(BlockState state) {
        if (state.getBlock() instanceof WeatheringCopper copper) {
            return copper.getAge();
        }
        return Optional.ofNullable(DataMapHooks.getBlockUnwaxed(state.getBlock())).filter(b -> b instanceof WeatheringCopper)
                .map(b -> ((WeatheringCopper) b).getAge()).orElse(WeatherState.UNAFFECTED);
    }

    @Unique
    private BlockPattern haven_backports$base() {
        if (haven_backports$copperGolemBase == null) {
            haven_backports$copperGolemBase = BlockPatternBuilder.start().aisle(" ", "#")
                    .where('#', BlockInWorld.hasState(s -> s.is(Golems.COPPER))).build();
        }
        return haven_backports$copperGolemBase;
    }

    @Unique
    private BlockPattern haven_backports$full() {
        if (haven_backports$copperGolemFull == null) {
            haven_backports$copperGolemFull = BlockPatternBuilder.start().aisle("^", "#")
                    .where('^', BlockInWorld.hasState(PUMPKINS_PREDICATE)).where('#', BlockInWorld.hasState(s -> s.is(Golems.COPPER))).build();
        }
        return haven_backports$copperGolemFull;
    }
}
