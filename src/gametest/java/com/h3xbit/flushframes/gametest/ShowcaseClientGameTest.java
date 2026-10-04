package com.h3xbit.flushframes.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.entity.decoration.painting.PaintingVariants;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Builds a few clean scenes and takes screenshots for the mod page. Only runs when the
// FLUSH_FRAMES_SHOWCASE environment variable is set - the screenshots end up in
// build/run/clientGameTest/screenshots. Files starting with gif_ are frames for animations.
public class ShowcaseClientGameTest implements FabricClientGameTest {
    private static final Logger LOGGER = LoggerFactory.getLogger("flush_frames-showcase");
    private static final double EYE_HEIGHT = 1.62;

    private static final BlockPos[] DOORS = {new BlockPos(14, 100, 0), new BlockPos(16, 100, 0), new BlockPos(31, 100, 0)};
    private static final BlockPos[] TRAPDOORS = {new BlockPos(21, 100, 0), new BlockPos(23, 100, 0), new BlockPos(37, 100, 0)};
    private static final BlockPos[] PLATES = {new BlockPos(-16, 100, 0), new BlockPos(-14, 100, 0), new BlockPos(35, 100, 0)};

    public static boolean enabled() {
        return System.getenv("FLUSH_FRAMES_SHOWCASE") != null;
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        if (!enabled()) {
            return;
        }

        // Full size window - the images get cropped afterwards
        context.runOnClient(client -> GLFW.glfwMaximizeWindow(client.getWindow().handle()));
        context.waitTicks(10);
        int[] size = context.computeOnClient(client -> {
            int[] width = new int[1];
            int[] height = new int[1];
            GLFW.glfwGetFramebufferSize(client.getWindow().handle(), width, height);
            return new int[]{width[0], height[0]};
        });
        LOGGER.info("Showcase window size {}x{}", size[0], size[1]);
        context.getInput().resizeWindow(size[0], size[1]);

        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            TestServerContext server = singleplayer.getServer();
            server.runCommand("gamemode creative @a");
            server.runCommand("time set 6000");
            server.runCommand("weather clear 100000");
            server.runCommand("gamerule advance_time false");
            server.runCommand("gamerule doDaylightCycle false");
            server.runCommand("gamerule doMobSpawning false");
            server.runCommand("tp @a 0.5 101 8.5");
            context.waitTicks(40);

            buildPlatform(server);
            buildHeroRow(server);
            buildDoors(server);
            buildTrapdoors(server);
            buildTopFaces(server);
            buildBanners(server);
            buildIconPost(server);
            buildFeatures(server);
            context.waitTicks(20);

            // Spectator so the camera can go anywhere without bumping into blocks
            server.runCommand("gamemode spectator @a");
            // F1 - hide the HUD and hand
            context.getInput().pressKey(options -> options.keyToggleGui);

            shot(context, server, "features", 34.2, 101.8, 4.8, 34.2, 100.6, 0.3);
            setDoors(server, true);
            setTrapdoors(server, true);
            setPlates(server, true);
            capture(context, "features_moved");
            setDoors(server, false);
            setTrapdoors(server, false);
            setPlates(server, false);
            shot(context, server, "hero", 4.0, 101.4, 4.0, -1.0, 100.5, 0.5);
            shot(context, server, "hero_front", 0.5, 100.9, 6.0, 0.5, 100.5, 0.5);
            shot(context, server, "detail_fence", -4.3, 101.0, 2.8, -6.0, 100.5, 0.9);
            shot(context, server, "detail_bars", -0.4, 101.0, 2.6, -2.0, 100.5, 0.9);
            shot(context, server, "top_faces", -14.0, 102.0, 2.6, -14.0, 100.1, 0.2);
            shot(context, server, "banners", 2.5, 101.3, -8.0, 2.5, 101.0, -13.0);
            shot(context, server, "doors_closed", 18.0, 101.6, 4.0, 15.3, 100.9, 0.5);
            setDoors(server, true);
            capture(context, "doors_open");
            setDoors(server, false);

            // Animation frames - each toggle is instant in game, so two states per loop is enough
            camera(server, 34.2, 101.8, 4.8, 34.2, 100.6, 0.3);
            for (int i = 0; i < 2; i++) {
                setDoors(server, false);
                setTrapdoors(server, false);
                setPlates(server, false);
                capture(context, "gif_features_" + (i * 2));
                setDoors(server, true);
                setTrapdoors(server, true);
                setPlates(server, true);
                capture(context, "gif_features_" + (i * 2 + 1));
            }
            setDoors(server, false);
            setTrapdoors(server, false);
            setPlates(server, false);

            camera(server, 17.6, 101.5, 3.2, 15.3, 100.9, 0.5);
            for (int i = 0; i < 2; i++) {
                setDoors(server, false);
                capture(context, "gif_door_" + (i * 2));
                setDoors(server, true);
                capture(context, "gif_door_" + (i * 2 + 1));
            }
            setDoors(server, false);

            camera(server, 24.2, 101.5, 2.6, 22.0, 100.3, 0.4);
            for (int i = 0; i < 2; i++) {
                setTrapdoors(server, false);
                capture(context, "gif_trapdoor_" + (i * 2));
                setTrapdoors(server, true);
                capture(context, "gif_trapdoor_" + (i * 2 + 1));
            }
            setTrapdoors(server, false);

            context.runOnClient(client -> client.options.fov().set(40));
            camera(server, -15.5, 100.3, 1.5, -15.5, 100.05, 0.5);
            for (int i = 0; i < 2; i++) {
                setPlates(server, false);
                capture(context, "gif_pressure_plate_" + (i * 2));
                setPlates(server, true);
                capture(context, "gif_pressure_plate_" + (i * 2 + 1));
            }
            setPlates(server, false);

            // Close-up for the icon - narrow field of view so it looks flat and clean
            context.runOnClient(client -> client.options.fov().set(30));
            shot(context, server, "icon_source", 0.5, 100.5, 17.2, 0.5, 100.5, 14.5);
            server.runCommand("fill -3 99 12 3 103 12 minecraft:white_concrete");
            shot(context, server, "icon_source_light", 0.5, 100.5, 17.2, 0.5, 100.5, 14.5);
            context.runOnClient(client -> client.options.fov().set(70));
            context.getInput().pressKey(options -> options.keyToggleGui);
        }
        LOGGER.info("Showcase screenshots taken");
    }

    // Eye position -> the tp command takes the feet position, and aims from the feet too
    private static void camera(TestServerContext server, double x, double eyeY, double z, double lookX, double lookY, double lookZ) {
        server.runCommand(String.format("tp @a %.2f %.2f %.2f facing %.2f %.2f %.2f", x, eyeY - EYE_HEIGHT, z, lookX, lookY - EYE_HEIGHT, lookZ));
    }

    private static void shot(ClientGameTestContext context, TestServerContext server, String name, double x, double eyeY, double z, double lookX, double lookY, double lookZ) {
        camera(server, x, eyeY, z, lookX, lookY, lookZ);
        context.waitTicks(10);
        capture(context, name);
    }

    private static void capture(ClientGameTestContext context, String name) {
        context.waitTicks(8);
        context.waitFor(client -> client.levelRenderer.hasRenderedAllSections(), 400);
        context.waitTicks(3);
        context.takeScreenshot(name);
    }

    private static void setDoors(TestServerContext server, boolean open) {
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            for (BlockPos door : DOORS) {
                BlockState state = level.getBlockState(door);
                ((DoorBlock) state.getBlock()).setOpen(null, level, state, door, open);
            }
        });
    }

    private static void setTrapdoors(TestServerContext server, boolean open) {
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            for (BlockPos trapdoor : TRAPDOORS) {
                level.setBlock(trapdoor, level.getBlockState(trapdoor).setValue(TrapDoorBlock.OPEN, open), 3);
            }
        });
    }

    private static void setPlates(TestServerContext server, boolean powered) {
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            for (BlockPos plate : PLATES) {
                level.setBlock(plate, level.getBlockState(plate).setValue(PressurePlateBlock.POWERED, powered), 2);
            }
        });
    }

    // Plank floor with stone brick backdrops behind each scene
    private static void buildPlatform(TestServerContext server) {
        server.runCommand("fill -30 100 -20 50 110 22 minecraft:air");
        server.runCommand("fill -30 99 -20 50 99 22 minecraft:grass_block");
        server.runCommand("fill -30 98 -20 50 98 22 minecraft:dirt");
        server.runCommand("fill -19 99 -3 43 99 4 minecraft:spruce_planks");
        server.runCommand("fill -19 100 -3 43 102 -3 minecraft:stone_bricks");
        server.runCommand("fill -19 103 -3 43 103 -3 minecraft:stone_brick_slab");
        server.runCommand("kill @e[type=!minecraft:player]");
    }

    // A row of thin blocks with frames holding items, all facing the camera
    private static void buildHeroRow(TestServerContext server) {
        server.runCommand("setblock -6 100 0 minecraft:oak_fence");
        server.runCommand("setblock -4 100 0 minecraft:mossy_cobblestone_wall");
        server.runCommand("setblock -2 100 0 minecraft:iron_bars");
        server.runCommand("setblock 0 100 0 minecraft:chest[facing=south]");
        server.runCommand("setblock 2 100 0 minecraft:decorated_pot");
        server.runCommand("setblock 4 100 0 minecraft:lightning_rod");
        server.runCommand("setblock 6 100 0 minecraft:spruce_fence");
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            frame(level, new BlockPos(-6, 100, 1), Direction.SOUTH, Items.COMPASS);
            frame(level, new BlockPos(-4, 100, 1), Direction.SOUTH, Items.GOLDEN_APPLE);
            frame(level, new BlockPos(-2, 100, 1), Direction.SOUTH, Items.DIAMOND);
            frame(level, new BlockPos(0, 100, 1), Direction.SOUTH, Items.EMERALD);
            frame(level, new BlockPos(2, 100, 1), Direction.SOUTH, Items.AMETHYST_SHARD);
            frame(level, new BlockPos(4, 100, 1), Direction.SOUTH, Items.COPPER_INGOT);
            painting(level, new BlockPos(6, 100, 1), Direction.SOUTH, PaintingVariants.KEBAB);
        });
    }

    // Two doors: one with a 1x2 painting, one with a frame on each half
    private static void buildDoors(TestServerContext server) {
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            door(level, DOORS[0], Blocks.SPRUCE_DOOR.defaultBlockState());
            door(level, DOORS[1], Blocks.OAK_DOOR.defaultBlockState());
            painting(level, DOORS[0].south(), Direction.SOUTH, PaintingVariants.WANDERER);
            frame(level, DOORS[1].south(), Direction.SOUTH, Items.CLOCK);
            frame(level, DOORS[1].south().above(), Direction.SOUTH, Items.FILLED_MAP);
        });
    }

    // The main blocks side by side: a fence, a door, a half slab, a pressure plate, a trapdoor and a wall
    private static void buildFeatures(TestServerContext server) {
        server.runCommand("setblock 29 100 0 minecraft:oak_fence");
        server.runCommand("setblock 39 100 0 minecraft:mossy_cobblestone_wall");
        server.runCommand("setblock 33 100 0 minecraft:spruce_slab[type=bottom]");
        server.runCommand("setblock 35 100 0 minecraft:oak_pressure_plate");
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            door(level, DOORS[2], Blocks.SPRUCE_DOOR.defaultBlockState());
            level.setBlock(TRAPDOORS[2], Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.HALF, Half.BOTTOM), 3);
            frame(level, DOORS[2].south(), Direction.SOUTH, Items.COMPASS);
            frame(level, DOORS[2].south().above(), Direction.SOUTH, Items.FILLED_MAP);
            frame(level, new BlockPos(33, 101, 0), Direction.UP, Items.DIAMOND);
            frame(level, PLATES[2].above(), Direction.UP, Items.GOLD_INGOT);
            frame(level, TRAPDOORS[2].above(), Direction.UP, Items.EMERALD);
            frame(level, new BlockPos(29, 100, 1), Direction.SOUTH, Items.CLOCK);
            frame(level, new BlockPos(39, 100, 1), Direction.SOUTH, Items.GOLDEN_APPLE);
        });
    }

    // Closed trapdoors on the floor with frames lying on top
    private static void buildTrapdoors(TestServerContext server) {
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            level.setBlock(TRAPDOORS[0], Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.HALF, Half.BOTTOM), 3);
            level.setBlock(TRAPDOORS[1], Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.NORTH).setValue(TrapDoorBlock.HALF, Half.BOTTOM), 3);
            frame(level, TRAPDOORS[0].above(), Direction.UP, Items.GLOW_BERRIES);
            frame(level, TRAPDOORS[1].above(), Direction.UP, Items.SPYGLASS);
        });
    }

    // Frames lying on top of pressure plates and slabs
    private static void buildTopFaces(TestServerContext server) {
        server.runCommand("setblock -16 100 0 minecraft:oak_pressure_plate");
        server.runCommand("setblock -14 100 0 minecraft:stone_pressure_plate");
        server.runCommand("setblock -12 100 0 minecraft:smooth_stone_slab");
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            frame(level, new BlockPos(-16, 101, 0), Direction.UP, Items.GOLD_INGOT);
            frame(level, new BlockPos(-14, 101, 0), Direction.UP, Items.REDSTONE);
            frame(level, new BlockPos(-12, 101, 0), Direction.UP, Items.APPLE);
        });
    }

    // A standing banner and a wall banner with frames on the cloth
    private static void buildBanners(TestServerContext server) {
        server.runCommand("fill -3 99 -15 8 99 -8 minecraft:spruce_planks");
        server.runCommand("fill -3 100 -15 8 102 -15 minecraft:stone_bricks");
        server.runCommand("fill -3 103 -15 8 103 -15 minecraft:stone_brick_slab");
        server.runOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            level.setBlock(new BlockPos(0, 100, -12), Blocks.BANNER.red().defaultBlockState().setValue(BannerBlock.ROTATION, 0), 3);
            level.setBlock(new BlockPos(5, 101, -14), Blocks.WALL_BANNER.blue().defaultBlockState().setValue(WallBannerBlock.FACING, Direction.SOUTH), 3);
            frame(level, new BlockPos(0, 100, -11), Direction.SOUTH, Items.IRON_SWORD);
            frame(level, new BlockPos(5, 101, -13), Direction.SOUTH, Items.BOW);
        });
    }

    // A single fence post with a frame in front of a plain wall, for the icon close-up
    private static void buildIconPost(TestServerContext server) {
        server.runCommand("fill -3 99 12 3 99 17 minecraft:spruce_planks");
        server.runCommand("fill -3 99 12 3 103 12 minecraft:gray_concrete");
        server.runCommand("setblock 0 100 14 minecraft:oak_fence");
        server.runOnServer(minecraftServer -> frame(minecraftServer.overworld(), new BlockPos(0, 100, 15), Direction.SOUTH, Items.EMERALD));
    }

    private static void door(ServerLevel level, BlockPos pos, BlockState door) {
        BlockState lower = door.setValue(DoorBlock.FACING, Direction.NORTH).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(pos, lower, 3);
        level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    private static void frame(ServerLevel level, BlockPos anchor, Direction facing, Item item) {
        ItemFrame frame = new ItemFrame(level, anchor, facing);
        frame.setItem(new ItemStack(item));
        level.addFreshEntity(frame);
    }

    private static void painting(ServerLevel level, BlockPos anchor, Direction facing, ResourceKey<PaintingVariant> variant) {
        level.addFreshEntity(new Painting(level, anchor, facing, level.registryAccess().lookupOrThrow(Registries.PAINTING_VARIANT).getOrThrow(variant)));
    }
}
