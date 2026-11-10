package dev.frosthaven.havenbackports.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x's quad leash for the vanilla mobs that support it (Leashable.supportQuadLeash and getQuadLeashOffsets on
 * AbstractHorse, AbstractChestedHorse, ZombieHorse, Camel, Sniffer and AbstractBoat; Llama opts out): such a mob on a
 * lead to a happy ghast hangs from it on four ropes. 1.21.1's classes cannot carry the overrides, so they are read
 * here by class, with Mojang's numbers.
 */
public final class QuadLeash {
    private QuadLeash() {}

    public static boolean supports(Entity entity) {
        return entity instanceof AbstractHorse && !(entity instanceof Llama) || entity instanceof Sniffer || entity instanceof Boat;
    }

    public static Vec3[] offsets(Entity entity) {
        if (entity instanceof Camel) {
            return create(entity, 0.02, 0.48, 0.25, 0.82);
        }
        if (entity instanceof AbstractChestedHorse || entity instanceof ZombieHorse) {
            return create(entity, 0.04, 0.41, 0.18, 0.73);
        }
        if (entity instanceof AbstractHorse) {
            return create(entity, 0.04, 0.52, 0.23, 0.87);
        }
        if (entity instanceof Sniffer) {
            return create(entity, -0.01, 0.63, 0.38, 1.15);
        }
        return create(entity, 0.0, 0.64, 0.382, 0.88);   // AbstractBoat
    }

    /** 26.x Leashable.createQuadLeashOffsets. */
    public static Vec3[] create(Entity entity, double frontOffset, double frontBack, double leftRight, double height) {
        float width = entity.getBbWidth();
        double front = frontOffset * width, fb = frontBack * width, lr = leftRight * width, h = height * entity.getBbHeight();
        return new Vec3[]{new Vec3(-lr, h, fb + front), new Vec3(-lr, h, -fb + front), new Vec3(lr, h, -fb + front), new Vec3(lr, h, fb + front)};
    }

    /** 26.x Leashable defaults (elastic 6, snap 12) for a vanilla mob on a lead to a happy ghast; quad where supported. */
    public static Leash26.Rules rules(Entity entity) {
        boolean quad = supports(entity);
        return new Leash26.Rules() {
            @Override
            public boolean quad() {
                return quad;
            }
        };
    }
}
