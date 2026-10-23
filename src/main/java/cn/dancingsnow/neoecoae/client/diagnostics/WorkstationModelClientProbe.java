package cn.dancingsnow.neoecoae.client.diagnostics;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation;
import cn.dancingsnow.neoecoae.blocks.entity.ECOLargeIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.recipe.LargeWorkstationModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in real chunk-rendering fixtures. Working states are visual samples, not recipe execution tests. */
final class WorkstationModelClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("neoecoae.workstationModelSmoke");
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static int specimen;
    private static int stage;
    private static int ticks;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static boolean previousPauseOnLostFocus;
    private static boolean previousHideGui;
    private static int previousFov;
    private static boolean finished;

    private WorkstationModelClientProbe() {}

    static void tick(Minecraft client) {
        if (failure != null) throw new IllegalStateException("Workstation model fixture failed", failure);
        if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) client.setScreen(null);
        if (++ticks > 600) throw new IllegalStateException("Workstation model fixture timed out at " + specimen + ":" + stage);
        String run = System.getProperty("neoecoae.workstationModelSmoke");
        int count = run.equals("before") ? 2 : 64;
        if (specimen == count) {
            if (!finished) {
                finished = true;
                prepared = false;
                client.getSingleplayerServer().execute(() -> {
                    try {
                        ServerLevel level = client.getSingleplayerServer().overworld();
                        BlockPos controller = position(Direction.NORTH);
                        level.setBlockAndUpdate(controller.west(), Blocks.AIR.defaultBlockState());
                        require(!level.getBlockState(controller).getValue(ECOIntegratedWorkingStation.FORMED),
                            "controller remained formed after dismantling");
                        for (BlockPos member : BlockPos.betweenClosed(controller.offset(-1, -1, 0), controller.offset(1, 0, 1))) {
                            var state = level.getBlockState(member);
                            require(state.isAir() || state.getRenderShape() != net.minecraft.world.level.block.RenderShape.INVISIBLE,
                                "member remained hidden after dismantling: " + member);
                        }
                        LOG.info("WORKSTATION_MODEL dismantling restored standalone component models");
                        prepared = true;
                    } catch (Throwable error) {
                        failure = error;
                    }
                });
                ticks = 0;
            }
            if (prepared && ticks >= 20) {
                LOG.info("WORKSTATION_MODEL PASS: {} real chunk-rendered views; working states are visual fixtures", count);
                client.options.pauseOnLostFocus = previousPauseOnLostFocus;
                client.options.hideGui = previousHideGui;
                client.options.fov().set(previousFov);
                client.stop();
            }
            return;
        }
        Direction front = DIRECTIONS[(specimen / 4) % 4];
        LargeWorkstationModule module = LargeWorkstationModule.values()[specimen / 16];
        boolean working = (specimen / 2) % 2 != 0;
        boolean rear = specimen % 2 != 0;
        BlockPos pos = position(front);
        switch (stage) {
            case 0 -> {
                if (specimen == 0) {
                    previousPauseOnLostFocus = client.options.pauseOnLostFocus;
                    previousHideGui = client.options.hideGui;
                    previousFov = client.options.fov().get();
                }
                client.options.pauseOnLostFocus = false;
                client.options.hideGui = true;
                client.options.fov().set(40);
                client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                prepared = false;
                var playerId = client.player.getUUID();
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                        ServerLevel level = player.serverLevel();
                        if (specimen == 0) {
                            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                            level.setDayTime(6000);
                            level.setWeatherParameters(6000, 0, false, false);
                            player.setGameMode(GameType.SPECTATOR);
                            for (BlockPos floor : BlockPos.betweenClosed(-10, 168, -10, 26, 168, 26)) {
                                level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                            }
                            for (Direction direction : DIRECTIONS) build(level, position(direction), direction);
                        }
                        var controller = (ECOLargeIntegratedWorkingStationBlockEntity) level.getBlockEntity(pos);
                        controller.getModuleInventory().setStackInSlot(0, module == LargeWorkstationModule.REGULAR
                            ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(module.upgradeCardId())));
                        controller.rebuildMultiblock();
                        require(controller.isFormed(), "structure did not form: " + front);
                        Vec3 target = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(front.getOpposite().getNormal()).scale(0.5)).add(0, -0.4, 0);
                        Vec3 eye = target.add(Vec3.atLowerCornerOf(front.getNormal()).scale(rear ? -4 : 4))
                            .add(Vec3.atLowerCornerOf(front.getClockWise().getNormal()).scale(rear ? -3.2 : 3.2)).add(0, 2.5, 0);
                        Vec3 delta = target.subtract(eye);
                        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
                        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance()));
                        player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
                        if (specimen == 0) {
                            for (BlockPos member : BlockPos.betweenClosed(pos.offset(-1, -1, 0), pos.offset(1, 0, 1))) {
                                var state = level.getBlockState(member);
                                LOG.info("WORKSTATION_MODEL server member {} {} render={}", member, state, state.getRenderShape());
                            }
                        }
                        prepared = true;
                    } catch (Throwable error) {
                        failure = error;
                    }
                });
                advance();
            }
            case 1 -> {
                if (!prepared || ticks < 30 || !client.level.getBlockState(pos).is(NEBlocks.INTEGRATED_WORKING_STATION.get())
                    || !client.level.getBlockState(pos).getValue(ECOIntegratedWorkingStation.FORMED)) return;
                // Select the live block model independently of recipe availability in this isolated world.
                ((ECOLargeIntegratedWorkingStationBlockEntity) client.level.getBlockEntity(pos)).setWorking(working);
                client.levelRenderer.allChanged();
                advance();
            }
            case 2 -> {
                var state = client.level.getBlockState(pos);
                // The idle server sends its real visual state every second. Keep the opt-in render sample
                // consistent in the client entity and wait for the changed chunk to reach the framebuffer.
                if (state.getValue(ECOIntegratedWorkingStation.WORKING) != working) {
                    ((ECOLargeIntegratedWorkingStationBlockEntity) client.level.getBlockEntity(pos)).setWorking(working);
                    ticks = 0;
                    return;
                }
                if (ticks < 10) return;
                require(state.getValue(ECOIntegratedWorkingStation.MODULE) == module, "module state not synchronized");
                require(state.getValue(ECOIntegratedWorkingStation.WORKING) == working, "visual working state changed");
                if (!run.equals("before")) {
                    var model = client.getBlockRenderer().getBlockModel(state);
                    var layers = model.getRenderTypes(state, RandomSource.create(0), ModelData.EMPTY).asList();
                    require(layers.equals(java.util.List.of(RenderType.cutout())), "formed model did not retain cutout rendering");
                    var quads = model.getQuads(state, null, RandomSource.create(0), ModelData.EMPTY, RenderType.cutout());
                    require(quads.size() == 36, "unexpected duplicate/missing formed model faces: " + quads.size());
                    int lights = 0;
                    for (var quad : quads) {
                        if (!quad.getSprite().contents().name().getPath().contains("controller_formed_light")) continue;
                        lights++;
                        require(!quad.hasAmbientOcclusion(), "light face retained ambient occlusion");
                        for (int vertex = 0; vertex < 4; vertex++) {
                            int light = quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV2];
                            require(light == 0x00F000F0, "light face did not receive native Forge full brightness");
                        }
                    }
                    require(lights == 5, "expected five light overlay faces: " + lights);
                    LOG.info("WORKSTATION_MODEL baked audit: {} {} {} faces={} lightFaces={} layers={}",
                        module, working, front, quads.size(), lights, layers);
                }
                String name = "workstation-" + run + "-" + module.getSerializedName() + "-" + (working ? "on" : "off")
                    + "-" + front.getSerializedName() + "-" + (rear ? "rear" : "front") + ".png";
                Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(), message -> LOG.info("WORKSTATION_MODEL {}", message.getString()));
                LOG.info("WORKSTATION_MODEL captured {} state={}", name, state);
                specimen++;
                advance();
                stage = 0;
            }
        }
    }

    static void build(ServerLevel level, BlockPos controller, Direction front) {
        Direction back = front.getOpposite();
        Direction right = front.getClockWise();
        for (int x = -1; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                for (int y = -1; y <= 0; y++) {
                    Block block = NEBlocks.LARGE_INTEGRATED_WORKING_STATION_CASING.get();
                    if (y == -1 && z == 1) {
                        block = x == -1 ? NEBlocks.LARGE_INTEGRATED_WORKING_STATION_OUTPUT_HATCH.get()
                            : x == 1 ? NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INPUT_HATCH.get()
                            : NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.get();
                    }
                    if (x == 0 && y == 0 && z == 0) continue;
                    level.setBlock(controller.relative(right, x).relative(back, z).offset(0, y, 0),
                        block.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        level.setBlock(controller, NEBlocks.INTEGRATED_WORKING_STATION.getDefaultState()
            .setValue(ECOIntegratedWorkingStation.FACING, front), Block.UPDATE_ALL);
    }

    private static BlockPos position(Direction front) {
        return switch (front) {
            case NORTH -> new BlockPos(0, 170, 0);
            case EAST -> new BlockPos(16, 170, 0);
            case SOUTH -> new BlockPos(0, 170, 16);
            case WEST -> new BlockPos(16, 170, 16);
            default -> throw new IllegalArgumentException("Horizontal directions only");
        };
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void advance() {
        stage++;
        ticks = 0;
    }
}
