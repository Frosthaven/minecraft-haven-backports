package dev.frosthaven.havenbackports.golem;

import dev.frosthaven.havenbackports.Golems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Mojang's 26.x CopperGolemStatueBlockEntity: keeps the golem's name, and turns back into a golem. */
public class CopperGolemStatueBlockEntity extends BlockEntity {
    public CopperGolemStatueBlockEntity(BlockPos pos, BlockState state) {
        super(Golems.STATUE_ENTITY.get(), pos, state);
    }

    public void createStatue(CopperGolem golem) {
        this.setComponents(DataComponentMap.builder().addAll(this.components()).set(DataComponents.CUSTOM_NAME, golem.getCustomName()).build());
        super.setChanged();
    }

    @Nullable
    public CopperGolem removeStatue(BlockState state) {
        CopperGolem golem = Golems.COPPER_GOLEM.get().create(this.level);   // 26.x: EntitySpawnReason.TRIGGERED
        if (golem == null) {
            return null;
        }
        golem.setCustomName(this.components().get(DataComponents.CUSTOM_NAME));
        BlockPos pos = this.getBlockPos();
        golem.moveTo(Vec3.atCenterOf(pos).x, pos.getY(), Vec3.atCenterOf(pos).z, state.getValue(CopperGolemStatueBlock.FACING).toYRot(), 0.0F);
        golem.yHeadRot = golem.getYRot();
        golem.yBodyRot = golem.getYRot();
        golem.playSpawnSound();
        return golem;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveCustomOnly(registries);
    }

    public ItemStack getItem(ItemStack stack, CopperGolemStatueBlock.Pose pose) {
        stack.applyComponents(this.collectComponents());
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(CopperGolemStatueBlock.POSE, pose));
        return stack;
    }
}
