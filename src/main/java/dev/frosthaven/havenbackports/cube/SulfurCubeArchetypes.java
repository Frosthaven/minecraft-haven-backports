package dev.frosthaven.havenbackports.cube;

import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** 26.x SulfurCube's archetype methods (collectEquipmentChanges BODY part, onChangedBlock's location effects),
 *  line for line, split out of SulfurCube (each file stays under 500 lines). */
final class SulfurCubeArchetypes {
    private SulfurCubeArchetypes() {}

    static void onChangedBlock(SulfurCube cube, ServerLevel level) {
        ItemStack item = cube.getItemBySlot(EquipmentSlot.BODY);
        EnchantedItemInUse inUse = new EnchantedItemInUse(item, EquipmentSlot.BODY, cube);
        for (SulfurCubeArchetype archetype : cube.matchingArchetypes(item)) {
            for (ConditionalEffect<EnchantmentLocationBasedEffect> effect : archetype.locationBasedEffects()) {
                boolean active = cube.activeLocationDependentEffects.contains(effect.effect());
                if (effect.matches(getContext(cube, level, active))) {
                    effect.effect().onChangedBlock(level, 1, inUse, cube, cube.position(), false);
                    if (!active) {
                        cube.activeLocationDependentEffects.add(effect.effect());
                    }
                } else if (active) {
                    cube.activeLocationDependentEffects.remove(effect.effect());
                }
            }
        }
    }

    /** 26.x collectEquipmentChanges, BODY slot part (server; from NeoForge's LivingEquipmentChangeEvent). */
    static void onBodyChanged(SulfurCube cube, ItemStack previous, ItemStack current) {
        if (!current.isEmpty()) {
            cube.goalSelector.removeAllGoals(g -> true);
            cube.setSpeed(0.0F);
        } else {
            cube.registerCubeGoals();
        }
        List<SulfurCubeArchetype> previousArchetypes = cube.matchingArchetypes(previous);
        List<SulfurCubeArchetype> currentArchetypes = cube.matchingArchetypes(current);
        for (SulfurCubeArchetype a : previousArchetypes) {
            for (SulfurCubeArchetype.AttributeEntry mod : a.attributeModifiers()) {
                AttributeInstance attr = cube.getAttribute(mod.attribute());
                if (attr != null) {
                    attr.removeModifier(mod.modifier());
                }
            }
        }
        cube.floatsInLiquids = false;
        cube.explosionData = Optional.empty();
        cube.contactDamages.clear();
        cube.knockbackModifier = SulfurCubeArchetype.DEFAULT_KNOCKBACK_MODIFIERS;
        cube.soundSettings = SulfurCubeArchetype.defaultSoundSettings();
        for (SulfurCubeArchetype a : currentArchetypes) {
            if (a.buoyant()) {
                cube.floatsInLiquids = true;
            }
            if (a.explosion().isPresent()) {
                cube.explosionData = a.explosion();
            }
            a.contactDamage().ifPresent(cube.contactDamages::add);
            cube.knockbackModifier = a.knockbackModifiers();
            cube.soundSettings = a.soundSettings();
            for (SulfurCubeArchetype.AttributeEntry mod : a.attributeModifiers()) {
                AttributeInstance attr = cube.getAttribute(mod.attribute());
                if (attr != null) {
                    attr.addOrUpdateTransientModifier(mod.modifier());
                }
            }
        }
        EnchantedItemInUse previousInUse = new EnchantedItemInUse(previous, EquipmentSlot.BODY, cube);
        for (EnchantmentLocationBasedEffect effect : cube.activeLocationDependentEffects) {
            effect.onDeactivated(previousInUse, cube, cube.position(), 1);
        }
        cube.activeLocationDependentEffects.clear();
        EnchantedItemInUse currentInUse = new EnchantedItemInUse(current, EquipmentSlot.BODY, cube);
        ServerLevel level = (ServerLevel) cube.level();
        List<ConditionalEffect<EnchantmentLocationBasedEffect>> newEffects = currentArchetypes.stream()
                .filter(a -> !previousArchetypes.contains(a)).flatMap(a -> a.locationBasedEffects().stream()).toList();
        if (!newEffects.isEmpty()) {
            LootContext context = getContext(cube, level, false);
            for (ConditionalEffect<EnchantmentLocationBasedEffect> effect : newEffects) {
                if (effect.matches(context)) {
                    effect.effect().onChangedBlock(level, 1, currentInUse, cube, cube.position(), true);
                    cube.activeLocationDependentEffects.add(effect.effect());
                }
            }
        }
    }

    static LootContext getContext(SulfurCube cube, ServerLevel level, boolean active) {
        LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, cube)
                .withParameter(LootContextParams.ENCHANTMENT_LEVEL, 1).withParameter(LootContextParams.ORIGIN, cube.position())
                .withParameter(LootContextParams.ENCHANTMENT_ACTIVE, active).create(LootContextParamSets.ENCHANTED_LOCATION);
        return new LootContext.Builder(params).create(Optional.empty());
    }
}
