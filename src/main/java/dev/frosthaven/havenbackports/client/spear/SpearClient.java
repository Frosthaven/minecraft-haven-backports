package dev.frosthaven.havenbackports.client.spear;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.Spears;
import dev.frosthaven.havenbackports.mixin.MultiPlayerGameModeInvoker;
import dev.frosthaven.havenbackports.spear.SpearItem;
import dev.frosthaven.havenbackports.spear.Stab;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client side of 26.x spears: the jab on the attack key (26.x Minecraft.startAttack + MultiPlayerGameMode.piercingAttack),
 * the crosshair pick (LocalPlayer.raycastHitResult), first-person hands (FirstPersonHandsAndItemsRenderer) and the
 * player arm pose (AvatarRenderer.getArmPose), and the item-swap ticker 26.x's first-person hand height reads.
 */
@EventBusSubscriber(modid = HavenBackports.ID, value = Dist.CLIENT)
public final class SpearClient {
    /** swap_animation_scale of every 26.x spear item definition (tools/spear.py checks it). */
    public static final float SWAP_ANIMATION_SCALE = 1.95F;
    private static int itemSwapTicker;
    private static ItemStack lastItemInMainHand = ItemStack.EMPTY;

    private SpearClient() {}

    public static float partialTick(Entity entity) {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(!entity.level().tickRateManager().isEntityFrozen(entity));
    }

    public static HumanoidArm arm(LivingEntity entity, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? entity.getMainArm() : entity.getMainArm().getOpposite();
    }

    /** 26.x LivingEntity.getItemHeldByArm. */
    public static ItemStack heldByArm(LivingEntity entity, HumanoidArm arm) {
        return entity.getItemInHand(arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    /** "The current swing is STAB on this arm": 1.21.1 knows the swing from the swinging hand's item (a spear). */
    public static boolean isStab(LivingEntity entity, HumanoidArm arm) {
        return entity.swinging && entity.swingingArm != null && arm(entity, entity.swingingArm) == arm
                && entity.getItemInHand(entity.swingingArm).getItem() instanceof SpearItem;
    }

    /** 26.x HumanoidMobRenderer.usesSpearPose. */
    public static boolean usesSpearPose(ItemStack item, HumanoidArm arm, LivingEntity entity) {
        return isStab(entity, arm) || item.is(Spears.SPEARS);
    }

    /** 26.x Player.getItemSwapScale: like the attack strength scale, but reset only when the main-hand item changes. */
    public static float itemSwapScale(LocalPlayer player, float a) {
        return Math.clamp((itemSwapTicker + a) / player.getCurrentItemAttackStrengthDelay(), 0.0F, 1.0F);
    }

    /** 26.x Player.tick: itemSwapTicker++, reset when the main-hand item is no longer the same item. */
    @SubscribeEvent
    static void tick(ClientTickEvent.Post e) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        itemSwapTicker++;
        ItemStack main = player.getMainHandItem();
        if (!ItemStack.matches(lastItemInMainHand, main)) {
            if (!ItemStack.isSameItem(lastItemInMainHand, main)) {
                itemSwapTicker = 0;
            }
            lastItemInMainHand = main.copy();
        }
    }

    /** 26.x startAttack with a PIERCING_WEAPON: no entity attack, no block breaking; at full charge, the jab. NeoForge
     *  fires this after the same guards 26.x checks first (miss time, hit result, busy hands, item enabled). */
    @SubscribeEvent
    static void attack(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!e.isAttack() || e.getHand() != InteractionHand.MAIN_HAND || player == null || player.isSpectator()
                || !(player.getMainHandItem().getItem() instanceof SpearItem spear)) {
            return;
        }
        e.setCanceled(true);
        e.setSwingHand(false);
        if (SpearItem.cannotAttackWithItem(player, 0)) {
            return;
        }
        ((MultiPlayerGameModeInvoker) mc.gameMode).haven_backports$ensureHasSentCarriedItem();
        PacketDistributor.sendToServer(new Spears.StabPayload());
        player.resetAttackStrengthTicker();
        player.swing(InteractionHand.MAIN_HAND, false);   // LivingEntity.swing: no swing packet (26.x swing(hand, STAB, false))
        spear.piercing().makeSound(player);
    }

    /** 26.x LocalPlayer.raycastHitResult with an ATTACK_RANGE item: the closest hit along the spear's range; a block
     *  beyond block reach becomes a miss; a miss keeps vanilla's pick (already in {@code vanilla}). */
    public static HitResult pick(LocalPlayer player, Entity camera, float partial, HitResult vanilla) {
        ItemStack active = player.isSpectator() ? ItemStack.EMPTY : player.isUsingItem() ? player.getUseItem() : player.getMainHandItem();
        if (!(active.getItem() instanceof SpearItem)) {
            return vanilla;
        }
        HitResult hit = SpearItem.ATTACK_RANGE.getClosesetHit(camera, partial, Entity::isPickable);
        if (hit instanceof BlockHitResult) {
            Vec3 pos = camera.getEyePosition(partial);
            Vec3 at = hit.getLocation();
            if (!at.closerThan(pos, player.blockInteractionRange())) {   // GameRenderer/LocalPlayer.filterHitResult
                hit = BlockHitResult.miss(at, Direction.getNearest(at.x - pos.x, at.y - pos.y, at.z - pos.z), BlockPos.containing(at));
            }
        }
        return hit.getType() == HitResult.Type.MISS ? vanilla : hit;
    }

    /** 26.x Hud.extractCrosshair: the full attack indicator also needs the hit inside the item's attack range. */
    public static boolean inAttackRange(LocalPlayer player, HitResult hit) {
        ItemStack active = player.isUsingItem() ? player.getUseItem() : player.getMainHandItem();
        return !(active.getItem() instanceof SpearItem) || hit == null || SpearItem.ATTACK_RANGE.isInRange(player, hit.getLocation());
    }

    public static void register(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent e) {
        IClientItemExtensions spear = extensions();
        net.minecraft.core.registries.BuiltInRegistries.ITEM.stream().filter(i -> i instanceof SpearItem).forEach(i -> e.registerItem(spear, i));
    }

    private static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            /** 26.x AvatarRenderer.getArmPose: SPEAR while using (ItemUseAnimation.SPEAR) and whenever usesSpearPose. */
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return usesSpearPose(stack, arm(entity, hand), entity) ? SpearArmPose.pose() : null;
            }

            /** 26.x FirstPersonHandsAndItemsRenderer.submitArmWithItem for a spear (the auto-spin case stays vanilla). */
            @Override
            public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
                    float equipProcess, float swingProcess) {
                int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
                InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    poseStack.translate(invert * 0.56F, -0.52F, -0.72F);
                    float timeHeld = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTick + 1.0F);
                    SpearAnimations.firstPersonUse(Stab.ticksSinceLastKineticHitFeedback(player, partialTick), poseStack, timeHeld, arm, stack);
                    return true;
                }
                if (player.isAutoSpinAttack()) {
                    return false;
                }
                // applyItemArmTransform(inverseArmHeight = swap_animation_scale * (1 - height))
                poseStack.translate(invert * 0.56F, -0.52F + equipProcess * SWAP_ANIMATION_SCALE * -0.6F, -0.72F);
                if (player.swinging && player.swingingArm == hand && isStab(player, arm)) {
                    SpearAnimations.firstPersonAttack(swingProcess, poseStack, invert, arm);
                }
                return true;
            }
        };
    }

    /** Entity hit picked for the crosshair. */
    public static Entity pickedEntity(HitResult hit) {
        return hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
    }
}
