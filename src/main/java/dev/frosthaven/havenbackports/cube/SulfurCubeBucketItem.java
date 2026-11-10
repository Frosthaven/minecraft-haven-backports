package dev.frosthaven.havenbackports.cube;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 26.x Items.SULFUR_CUBE_BUCKET: a MobBucketItem with no fluid (26.x BucketItem.use, MobBucketItem.emptyContents,
 * checkExtraContent and spawn). 1.21.1's BucketItem treats an empty-fluid bucket as an empty bucket (it would pick up
 * water), so this ports 26.x's use path for it. The spawned cube takes the bucket's SulfurCubeContent (26.x applies
 * an entity's implicit components from the stack; 1.21.1 entities have none), then its bucket data. 26.x shows the
 * content in the tooltip (SulfurCubeContent is a TooltipProvider).
 */
public class SulfurCubeBucketItem extends Item implements DispensibleContainerItem {
    public SulfurCubeBucketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);   // MobBucketItem.getFluidContext
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(stack);
        }
        BlockPos pos = hit.getBlockPos();
        BlockPos placePos = pos.relative(hit.getDirection());
        if (!level.mayInteract(player, pos) || !player.mayUseItemAt(placePos, hit.getDirection(), stack)) {
            return InteractionResultHolder.fail(stack);
        }
        this.emptyContents(player, level, placePos, hit);
        this.checkExtraContent(player, level, stack, placePos);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(ItemUtils.createFilledResult(stack, player, BucketItem.getEmptySuccessItem(stack, player)),
                level.isClientSide());
    }

    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult hit) {
        level.playSound(player, pos, CubeSounds.BUCKET_EMPTY.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
        return true;
    }

    @Override
    public void checkExtraContent(@Nullable Player player, Level level, ItemStack stack, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            this.spawn(serverLevel, stack, pos);
            level.gameEvent(player, GameEvent.ENTITY_PLACE, pos);
        }
    }

    private void spawn(ServerLevel level, ItemStack stack, BlockPos pos) {
        SulfurCube cube = Cube.SULFUR_CUBE.get().create(level, EntityType.createDefaultStackConfig(level, stack, null), pos, MobSpawnType.BUCKET,
                true, false);
        if (cube != null) {
            SulfurCubeContent content = stack.get(Cube.SULFUR_CUBE_CONTENT.get());
            if (content != null) {
                cube.setContent(content);
            }
            cube.loadFromBucketTag(stack.getOrDefault(DataComponents.BUCKET_ENTITY_DATA, CustomData.EMPTY).copyTag());
            cube.setFromBucket(true);
            level.addFreshEntityWithPassengers(cube);
            cube.playAmbientSound();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        SulfurCubeContent content = stack.get(Cube.SULFUR_CUBE_CONTENT.get());
        if (content != null) {
            content.addToTooltip(lines::add);
        }
    }
}
