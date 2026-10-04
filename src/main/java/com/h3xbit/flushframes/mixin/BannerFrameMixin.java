package com.h3xbit.flushframes.mixin;

import com.h3xbit.flushframes.BannerSway;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.WeakHashMap;

// Frames on the front of a banner's flag swing with it
@Mixin(ItemFrameRenderer.class)
public abstract class BannerFrameMixin {

    private static final WeakHashMap<ItemFrameRenderState, FlagSway> FLAG_SWAYS = new WeakHashMap<>();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void swayWithBanner(ItemFrame frame, ItemFrameRenderState renderState, float partialTicks, CallbackInfo ci) {
        FLAG_SWAYS.remove(renderState);

        Direction direction = frame.getDirection();
        BlockPos bannerPos = frame.getPos().relative(direction.getOpposite());
        if (frame.level().getBlockState(bannerPos).isAir()) {
            bannerPos = bannerPos.below();
        }
        BlockState bannerState = frame.level().getBlockState(bannerPos);

        Vec3 pivot;
        float yRot;
        Quaternionf yaw = new Quaternionf();
        if (bannerState.getBlock() instanceof BannerBlock) {
            yRot = BannerSway.yRot(bannerState);
            // Frames only face 4 ways - turn it the rest of the way to match the banner's rotation
            float yawOffset = Mth.wrapDegrees(yRot - direction.toYRot());
            if (Math.abs(yawOffset) > 45.0F) {
                yawOffset = Mth.wrapDegrees(yRot + 180.0F - direction.toYRot());
            }
            if (Math.abs(yawOffset) > 45.0F) {
                return;
            }
            yaw.rotateY(-yawOffset * Mth.DEG_TO_RAD);
            pivot = BannerSway.STANDING_PIVOT;
        } else if (bannerState.getBlock() instanceof WallBannerBlock) {
            if (direction != bannerState.getValue(WallBannerBlock.FACING)) {
                return;
            }
            yRot = BannerSway.yRot(bannerState);
            pivot = BannerSway.WALL_PIVOT;
        } else {
            return;
        }

        float xRot = BannerSway.xRot(bannerPos, frame.level().getGameTime(), partialTicks);
        Vec3 worldPivot = Vec3.atLowerCornerOf(bannerPos).add(BannerSway.rotate(pivot, yRot));
        FLAG_SWAYS.put(renderState, new FlagSway(worldPivot, BannerSway.flagRotation(yRot, xRot), Vec3.atBottomCenterOf(bannerPos), yaw));
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemFrameRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"))
    private void applyFlagSway(ItemFrameRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState, CallbackInfo ci) {
        FlagSway sway = FLAG_SWAYS.get(renderState);
        if (sway == null) {
            return;
        }

        Vec3 renderOffset = ((ItemFrameRenderer<?>) (Object) this).getRenderOffset(renderState);
        double dx = sway.pivot.x - renderState.x - renderOffset.x;
        double dy = sway.pivot.y - renderState.y - renderOffset.y;
        double dz = sway.pivot.z - renderState.z - renderOffset.z;

        poseStack.translate(dx, dy, dz);
        poseStack.mulPose(sway.rotation);
        poseStack.translate(-dx, -dy, -dz);

        double cx = sway.center.x - renderState.x - renderOffset.x;
        double cy = sway.center.y - renderState.y - renderOffset.y;
        double cz = sway.center.z - renderState.z - renderOffset.z;

        poseStack.translate(cx, cy, cz);
        poseStack.mulPose(sway.yaw);
        poseStack.translate(-cx, -cy, -cz);
    }

    private record FlagSway(Vec3 pivot, Quaternionf rotation, Vec3 center, Quaternionf yaw) {
    }
}

// Clickable shape covers the whole standing banner instead of the vanilla one block column
@Mixin(BannerBlock.class)
abstract class BannerShapeMixin {
    @Inject(method = "getShape", at = @At("RETURN"), cancellable = true)
    private void fullBannerShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> returnInfo) {
        returnInfo.setReturnValue(BannerSway.standingShape(state));
    }
}

// Outline drawn around the pole, bar and flag, with the flag swinging like the real one
@Mixin(LevelRenderer.class)
abstract class BannerOutlineMixin {
    @Inject(method = "submitHitOutline", at = @At("HEAD"), cancellable = true)
    private void outlineBanner(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, RenderType renderType, BlockOutlineRenderState outline, int color, float lineWidth, boolean translucent, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState state = minecraft.level.getBlockState(outline.pos());
        if (!BannerSway.isBanner(state)) {
            return;
        }

        boolean standing = state.getBlock() instanceof BannerBlock;
        float yRot = BannerSway.yRot(state);
        float xRot = BannerSway.xRot(outline.pos(), minecraft.level.getGameTime(), minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        VoxelShape still = standing ? Shapes.or(Shapes.create(BannerSway.STANDING_POLE), Shapes.create(BannerSway.STANDING_BAR)) : Shapes.create(BannerSway.WALL_BAR);
        AABB flag = standing ? BannerSway.STANDING_FLAG : BannerSway.WALL_FLAG;
        Vec3 pivot = standing ? BannerSway.STANDING_PIVOT : BannerSway.WALL_PIVOT;

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(new Quaternionf().rotateY(-yRot * Mth.DEG_TO_RAD));
        poseStack.translate(-0.5, 0.0, -0.5);
        submitNodeCollector.submitShapeOutline(poseStack, still, renderType, color, lineWidth, translucent);

        poseStack.translate(pivot.x, pivot.y, pivot.z);
        poseStack.mulPose(new Quaternionf().rotateX(xRot));
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z);
        submitNodeCollector.submitShapeOutline(poseStack, Shapes.create(flag), renderType, color, lineWidth, translucent);
        poseStack.popPose();
        ci.cancel();
    }
}

// Vanilla raytracing only tests a block's shape while the ray is inside that block's own cell, so
// the top half of a standing banner (up in the air block above) can't be aimed at on its own.
// Frames hanging on a banner always win over the banner itself - on a rotated banner the banner's
// box sticks out past the frame's.
@Mixin(LocalPlayer.class)
abstract class BannerPickMixin {
    @Inject(method = "raycastHitResult", at = @At("RETURN"), cancellable = true)
    private void pickBanner(float partialTicks, Entity camera, CallbackInfoReturnable<HitResult> returnInfo) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        Level level = camera.level();
        Vec3 from = camera.getEyePosition(partialTicks);
        Vec3 view = camera.getViewVector(partialTicks);
        Vec3 to = from.add(view.scale(player.blockInteractionRange()));

        BlockHitResult bannerHit = BlockGetter.traverseBlocks(from, to, level, (blockGetter, pos) -> {
            BlockPos bannerPos = pos.below();
            BlockState below = blockGetter.getBlockState(bannerPos);
            if (!blockGetter.getBlockState(pos).isAir() || !(below.getBlock() instanceof BannerBlock)) {
                return null;
            }
            BlockHitResult hit = below.getShape(blockGetter, bannerPos).clip(from, to, bannerPos);
            if (hit == null || hit.getLocation().y < pos.getY()) {
                return null;
            }
            return hit;
        }, blockGetter -> null);

        HitResult result = returnInfo.getReturnValue();
        if (bannerHit != null && (result == null || result.getType() == HitResult.Type.MISS
                || from.distanceToSqr(bannerHit.getLocation()) <= from.distanceToSqr(result.getLocation()))) {
            result = bannerHit;
        }

        if (result instanceof BlockHitResult blockHit && BannerSway.isBanner(level.getBlockState(blockHit.getBlockPos()))) {
            BlockPos bannerPos = blockHit.getBlockPos();
            Vec3 entityTo = from.add(view.scale(player.entityInteractionRange()));
            ItemFrame closestFrame = null;
            Vec3 closestHit = null;
            for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, new AABB(bannerPos).inflate(2.0))) {
                BlockPos supportPos = frame.getPos().relative(frame.getDirection().getOpposite());
                if (!supportPos.equals(bannerPos) && !supportPos.below().equals(bannerPos)) {
                    continue;
                }
                Vec3 hit = frame.getBoundingBox().clip(from, entityTo).orElse(null);
                if (hit != null && (closestHit == null || from.distanceToSqr(hit) < from.distanceToSqr(closestHit))) {
                    closestFrame = frame;
                    closestHit = hit;
                }
            }
            if (closestFrame != null) {
                result = new EntityHitResult(closestFrame, closestHit);
            }
        }

        if (result != returnInfo.getReturnValue()) {
            returnInfo.setReturnValue(result);
        }
    }
}

// The server rejects clicks more than a block from the clicked block, so a frame placed on the top
// half of a standing banner is sent as a click on the air block above it instead.
@Mixin(MultiPlayerGameMode.class)
abstract class BannerTopPlaceMixin {
    @ModifyVariable(method = "useItemOn", at = @At("HEAD"), argsOnly = true)
    private BlockHitResult placeOnBannerTop(BlockHitResult hit, LocalPlayer player, InteractionHand hand) {
        BlockPos pos = hit.getBlockPos();
        if (player.getItemInHand(hand).getItem() instanceof ItemFrameItem
                && hit.getLocation().y >= pos.getY() + 1
                && player.level().getBlockState(pos).getBlock() instanceof BannerBlock) {
            return new BlockHitResult(hit.getLocation(), hit.getDirection(), pos.above(), hit.isInside());
        }
        return hit;
    }
}

// Frames on the top half of a standing banner hang on air, so vanilla would pop them off
@Mixin(ItemFrame.class)
abstract class BannerTopFrameMixin extends HangingEntity {
    protected BannerTopFrameMixin(EntityType<? extends HangingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "survives", at = @At("HEAD"), cancellable = true)
    private void survivesOnBannerTop(CallbackInfoReturnable<Boolean> returnInfo) {
        BlockPos supportPos = this.pos.relative(this.getDirection().getOpposite());
        if (this.getDirection().getAxis() != Direction.Axis.Y
                && this.level().getBlockState(supportPos).isAir()
                && this.level().getBlockState(supportPos.below()).getBlock() instanceof BannerBlock) {
            returnInfo.setReturnValue(this.canCoexist(true));
        }
    }
}
