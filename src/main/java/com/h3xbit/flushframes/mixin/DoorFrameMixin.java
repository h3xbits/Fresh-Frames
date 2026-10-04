package com.h3xbit.flushframes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Frames and paintings on a door or trapdoor move with the panel when it opens/closes, staying on the
// same face. Runs on both sides every tick - a frame's direction is only sent to the client when it
// spawns, so the client has to work out the new direction itself from the same block change.
@Mixin({ItemFrame.class, Painting.class})
public abstract class DoorFrameMixin extends HangingEntity {
    @Unique
    private BlockState lastSupportState;

    @Unique
    private Direction lastDirection;

    protected DoorFrameMixin(EntityType<? extends HangingEntity> entityType, Level level) {
        super(entityType, level);
    }

    // Only the server moves frames on doors. Its new facing is synced to the client but the anchor
    // block isn't, so when the facing changes move the client's anchor onto the same door.
    @Inject(method = "onSyncedDataUpdated", at = @At("HEAD"))
    private void keepDoorAnchor(EntityDataAccessor<?> accessor, CallbackInfo ci) {
        Direction direction = this.getDirection();
        if (this.level().isClientSide() && this.pos != null && this.lastDirection != null && this.lastDirection != direction) {
            BlockPos supportPos = this.pos.relative(this.lastDirection.getOpposite());
            Block support = this.level().getBlockState(supportPos).getBlock();
            if (support instanceof DoorBlock || support instanceof TrapDoorBlock) {
                this.pos = supportPos.relative(direction);
            }
        }
        this.lastDirection = direction;
    }

    @Override
    public void tick() {
        super.tick();

        BlockPos supportPos = this.pos.relative(this.getDirection().getOpposite());
        BlockState supportState = this.level().getBlockState(supportPos);
        BlockState previousState = this.lastSupportState;
        this.lastSupportState = supportState;

        if (previousState == null || previousState.getBlock() != supportState.getBlock()) {
            return;
        }

        // Doors and trapdoors are placed by AttachItemFrameMixin.createBoundingBox, just re-run it when they change
        if (previousState != supportState && (supportState.getBlock() instanceof DoorBlock || supportState.getBlock() instanceof TrapDoorBlock)) {
            this.recalculateBoundingBox();
            this.needsSync = true;
        }
    }
}
