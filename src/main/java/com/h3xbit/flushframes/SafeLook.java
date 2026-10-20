package com.h3xbit.flushframes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

// Whether a frame or painting may look at the blocks around it. Only when that chunk is already fully loaded: while
// the world is being generated (a structure placing its frames and paintings, on a worker thread) or a chunk is still
// loading (its frames and paintings are read from disk before the chunk is done), asking the world for a block waits
// for that chunk - and the thread it waits on is waiting on us, so the server freezes for good.
// Then the frame just keeps vanilla's position.
public final class SafeLook {
    private SafeLook() {
    }

    public static boolean canLook(Level level, BlockPos pos) {
        if (level.isClientSide()) {
            return true;
        }
        MinecraftServer server = level.getServer();
        return server != null && server.isSameThread()
                && level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ())) != null;
    }
}
