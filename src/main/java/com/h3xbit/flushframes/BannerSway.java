package com.h3xbit.flushframes;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

// Banner geometry and sway copied from BannerModel/BannerFlagModel/BannerRenderer. Boxes are in
// block coords for a banner facing south (yRot 0) - rotate them with rotate()/rotateBox().
public final class BannerSway {
    // One banner model pixel in blocks (1/16, rendered at 2/3 scale)
    private static final double PIXEL = 1.0 / 24.0;

    public static final AABB STANDING_POLE = new AABB(0.5 - PIXEL, 0.0, 0.5 - PIXEL, 0.5 + PIXEL, 42 * PIXEL, 0.5 + PIXEL);
    public static final AABB STANDING_BAR = new AABB(0.5 - 10 * PIXEL, 42 * PIXEL, 0.5 - PIXEL, 0.5 + 10 * PIXEL, 44 * PIXEL, 0.5 + PIXEL);
    public static final AABB STANDING_FLAG = new AABB(0.5 - 10 * PIXEL, 4 * PIXEL, 0.5 + PIXEL, 0.5 + 10 * PIXEL, 44 * PIXEL, 0.5 + 2 * PIXEL);
    public static final Vec3 STANDING_PIVOT = new Vec3(0.5, 44 * PIXEL, 0.5);

    public static final AABB WALL_BAR = new AABB(0.5 - 10 * PIXEL, 18.5 * PIXEL, 0.5 - 11.5 * PIXEL, 0.5 + 10 * PIXEL, 20.5 * PIXEL, 0.5 - 9.5 * PIXEL);
    public static final AABB WALL_FLAG = new AABB(0.5 - 10 * PIXEL, -19.5 * PIXEL, 0.5 - 9.5 * PIXEL, 0.5 + 10 * PIXEL, 20.5 * PIXEL, 0.5 - 8.5 * PIXEL);
    public static final Vec3 WALL_PIVOT = new Vec3(0.5, 20.5 * PIXEL, 0.5 - 10.5 * PIXEL);

    private static final VoxelShape[] STANDING_SHAPES = new VoxelShape[16];

    private BannerSway() {
    }

    public static boolean isBanner(BlockState state) {
        return state.getBlock() instanceof BannerBlock || state.getBlock() instanceof WallBannerBlock;
    }

    public static float yRot(BlockState state) {
        if (state.getBlock() instanceof BannerBlock) {
            return RotationSegment.convertToDegrees(state.getValue(BannerBlock.ROTATION));
        }
        return state.getValue(WallBannerBlock.FACING).toYRot();
    }

    public static float xRot(BlockPos bannerPos, long gameTime, float partialTick) {
        float phase = (Math.floorMod(bannerPos.getX() * 7 + bannerPos.getY() * 9 + bannerPos.getZ() * 13 + gameTime, 100L) + partialTick) / 100.0F;
        return (-0.0125F + 0.01F * Mth.cos(Mth.TWO_PI * phase)) * Mth.PI;
    }

    public static Vec3 rotate(Vec3 point, float yRot) {
        return point.subtract(0.5, 0.0, 0.5).yRot(-yRot * Mth.DEG_TO_RAD).add(0.5, 0.0, 0.5);
    }

    public static AABB rotateBox(AABB box, float yRot) {
        Vec3 a = rotate(new Vec3(box.minX, box.minY, box.minZ), yRot);
        Vec3 b = rotate(new Vec3(box.maxX, box.maxY, box.minZ), yRot);
        Vec3 c = rotate(new Vec3(box.minX, box.minY, box.maxZ), yRot);
        Vec3 d = rotate(new Vec3(box.maxX, box.maxY, box.maxZ), yRot);
        return new AABB(
                Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)), box.minY, Math.min(Math.min(a.z, b.z), Math.min(c.z, d.z)),
                Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)), box.maxY, Math.max(Math.max(a.z, b.z), Math.max(c.z, d.z)));
    }

    // Rotation around the flag's pivot, in world space
    public static Quaternionf flagRotation(float yRot, float xRot) {
        float yRotRadians = yRot * Mth.DEG_TO_RAD;
        return new Quaternionf().rotateY(-yRotRadians).rotateX(xRot).rotateY(yRotRadians);
    }

    // Pole, bar and resting flag - what gets clicked
    public static VoxelShape standingShape(BlockState state) {
        int rotation = state.getValue(BannerBlock.ROTATION);
        if (STANDING_SHAPES[rotation] == null) {
            float yRot = RotationSegment.convertToDegrees(rotation);
            STANDING_SHAPES[rotation] = Shapes.or(
                    Shapes.create(STANDING_POLE),
                    Shapes.create(rotateBox(STANDING_BAR, yRot)),
                    Shapes.create(rotateBox(STANDING_FLAG, yRot)));
        }
        return STANDING_SHAPES[rotation];
    }
}
