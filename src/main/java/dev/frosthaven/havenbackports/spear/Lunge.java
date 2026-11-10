package dev.frosthaven.havenbackports.spear;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.frosthaven.havenbackports.HavenBackports;
import dev.frosthaven.havenbackports.mixin.PlayerAccessor;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/**
 * What 26.x's Lunge (data/haven_backports/enchantment/lunge.json, tools/spear.py) needs that 1.21.1 lacks, each the 26.x
 * class of the same name: the {@code post_piercing_attack} effect component (run by
 * {@link #doPostPiercingAttackEffects}, 26.x EnchantmentHelper), the entity effects {@code apply_exhaustion},
 * {@code apply_impulse} and {@code play_sound} (26.x's sound-list form; 1.21.1's takes one sound), and the entity
 * predicate parts: 26.x EntityFlagsPredicate's {@code is_in_water} and {@code is_fall_flying} ({@code flags}) and 26.x
 * PlayerPredicate's {@code food} ({@code player_food}).
 * <p>{@code applyPostImpulseGraceTime(10)}: 1.21.1 keeps the impulse grace counter on Player only (26.x: LivingEntity),
 * so only players get it, as only players ever had it read.
 */
public final class Lunge {
    static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.ENCHANTMENT_EFFECT_COMPONENT_TYPE, HavenBackports.ID);
    static final DeferredRegister<MapCodec<? extends EnchantmentEntityEffect>> EFFECTS = DeferredRegister.create(Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE, HavenBackports.ID);
    static final DeferredRegister<MapCodec<? extends EntitySubPredicate>> PREDICATES = DeferredRegister.create(Registries.ENTITY_SUB_PREDICATE_TYPE, HavenBackports.ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ConditionalEffect<EnchantmentEntityEffect>>>> POST_PIERCING_ATTACK =
            COMPONENTS.register("post_piercing_attack", () -> DataComponentType.<List<ConditionalEffect<EnchantmentEntityEffect>>>builder()
                    .persistent(ConditionalEffect.codec(EnchantmentEntityEffect.CODEC, LootContextParamSets.ENCHANTED_DAMAGE).listOf()).build());

    static {
        EFFECTS.register("apply_exhaustion", () -> ApplyExhaustion.CODEC);
        EFFECTS.register("apply_impulse", () -> ApplyEntityImpulse.CODEC);
        EFFECTS.register("play_sound", () -> PlaySoundEffect.CODEC);
        PREDICATES.register("flags", () -> Flags.CODEC);
        PREDICATES.register("player_food", () -> PlayerFood.CODEC);
    }

    private Lunge() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
        EFFECTS.register(modBus);
        PREDICATES.register(modBus);
    }

    /** 26.x EnchantmentHelper.doPostPiercingAttackEffects + Enchantment.doPostPiercingAttack. */
    public static void doPostPiercingAttackEffects(ServerLevel serverLevel, LivingEntity user) {
        EnchantmentHelper.runIterationOnItem(user.getWeaponItem(), EquipmentSlot.MAINHAND, user, (enchantment, level, item) ->
                Enchantment.applyEffects(enchantment.value().getEffects(POST_PIERCING_ATTACK.get()),
                        Enchantment.entityContext(serverLevel, level, user, user.position()),
                        e -> e.apply(serverLevel, level, item, user, user.position())));
    }

    public record ApplyExhaustion(LevelBasedValue amount) implements EnchantmentEntityEffect {
        public static final MapCodec<ApplyExhaustion> CODEC = RecordCodecBuilder.mapCodec(
                i -> i.group(LevelBasedValue.CODEC.fieldOf("amount").forGetter(ApplyExhaustion::amount)).apply(i, ApplyExhaustion::new));

        @Override
        public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
            if (entity instanceof Player player) {
                player.causeFoodExhaustion(this.amount.calculate(enchantmentLevel));
            }
        }

        @Override
        public MapCodec<ApplyExhaustion> codec() {
            return CODEC;
        }
    }

    /** 26.x getLookQuaternion = calculateViewQuaternion(xRot, yRot); 1.21.1's syncVelocity is hurtMarked. */
    public record ApplyEntityImpulse(Vec3 direction, Vec3 coordinateScale, LevelBasedValue magnitude) implements EnchantmentEntityEffect {
        public static final MapCodec<ApplyEntityImpulse> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Vec3.CODEC.fieldOf("direction").forGetter(ApplyEntityImpulse::direction),
                Vec3.CODEC.fieldOf("coordinate_scale").forGetter(ApplyEntityImpulse::coordinateScale),
                LevelBasedValue.CODEC.fieldOf("magnitude").forGetter(ApplyEntityImpulse::magnitude)).apply(i, ApplyEntityImpulse::new));

        @Override
        public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
            Quaternionf look = new Quaternionf().rotationY(-entity.getYRot() * (float) (Math.PI / 180.0)).rotateX(entity.getXRot() * (float) (Math.PI / 180.0));
            Vector3d direction = look.transform(this.direction.x, this.direction.y, this.direction.z, new Vector3d())
                    .mul(this.coordinateScale.x, this.coordinateScale.y, this.coordinateScale.z)
                    .mul(this.magnitude.calculate(enchantmentLevel));
            entity.addDeltaMovement(new Vec3(direction.x, direction.y, direction.z));
            if (entity instanceof ServerPlayer player) {
                player.connection.send(new ClientboundSetEntityMotionPacket(entity));
                entity.hurtMarked = false;
            } else {
                entity.hurtMarked = true;
            }
            if (entity instanceof Player player) {
                PlayerAccessor access = (PlayerAccessor) player;
                access.haven_backports$setGraceTime(Math.max(access.haven_backports$graceTime(), 10));
            }
        }

        @Override
        public MapCodec<ApplyEntityImpulse> codec() {
            return CODEC;
        }
    }

    /** 26.x PlaySoundEffect: the sound for the level (index level - 1, clamped). 26.x's single-or-list codec. */
    public record PlaySoundEffect(List<Holder<SoundEvent>> soundEvents, FloatProvider volume, FloatProvider pitch) implements EnchantmentEntityEffect {
        private static final Codec<List<Holder<SoundEvent>>> SOUNDS = Codec.either(SoundEvent.CODEC, SoundEvent.CODEC.listOf(1, 255))
                .xmap(e -> e.map(List::of, l -> l), l -> l.size() == 1 ? Either.left(l.getFirst()) : Either.right(l));
        public static final MapCodec<PlaySoundEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                SOUNDS.fieldOf("sound").forGetter(PlaySoundEffect::soundEvents),
                FloatProvider.codec(1.0E-5F, 10.0F).fieldOf("volume").forGetter(PlaySoundEffect::volume),
                FloatProvider.codec(1.0E-5F, 2.0F).fieldOf("pitch").forGetter(PlaySoundEffect::pitch)).apply(i, PlaySoundEffect::new));

        @Override
        public void apply(ServerLevel serverLevel, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 position) {
            if (!entity.isSilent()) {
                RandomSource random = entity.getRandom();
                int index = (int) Math.clamp((long) (enchantmentLevel - 1), 0, this.soundEvents.size() - 1);
                serverLevel.playSound(null, position.x(), position.y(), position.z(), this.soundEvents.get(index), entity.getSoundSource(),
                        this.volume.sample(random), this.pitch.sample(random));
            }
        }

        @Override
        public MapCodec<PlaySoundEffect> codec() {
            return CODEC;
        }
    }

    /** 26.x EntityFlagsPredicate's two new flags (1.21.1's {@code flags} keeps the others). */
    public record Flags(Optional<Boolean> isInWater, Optional<Boolean> isFallFlying) implements EntitySubPredicate {
        public static final MapCodec<Flags> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("is_in_water").forGetter(Flags::isInWater),
                Codec.BOOL.optionalFieldOf("is_fall_flying").forGetter(Flags::isFallFlying)).apply(i, Flags::new));

        @Override
        public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
            if (this.isInWater.isPresent() && entity.isInWater() != this.isInWater.get()) {
                return false;
            }
            return !(this.isFallFlying.isPresent() && entity instanceof LivingEntity living && living.isFallFlying() != this.isFallFlying.get());
        }

        @Override
        public MapCodec<Flags> codec() {
            return CODEC;
        }
    }

    /** 26.x PlayerPredicate's {@code food} (FoodPredicate: level, saturation); like every player predicate, server players only. */
    public record PlayerFood(MinMaxBounds.Ints level, MinMaxBounds.Doubles saturation) implements EntitySubPredicate {
        private static final MapCodec<PlayerFood> FOOD = RecordCodecBuilder.mapCodec(i -> i.group(
                MinMaxBounds.Ints.CODEC.optionalFieldOf("level", MinMaxBounds.Ints.ANY).forGetter(PlayerFood::level),
                MinMaxBounds.Doubles.CODEC.optionalFieldOf("saturation", MinMaxBounds.Doubles.ANY).forGetter(PlayerFood::saturation)).apply(i, PlayerFood::new));
        public static final MapCodec<PlayerFood> CODEC = FOOD.codec().fieldOf("food");

        @Override
        public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
            return entity instanceof ServerPlayer player && this.level.matches(player.getFoodData().getFoodLevel())
                    && this.saturation.matches(player.getFoodData().getSaturationLevel());
        }

        @Override
        public MapCodec<PlayerFood> codec() {
            return CODEC;
        }
    }
}
