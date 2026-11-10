package dev.frosthaven.havenbackports.ghast;

import dev.frosthaven.havenbackports.Ghasts;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

/**
 * A harness: 26.x Items.HARNESS with Equippable.harness(color) (slot body, equip sound entity.happy_ghast.equip, only
 * #can_equip_harness, equip on interact, dispensable, shears take it off with entity.happy_ghast.unequip). 1.21.1 has
 * no equippable component, so this item is a 1.21.1 Equipable (LivingEntity.onEquipItem plays the equip sound) and
 * carries 26.x's equipOnTarget, isEquippableInSlot and EquipmentDispenseItemBehavior itself; the shearing is in
 * HappyGhastInteraction. The renderer reads {@link #color()} for Mojang's equipment texture.
 */
public class HarnessItem extends Item implements Equipable {
    private final DyeColor color;

    public HarnessItem(DyeColor color, Properties properties) {
        super(properties);
        this.color = color;
    }

    public DyeColor color() {
        return color;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.BODY;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return Ghasts.HARNESS_EQUIP;
    }

    /** 26.x LivingEntity.isEquippableInSlot for an Equippable(BODY, #can_equip_harness). */
    static boolean equippableBy(LivingEntity target) {
        return target.canUseSlot(EquipmentSlot.BODY) && target.getType().is(Ghasts.CAN_EQUIP_HARNESS);
    }

    /** 26.x Equippable.equipOnTarget. */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (equippableBy(target) && target.getItemBySlot(EquipmentSlot.BODY).isEmpty() && target.isAlive()) {
            if (!player.level().isClientSide()) {
                target.setItemSlot(EquipmentSlot.BODY, stack.split(1));
                if (target instanceof Mob mob) {
                    mob.setGuaranteedDrop(EquipmentSlot.BODY);
                }
            }
            return InteractionResult.sidedSuccess(player.level().isClientSide());
        }
        return InteractionResult.PASS;
    }

    /** 26.x EquipmentDispenseItemBehavior with LivingEntity.canEquipWithDispenser (HappyGhast.canDispenserEquipIntoSlot: body). */
    static final DefaultDispenseItemBehavior DISPENSE = new DefaultDispenseItemBehavior() {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            BlockPos pos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            List<LivingEntity> targets = source.level().getEntitiesOfClass(LivingEntity.class, new AABB(pos),
                    e -> e.isAlive() && !e.isSpectator() && equippableBy(e) && e.getItemBySlot(EquipmentSlot.BODY).isEmpty());
            if (targets.isEmpty()) {
                return super.execute(source, stack);
            }
            LivingEntity target = targets.get(0);
            target.setItemSlot(EquipmentSlot.BODY, stack.split(1));
            if (target instanceof Mob mob) {
                mob.setGuaranteedDrop(EquipmentSlot.BODY);
                mob.setPersistenceRequired();
            }
            return stack;
        }
    };

    public static void registerDispenser(List<? extends Item> harnesses) {
        harnesses.forEach(h -> DispenserBlock.registerBehavior(h, DISPENSE));
    }
}
