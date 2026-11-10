package dev.frosthaven.havenbackports.cube;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * 26.x SulfurCubeArchetype: what a Sulfur Cube becomes with a block of the archetype's items inside. A datapack
 * registry (haven_backports:sulfur_cube_archetype, Mojang's 13 files converted by tools/cube.py). 1.21.1 forms:
 * FloatProvider.codec for 26.x FloatProviders.codec, ConditionalEffect list without 26.x's validatedListCodec (the
 * same entries; 1.21.1's ConditionalEffect codec checks the loot context params).
 */
public record SulfurCubeArchetype(HolderSet<Item> items, List<AttributeEntry> attributeModifiers, boolean buoyant,
                                  Optional<ExplosionData> explosion, Optional<ContactDamage> contactDamage,
                                  KnockbackModifiers knockbackModifiers, SoundSettings soundSettings,
                                  List<ConditionalEffect<EnchantmentLocationBasedEffect>> locationBasedEffects) {
    public static final Codec<SulfurCubeArchetype> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
                    RegistryCodecs.homogeneousList(Registries.ITEM).fieldOf("items").forGetter(SulfurCubeArchetype::items),
                    AttributeEntry.CODEC.listOf().fieldOf("attribute_modifiers").forGetter(SulfurCubeArchetype::attributeModifiers),
                    Codec.BOOL.optionalFieldOf("buoyant", false).forGetter(SulfurCubeArchetype::buoyant),
                    ExplosionData.CODEC.optionalFieldOf("explosion").forGetter(SulfurCubeArchetype::explosion),
                    ContactDamage.CODEC.optionalFieldOf("contact_damage").forGetter(SulfurCubeArchetype::contactDamage),
                    KnockbackModifiers.CODEC.fieldOf("knockback_modifiers").forGetter(SulfurCubeArchetype::knockbackModifiers),
                    SoundSettings.CODEC.fieldOf("sound_settings").forGetter(SulfurCubeArchetype::soundSettings),
                    ConditionalEffect.codec(EnchantmentLocationBasedEffect.CODEC, LootContextParamSets.ENCHANTED_LOCATION).listOf()
                            .optionalFieldOf("location_based_effects", List.of()).forGetter(SulfurCubeArchetype::locationBasedEffects))
            .apply(i, SulfurCubeArchetype::new));
    public static final KnockbackModifiers DEFAULT_KNOCKBACK_MODIFIERS = new KnockbackModifiers(0.33F, 0.06F);

    /** 26.x DEFAULT_SOUND_SETTINGS: the regular archetype's hit and push sounds, threshold 0.2, cooldown 0.5. */
    public static SoundSettings defaultSoundSettings() {
        return new SoundSettings(Optional.of(CubeSounds.REGULAR_HIT), Optional.of(CubeSounds.REGULAR_PUSH), 0.2F, 0.5F);
    }

    public record AttributeEntry(Holder<Attribute> attribute, AttributeModifier modifier) {
        public static final Codec<AttributeEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Attribute.CODEC.fieldOf("attribute").forGetter(AttributeEntry::attribute),
                        AttributeModifier.MAP_CODEC.forGetter(AttributeEntry::modifier))
                .apply(i, AttributeEntry::new));
    }

    public record ContactDamage(Holder<DamageType> damageType, FloatProvider amount, boolean attributeToSource) {
        public static final Codec<ContactDamage> CODEC = RecordCodecBuilder.create(i -> i.group(
                        DamageType.CODEC.fieldOf("damage_type").forGetter(ContactDamage::damageType),
                        FloatProvider.codec(0.0F, Float.MAX_VALUE).fieldOf("amount").forGetter(ContactDamage::amount),
                        Codec.BOOL.fieldOf("attribute_to_source").forGetter(ContactDamage::attributeToSource))
                .apply(i, ContactDamage::new));
    }

    public record ExplosionData(int power, boolean causesFire, int fuse) {
        public static final Codec<ExplosionData> CODEC = RecordCodecBuilder.create(i -> i.group(
                        ExtraCodecs.NON_NEGATIVE_INT.fieldOf("power").forGetter(ExplosionData::power),
                        Codec.BOOL.fieldOf("causes_fire").forGetter(ExplosionData::causesFire),
                        ExtraCodecs.POSITIVE_INT.fieldOf("fuse").forGetter(ExplosionData::fuse))
                .apply(i, ExplosionData::new));
    }

    public record KnockbackModifiers(float horizontalPower, float verticalPower) {
        public static final Codec<KnockbackModifiers> CODEC = RecordCodecBuilder.create(i -> i.group(
                        Codec.FLOAT.fieldOf("horizontal_power").forGetter(KnockbackModifiers::horizontalPower),
                        Codec.FLOAT.fieldOf("vertical_power").forGetter(KnockbackModifiers::verticalPower))
                .apply(i, KnockbackModifiers::new));
    }

    public record SoundSettings(Optional<Holder<SoundEvent>> hitSound, Optional<Holder<SoundEvent>> pushSound, float pushSoundImpulseThreshold,
                                float pushSoundCooldown) {
        public static final Codec<SoundSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
                        SoundEvent.CODEC.optionalFieldOf("hit_sound").forGetter(SoundSettings::hitSound),
                        SoundEvent.CODEC.optionalFieldOf("push_sound").forGetter(SoundSettings::pushSound),
                        Codec.FLOAT.fieldOf("push_sound_impulse_threshold").forGetter(SoundSettings::pushSoundImpulseThreshold),
                        Codec.FLOAT.fieldOf("push_sound_cooldown").forGetter(SoundSettings::pushSoundCooldown))
                .apply(i, SoundSettings::new));
    }
}
