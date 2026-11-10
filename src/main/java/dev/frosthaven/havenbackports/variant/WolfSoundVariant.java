package dev.frosthaven.havenbackports.variant;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/** Mojang's 26.x WolfSoundVariant: the sounds of an adult and of a puppy. Sounds 1.21.1 lacks are written inline (direct). */
public record WolfSoundVariant(SoundSet adultSounds, SoundSet babySounds) {
    public record SoundSet(Optional<Holder<SoundEvent>> ambientSound, Optional<Holder<SoundEvent>> deathSound, Optional<Holder<SoundEvent>> growlSound,
                           Optional<Holder<SoundEvent>> hurtSound, Optional<Holder<SoundEvent>> pantSound, Optional<Holder<SoundEvent>> stepSound,
                           Optional<Holder<SoundEvent>> whineSound) {
        static final Codec<SoundSet> CODEC = RecordCodecBuilder.create(i -> i.group(
                SoundEvent.CODEC.optionalFieldOf("ambient_sound").forGetter(SoundSet::ambientSound),
                SoundEvent.CODEC.optionalFieldOf("death_sound").forGetter(SoundSet::deathSound),
                SoundEvent.CODEC.optionalFieldOf("growl_sound").forGetter(SoundSet::growlSound),
                SoundEvent.CODEC.optionalFieldOf("hurt_sound").forGetter(SoundSet::hurtSound),
                SoundEvent.CODEC.optionalFieldOf("pant_sound").forGetter(SoundSet::pantSound),
                SoundEvent.CODEC.optionalFieldOf("step_sound").forGetter(SoundSet::stepSound),
                SoundEvent.CODEC.optionalFieldOf("whine_sound").forGetter(SoundSet::whineSound)).apply(i, SoundSet::new));
    }

    public static final Codec<WolfSoundVariant> CODEC = RecordCodecBuilder.create(i -> i.group(
            SoundSet.CODEC.fieldOf("adult_sounds").forGetter(WolfSoundVariant::adultSounds),
            SoundSet.CODEC.fieldOf("baby_sounds").forGetter(WolfSoundVariant::babySounds)).apply(i, WolfSoundVariant::new));
}
