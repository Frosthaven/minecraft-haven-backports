package dev.frosthaven.havenbackports.nautilus;

import dev.frosthaven.havenbackports.Nautiluses;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.AABB;

/**
 * Nautilus armor: 26.x Item.Properties.nautilusArmor(material) (the material's body attributes, Equippable BODY with
 * equip sound item.armor.equip_nautilus, the material's equipment asset, only #can_wear_nautilus_armor, no damage on
 * hurt, equip on interact, dispensable, shears take it off with item.armor.unequip_nautilus, stacks of 1). 1.21.1 has
 * no equippable component, so this is a body ArmorItem carrying those rules (as HarnessItem does for the harness);
 * the shearing is in NautilusInteraction. {@link #asset()} names Mojang's entity/equipment/nautilus_body texture.
 */
public class NautilusArmorItem extends ArmorItem {
    private final String asset;

    public NautilusArmorItem(Holder<ArmorMaterial> material, String asset, Item.Properties properties) {
        super(material, Type.BODY, properties.stacksTo(1));
        this.asset = asset;
    }

    public ResourceLocation texture() {
        return ResourceLocation.fromNamespaceAndPath(dev.frosthaven.havenbackports.HavenBackports.ID,
                "textures/entity/equipment/nautilus_body/" + this.asset + ".png");
    }

    public String asset() {
        return this.asset;
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return Nautiluses.ARMOR_EQUIP_NAUTILUS;
    }

    /** 26.x LivingEntity.isEquippableInSlot for Equippable(BODY, #can_wear_nautilus_armor). */
    static boolean equippableBy(LivingEntity target) {
        return target.canUseSlot(EquipmentSlot.BODY) && target.getType().is(Nautiluses.CAN_WEAR_NAUTILUS_ARMOR);
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

    /** 26.x EquipmentDispenseItemBehavior with AbstractNautilus.canDispenserEquipIntoSlot (body); ArmorItem's own
     *  dispenser behaviour is replaced (it would put the armor on any mob that takes body armor). */
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

    public static void registerDispenser(List<? extends Item> armors) {
        armors.forEach(a -> DispenserBlock.registerBehavior(a, DISPENSE));
    }
}
