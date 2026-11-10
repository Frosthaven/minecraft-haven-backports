package dev.frosthaven.havenbackports.variant;

import dev.frosthaven.havenbackports.Variants;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Position;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/**
 * 26.x's blue and brown eggs: an egg whose chicks are cold or warm chickens (26.x Items.BLUE_EGG / BROWN_EGG carry a
 * chicken/variant component the thrown egg passes on). 1.21.1's ThrownEgg always hatches plain chickens, so the item
 * throws {@link Thrown}, whose hatching is 1.21.1's ThrownEgg.onHit with the variant set.
 */
public class VariantEgg extends EggItem {
    private final ResourceKey<FarmVariant> variant;

    public VariantEgg(ResourceKey<FarmVariant> variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EGG_THROW, SoundSource.PLAYERS, 0.5F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            Thrown egg = new Thrown(level, player, variant);
            egg.setItem(stack);
            egg.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(egg);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        Thrown egg = new Thrown(level, pos.x(), pos.y(), pos.z(), variant);
        egg.setItem(stack);
        return egg;
    }

    public static class Thrown extends ThrownEgg {
        private final ResourceKey<FarmVariant> variant;

        Thrown(Level level, LivingEntity owner, ResourceKey<FarmVariant> variant) {
            super(level, owner);
            this.variant = variant;
        }

        Thrown(Level level, double x, double y, double z, ResourceKey<FarmVariant> variant) {
            super(level, x, y, z);
            this.variant = variant;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void onHit(HitResult result) {
            if (result.getType() == HitResult.Type.ENTITY) {
                this.onHitEntity((net.minecraft.world.phys.EntityHitResult) result);
            } else if (result.getType() == HitResult.Type.BLOCK) {   // Projectile.onHit, which ThrownEgg.onHit starts with
                net.minecraft.world.phys.BlockHitResult block = (net.minecraft.world.phys.BlockHitResult) result;
                this.onHitBlock(block);
                this.level().gameEvent(net.minecraft.world.level.gameevent.GameEvent.PROJECTILE_LAND, block.getBlockPos(),
                        net.minecraft.world.level.gameevent.GameEvent.Context.of(this, this.level().getBlockState(block.getBlockPos())));
            }
            if (!this.level().isClientSide) {
                if (this.random.nextInt(8) == 0) {
                    int count = this.random.nextInt(32) == 0 ? 4 : 1;
                    Holder<FarmVariant> chick = this.level().registryAccess().registryOrThrow(Variants.CHICKEN).getHolderOrThrow(variant);
                    for (int j = 0; j < count; j++) {
                        Chicken chicken = EntityType.CHICKEN.create(this.level());
                        if (chicken != null) {
                            chicken.setAge(-24000);
                            ((VariantHolder<FarmVariant>) chicken).haven_backports$setVariant(chick);
                            chicken.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                            if (!chicken.fudgePositionAfterSizeChange(EntityDimensionsHolder.ZERO)) {
                                break;
                            }
                            this.level().addFreshEntity(chicken);
                        }
                    }
                }
                this.level().broadcastEntityEvent(this, (byte) 3);
                this.discard();
            }
        }

        @Override
        protected Item getDefaultItem() {
            return net.minecraft.world.item.Items.EGG;
        }
    }

    /** ThrownEgg.ZERO_SIZED_DIMENSIONS (private in 1.21.1). */
    static final class EntityDimensionsHolder {
        static final net.minecraft.world.entity.EntityDimensions ZERO = net.minecraft.world.entity.EntityDimensions.fixed(0.0F, 0.0F);
    }
}
