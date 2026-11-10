package dev.frosthaven.havenbackports.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.havenbackports.Night26;
import dev.frosthaven.havenbackports.PaleGarden;
import dev.frosthaven.havenbackports.particle.TrailParticleOption;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x EyeblossomBlock, line for line: opens at night and closes by day (26.x's eyeblossom_open environment
 * attribute; {@link Night26}), with a long sound, a trail particle and a cascade of scheduled ticks to equal blooms
 * within 3 x 2 x 3 (each switching with the short sound). The open bloom hums on pale moss. Bees inside get poison
 * (26.x Bee.attractsBees is #bee_attractive; 1.21.1's bees go to #flowers, which holds both blooms).
 */
public class EyeblossomBlock extends FlowerBlock {
    public enum Type {
        OPEN(true, MobEffects.BLINDNESS, 11.0F, 16545810),
        CLOSED(false, MobEffects.CONFUSION, 7.0F, 6250335);

        final boolean open;
        final Holder<MobEffect> effect;
        final float effectDuration;
        final int particleColor;

        Type(boolean open, Holder<MobEffect> effect, float duration, int particleColor) {
            this.open = open;
            this.effect = effect;
            this.effectDuration = duration;
            this.particleColor = particleColor;
        }

        public Block block() {
            return (this.open ? PaleGarden.OPEN_EYEBLOSSOM : PaleGarden.CLOSED_EYEBLOSSOM).get();
        }

        public Type transform() {
            return fromBoolean(!this.open);
        }

        public static Type fromBoolean(boolean open) {
            return open ? OPEN : CLOSED;
        }

        public Supplier<SoundEvent> longSwitchSound() {
            return this.open ? PaleGarden.EYEBLOSSOM_OPEN_LONG : PaleGarden.EYEBLOSSOM_CLOSE_LONG;
        }

        Supplier<SoundEvent> shortSwitchSound() {
            return this.open ? PaleGarden.EYEBLOSSOM_OPEN : PaleGarden.EYEBLOSSOM_CLOSE;
        }

        public void spawnTransformParticle(ServerLevel level, BlockPos pos, RandomSource random) {
            Vec3 start = Vec3.atCenterOf(pos);
            double lifetime = 0.5 + random.nextDouble();
            Vec3 velocity = new Vec3(random.nextDouble() - 0.5, random.nextDouble() + 1.0, random.nextDouble() - 0.5);
            Vec3 target = start.add(velocity.scale(lifetime));
            level.sendParticles(new TrailParticleOption(target, this.particleColor, (int) (20.0 * lifetime)), start.x, start.y, start.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private final Type type;

    public EyeblossomBlock(Type type, Properties properties) {
        super(type.effect, type.effectDuration, properties);
        this.type = type;
    }

    @Override
    public MapCodec<? extends FlowerBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (this.type.open && random.nextInt(700) == 0 && level.getBlockState(pos.below()).is(PaleGarden.PALE_MOSS_BLOCK.get())) {
            level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), PaleGarden.EYEBLOSSOM_IDLE.get(), SoundSource.AMBIENT, 1.0F, 1.0F, false);
        }
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.tryChangingState(state, level, pos, random)) {
            level.playSound(null, pos, this.type.transform().longSwitchSound().get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        super.randomTick(state, level, pos, random);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (this.tryChangingState(state, level, pos, random)) {
            level.playSound(null, pos, this.type.transform().shortSwitchSound().get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        super.tick(state, level, pos, random);
    }

    private boolean tryChangingState(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean shouldBeOpen = Night26.active(level).orElse(this.type.open);
        if (shouldBeOpen == this.type.open) {
            return false;
        }
        Type next = this.type.transform();
        level.setBlockAndUpdate(pos, next.block().defaultBlockState());
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(state));
        next.spawnTransformParticle(level, pos, random);
        Scan26.forEach(level, pos.offset(-3, -2, -3), pos.offset(3, 2, 3), s -> s == state, (near, s) -> {
            double distance = Math.sqrt(pos.distSqr(near));
            int delay = random.nextIntBetweenInclusive((int) (distance * 5.0), (int) (distance * 10.0));
            level.scheduleTick(near, state.getBlock(), delay);
        });
        return true;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide() && level.getDifficulty() != Difficulty.PEACEFUL && entity instanceof Bee bee && state.is(BlockTags.FLOWERS)
                && !bee.hasEffect(MobEffects.POISON)) {
            bee.addEffect(new MobEffectInstance(MobEffects.POISON, 25));
        }
    }
}
