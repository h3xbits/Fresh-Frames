package com.h3xbit.flushframes.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.SectionMesh;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.entity.ItemFrameRenderer;
import net.minecraft.client.renderer.entity.state.ItemFrameRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.WeakHashMap;

@Mixin(ItemFrameRenderer.class)
public abstract class ItemFrameFrameDelayMixin {

    // Frames without a rebuild to wait for (e.g. just placed) still move after this many frames
    private static final int MAX_HOLD_FRAMES = 3;

    private static final WeakHashMap<ItemFrame, RenderHold> RENDER_HOLDS = new WeakHashMap<>();

    // The level is drawn before sections are rebuilt, so a block's new model only shows up the frame
    // after its block state changes. Draw the frame from the same block state its support block's
    // on-screen model was built from: when that section's mesh changes, it was built at the end of
    // last frame, so switch to last frame's target.
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void placeFromBlockState(ItemFrame frame, ItemFrameRenderState renderState, float partialTicks, CallbackInfo ci) {
        BlockPos anchor = frame.getPos();
        frame.setPos(anchor.getX() + 0.5, anchor.getY() + 0.5, anchor.getZ() + 0.5);
        Vec3 targetPosition = frame.position();
        Direction targetDirection = frame.getDirection();

        SectionRenderDispatcher.RenderSection section = findSection(frame.getPos().relative(targetDirection.getOpposite()));
        SectionMesh mesh = section != null ? section.getSectionMesh() : null;

        RenderHold hold = RENDER_HOLDS.get(frame);
        if (hold == null || mesh == null) {
            hold = new RenderHold(targetPosition, targetDirection, mesh);
            RENDER_HOLDS.put(frame, hold);
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

/*
// Sections holding a block an item frame or painting hangs on (doors, trapdoors, pressure plates) get rebuilt
// right away instead of on a background thread, so the block's new model shows up in the same
// frame as its new block state - the frame is placed from that block state, so they always match.
@Mixin(LevelRenderer.class)
abstract class ItemFrameSectionSyncMixin {
    @Redirect(method = "compileSections", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/chunk/SectionRenderDispatcher$RenderSection;compileAsync(Lnet/minecraft/client/renderer/chunk/RenderSectionRegion;)V"))
    private void compileFrameSupportNow(SectionRenderDispatcher.RenderSection section, RenderSectionRegion region) {
        if (holdsFrameSupport(section)) {
            section.compileSync(region);
        } else {
            section.compileAsync(region);
        }
    }

    private static boolean holdsFrameSupport(SectionRenderDispatcher.RenderSection section) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        AABB box = section.getBoundingBox();
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box.inflate(1.0))) {
            BlockPos support = frame.getPos().relative(frame.getDirection().getOpposite());
            if (box.contains(Vec3.atCenterOf(support))) {
                return true;
            }
        }
        // Paintings can be up to 4x4, so any painting touching the section counts
        return !level.getEntitiesOfClass(net.minecraft.world.entity.decoration.painting.Painting.class, box.inflate(1.0)).isEmpty();
    }
}
*/
