package dev.frosthaven.havenbackports.decoration;

import dev.frosthaven.havenbackports.Cushions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Mojang's 26.x CushionItem: placed on the top face of a block (on #cushion_uses_collision_shape blocks, the face of
 * their collision shape), facing the player's way, one per spot. 26.x's CUSHION_COLOR component is this item's color.
 */
public class CushionItem extends Item {
    private final DyeColor color;

    public CushionItem(DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    public DyeColor color() {
        return color;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        UseOnContext ctx = recalculateForCollisionShape(context);
        Level level = context.getLevel();
        if (ctx.getClickedFace() != Direction.UP) {
            return InteractionResult.FAIL;
        }
        BlockPlaceContext place = new BlockPlaceContext(ctx);
        BlockPos pos = place.getClickedPos();
        Vec3 at = new Vec3(pos.getX() + 0.5, ctx.getClickLocation().y, pos.getZ() + 0.5);
        AABB box = Cushions.CUSHION.get().getDimensions().makeBoundingBox(at);
        if (!Cushion.canBePlacedAt(level, box)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel server) {
            if (!server.getEntitiesOfClass(Cushion.class, box).isEmpty()) {
                return InteractionResult.FAIL;
            }
            Cushion cushion = Cushions.CUSHION.get().create(server, EntityType.createDefaultStackConfig(server, stack, context.getPlayer()), pos,
                    MobSpawnType.SPAWN_EGG, true, true);
            if (cushion == null) {
                return InteractionResult.FAIL;
            }
            cushion.setColor(color);
            cushion.moveTo(at, Direction.fromYRot(place.getRotation()).toYRot(), 0.0F);
            server.addFreshEntity(cushion);
            cushion.destroyIfInFire(server);
            level.playSound(null, cushion.getX(), cushion.getY(), cushion.getZ(), Cushions.PLACE.get(), SoundSource.BLOCKS, 0.75F, 0.8F);
            cushion.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
            stack.consume(1, place.getPlayer());
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static UseOnContext recalculateForCollisionShape(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return context;
        }
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        BlockState state = level.getBlockState(clicked);
        if (!state.is(Cushions.CUSHION_USES_COLLISION_SHAPE)) {
            return context;
        }
        Vec3 from = player.getEyePosition();
        Vec3 ray = context.getClickLocation().subtract(from);
        Vec3 to = context.getClickLocation().add(ray.normalize().scale(0.001));
        BlockHitResult hit = state.getCollisionShape(level, clicked).clip(from, to, clicked);
        return hit == null ? context : new UseOnContext(player, context.getHand(), hit);
    }
}
