package dev.frosthaven.havenbackports;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SimpleTier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Copper tools and armor, copper and netherite horse armor, as 26.4's ToolMaterial, ArmorMaterials and Items define
 * them. 26.x's {@code sword(material, damage, speed)} etc. add the material's damage bonus, as 1.21.1's createAttributes.
 */
final class Gear {
    static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, HavenBackports.ID);
    static final TagKey<Block> INCORRECT_FOR_COPPER_TOOL = BlockTags.create(ResourceLocation.withDefaultNamespace("incorrect_for_copper_tool"));
    static final TagKey<Item> COPPER_TOOL_MATERIALS = ItemTags.create(ResourceLocation.withDefaultNamespace("copper_tool_materials"));
    static final TagKey<Item> REPAIRS_COPPER_ARMOR = ItemTags.create(ResourceLocation.withDefaultNamespace("repairs_copper_armor"));
    static final TagKey<Item> REPAIRS_NETHERITE_ARMOR = ItemTags.create(ResourceLocation.withDefaultNamespace("repairs_netherite_armor"));

    /** ToolMaterial.COPPER: incorrect_for_copper_tool, 190 uses, speed 5, +1 damage, enchantability 13. */
    static final SimpleTier COPPER_TIER = new SimpleTier(INCORRECT_FOR_COPPER_TOOL, 190, 5.0F, 1.0F, 13, () -> Ingredient.of(COPPER_TOOL_MATERIALS));

    static final DeferredHolder<SoundEvent, SoundEvent> EQUIP_COPPER = Content.sound("item.armor.equip_copper");

    /** ArmorMaterials.COPPER: durability 11, defense 1/3/4/2 (boots, legs, chest, helmet), body 4, enchantability 8. */
    static final DeferredHolder<ArmorMaterial, ArmorMaterial> COPPER = MATERIALS.register("copper", () -> new ArmorMaterial(
            defense(1, 3, 4, 2, 4), 8, EQUIP_COPPER, () -> Ingredient.of(REPAIRS_COPPER_ARMOR),
            List.of(new ArmorMaterial.Layer(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, "copper"))), 0.0F, 0.0F));
    /** ArmorMaterials.NETHERITE as 26.x has it, for the horse: body 19 (1.21.1's netherite has 11), toughness 3, 0.1. */
    static final DeferredHolder<ArmorMaterial, ArmorMaterial> NETHERITE = MATERIALS.register("netherite", () -> new ArmorMaterial(
            defense(3, 6, 8, 3, 19), 15, SoundEvents.ARMOR_EQUIP_NETHERITE, () -> Ingredient.of(REPAIRS_NETHERITE_ARMOR),
            List.of(new ArmorMaterial.Layer(ResourceLocation.withDefaultNamespace("netherite"))), 3.0F, 0.1F));

    private Gear() {}

    private static Map<ArmorItem.Type, Integer> defense(int boots, int legs, int chest, int helmet, int body) {
        Map<ArmorItem.Type, Integer> m = new EnumMap<>(ArmorItem.Type.class);
        m.put(ArmorItem.Type.BOOTS, boots);
        m.put(ArmorItem.Type.LEGGINGS, legs);
        m.put(ArmorItem.Type.CHESTPLATE, chest);
        m.put(ArmorItem.Type.HELMET, helmet);
        m.put(ArmorItem.Type.BODY, body);
        return m;
    }

    static void register(IEventBus modBus) {
        tool("copper_sword", () -> new SwordItem(COPPER_TIER, new Item.Properties().attributes(SwordItem.createAttributes(COPPER_TIER, 3.0F, -2.4F))));
        tool("copper_shovel", () -> new ShovelItem(COPPER_TIER, new Item.Properties().attributes(ShovelItem.createAttributes(COPPER_TIER, 1.5F, -3.0F))));
        tool("copper_pickaxe", () -> new PickaxeItem(COPPER_TIER, new Item.Properties().attributes(PickaxeItem.createAttributes(COPPER_TIER, 1.0F, -2.8F))));
        tool("copper_axe", () -> new AxeItem(COPPER_TIER, new Item.Properties().attributes(AxeItem.createAttributes(COPPER_TIER, 7.0F, -3.2F))));
        tool("copper_hoe", () -> new HoeItem(COPPER_TIER, new Item.Properties().attributes(HoeItem.createAttributes(COPPER_TIER, -1.0F, -2.0F))));
        armor("copper_helmet", ArmorItem.Type.HELMET);
        armor("copper_chestplate", ArmorItem.Type.CHESTPLATE);
        armor("copper_leggings", ArmorItem.Type.LEGGINGS);
        armor("copper_boots", ArmorItem.Type.BOOTS);
        tool("copper_horse_armor", () -> new AnimalArmorItem(COPPER, AnimalArmorItem.BodyType.EQUESTRIAN, false, new Item.Properties().stacksTo(1)));
        tool("netherite_horse_armor", () -> new AnimalArmorItem(NETHERITE, AnimalArmorItem.BodyType.EQUESTRIAN, false,
                new Item.Properties().stacksTo(1).fireResistant()));
        MATERIALS.register(modBus);
    }

    private static void tool(String id, java.util.function.Supplier<Item> item) {
        DeferredItem<Item> d = Content.ITEMS.register(id, item);
        Content.ITEM.put(id, d);
    }

    /** humanoidArmor(COPPER, type): durability type.getDurability(11). */
    private static void armor(String id, ArmorItem.Type type) {
        tool(id, () -> new ArmorItem(COPPER, type, new Item.Properties().durability(type.getDurability(11))));
    }
}
