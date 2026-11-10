package dev.frosthaven.havenbackports.spear;

import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * A 26.x spear: {@code Item.Properties.spear(material, attackDuration, damageMultiplier, delay, dismountTime,
 * dismountThreshold, knockbackTime, knockbackThreshold, damageTime, damageThreshold)}, expression for expression. 26.x
 * puts each part in an item component; 1.21.1 has none of them, so this class holds them and does what 26.x's Item
 * and ItemStack do with them:
 * <ul>
 * <li>KINETIC_WEAPON: {@code use} starts using and plays the use sound; {@code onUseTick} runs the charge (server).</li>
 * <li>PIERCING_WEAPON, ATTACK_RANGE, MINIMUM_ATTACK_CHARGE 1.0: the jab (client/spear/SpearClient, Spears.StabPayload).</li>
 * <li>ATTACK_ANIMATION STAB, (int)(attackDuration * 20) ticks: mixin/LivingEntityMixin's swing duration.</li>
 * <li>WEAPON(1): {@code hurtEnemy} true (1.21.1 then counts the item-used stat as 26.x does), {@code postHurtEnemy}
 * costs 1 durability.</li>
 * <li>USE_EFFECTS(canSprint, no vibrations, speed 1.0): mixin/LocalPlayerMixin. Use vibrations: 1.21.1 emits
 * ITEM_INTERACT_START/FINISH for every used item; noted, not emulated.</li>
 * <li>DAMAGE_TYPE minecraft:spear: haven_backports:spear (Stab).</li>
 * </ul>
 * Not a TieredItem: 1.21.1's piglins treat any TieredItem as a melee weapon (AbstractPiglin.isHoldingMeleeWeapon), 26.x
 * tests the TOOL component, which spears lack. {@code getUseAnimation} is NONE: 1.21.1's SPEAR is the trident's.
 */
public class SpearItem extends Item {
    /** Item.Properties.spear: new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F). */
    public static final AttackRange ATTACK_RANGE = new AttackRange(2.0F, 4.5F, 2.0F, 6.5F, 0.125F, 0.5F);
    private final Tier tier;
    private final TagKey<Item> repairItems;
    private final KineticWeapon kinetic;
    private final PiercingWeapon piercing;
    private final int swingTicks;

    public SpearItem(Tier tier, TagKey<Item> repairItems, boolean wood, Sounds sounds, float attackDuration, float damageMultiplier, float delay,
            float dismountTime, float dismountThreshold, float knockbackTime, float knockbackThreshold, float damageTime, float damageThreshold,
            Properties properties) {
        super(properties.durability(tier.getUses()).attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 0.0F + tier.getAttackDamageBonus(),
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, 1.0F / attackDuration - 4.0,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()));
        this.tier = tier;
        this.repairItems = repairItems;
        this.kinetic = new KineticWeapon(10, (int) (delay * 20.0F),
                KineticWeapon.Condition.ofAttackerSpeed((int) (dismountTime * 20.0F), dismountThreshold),
                KineticWeapon.Condition.ofAttackerSpeed((int) (knockbackTime * 20.0F), knockbackThreshold),
                KineticWeapon.Condition.ofRelativeSpeed((int) (damageTime * 20.0F), damageThreshold),
                0.38F, damageMultiplier, Optional.of(wood ? sounds.woodUse : sounds.use), Optional.of(wood ? sounds.woodHit : sounds.hit));
        this.piercing = new PiercingWeapon(true, false, Optional.of(wood ? sounds.woodAttack : sounds.attack), Optional.of(wood ? sounds.woodHit : sounds.hit));
        this.swingTicks = (int) (attackDuration * 20.0F);
    }

    /** 26.x SoundEvents.SPEAR_* and SPEAR_WOOD_*. */
    public record Sounds(Supplier<SoundEvent> attack, Supplier<SoundEvent> hit, Supplier<SoundEvent> use,
            Supplier<SoundEvent> woodAttack, Supplier<SoundEvent> woodHit, Supplier<SoundEvent> woodUse) {}

    public KineticWeapon kinetic() {
        return this.kinetic;
    }

    public PiercingWeapon piercing() {
        return this.piercing;
    }

    /** ATTACK_ANIMATION duration (SwingAnimation(STAB, (int)(attackDuration * 20))). */
    public int swingTicks() {
        return this.swingTicks;
    }

    /** 26.x LivingEntity.getAttackRangeWith: the spear's range (callers only ask for spears). */
    public static AttackRange attackRange(LivingEntity entity, ItemStack weapon) {
        return ATTACK_RANGE;
    }

    /** 26.x Player.cannotAttackWithItem: MINIMUM_ATTACK_CHARGE 1.0 against (ticker + tolerance) / delay. 1.21.1's
     *  getAttackStrengthScale clamps the same quotient to 1, so "below 1.0" is the same test. */
    public static boolean cannotAttackWithItem(Player player, float tolerance) {
        return player.getAttackStrengthScale(tolerance) < 1.0F;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        this.kinetic.makeSound(player);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** 26.x ItemStack.onUseTick: a kinetic weapon damages entities on the server instead of the item's own tick. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide()) {
            this.kinetic.damageEntities(stack, remaining, entity,
                    entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public int getEnchantmentValue() {
        return this.tier.getEnchantmentValue();
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(this.repairItems);
    }
}
