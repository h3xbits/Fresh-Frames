package com.h3xbit.flushframes;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;

public class FlushFramesClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DebugScreenEntries.register(ItemFrameDebugEntry.ID, new ItemFrameDebugEntry());
    }
}
