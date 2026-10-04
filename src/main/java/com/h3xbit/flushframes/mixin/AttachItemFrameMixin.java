package com.h3xbit.flushframes.mixin;

//import com.h3xbit.flushframes.ItemFrameBoxInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrame.class)
public abstract class AttachItemFrameMixin extends HangingEntity {
    protected AttachItemFrameMixin(EntityType<? extends HangingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public void setPos(double x, double y, double z) {
        BlockPos blockPos = BlockPos.containing(x, y, z);

        // Server position updates don't line up with our shifted frames (they decode to a spot next
        // to the frame), so after spawning the client keeps its own anchor and places the frame itself
        if (this.pos != null && !this.recreatingFromPacket && this.level() != null && this.level().isClientSide() && !blockPos.equals(this.pos)) {
            this.recalculateBoundingBox();
            return;
        }

        // A shifted frame's center sits in the block it's attached to, one block away from its
        // real anchor - keep the anchor and only move the raw position, otherwise the anchor
        // would drift onto the support block. pos is null on the very first call (from Entity's
        // constructor), so there's no anchor to compare against yet.
        if (this.pos != null && blockPos.distManhattan(this.pos) == 1) {
            this.setPosRaw(x, y, z);
            return;
        }

        this.pos = blockPos;
        this.recalculateBoundingBox();
        this.needsSync = true;
    }

    // set this to true always
    // If not the item frame will stop the pressure plate from moving
    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Unique
    private boolean recreatingFromPacket;

    @Inject(method = "recreateFromPacket", at = @At("HEAD"))
    private void startRecreate(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        this.recreatingFromPacket = true;
    }

    @Inject(method = "recreateFromPacket", at = @At("TAIL"))
    private void endRecreate(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        this.recreatingFromPacket = false;
    }

    @Inject(method = "createBoundingBox", at = @At("RETURN"), cancellable = true)
    private void createBoundingBox(BlockPos blockPos, Direction direction, boolean hasFramedMap, CallbackInfoReturnable<AABB> returnInfo) {

        // Stored Variables
        double shiftToBlockWall = -0.46875F; //Default: -0.46875F;
        double shiftPositon = 0.0625F; //Default: 0.0625F;
        double shiftDirection = 0.75F; //Default: 0.75F;

        // Cashed references
        ItemFrame itemFrame = (ItemFrame) (Object) this;
        BlockState supportState = itemFrame.level().getBlockState(blockPos.relative(direction.getOpposite()));
        Block block = supportState.getBlock();

        if (supportState.isAir() && direction.getAxis() != Direction.Axis.Y) {
            BlockState belowSupport = itemFrame.level().getBlockState(blockPos.relative(direction.getOpposite()).below());
            if (belowSupport.getBlock() instanceof BannerBlock) {
                supportState = belowSupport;
            }
        }
        // Shift frame logic
        if (block instanceof TrapDoorBlock) {
            if(supportState.getValue(TrapDoorBlock.OPEN)){
                if(supportState.getValue(TrapDoorBlock.HALF) == Half.BOTTOM){
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(TrapDoorBlock.FACING), Direction.DOWN, -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(TrapDoorBlock.FACING).getOpposite(), Direction.UP, -0.46875F, shiftToBlockWall);
                }
                if(supportState.getValue(TrapDoorBlock.HALF) == Half.TOP){
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(TrapDoorBlock.FACING), Direction.UP, -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(TrapDoorBlock.FACING).getOpposite(), Direction.DOWN, -0.46875F, shiftToBlockWall);
                }
            }
            if(!supportState.getValue(TrapDoorBlock.OPEN)){
                if(supportState.getValue(TrapDoorBlock.HALF) == Half.BOTTOM){
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, Direction.UP, supportState.getValue(TrapDoorBlock.FACING).getOpposite(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, Direction.DOWN, supportState.getValue(TrapDoorBlock.FACING), -0.46875F, shiftToBlockWall);
                }
                if(supportState.getValue(TrapDoorBlock.HALF) == Half.TOP){
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, Direction.DOWN, supportState.getValue(TrapDoorBlock.FACING).getOpposite(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, Direction.UP, supportState.getValue(TrapDoorBlock.FACING), -0.46875F, shiftToBlockWall);
                }
            }
            blockPos = this.pos;
            direction = this.getDirection();
        }
        if (block instanceof PressurePlateBlock) {
            shiftToBlockWall = supportState.getValue(PressurePlateBlock.POWERED) ? -1.4375F : -1.40625F;
        }

        if (block instanceof SlabBlock) {
            // Checks if we are placing the item frame on the top/bottom of the slab.
            // Then shifts it down if depending on the slab type.
            SlabType slabType = supportState.getValue(SlabBlock.TYPE);
            if(direction == Direction.DOWN && slabType == SlabType.TOP ||
                   direction == Direction.UP && slabType == SlabType.BOTTOM){
                shiftToBlockWall = -0.96875F;
            }
        }

        if (block instanceof CarpetBlock) {
            shiftToBlockWall = -1.40625F;
        }
        if (block instanceof FarmlandBlock) {
            shiftToBlockWall = -0.53125F;
        }
        if (block instanceof DiodeBlock) {
            shiftToBlockWall = -1.34375F;
        }
        if (block instanceof SnowLayerBlock) {
            shiftToBlockWall = -1.34375F;
        }
        if (block instanceof CrossCollisionBlock && direction.getAxis() != Direction.Axis.Y) {
            // Iron bars / glass panes: adjust -0.90625F here
            double flushShift = block instanceof IronBarsBlock ? -0.90625F : -0.84375F;
            shiftToBlockWall = checkDirection(itemFrame, blockPos, direction, flushShift);
        }

        if (block instanceof WallBlock && direction.getAxis() != Direction.Axis.Y) {
            shiftToBlockWall = checkDirection(itemFrame, blockPos, direction, getShiftToBlockWall(supportState));
        }

        if (block instanceof DaylightDetectorBlock && direction == Direction.UP) {
            shiftToBlockWall = -1.09375F;
        }
        if (block instanceof FenceGateBlock && direction.getAxis() == supportState.getValue(FenceGateBlock.FACING).getAxis()) {
            shiftToBlockWall = -0.84375F;
        }
        if (block instanceof LightningRodBlock && direction.getAxis() != supportState.getValue(LightningRodBlock.FACING).getAxis()) {
            shiftToBlockWall = -0.84375F;
        }

        /*
        if (block instanceof DoorBlock) {
            if(supportState.getValue(DoorBlock.OPEN)){
                if(offset){
                    itemFrame.setPos(?);
                    // set facing
                }
                if(nooffeset){
                    itemFrame.setPos(?);
                    // set facing
                }
            }
            if(!supportState.getValue(DoorBlock.OPEN)){
                if(offset){
                    itemFrame.setPos(?);
                    // set facing
                }
                if(nooffeset){
                    itemFrame.setPos(?);
                    // set facing
                }
            }
        }
        */

        // -- WIP -- TESTING
        /*if (block instanceof DoorBlock) {
            /* Initial Setup:
             * Door -
             *  Facing: west
             *  hinge: left
             *  open: false
             *  Visual: green
             * Item frame:
             *
             *
             * /
            if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT){
                if(direction == supportState.getValue(DoorBlock.FACING).getCounterClockWise() || direction == supportState.getValue(DoorBlock.FACING).getOpposite()){
                    this.pos = blockPos.relative(direction.getOpposite()).relative(supportState.getValue(DoorBlock.FACING).getCounterClockWise());
                    // set facing
                    this.setDirectionRaw(supportState.getValue(DoorBlock.FACING).getCounterClockWise());
                    this.setYRot(supportState.getValue(DoorBlock.FACING).getCounterClockWise().get2DDataValue() * 90);
                    shiftToBlockWall = -1.28125F;
                }
                if(direction == supportState.getValue(DoorBlock.FACING).getClockWise() || direction == supportState.getValue(DoorBlock.FACING)){
                    this.pos = blockPos.relative(direction.getOpposite()).relative(supportState.getValue(DoorBlock.FACING).getClockWise());
                    // set facing
                    this.setDirectionRaw(supportState.getValue(DoorBlock.FACING).getClockWise());
                    this.setYRot(supportState.getValue(DoorBlock.FACING).getClockWise().get2DDataValue() * 90);
                    shiftToBlockWall = -0.46875F;
                }
            }
            if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT){
                if(direction == supportState.getValue(DoorBlock.FACING).getClockWise() || direction == supportState.getValue(DoorBlock.FACING).getOpposite()){
                    this.pos = blockPos.relative(direction.getOpposite()).relative(supportState.getValue(DoorBlock.FACING).getClockWise());
                    // set facing
                    this.setDirectionRaw(supportState.getValue(DoorBlock.FACING).getClockWise());
                    this.setYRot(supportState.getValue(DoorBlock.FACING).getClockWise().get2DDataValue() * 90);
                    shiftToBlockWall = -1.28125F;
                }
                if(direction == supportState.getValue(DoorBlock.FACING).getCounterClockWise() || direction == supportState.getValue(DoorBlock.FACING)){
                    this.pos = blockPos.relative(direction.getOpposite()).relative(supportState.getValue(DoorBlock.FACING).getCounterClockWise());
                    // set facing
                    this.setDirectionRaw(supportState.getValue(DoorBlock.FACING).getCounterClockWise());
                    this.setYRot(supportState.getValue(DoorBlock.FACING).getCounterClockWise().get2DDataValue() * 90);
                    shiftToBlockWall = -0.46875F;
                }
            }
            blockPos = this.pos;
            direction = this.getDirection();
        }*/

        if (block instanceof DoorBlock) {
            if(supportState.getValue(DoorBlock.OPEN)){
                if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT){
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(DoorBlock.FACING).getCounterClockWise(), supportState.getValue(DoorBlock.FACING).getOpposite(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(DoorBlock.FACING).getClockWise(), supportState.getValue(DoorBlock.FACING), -0.46875F, shiftToBlockWall);
                }
                if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT){
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(DoorBlock.FACING).getClockWise(), supportState.getValue(DoorBlock.FACING).getOpposite(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(DoorBlock.FACING).getCounterClockWise(), supportState.getValue(DoorBlock.FACING), -0.46875F, shiftToBlockWall);
                }
            }
            if(!supportState.getValue(DoorBlock.OPEN)){
                if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.RIGHT){
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(DoorBlock.FACING), supportState.getValue(DoorBlock.FACING).getClockWise(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(DoorBlock.FACING).getOpposite(), supportState.getValue(DoorBlock.FACING).getCounterClockWise(), -0.46875F, shiftToBlockWall);
                }
                if(supportState.getValue(DoorBlock.HINGE) == DoorHingeSide.LEFT){
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(DoorBlock.FACING), supportState.getValue(DoorBlock.FACING).getCounterClockWise(), -1.28125F, shiftToBlockWall);
                    shiftToBlockWall = placeOnDoorFace(itemFrame, blockPos, direction, supportState.getValue(DoorBlock.FACING).getOpposite(), supportState.getValue(DoorBlock.FACING).getClockWise(), -0.46875F, shiftToBlockWall);
                }
            }
            blockPos = this.pos;
            direction = this.getDirection();
        }
        if (block instanceof ChainBlock && direction.getAxis() != supportState.getValue(ChainBlock.AXIS)) {
            shiftToBlockWall = -0.875F;
        }
        if (block instanceof LanternBlock) {
            boolean hanging = supportState.getValue(LanternBlock.HANGING);
            if (direction.getAxis() != Direction.Axis.Y) {
                shiftToBlockWall = -0.78125F;
            }
            if (direction == Direction.UP) {
                shiftToBlockWall = hanging ? -0.84375F : -0.90625F;
            }
            if (direction == Direction.DOWN && hanging) {
                shiftToBlockWall = -0.53125F;
            }
        }
        if (block instanceof DriedGhastBlock) {
            shiftToBlockWall = direction == Direction.UP ? -0.84375F : -0.65625F;
        }
        if (block instanceof BannerBlock && direction.getAxis() != Direction.Axis.Y) {
            int rotation = supportState.getValue(BannerBlock.ROTATION);
            float bannerYRot = RotationSegment.convertToDegrees(rotation);
            if (Math.abs(Mth.wrapDegrees(bannerYRot - direction.toYRot())) <= 45.0F) {
                shiftToBlockWall = -0.8854167F;
            } else if (Math.abs(Mth.wrapDegrees(bannerYRot + 180.0F - direction.toYRot())) <= 45.0F) {
                shiftToBlockWall = -0.9270833F;
            } else {
                shiftToBlockWall = -0.71875F;
            }
        }
        if (block instanceof WallBannerBlock) {
            if (direction == supportState.getValue(WallBannerBlock.FACING)) {
                shiftToBlockWall = -1.3229167F;
            }
            if (direction == Direction.UP) {
                shiftToBlockWall = -0.6875F;
            }
        }
        if (block instanceof AbstractChestBlock) {
            shiftToBlockWall = direction == Direction.UP ? -0.59375F : -0.53125F;
        }
        if (block instanceof DecoratedPotBlock && direction.getAxis() != Direction.Axis.Y) {
            shiftToBlockWall = -0.53125F;
        }
        if (block instanceof EndPortalFrameBlock && direction == Direction.UP) {
            shiftToBlockWall = supportState.getValue(EndPortalFrameBlock.HAS_EYE) ? -0.46875F : -0.65625F;
        }
        if (block instanceof BedBlock && direction == Direction.UP) {
            shiftToBlockWall = -0.90625F;
        }
        if (block instanceof BambooStalkBlock && direction.getAxis() != Direction.Axis.Y) {
            double stalkShift = supportState.getValue(BambooStalkBlock.AGE) == BambooStalkBlock.AGE_THIN_BAMBOO ? -0.90625F : -0.875F;
            Vec3 offset = supportState.getOffset(blockPos.relative(direction.getOpposite()));
            shiftToBlockWall = stalkShift + offset.get(direction.getAxis()) * direction.getAxisDirection().getStep();
        }

        // Base minecraft shift logic
        Vec3 position = Vec3.atCenterOf(blockPos).relative(direction, shiftToBlockWall);
        double width = hasFramedMap ? 1.0F : shiftDirection;
        double height = hasFramedMap ? 1.0F : shiftDirection;

        Direction.Axis axis = direction.getAxis();
        double xSize = axis == Direction.Axis.X ? shiftPositon : width;
        double ySize = axis == Direction.Axis.Y ? shiftPositon : height;
        double zSize = axis == Direction.Axis.Z ? shiftPositon : width;

        returnInfo.setReturnValue(AABB.ofSize(position, xSize, ySize, zSize));
    }

    // Moves the frame onto one face of a door. Runs when the frame is already on that face, or on the
    // face it comes from when the door toggles.
    @Unique
    private double placeOnDoorFace(ItemFrame itemFrame, BlockPos blockPos, Direction direction, Direction face, Direction arrivingFrom, double shift, double shiftToBlockWall) {
        if (direction != face && direction != arrivingFrom) {
            return shiftToBlockWall;
        }
        this.pos = blockPos.relative(direction.getOpposite()).relative(face);
        // set facing
        this.setDirectionRaw(face);
        if (face.getAxis().isHorizontal()) {
            this.setXRot(0.0F);
            this.setYRot(face.get2DDataValue() * 90);
        } else {
            this.setXRot(-90 * face.getAxisDirection().getStep());
            this.setYRot(0.0F);
        }
        return this.getDirection() == face ? shift : shiftToBlockWall;
    }

    @Unique
    private double checkDirection(ItemFrame itemFrame, BlockPos framePos, Direction direction, double flushShift) {
        BlockPos supportPos = framePos.relative(direction.getOpposite());
        AABB nearby = new AABB(supportPos).inflate(1.0);

        if (canPlaceOnWall(itemFrame, nearby, supportPos, direction.getClockWise(), direction.getCounterClockWise())) {
            return flushShift;
        }
        return -1;
    }

    @Unique
    private boolean canPlaceOnWall(ItemFrame itemFrame, AABB nearby, BlockPos wallPos, Direction sideA, Direction sideB) {
        for (ItemFrame other : itemFrame.level().getEntitiesOfClass(ItemFrame.class, nearby)) {
            if (other == itemFrame) {
                continue;
            }
            if (isHangingOnSide(other, wallPos, sideA) || isHangingOnSide(other, wallPos, sideB)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private static boolean isHangingOnSide(ItemFrame frame, BlockPos wallPos, Direction side) {
        return frame.getPos().equals(wallPos.relative(side)) && frame.getDirection() == side;
    }

    @Unique
    private double getShiftToBlockWall(BlockState supportState) {
        return supportState.getValue(WallBlock.UP) ? WallShift.WALL_PILER : WallShift.WALL_FLAT;
    }
}
