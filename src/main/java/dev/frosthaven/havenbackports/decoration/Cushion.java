package dev.frosthaven.havenbackports.decoration;

import dev.frosthaven.havenbackports.Cushions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Mojang's 26.x Cushion: a seat that rests on a block, in 16 colors; it breaks when its support goes, in fire or
 * lava, by lightning or a hit, and drops itself (with its name). 1.21.1 forms: the color is a byte (1.21.1 has no
 * DYE_COLOR serializer; DyeColor ids); 26.x's BlockAttachedEntity tickAtCheckInterval, mob griefing rule and explosion
 * rule are here (1.21.1's BlockAttachedEntity has none of them); the fluid check is 26.x's: lava burns it
 * (Entity.lavaHurt), water puts it out; 26.x's CUSHION_COLOR item component is the item's own color. Not taken: the
 * 20 ticks a cushion dropped by lightning cannot be burnt (1.21.1's ItemEntity has no invulnerability timer).
 */
public class Cushion extends BlockAttachedEntity {
    private static final EntityDataAccessor<Byte> DATA_COLOR = SynchedEntityData.defineId(Cushion.class, EntityDataSerializers.BYTE);
    private int ticksSinceLastCheck;

    public Cushion(EntityType<? extends Cushion> type, Level level) {
        super(type, level);
    }

    public DyeColor getColor() {
        return DyeColor.byId(this.entityData.get(DATA_COLOR));
    }

    public void setColor(DyeColor color) {
        this.entityData.set(DATA_COLOR, (byte) color.getId());
    }

    @Override
    public void dropItem(@Nullable Entity causedBy) {
        this.playSound(Cushions.BREAK.get(), 1.0F, 1.0F);
        this.showBreakingParticles();
        if (this.level() instanceof ServerLevel level && level.getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)
                && !(causedBy instanceof Player player && player.hasInfiniteMaterials())) {
            ItemStack stack = new ItemStack(Cushions.item(this.getColor()));
            stack.set(DataComponents.CUSTOM_NAME, this.getCustomName());
            this.spawnAtLocation(stack);   // 26.x also makes it invulnerable for 20 ticks after lightning: 1.21.1 items have no such timer
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && (!player.mayBuild() || !this.level().isClientSide() && !this.level().mayInteract(player, this.pos))) {
            return false;
        }
        if (!this.level().isClientSide() && !this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING) && source.getEntity() instanceof Mob) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        Entity direct = explosion.getDirectSourceEntity();
        if (direct != null && direct.isInWater()) {
            return true;
        }
        Explosion.BlockInteraction interaction = explosion.getBlockInteraction();
        return interaction == Explosion.BlockInteraction.KEEP || interaction == Explosion.BlockInteraction.TRIGGER_BLOCK || super.ignoreExplosion(explosion);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || this.isVehicle()) {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide() && player.startRiding(this)) {
            this.playSound(Cushions.SIT.get(), 1.0F, 1.0F);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!this.level().isClientSide() && this.getRemovalReason() == null) {
            this.playSound(Cushions.GET_UP.get(), 1.0F, 1.0F);
        }
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Cushions.item(this.getColor()));
    }

    /** 26.x BlockAttachedEntity.tick with its tickAtCheckInterval hook. */
    @Override
    public void tick() {
        if (this.level() instanceof ServerLevel level) {
            this.checkBelowWorld();
            if (this.ticksSinceLastCheck++ >= 100) {
                this.ticksSinceLastCheck = 0;
                this.tickAtCheckInterval(level);
                if (!this.isRemoved() && !this.survives()) {
                    this.discard();
                    this.dropItem(null);
                }
            }
        }
    }

    private void tickAtCheckInterval(ServerLevel level) {
        BlockPos pos = this.blockPosition();
        FluidState fluid = level.getBlockState(pos).getFluidState();
        if (!fluid.isEmpty() && dev.frosthaven.havenbackports.entity.Traversal.collidedWithFluid(this, fluid, pos, this.position(), this.position())) {
            if (fluid.is(FluidTags.LAVA)) {
                this.lavaHurt();   // 26.x LavaFluid.entityInside: LAVA_IGNITE then lavaHurt
            } else if (fluid.is(FluidTags.WATER)) {
                this.clearFire();   // 26.x WaterFluid.entityInside: EXTINGUISH
            }
        }
        this.destroyIfInFire(level);
    }

    public void destroyIfInFire(ServerLevel level) {
        if (this.isRemoved()) {
            return;
        }
        AABB box = nextDeflated(this.getBoundingBox());
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (level.getBlockState(p).is(BlockTags.FIRE)) {
                this.hurt(this.damageSources().inFire(), 1.0F);
                return;
            }
        }
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt) {
        if (!this.isRemoved()) {
            this.kill();
            this.dropItem(bolt);
        }
    }

    @Override
    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z);
        super.setPos(x, y, z);
    }

    private void showBreakingParticles() {
        if (this.level() instanceof ServerLevel level) {
            var wool = BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(this.getColor().getName() + "_wool"));
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, wool.defaultBlockState()), this.getX(), this.getY(0.6666666666666666),
                    this.getZ(), 10, this.getBbWidth() / 4.0F, this.getBbHeight() / 4.0F, this.getBbWidth() / 4.0F, 0.05);
        }
    }

    public static boolean canBePlacedAt(Level level, AABB box) {
        return wouldSurviveAt(level, box) && !isAnchorBuried(level, box);
    }

    public static boolean wouldSurviveAt(Level level, AABB box) {
        return hasAnchorBelow(level, box) && !isCoveredBySuffocatingBlocks(level, box);
    }

    private static boolean hasAnchorBelow(Level level, AABB box) {
        AABB anchor = new AABB(box.minX, box.minY - 0.015625, box.minZ, Math.nextDown(box.maxX), box.minY, Math.nextDown(box.maxZ));
        AABB search = anchor.expandTowards(0.0, -0.125, 0.0);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ), BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
            VoxelShape shape = level.getBlockState(p).getShape(level, p);
            if (!shape.isEmpty() && shape.bounds().move(p).intersects(anchor)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAnchorBuried(Level level, AABB box) {
        AABB slice = nextDeflated(new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + 0.015625, box.maxZ));
        VoxelShape exposed = Shapes.create(slice);
        for (VoxelShape collider : level.getBlockCollisions(null, slice)) {
            exposed = Shapes.join(exposed, collider, BooleanOp.ONLY_FIRST);
            if (exposed.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCoveredBySuffocatingBlocks(Level level, AABB box) {
        AABB b = nextDeflated(box);
        for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(b.minX, b.minY, b.minZ), BlockPos.containing(b.maxX, b.maxY, b.maxZ))) {
            if (!level.getBlockState(p).isSuffocating(level, p)) {
                return false;
            }
        }
        return true;
    }

    /** 26.x AABB.nextDeflated. */
    static AABB nextDeflated(AABB b) {
        return new AABB(Math.nextUp(b.minX), Math.nextUp(b.minY), Math.nextUp(b.minZ), Math.nextDown(b.maxX), Math.nextDown(b.maxY), Math.nextDown(b.maxZ));
    }

    @Override
    public boolean survives() {
        return wouldSurviveAt(this.level(), this.getBoundingBox());
    }

    @Override
    protected void recalculateBoundingBox() {
        this.setBoundingBox(this.makeBoundingBox());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, (byte) DyeColor.WHITE.getId());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("color", this.getColor().getSerializedName());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.setColor(DyeColor.byName(tag.getString("color"), DyeColor.WHITE));
    }
}
