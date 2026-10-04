package com.h3xbit.flushframes.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// Paintings on doors and fences in a real client + integrated server: checks after every door toggle
// that the painting is still on the door and that the client and server copies agree, and that
// paintings bigger than what they hang on keep vanilla placement.
// Run with: ./gradlew runClientGameTest   (screenshots end up in build/run/clientGameTest/screenshots)
public class PaintingClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("flush_frames-gametest");
    private static final int SETTLE_TICKS = 20;

    @Override
    public void runTest(ClientGameTestContext context) {
        if (ShowcaseClientGameTest.enabled()) {
            return;
        }
        List<String> failures = new ArrayList<>();

        // Keep the test window above other windows (without focusing it or grabbing the mouse)
        context.runOnClient(client -> GLFW.glfwSetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_FLOATING, GLFW.GLFW_TRUE));

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            TestServerContext server = singleplayer.getServer();

            BlockPos door = new BlockPos(0, 100, 0);
            server.runCommand("tp @a 0.5 101 4.5");
            context.waitTicks(SETTLE_TICKS);

            // Paintings on doors - a 1x1 and a 1x2 that covers both door halves
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (DoorHingeSide hinge : DoorHingeSide.values()) {
                    runPaintingDoorCase(context, server, door, facing, hinge, PaintingVariants.KEBAB, failures);
                    runPaintingDoorCase(context, server, door, facing, hinge, PaintingVariants.WANDERER, failures);
                }
            }

            // Paintings on fences - flush on a post, and vanilla when the blocks behind it don't all match
            runPaintingFenceCases(context, server, door, failures);

            // Paintings bigger than what they hang on stay put - a 2x1 across two doors, a 1x2 across two fences
            runPaintingTooBigCases(context, server, door, failures);

            // Creative: clicking a painting on a fence post breaks the painting but not the fence behind it
            runPaintingClickThroughCase(context, server, door, failures);

            // Item frames on both sides of a door - neither may pop off while it opens and closes
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                for (DoorHingeSide hinge : DoorHingeSide.values()) {
                    runFramesBothSidesCase(context, server, door, facing, hinge, failures);
                }
            }

            // Spam a door with a painting on it at 10 fps
            int paintingId = runPaintingDoorCase(context, server, door, Direction.WEST, DoorHingeSide.LEFT, PaintingVariants.WANDERER, failures);
            context.runOnClient(client -> client.options.framerateLimit().set(10));
            for (int toggle = 0; toggle < 21; toggle++) {
                setDoorOpen(server, door, toggle % 2 == 0);
                context.waitTicks(1 + toggle % 3);
            }
            context.waitTicks(SETTLE_TICKS);
            check(context, server, "painting_door_spam_open", paintingId, door, failures);
            context.runOnClient(client -> client.options.framerateLimit().set(260));
        }

        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " painting check(s) failed:\n" + String.join("\n", failures));
        }
        LOGGER.info("All painting checks passed");
    }

    private static int runPaintingDoorCase(ClientGameTestContext context, TestServerContext server, BlockPos door, Direction facing, DoorHingeSide hinge, ResourceKey<PaintingVariant> variant, List<String> failures) {
        String name = "painting_" + variant.identifier().getPath() + "_door_" + facing.getName() + "_" + hinge.getSerializedName();

        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", door.getX() - 3, door.getY(), door.getZ() - 3, door.getX() + 3, door.getY() + 3, door.getZ() + 3));
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", door.getX() - 6, door.getY() - 1, door.getZ() - 6, door.getX() + 6, door.getY() - 1, door.getZ() + 6));
        server.runCommand("kill @e[type=minecraft:item_frame]");
        server.runCommand("kill @e[type=minecraft:painting]");

        // Flush on the closed door's outer face, like placing it by hand
        int paintingId = server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HINGE, hinge)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(door, lower, 3);
            level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

            Painting painting = new Painting(level, door.relative(facing.getOpposite()), facing.getOpposite(), level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(variant));
            level.addFreshEntity(painting);
            return painting.getId();
        });

        BlockPos view = door.relative(facing.getOpposite(), 3).relative(facing.getClockWise(), 2);
        server.runCommand(String.format("tp @a %.1f %d %.1f facing %.1f %.1f %.1f", view.getX() + 0.5, view.getY(), view.getZ() + 0.5, door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5));
        context.waitFor(client -> client.level != null && client.level.getEntity(paintingId) instanceof Painting);
        context.waitTicks(SETTLE_TICKS);

        check(context, server, name + "_closed", paintingId, door, failures);
        setDoorOpen(server, door, true);
        context.waitTicks(SETTLE_TICKS);
        check(context, server, name + "_open", paintingId, door, failures);
        setDoorOpen(server, door, false);
        context.waitTicks(SETTLE_TICKS);
        check(context, server, name + "_closed_again", paintingId, door, failures);
        return paintingId;
    }

    private static void runPaintingFenceCases(ClientGameTestContext context, TestServerContext server, BlockPos fence, List<String> failures) {
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", fence.getX() - 3, fence.getY(), fence.getZ() - 3, fence.getX() + 3, fence.getY() + 3, fence.getZ() + 3));
        server.runCommand("kill @e[type=minecraft:painting]");
        server.runCommand(String.format("setblock %d %d %d minecraft:oak_fence", fence.getX(), fence.getY(), fence.getZ()));
        server.runCommand(String.format("setblock %d %d %d minecraft:stone", fence.getX(), fence.getY(), fence.getZ() + 1));

        // 1x1 on the fence post's east face - should sit flush against the post (post face at x + 0.625)
        int small = server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            Painting painting = new Painting(level, fence.east(), Direction.EAST, level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(PaintingVariants.KEBAB));
            level.addFreshEntity(painting);
            return painting.getId();
        });
        // 2x1 on the west side across the fence and the stone next to it - blocks differ, so vanilla placement
        int wide = server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            Painting painting = new Painting(level, fence.west(), Direction.WEST, level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(PaintingVariants.POOL));
            level.addFreshEntity(painting);
            return painting.getId();
        });

        server.runCommand(String.format("tp @a %.1f %d %.1f facing %.1f %.1f %.1f", fence.getX() + 0.5, fence.getY(), fence.getZ() - 3.5, fence.getX() + 0.5, fence.getY() + 0.5, fence.getZ() + 0.5));
        context.waitFor(client -> client.level != null && client.level.getEntity(small) instanceof Painting && client.level.getEntity(wide) instanceof Painting);
        context.waitTicks(SETTLE_TICKS);

        double smallX = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getEntity(small).getBoundingBox().minX);
        LOGGER.info("[painting_fence_1x1] box minX={} (expected {})", smallX, fence.getX() + 0.625);
        if (Math.abs(smallX - (fence.getX() + 0.625)) > 0.001) {
            failures.add("painting_fence_1x1: not flush against the fence post, box minX=" + smallX);
        }
        PaintingSnapshot wideSnapshot = server.computeOnServer(minecraftServer -> PaintingSnapshot.of(minecraftServer.overworld().getEntity(wide)));
        double wideMaxX = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getEntity(wide).getBoundingBox().maxX);
        LOGGER.info("[painting_fence_mixed_2x1] server: {} box maxX={} (expected {})", wideSnapshot, wideMaxX, fence.getX());
        if (wideSnapshot == null || !wideSnapshot.attached() || Math.abs(wideMaxX - fence.getX()) > 0.001) {
            failures.add("painting_fence_mixed_2x1: should hang in the vanilla spot against the block face - " + wideSnapshot + " maxX=" + wideMaxX);
        }
        context.takeScreenshot("painting_fence");
    }

    // Same call a player's click ends up in, so both door halves update like in normal play
    private static void runPaintingTooBigCases(ClientGameTestContext context, TestServerContext server, BlockPos door, List<String> failures) {
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", door.getX() - 3, door.getY(), door.getZ() - 3, door.getX() + 3, door.getY() + 3, door.getZ() + 3));
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", door.getX() - 6, door.getY() - 1, door.getZ() - 6, door.getX() + 6, door.getY() - 1, door.getZ() + 6));
        server.runCommand("kill @e[type=minecraft:painting]");

        // Two doors facing west side by side (door and the one south of it), 2x1 painting on their east faces
        Direction facing = Direction.WEST;
        Direction paintingFacing = facing.getOpposite();
        BlockPos secondDoor = door.relative(paintingFacing.getCounterClockWise());
        int wide = server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            for (BlockPos pos : List.of(door, secondDoor)) {
                BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
                level.setBlock(pos, lower, 3);
                level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
            }
            Painting painting = new Painting(level, door.relative(paintingFacing), paintingFacing, level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(PaintingVariants.POOL));
            level.addFreshEntity(painting);
            return painting.getId();
        });

        // 1x2 painting across two stacked fences a few blocks away
        BlockPos fence = door.north(2);
        server.runCommand(String.format("setblock %d %d %d minecraft:oak_fence", fence.getX(), fence.getY(), fence.getZ()));
        server.runCommand(String.format("setblock %d %d %d minecraft:oak_fence", fence.getX(), fence.getY() + 1, fence.getZ()));
        int tall = server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            Painting painting = new Painting(level, fence.east(), Direction.EAST, level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(PaintingVariants.WANDERER));
            level.addFreshEntity(painting);
            return painting.getId();
        });

        server.runCommand(String.format("tp @a %.1f %d %.1f facing %.1f %.1f %.1f", door.getX() + 4.5, door.getY(), door.getZ() - 0.5, door.getX() + 0.5, door.getY() + 0.5, door.getZ() - 0.5));
        context.waitFor(client -> client.level != null && client.level.getEntity(wide) instanceof Painting && client.level.getEntity(tall) instanceof Painting);
        context.waitTicks(SETTLE_TICKS);

        PaintingSnapshot wideBefore = server.computeOnServer(minecraftServer -> PaintingSnapshot.of(minecraftServer.overworld().getEntity(wide)));
        setDoorOpen(server, door, true);
        setDoorOpen(server, secondDoor, true);
        context.waitTicks(SETTLE_TICKS);
        PaintingSnapshot wideAfter = server.computeOnServer(minecraftServer -> PaintingSnapshot.of(minecraftServer.overworld().getEntity(wide)));
        PaintingSnapshot wideClient = context.computeOnClient((Minecraft client) -> PaintingSnapshot.of(client.level.getEntity(wide)));
        LOGGER.info("[painting_two_doors_2x1] before: {} after opening: {} client: {}", wideBefore, wideAfter, wideClient);
        context.takeScreenshot("painting_two_doors_open");
        if (wideAfter == null || !wideAfter.anchor().equals(wideBefore.anchor()) || wideAfter.facing() != wideBefore.facing()
                || wideClient == null || !wideClient.anchor().equals(wideBefore.anchor()) || wideClient.facing() != wideBefore.facing()) {
            failures.add("painting_two_doors_2x1: a painting across two doors should not move when they open - before " + wideBefore + ", after " + wideAfter + ", client " + wideClient);
        }

        double tallMinX = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getEntity(tall).getBoundingBox().minX);
        LOGGER.info("[painting_stacked_fences_1x2] box minX={} (expected {})", tallMinX, fence.getX() + 1.0);
        if (Math.abs(tallMinX - (fence.getX() + 1.0)) > 0.001) {
            failures.add("painting_stacked_fences_1x2: a 1x2 painting across two fences should hang in the vanilla spot, box minX=" + tallMinX);
        }
    }

    private static void runPaintingClickThroughCase(ClientGameTestContext context, TestServerContext server, BlockPos fence, List<String> failures) {
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", fence.getX() - 3, fence.getY(), fence.getZ() - 3, fence.getX() + 3, fence.getY() + 3, fence.getZ() + 3));
        server.runCommand("kill @e[type=minecraft:painting]");
        server.runCommand(String.format("setblock %d %d %d minecraft:oak_fence", fence.getX(), fence.getY(), fence.getZ()));
        server.runCommand("gamemode creative @a");

        int paintingId = server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            Painting painting = new Painting(level, fence.east(), Direction.EAST, level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(PaintingVariants.KEBAB));
            level.addFreshEntity(painting);
            return painting.getId();
        });
        server.runCommand(String.format("tp @a %.1f %d %.1f facing %.1f %.1f %.1f", fence.getX() + 2.5, fence.getY(), fence.getZ() + 0.5, fence.getX() + 0.5, fence.getY() + 0.5, fence.getZ() + 0.5));
        context.waitFor(client -> client.level != null && client.level.getEntity(paintingId) instanceof Painting);
        context.waitTicks(SETTLE_TICKS);
        context.runOnClient(client -> client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new net.minecraft.world.phys.Vec3(fence.getX() + 0.66, fence.getY() + 0.5, fence.getZ() + 0.5)));
        context.waitTicks(2);

        // A normal click - the button stays down for a few ticks, like a player's click
        context.getInput().holdKeyFor(options -> options.keyAttack, 5);
        context.waitTicks(SETTLE_TICKS);

        boolean paintingGone = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getEntity(paintingId) == null);
        String fenceState = server.computeOnServer(minecraftServer -> minecraftServer.overworld().getBlockState(fence).toString());
        LOGGER.info("[painting_click_through] painting broken={} fence={}", paintingGone, fenceState);
        context.takeScreenshot("painting_click_through");
        if (!paintingGone) {
            failures.add("painting_click_through: clicking the painting did not break it");
        }
        if (!fenceState.contains("oak_fence")) {
            failures.add("painting_click_through: clicking the painting also broke the fence behind it - " + fenceState);
        }
        server.runCommand("gamemode survival @a");
    }

    private static void runFramesBothSidesCase(ClientGameTestContext context, TestServerContext server, BlockPos door, Direction facing, DoorHingeSide hinge, List<String> failures) {
        String name = "frames_both_sides_" + facing.getName() + "_" + hinge.getSerializedName();

        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:air", door.getX() - 3, door.getY(), door.getZ() - 3, door.getX() + 3, door.getY() + 3, door.getZ() + 3));
        server.runCommand(String.format("fill %d %d %d %d %d %d minecraft:stone", door.getX() - 6, door.getY() - 1, door.getZ() - 6, door.getX() + 6, door.getY() - 1, door.getZ() + 6));
        server.runCommand("kill @e[type=minecraft:item_frame]");
        server.runCommand("kill @e[type=minecraft:painting]");

        // One frame on the closed door's inner face (set back inside the door block), one on its outer face (flush)
        int[] frameIds = server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            BlockState lower = Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, facing)
                    .setValue(DoorBlock.HINGE, hinge)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(door, lower, 3);
            level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

            ItemFrame inner = new ItemFrame(level, door.relative(facing), facing);
            ItemFrame outer = new ItemFrame(level, door.relative(facing.getOpposite()), facing.getOpposite());
            level.addFreshEntity(inner);
            level.addFreshEntity(outer);
            return new int[]{inner.getId(), outer.getId()};
        });

        BlockPos view = door.relative(facing.getClockWise(), 3).above();
        server.runCommand(String.format("tp @a %.1f %d %.1f facing %.1f %.1f %.1f", view.getX() + 0.5, view.getY(), view.getZ() + 0.5, door.getX() + 0.5, door.getY() + 0.5, door.getZ() + 0.5));
        context.waitFor(client -> client.level != null && client.level.getEntity(frameIds[0]) instanceof ItemFrame && client.level.getEntity(frameIds[1]) instanceof ItemFrame);
        context.waitTicks(SETTLE_TICKS);

        check(context, server, name + "_closed_inner", frameIds[0], door, failures);
        check(context, server, name + "_closed_outer", frameIds[1], door, failures);

        // Toggle slowly, checking vanilla's "can this stay up" every tick for both frames
        for (int toggle = 0; toggle < 6; toggle++) {
            boolean open = toggle % 2 == 0;
            setDoorOpen(server, door, open);
            for (int tick = 0; tick < 25; tick++) {
                String problem = server.computeOnServer(minecraftServer -> {
                    StringBuilder result = new StringBuilder();
                    for (int id : frameIds) {
                        Entity entity = minecraftServer.overworld().getEntity(id);
                        if (!(entity instanceof ItemFrame frame)) {
                            result.append("frame ").append(id).append(" is gone; ");
                        } else if (!frame.survives()) {
                            result.append("frame ").append(id).append(" would pop off: ").append(PaintingSnapshot.of(frame)).append("; ");
                        }
                    }
                    return result.isEmpty() ? null : result + "door=" + minecraftServer.overworld().getBlockState(door);
                });
                if (problem != null) {
                    failures.add(name + " toggle " + toggle + " tick " + tick + ": " + problem);
                    LOGGER.info("[{}] toggle {} tick {}: {}", name, toggle, tick, problem);
                    break;
                }
                context.waitTick();
            }
            String state = open ? "_open_" : "_closed_";
            check(context, server, name + state + toggle + "_first", frameIds[0], door, failures);
            check(context, server, name + state + toggle + "_second", frameIds[1], door, failures);
        }

        // Then spam it
        for (int toggle = 0; toggle < 20; toggle++) {
            setDoorOpen(server, door, toggle % 2 == 0);
            context.waitTicks(1 + toggle % 3);
        }
        context.waitTicks(SETTLE_TICKS);
        check(context, server, name + "_spam_first", frameIds[0], door, failures);
        check(context, server, name + "_spam_second", frameIds[1], door, failures);
        context.takeScreenshot(name);
    }

    private static void setDoorOpen(TestServerContext server, BlockPos door, boolean open) {
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            BlockState state = level.getBlockState(door);
            ((DoorBlock) state.getBlock()).setOpen(null, level, state, door, open);
        });
    }

    private static void check(ClientGameTestContext context, TestServerContext server, String label, int paintingId, BlockPos door, List<String> failures) {
        PaintingSnapshot onServer = server.computeOnServer(minecraftServer -> PaintingSnapshot.of(minecraftServer.overworld().getEntity(paintingId)));
        PaintingSnapshot onClient = context.computeOnClient((Minecraft client) -> PaintingSnapshot.of(client.level.getEntity(paintingId)));
        LOGGER.info("[{}] server: {}", label, onServer);
        LOGGER.info("[{}] client: {}", label, onClient);
        context.takeScreenshot(label);

        if (onServer == null) {
            failures.add(label + ": painting is gone on the server (popped off)");
            return;
        }
        if (onClient == null) {
            failures.add(label + ": painting is missing on the client");
            return;
        }
        if (!onServer.anchor().equals(onClient.anchor()) || onServer.facing() != onClient.facing()) {
            failures.add(label + ": client and server disagree - server " + onServer + ", client " + onClient);
        }
        if (!onServer.support().equals(door) && !onServer.support().equals(door.above())) {
            failures.add(label + ": painting is not hanging on the door - " + onServer);
        }
        if (!onServer.attached()) {
            failures.add(label + ": server says the painting is not attached - " + onServer);
        }
        if (!onClient.attached()) {
            failures.add(label + ": client says the painting is not attached - " + onClient);
        }
    }

    private record PaintingSnapshot(BlockPos anchor, Direction facing, BlockPos support, boolean attached, String position) {
        static PaintingSnapshot of(Entity entity) {
            if (!(entity instanceof HangingEntity painting)) {
                return null;
            }
            BlockPos anchor = painting.getPos();
            return new PaintingSnapshot(anchor, painting.getDirection(), anchor.relative(painting.getDirection().getOpposite()), painting.survives(),
                    String.format("%.3f, %.3f, %.3f", painting.getX(), painting.getY(), painting.getZ()));
        }

        @Override
        public String toString() {
            return "anchor=" + anchor.toShortString() + " facing=" + facing.getName() + " support=" + support.toShortString()
                    + " attached=" + attached + " position=" + position;
        }
    }
}
