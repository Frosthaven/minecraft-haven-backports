package dev.frosthaven.havenbackports;

import dev.frosthaven.havenbackports.spear.Lunge;
import dev.frosthaven.havenbackports.spear.SpearBehaviors;
import dev.frosthaven.havenbackports.spear.SpearItem;
import dev.frosthaven.havenbackports.spear.SpearMobsTrigger;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Spears (26.4 snapshot 7 Items.*_SPEAR), their sounds, damage type, advancement trigger, piglin memories, the jab
 * packet and Lunge's registry entries. The spear behaviour itself is in {@code spear/} (ported 26.x classes).
 */
public final class Spears {
    static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, HavenBackports.ID);
    static final DeferredRegister<MemoryModuleType<?>> MEMORIES = DeferredRegister.create(Registries.MEMORY_MODULE_TYPE, HavenBackports.ID);

    public static final ResourceKey<DamageType> SPEAR_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, id("spear"));
    public static final TagKey<Item> SPEARS = ItemTags.create(ResourceLocation.withDefaultNamespace("spears"));
    public static final TagKey<EntityType<?>> CANNOT_BE_DISMOUNTED_BY_ITEM_USAGE =
            TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.withDefaultNamespace("cannot_be_dismounted_by_item_usage"));

    public static final DeferredHolder<CriterionTrigger<?>, SpearMobsTrigger> SPEAR_MOBS_TRIGGER = TRIGGERS.register("spear_mobs", SpearMobsTrigger::new);

    // 26.x MemoryModuleType.SPEAR_* (no codec: not saved, as in 26.x)
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> SPEAR_FLEEING_TIME = memory("spear_fleeing_time");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Vec3>> SPEAR_FLEEING_POSITION = memory("spear_fleeing_position");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Vec3>> SPEAR_CHARGE_POSITION = memory("spear_charge_position");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<Integer>> SPEAR_ENGAGE_TIME = memory("spear_engage_time");
    public static final DeferredHolder<MemoryModuleType<?>, MemoryModuleType<SpearBehaviors.SpearStatus>> SPEAR_STATUS = memory("spear_status");

    // 26.x SoundEvents.SPEAR_* (sounds.json from Mojang's asset store); the lunge sounds are named by lunge.json
    static final DeferredHolder<SoundEvent, SoundEvent> ATTACK = Content.sound("item.spear.attack");
    static final DeferredHolder<SoundEvent, SoundEvent> HIT = Content.sound("item.spear.hit");
    static final DeferredHolder<SoundEvent, SoundEvent> USE = Content.sound("item.spear.use");
    static final DeferredHolder<SoundEvent, SoundEvent> WOOD_ATTACK = Content.sound("item.spear_wood.attack");
    static final DeferredHolder<SoundEvent, SoundEvent> WOOD_HIT = Content.sound("item.spear_wood.hit");
    static final DeferredHolder<SoundEvent, SoundEvent> WOOD_USE = Content.sound("item.spear_wood.use");
    static {
        for (int level = 1; level <= 3; level++) {
            Content.sound("item.spear.lunge_" + level);
        }
    }
    private static final SpearItem.Sounds SOUNDS = new SpearItem.Sounds(ATTACK, HIT, USE, WOOD_ATTACK, WOOD_HIT, WOOD_USE);

    /** Spear item ids, in 26.x's creative tab order (Tabs). */
    static final List<String> IDS = List.of("wooden_spear", "stone_spear", "copper_spear", "iron_spear", "golden_spear", "diamond_spear", "netherite_spear");

    private Spears() {}

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, path);
    }

    private static <T> DeferredHolder<MemoryModuleType<?>, MemoryModuleType<T>> memory(String name) {
        return MEMORIES.register(name, () -> new MemoryModuleType<>(Optional.empty()));
    }

    static void register(IEventBus modBus) {
        // Items.*_SPEAR: spear(material, attackDuration, damageMultiplier, delay, dismountTime, dismountThreshold,
        // knockbackTime, knockbackThreshold, damageTime, damageThreshold). The wooden spear's fuel value
        // (cookingFuel(COOKING_TIME_WOOD_ITEMS_LARGE)) is a data map, written by tools/spear.py.
        spear("wooden_spear", Tiers.WOOD, "wooden", 0.65F, 0.7F, 0.75F, 5.0F, 14.0F, 10.0F, 5.1F, 15.0F, 4.6F, new Item.Properties());
        spear("stone_spear", Tiers.STONE, "stone", 0.75F, 0.82F, 0.7F, 4.5F, 13.0F, 9.0F, 5.1F, 13.75F, 4.6F, new Item.Properties());
        spear("copper_spear", Gear.COPPER_TIER, "copper", 0.85F, 0.82F, 0.65F, 4.0F, 12.0F, 8.25F, 5.1F, 12.5F, 4.6F, new Item.Properties());
        spear("iron_spear", Tiers.IRON, "iron", 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F, new Item.Properties());
        spear("golden_spear", Tiers.GOLD, "gold", 0.95F, 0.7F, 0.7F, 3.5F, 13.0F, 8.5F, 5.1F, 13.75F, 4.6F, new Item.Properties());
        spear("diamond_spear", Tiers.DIAMOND, "diamond", 1.05F, 1.075F, 0.5F, 3.0F, 10.0F, 6.5F, 5.1F, 10.0F, 4.6F, new Item.Properties());
        spear("netherite_spear", Tiers.NETHERITE, "netherite", 1.15F, 1.2F, 0.4F, 2.5F, 9.0F, 5.5F, 5.1F, 8.75F, 4.6F, new Item.Properties().fireResistant());
        TRIGGERS.register(modBus);
        MEMORIES.register(modBus);
        Lunge.register(modBus);
        modBus.addListener(RegisterPayloadHandlersEvent.class, e -> e.registrar("1").playToServer(StabPayload.TYPE, StabPayload.CODEC,
                (payload, context) -> stab((ServerPlayer) context.player())));
        // 26.x ServerGamePacketListenerImpl.handleAttack: no plain attack with a PIERCING_WEAPON in the main hand
        NeoForge.EVENT_BUS.addListener(AttackEntityEvent.class, e -> {
            if (e.getEntity().getMainHandItem().getItem() instanceof SpearItem) {
                e.setCanceled(true);
            }
        });
    }

    /**
     * 26.x ToolMaterial: durability, enchantability and damage bonus as 1.21.1's Tiers (the same values; copper is
     * Gear.COPPER_TIER), repair items the 26.x {@code <material>_tool_materials} tag.
     */
    private static void spear(String id, Tier tier, String material, float attackDuration, float damageMultiplier, float delay,
            float dismountTime, float dismountThreshold, float knockbackTime, float knockbackThreshold, float damageTime, float damageThreshold,
            Item.Properties properties) {
        TagKey<Item> repair = ItemTags.create(ResourceLocation.withDefaultNamespace(material + "_tool_materials"));
        Content.ITEM.put(id, Content.ITEMS.register(id, () -> new SpearItem(tier, repair, tier == Tiers.WOOD, SOUNDS, attackDuration, damageMultiplier,
                delay, dismountTime, dismountThreshold, knockbackTime, knockbackThreshold, damageTime, damageThreshold, properties)));
    }

    /** 26.x ServerGamePacketListenerImpl.handlePlayerAction, Action.STAB (5 ticks of tolerance on the charge). */
    private static void stab(ServerPlayer player) {
        player.resetLastActionTime();
        if (player.isSpectator() || SpearItem.cannotAttackWithItem(player, 5)) {
            return;
        }
        if (player.getMainHandItem().getItem() instanceof SpearItem spear) {
            spear.piercing().attack(player, EquipmentSlot.MAINHAND);
        }
    }

    /** 26.x ServerboundPlayerActionPacket.Action.STAB: 1.21.1's action enum has no STAB, so a payload of its own. */
    public record StabPayload() implements CustomPacketPayload {
        public static final Type<StabPayload> TYPE = new Type<>(id("stab"));
        public static final StreamCodec<ByteBuf, StabPayload> CODEC = StreamCodec.unit(new StabPayload());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
