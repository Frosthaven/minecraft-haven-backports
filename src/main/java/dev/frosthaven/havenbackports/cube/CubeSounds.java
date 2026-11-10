package dev.frosthaven.havenbackports.cube;

import dev.frosthaven.havenbackports.HavenBackports;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 26.x SoundEvents for the Sulfur Cube and its bucket (sounds.json from Mojang's asset store, tools/cube.py). */
public final class CubeSounds {
    static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, HavenBackports.ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> BUCKET_EMPTY = sound("item.bucket.empty_sulfur_cube");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUCKET_FILL = sound("item.bucket.fill_sulfur_cube");
    public static final DeferredHolder<SoundEvent, SoundEvent> ABSORB = sound("entity.sulfur_cube.absorb");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOUNCE = sound("entity.sulfur_cube.bounce");
    public static final DeferredHolder<SoundEvent, SoundEvent> DEATH = sound("entity.sulfur_cube.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> EJECT = sound("entity.sulfur_cube.eject");
    public static final DeferredHolder<SoundEvent, SoundEvent> HURT = sound("entity.sulfur_cube.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> JUMP = sound("entity.sulfur_cube.jump");
    public static final DeferredHolder<SoundEvent, SoundEvent> SQUISH = sound("entity.sulfur_cube.squish");
    public static final DeferredHolder<SoundEvent, SoundEvent> REGULAR_HIT = sound("entity.sulfur_cube.regular.hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> REGULAR_PUSH = sound("entity.sulfur_cube.regular.push");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMALL_DEATH = sound("entity.small_sulfur_cube.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMALL_HURT = sound("entity.small_sulfur_cube.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMALL_JUMP = sound("entity.small_sulfur_cube.jump");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMALL_SQUISH = sound("entity.small_sulfur_cube.squish");
    public static final DeferredHolder<SoundEvent, SoundEvent> SMALL_EAT = sound("entity.small_sulfur_cube.eat");

    static {
        // the archetypes' hit and push sounds (sulfur_cube_archetype data names them)
        for (String a : new String[]{"bouncy", "slow_bouncy", "slow_flat", "fast_flat", "light", "fast_sliding", "slow_sliding", "sticky",
                "high_resistance", "explosive", "hot"}) {   // cold uses fast_sliding's
            sound("entity.sulfur_cube." + a + ".hit");
            sound("entity.sulfur_cube." + a + ".push");
        }
    }

    private CubeSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(HavenBackports.ID, name)));
    }
}
