package dev.frosthaven.havenbackports;

import java.util.Optional;
import net.minecraft.world.level.Level;

/**
 * 26.x drives eyeblossoms and creaking hearts from the day timeline (data/minecraft/timeline/day.json:
 * gameplay/eyeblossom_open and gameplay/creaking_active are true from tick 12600 to 23401), which runs only in
 * overworld-type dimensions (#minecraft:in_overworld). 1.21.1 has no timelines: the same window of the day time, in
 * dimensions 1.21.1 calls natural (the overworld types); elsewhere there is no value (blocks keep their state).
 */
public final class Night26 {
    private Night26() {}

    public static Optional<Boolean> active(Level level) {
        if (!level.dimensionType().natural()) {
            return Optional.empty();
        }
        long t = level.getDayTime() % 24000L;
        return Optional.of(t >= 12600L && t < 23401L);
    }
}
