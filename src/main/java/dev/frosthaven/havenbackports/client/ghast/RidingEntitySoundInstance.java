package dev.frosthaven.havenbackports.client.ghast;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Mojang's 26.x RidingEntitySoundInstance (the happy ghast's riding loop). 26.x stops it when LocalPlayer's riding
 * sound id moves on; 1.21.1 has no such id, so GhastClient counts mounts and passes the count.
 */
public class RidingEntitySoundInstance extends AbstractTickableSoundInstance {
    private final LocalPlayer player;
    private final int ridingSoundId;
    private final java.util.function.IntSupplier currentId;
    private final Entity entity;
    private final boolean underwaterSound;
    private final float volumeMin, volumeMax, volumeAmplifier;

    public RidingEntitySoundInstance(LocalPlayer player, int ridingSoundId, java.util.function.IntSupplier currentId, Entity entity, boolean underwaterSound,
                                     SoundEvent sound, SoundSource source, float volumeMin, float volumeMax, float volumeAmplifier) {
        super(sound, source, SoundInstance.createUnseededRandom());
        this.player = player;
        this.ridingSoundId = ridingSoundId;
        this.currentId = currentId;
        this.entity = entity;
        this.x = entity.getX();
        this.y = entity.getY();
        this.z = entity.getZ();
        this.underwaterSound = underwaterSound;
        this.volumeMin = volumeMin;
        this.volumeMax = volumeMax;
        this.volumeAmplifier = volumeAmplifier;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = true;
        this.delay = 0;
        this.volume = volumeMin;
    }

    @Override
    public boolean canPlaySound() {
        return !entity.isSilent();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (entity.isRemoved() || !player.isPassenger() || player.getVehicle() != entity || currentId.getAsInt() != ridingSoundId) {
            this.stop();
        } else if (underwaterSound != entity.isUnderWater()) {
            this.volume = volumeMin;
        } else {
            float speed = (float) entity.getDeltaMovement().length();
            this.volume = speed >= 0.01F ? volumeAmplifier * Mth.clampedLerp(volumeMin, volumeMax, speed) : volumeMin;
            this.x = entity.getX();
            this.y = entity.getY();
            this.z = entity.getZ();
        }
    }
}
