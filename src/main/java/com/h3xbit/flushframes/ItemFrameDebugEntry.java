package com.h3xbit.flushframes;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// F3 + F6 "looking_at_item_frame" - laid out like vanilla's DebugEntryLookingAt, client data only
public class ItemFrameDebugEntry implements DebugScreenEntry {
    public static final Identifier ID = Identifier.withDefaultNamespace("looking_at_item_frame");

    @Override
    public void display(DebugScreenDisplayer displayer, Level serverOrClientLevel, LevelChunk clientChunk, LevelChunk serverChunk) {
        Entity cameraEntity = Minecraft.getInstance().getCameraEntity();
        Level level = Minecraft.getInstance().level;
        if (cameraEntity == null || level == null) {
            return;
        }

        HitResult hitResult = this.getHitResult(cameraEntity);
        List<String> result = new ArrayList<>();
        if (hitResult != null && hitResult.getType() == HitResult.Type.ENTITY
                && ((EntityHitResult) hitResult).getEntity() instanceof ItemFrame frame) {
            this.extractInfo(result, level, frame);
        }

        displayer.addToGroup(ID, result);
    }

    public HitResult getHitResult(Entity cameraEntity) {
        return Minecraft.getInstance().hitResult;
    }

    public void extractInfo(List<String> result, Level level, ItemFrame frame) {
        BlockPos pos = frame.getPos();
        result.add(ChatFormatting.UNDERLINE + "Targeted Item Frame: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        addFrameProperties(result, frame);
    }

    private static void addFrameProperties(List<String> result, ItemFrame frame) {
        Vec3 position = frame.position();
        result.add(getPropertyValueString("facing", frame.getDirection().getName()));
        result.add(getPropertyValueString("invisible", frame.isInvisible()));
        result.add(getPropertyValueString("invulnerable", frame.isInvulnerable()));
        result.add(getPropertyValueString("position", String.format("%.3f, %.3f, %.3f", position.x, position.y, position.z)));
        result.add(getPropertyValueString("rotation", String.format("%.1f, %.1f", frame.getXRot(), frame.getYRot())));
        result.add(getPropertyValueString("item_rotation", frame.getRotation() * 45 + "°"));
        result.add(getPropertyValueString("fixed", isFixed(frame)));
        result.add(getPropertyValueString("attached", frame.survives()));
    }

    // Reads the private ItemFrame.fixed field directly
    private static Object isFixed(ItemFrame frame) {
        try {
            Field fixed = ItemFrame.class.getDeclaredField("fixed");
            fixed.setAccessible(true);
            return fixed.getBoolean(frame);
        } catch (ReflectiveOperationException e) {
            return "unreadable";
        }
    }

    private static String getPropertyValueString(String name, Object value) {
        String valueName = String.valueOf(value);
        if (Boolean.TRUE.equals(value)) {
            valueName = ChatFormatting.GREEN + valueName;
        } else if (Boolean.FALSE.equals(value)) {
            valueName = ChatFormatting.RED + valueName;
        }
        return name + ": " + valueName;
    }
}
