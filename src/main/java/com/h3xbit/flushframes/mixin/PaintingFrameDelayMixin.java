package com.h3xbit.flushframes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

@Mixin(PaintingRenderer.class)
public abstract class PaintingFrameDelayMixin {

    // Paintings without a rebuild to wait for (e.g. just placed) still move after this many frames
    private static final int MAX_HOLD_FRAMES = 3;

    private static final WeakHashMap<Painting, RenderHold> RENDER_HOLDS = new WeakHashMap<>();

    // The level is drawn before sections are rebuilt, so a block's new model only shows up the frame
    // after its block state changes. Draw the painting from the same block state its support block's
    // on-screen model was built from: when that section's mesh changes, it was built at the end of
    // last frame, so switch to last frame's target.
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/decoration/painting/Painting;Lnet/minecraft/client/renderer/entity/state/PaintingRenderState;F)V", at = @At("TAIL"))
    private void placeFromBlockState(Painting painting, PaintingRenderState renderState, float partialTicks, CallbackInfo ci) {
        BlockPos anchor = painting.getPos();
        painting.setPos(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        Vec3 targetPosition = painting.position();
        Direction targetDirection = painting.getDirection();

        SectionRenderDispatcher.RenderSection section = findSection(painting.getPos().relative(targetDirection.getOpposite()));
        SectionMesh mesh = section != null ? section.getSectionMesh() : null;

        RenderHold hold = RENDER_HOLDS.get(painting);
        if (hold == null || mesh == null) {
            hold = new RenderHold(targetPosition, targetDirection, mesh);
            RENDER_HOLDS.put(painting, hold);
        } else if (mesh != hold.lastMesh) {
            hold.shownPosition = hold.lastTargetPosition;
            hold.shownDirection = hold.lastTargetDirection;
            hold.framesHeld = 0;
        } else if (!targetPosition.equals(hold.shownPosition) || targetDirection != hold.shownDirection) {
            if (++hold.framesHeld > MAX_HOLD_FRAMES) {
                hold.shownPosition = targetPosition;
                hold.shownDirection = targetDirection;
                hold.framesHeld = 0;
            }
        }
        hold.lastMesh = mesh;
        hold.lastTargetPosition = targetPosition;
        hold.lastTargetDirection = targetDirection;

        renderState.x = hold.shownPosition.x;
        renderState.y = hold.shownPosition.y;
        renderState.z = hold.shownPosition.z;
        renderState.direction = hold.shownDirection;
    }

    private static SectionRenderDispatcher.RenderSection findSection(BlockPos pos) {
        BlockPos origin = SectionPos.of(pos).origin();
        for (SectionRenderDispatcher.RenderSection section : Minecraft.getInstance().levelRenderer.visibleSections()) {
            if (section.getRenderOrigin().equals(origin)) {
                return section;
            }
        }
        return null;
    }

    private static final class RenderHold {
        Vec3 shownPosition;
        Direction shownDirection;
        Vec3 lastTargetPosition;
        Direction lastTargetDirection;
        SectionMesh lastMesh;
        int framesHeld;

        RenderHold(Vec3 position, Direction direction, SectionMesh mesh) {
            this.shownPosition = position;
            this.shownDirection = direction;
            this.lastTargetPosition = position;
            this.lastTargetDirection = direction;
            this.lastMesh = mesh;
        }
    }
}
