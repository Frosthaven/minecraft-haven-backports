package dev.frosthaven.havenbackports.spear;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;

/** 26.x SpearMobsTrigger ({@code minecraft:spear_mobs}; here haven_backports:spear_mobs): the mobs stabbed in one charge.
 *  1.21.1's player field is a ContextAwarePredicate (26.x: a LootItemCondition holder); same JSON. */
public class SpearMobsTrigger extends SimpleCriterionTrigger<SpearMobsTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int number) {
        this.trigger(player, t -> t.matches(number));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<Integer> count) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count").forGetter(TriggerInstance::count)).apply(i, TriggerInstance::new));

        public boolean matches(int requiredCount) {
            return this.count.isEmpty() || requiredCount >= this.count.get();
        }
    }
}
