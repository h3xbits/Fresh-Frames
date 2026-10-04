package com.h3xbit.flushframes.mixin;

import com.h3xbit.flushframes.FlushFrames;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Stops the click that destroys an item frame or a painting (always one hit) from also falling
// through onto the block behind it. This mod places frames flush against thin supports (ItemFrameFlushSupportMixin), so once
// the frame dies mid-click, the crosshair's next raytrace lands squarely on that block - and
// Minecraft.continueAttack() mines whatever the raytrace currently reports every tick the mouse
// stays down, with no notion of "is this the same target I started on".
//
// Fixed at the source rather than reacting after the fact: Minecraft already has missTime, a
// cooldown that both startAttack() and continueAttack() check and refuse to act while it's
// nonzero (decompiled and confirmed - same field vanilla itself sets to 10 on a miss). It also
// self-clears the instant the mouse is released (continueAttack resets it to 0 when isDown is
// false), so a fresh click right after works immediately with no separate expiry logic needed.
// Setting it here means the second destroy packet for the block is simply never sent - nothing
// to veto, nothing to correct, nothing for the client to mispredict.
@Mixin(Minecraft.class)
public abstract class ItemFrameAttackThroughMixin {

    @Shadow
    public net.minecraft.world.phys.HitResult hitResult;

    @Shadow
    public int missTime;

    @Inject(method = "startAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;attack(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;)V",
            shift = At.Shift.AFTER))
    private void preventFrameClickThrough(CallbackInfoReturnable<Boolean> cir) {
        if (this.hitResult instanceof EntityHitResult entityHit
                && (entityHit.getEntity() instanceof ItemFrame frame && frame.getItem().isEmpty()
                    || entityHit.getEntity() instanceof Painting)) {
            FlushFrames.LOGGER.info("[BREAK_GUARD][{}] attacked {} {} -> suppressing attack/mine input for this click (missTime={} -> 10)",
                    Thread.currentThread().getName(), entityHit.getEntity().getType().toShortString(), entityHit.getEntity().getUUID(), this.missTime);
            this.missTime = 10;
        }
    }
}
