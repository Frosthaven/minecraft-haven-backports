package dev.frosthaven.havenbackports.spear;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Fields 26.x adds to Entity and LivingEntity for spears; mixin/EntityMixin and mixin/LivingEntityMixin carry them. */
public final class SpearState {
    private SpearState() {}

    /** 26.x Entity: {@code lastKnownSpeed} (position change over the last tick, set by computeSpeed). */
    public interface Known {
        Vec3 haven_backports$lastKnownSpeed();

        void haven_backports$computeSpeed();
    }

    /** 26.x LivingEntity: {@code recentKineticEnemies} (server, while a kinetic weapon is in use) and
     *  {@code lastKineticHitFeedbackTime} (client, entity event 2). */
    public interface Living {
        Object2LongMap<Entity> haven_backports$recentKineticEnemies();

        void haven_backports$setRecentKineticEnemies(Object2LongMap<Entity> map);

        long haven_backports$lastKineticHitFeedbackTime();

        void haven_backports$setLastKineticHitFeedbackTime(long time);
    }
}
