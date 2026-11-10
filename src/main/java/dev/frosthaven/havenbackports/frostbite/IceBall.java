package dev.frosthaven.havenbackports.frostbite;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 26.x IceBall with its 26.x base ThrowableBallProjectile folded in (1.21.1 has no such base): on an entity hit it
 * deals ceil(speed * 1.395) thrown damage and runs the post-attack enchantment effects of its item; on any hit it
 * breaks into 8 item particles, plays ICE_BALL_BREAK and is gone.
 */
public class IceBall extends ThrowableItemProjectile {
    private static final float BASE_DAMAGE = 1.395F;

    public IceBall(EntityType<? extends IceBall> type, Level level) {
        super(type, level);
    }

    public IceBall(Level level, LivingEntity mob, ItemStack itemStack) {
        super(FrostbiteContent.ICE_BALL.get(), mob, level);
        this.setItem(itemStack);
    }

    public IceBall(Level level, double x, double y, double z, ItemStack itemStack) {
        super(FrostbiteContent.ICE_BALL.get(), x, y, z, level);
        this.setItem(itemStack);
    }

    @Override
    protected Item getDefaultItem() {
        return FrostbiteContent.ICE_BALL_ITEM.get();
    }

    protected int getDamage(EntityHitResult hitResult) {
        float pow = (float) this.getDeltaMovement().length();
        return Mth.ceil(Math.clamp(pow * BASE_DAMAGE, 0.0F, 2.1474836E9F));
    }

    protected ParticleOptions getParticle() {
        return new ItemParticleOption(ParticleTypes.ITEM, this.getItem());
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            ParticleOptions particle = this.getParticle();
            for (int i = 0; i < 8; i++) {
                this.level().addParticle(particle, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hitResult) {
        super.onHitEntity(hitResult);
        if (this.level() instanceof ServerLevel serverLevel) {
            int damage = this.getDamage(hitResult);
            Entity entity = hitResult.getEntity();
            DamageSource source = this.damageSources().thrown(this, this.getOwner());
            entity.hurt(source, damage);
            if (damage > 0 && entity instanceof LivingEntity mob) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(serverLevel, mob, source, this.getWeaponItem());
            }
        }
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!this.level().isClientSide()) {
            this.level().broadcastEntityEvent(this, (byte) 3);
            Vec3 at = hitResult.getLocation();
            this.level().playSound(null, at.x, at.y, at.z, FrostbiteContent.ICE_BALL_BREAK.get(), SoundSource.NEUTRAL, 0.5F,
                    0.4F / (this.level().getRandom().nextFloat() * 0.4F + 0.8F));
            this.discard();
        }
    }

    @Nullable
    @Override
    public ItemStack getWeaponItem() {
        return this.getItem();
    }
}
