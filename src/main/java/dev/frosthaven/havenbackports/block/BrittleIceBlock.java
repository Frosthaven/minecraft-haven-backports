package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.IceCaves;
import dev.frosthaven.havenbackports.net.LevelEvent26;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x BrittleIceBlock: cracks under a walking mob or player (not #does_not_break_brittle_ice; players not in spawn
 * protection, mobs only with mobGriefing), one step every 24 ticks at most, spreading to touching brittle ice, and
 * breaks without drops at age 3. 1.21.1 forms: IceBlock.meltsInto is static there (water), so playerDestroy and melt
 * are written out with 26.x's air; level event 2014 (block break particles, no sound) is LevelEvent26; ice faces
 * hide against #face_occluding_ice_blocks (26.x IceBlock). 26.x IceBlock's ambient ice sound (an engine-wide new
 * sound for every ice block) is not played.
 */
public class BrittleIceBlock extends CrackingIceBlock {
    private static final int TICK_DELAY = 24;

    public BrittleIceBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<? extends IceBlock> codec() {
        return simpleCodec(BrittleIceBlock::new);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState onState, Entity entity) {
        if (level instanceof ServerLevel server && this.shouldEntityIncreaseCracking(entity, pos, server)
                && !server.getBlockTicks().hasScheduledTick(pos, this)) {
            if (this.slightlyMelt(onState, server, pos)) {
                this.propagateMelting(server, pos, server.getRandom());
            }
            server.scheduleTick(pos, this, this.getTickDelay(server.getRandom()));
        }
    }

    protected boolean shouldEntityIncreaseCracking(Entity entity, BlockPos pos, ServerLevel level) {
        if (!(entity instanceof LivingEntity) || entity.getType().is(IceCaves.DOES_NOT_BREAK_BRITTLE_ICE)) {
            return false;
        }
        return entity instanceof Player player ? !level.getServer().isUnderSpawnProtection(level, pos, player)
                : level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    @Override
    protected int getTickDelay(RandomSource random) {
        return TICK_DELAY;
    }

    @Override
    protected void melt(BlockState state, Level level, BlockPos pos) {
        level.destroyBlock(pos, false);
    }

    /** Block.playerDestroy; 26.x IceBlock then sets meltsInto(), air for brittle ice: nothing to place. */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        player.awardStat(Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005F);
        dropResources(state, level, pos, blockEntity, player, tool);
    }

    @Override
    protected void playCrackingIceParticle(BlockState state, Level level, BlockPos pos) {
        if (state.getValue(AGE) == 0 && level instanceof ServerLevel server) {
            LevelEvent26.destroyBlockParticles(server, pos, state);
        }
    }

    @Override
    protected SoundEvent getCrackingIceSound() {
        return IceCaves.BRITTLE_ICE_CRACK.get();
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, Direction side) {
        return adjacent.is(IceCaves.FACE_OCCLUDING_ICE_BLOCKS) || super.skipRendering(state, adjacent, side);
    }
}
