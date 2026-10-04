package com.h3xbit.flushframes.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Painting.class)
public abstract class AttachPaintingMixin extends HangingEntity {
    protected AttachPaintingMixin(EntityType<? extends HangingEntity> entityType, Level level) {
        super(entityType, level);
    }

    // How far the box was pushed from vanilla's spot, so the support check can undo it
    @Unique
    private double wallShift;

    // Vanilla finds the blocks a painting hangs on by stepping back from its box - undo our shift first
    @Override
    protected AABB calculateSupportBox() {
        Direction direction = this.getDirection();
        double back = -this.wallShift - 0.5;
        return this.getBoundingBox().move(direction.getStepX() * back, 0, direction.getStepZ() * back).deflate(1.0E-7);
    }

    @Inject(method = "calculateBoundingBox", at = @At("RETURN"), cancellable = true)
    private void calculateBoundingBox(BlockPos blockPos, Direction direction, CallbackInfoReturnable<AABB> returnInfo) {

        // Stored Variables
        double shiftToBlockWall = -0.46875F; //Default: -0.46875F;
        double shiftPositon = 0.0625F; //Default: 0.0625F;
        this.wallShift = 0;

        // Cashed references
        Painting painting = (Painting) (Object) this;
        BlockState supportState = painting.level().getBlockState(blockPos.relative(direction.getOpposite()));
        Block block = supportState.getBlock();
        PaintingVariant variant = painting.getVariant().value();

        // Only paintings the size of what they hang on: 1x1 like an item frame, or 1x2 on one whole door
        if (variant.width() != 1 || variant.height() > 2) {
            return;
        }
        if (variant.height() == 2 && !(block instanceof DoorBlock && supportState.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER)) {
            return;
        }
        // And every block behind it has to be the same block
        AABB supportBox = returnInfo.getReturnValue().move(direction.getStepX() * -0.5, 0, direction.getStepZ() * -0.5).deflate(1.0E-7);
        for (BlockPos support : BlockPos.betweenClosed(BlockPos.containing(supportBox.minX, supportBox.minY, supportBox.minZ), BlockPos.containing(supportBox.maxX, supportBox.maxY, supportBox.maxZ))) {
            if (painting.level().getBlockState(support).getBlock() != block) {
                return;
            }
        }

        // Shift frame logic
        if (block instanceof CrossCollisionBlock && direction.getAxis() != Direction.Axis.Y) {
            // Iron bars / glass panes: adjust -0.90625F here
            double flushShift = block instanceof IronBarsBlock ? -0.90625F : -0.84375F;
            shiftToBlockWall = checkDirection(painting, blockPos, direction, flushShift);
        }

        if (block instanceof WallBlock && direction.getAxis() != Direction.Axis.Y) {
            shiftToBlockWall = checkDirection(painting, blockPos, direction, getShiftToBlockWall(supportState));
        }

        if (block instanceof FenceGateBlock && direction.getAxis() == supportState.getValue(FenceGateBlock.FACING).getAxis()) {
            shiftToBlockWall = -0.84375F;
        }
        if (block instanceof LightningRodBlock && direction.getAxis() != supportState.getValue(LightningRodBlock.FACING).getAxis()) {
            shiftToBlockWall = -0.84375F;
        }

        if (block instanceof DoorBlock) {
            if(supportState.getValue(DoorBlock.OPEN)){
                if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT){
                    shiftToBlockWall = placeOnDoorFace(painting, blockPos, direction, supportState.getValue(DoorBlock.FACING).getCounterClockWise(), supportState.getValue(DoorBlock.FACING).getOpposite(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(painting, blockPos, direction, supportState.getValue(DoorBlock.FACING).getClockWise(), supportState.getValue(DoorBlock.FACING), -0.46875F, shiftToBlockWall);
                }
                if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT){
                    shiftToBlockWall = placeOnDoorFace(painting, blockPos, direction, supportState.getValue(DoorBlock.FACING).getClockWise(), supportState.getValue(DoorBlock.FACING).getOpposite(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(painting, blockPos, direction, supportState.getValue(DoorBlock.FACING).getCounterClockWise(), supportState.getValue(DoorBlock.FACING), -0.46875F, shiftToBlockWall);
                }
            }
            if(!supportState.getValue(DoorBlock.OPEN)){
                if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT){
                    shiftToBlockWall = placeOnDoorFace(painting, blockPos, direction, supportState.getValue(DoorBlock.FACING), supportState.getValue(DoorBlock.FACING).getClockWise(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(painting, blockPos, direction, supportState.getValue(DoorBlock.FACING).getOpposite(), supportState.getValue(DoorBlock.FACING).getCounterClockWise(), -0.46875F, shiftToBlockWall);
                }
                if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT){
                    shiftToBlockWall = placeOnDoorFace(painting, blockPos, direction, supportState.getValue(DoorBlock.FACING), supportState.getValue(DoorBlock.FACING).getCounterClockWise(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(painting, blockPos, direction, supportState.getValue(DoorBlock.FACING).getOpposite(), supportState.getValue(DoorBlock.FACING).getClockWise(), -0.46875F, shiftToBlockWall);
                }
            }
            blockPos = this.pos;
            direction = this.getDirection();
        }
        if (block instanceof ChainBlock && direction.getAxis() != supportState.getValue(ChainBlock.AXIS)) {
            shiftToBlockWall = -0.875F;
        }
        if (block instanceof LanternBlock) {
            if (direction.getAxis() != Direction.Axis.Y) {
                shiftToBlockWall = -0.78125F;
            }
        }
        if (block instanceof DriedGhastBlock) {
            shiftToBlockWall = -0.65625F;
        }
        if (block instanceof AbstractChestBlock) {
            shiftToBlockWall = -0.53125F;
        }
        if (block instanceof DecoratedPotBlock && direction.getAxis() != Direction.Axis.Y) {
            shiftToBlockWall = -0.53125F;
        }
        if (block instanceof BambooStalkBlock && direction.getAxis() != Direction.Axis.Y) {
            double stalkShift = supportState.getValue(BambooStalkBlock.AGE) == BambooStalkBlock.AGE_THIN_BAMBOO ? -0.90625F : -0.875F;
            Vec3 offset = supportState.getOffset(blockPos.relative(direction.getOpposite()));
            shiftToBlockWall = stalkShift + offset.get(direction.getAxis()) * direction.getAxisDirection().getStep();
        }

        // Base minecraft shift logic
        Vec3 position = Vec3.atCenterOf(blockPos).relative(direction, shiftToBlockWall)
                .relative(direction.getCounterClockWise(), offsetForPaintingSize(variant.width()))
                .relative(Direction.UP, offsetForPaintingSize(variant.height()));
        double width = variant.width();
        double height = variant.height();

        Direction.Axis axis = direction.getAxis();
        double xSize = axis == Direction.Axis.X ? shiftPositon : width;
        double ySize = height;
        double zSize = axis == Direction.Axis.Z ? shiftPositon : width;

        this.wallShift = shiftToBlockWall + 0.46875F;
        returnInfo.setReturnValue(AABB.ofSize(position, xSize, ySize, zSize));
    }

    // Moves the painting onto one face of a door. Runs when the painting is already on that face, or on the
    // face it comes from when the door toggles.
    @Unique
    private double placeOnDoorFace(Painting painting, BlockPos blockPos, Direction direction, Direction face, Direction arrivingFrom, double shift, double shiftToBlockWall) {
        if (direction != face && direction != arrivingFrom) {
            return shiftToBlockWall;
        }
        this.pos = blockPos.relative(direction.getOpposite()).relative(face);
        // set facing
        this.setDirectionRaw(face);
        this.setXRot(0.0F);
        this.setYRot(face.get2DDataValue() * 90);
        return this.getDirection() == face ? shift : shiftToBlockWall;
    }

    @Unique
    private double checkDirection(Painting painting, BlockPos framePos, Direction direction, double flushShift) {
        BlockPos supportPos = framePos.relative(direction.getOpposite());
        AABB nearby = new AABB(supportPos).inflate(1.0);

        if (canPlaceOnWall(painting, nearby, supportPos, direction.getClockWise(), direction.getCounterClockWise())) {
            return flushShift;
        }
        return -1;
    }

    @Unique
    private boolean canPlaceOnWall(Painting painting, AABB nearby, BlockPos wallPos, Direction sideA, Direction sideB) {
        for (HangingEntity other : painting.level().getEntitiesOfClass(HangingEntity.class, nearby, entity -> entity instanceof ItemFrame || entity instanceof Painting)) {
            if (other == painting) {
                continue;
            }
            if (isHangingOnSide(other, wallPos, sideA) || isHangingOnSide(other, wallPos, sideB)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static boolean isHangingOnSide(HangingEntity other, BlockPos wallPos, Direction side) {
        return other.getPos().equals(wallPos.relative(side)) && other.getDirection() == side;
    }

    @Unique
    private double getShiftToBlockWall(BlockState supportState) {
        return supportState.getValue(WallBlock.UP) ? WallShift.WALL_PILER : WallShift.WALL_FLAT;
    }

    // Same as vanilla's Painting.offsetForPaintingSize - even sizes are centered between two blocks
    @Unique
    private static double offsetForPaintingSize(int size) {
        return size % 2 == 0 ? 0.5 : 0.0;
    }
}
