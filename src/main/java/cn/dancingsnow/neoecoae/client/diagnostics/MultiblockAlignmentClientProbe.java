package cn.dancingsnow.neoecoae.client.diagnostics;

import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.ECOCellModels;
import cn.dancingsnow.neoecoae.api.ECOComputationModels;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.blocks.NEBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.client.model.CoolingControllerBakedModel;
import cn.dancingsnow.neoecoae.client.all.NEExtraModels;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlanContext;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockRotation;
import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in checks against the real model manager, server calculator and client chunk renderer. */
final class MultiblockAlignmentClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("neoecoae.multiblockAlignment");
    private static final BlockPos CONTROLLER = new BlockPos(8, 180, 8);
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final List<Fixture> FIXTURES = new ArrayList<>();
    private static Map<BlockPos, BlockState> members = Map.of();
    private static Map<BlockPos, ItemStack> installedCells = Map.of();
    private static int specimen;
    private static int ticks;
    private static boolean submitted;
    private static boolean clearing;
    private static volatile boolean cleared;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static boolean finished;
    private static boolean previousPauseOnLostFocus;
    private static boolean previousHideGui;
    private static int previousFov;

    private MultiblockAlignmentClientProbe() {}

    static void verifyModels(Minecraft client) {
        List<ResourceLocation> models = new ArrayList<>(ECOCellModels.getDeferredModels().values());
        models.add(ECOCellModels.DEFAULT_MODEL);
        models.add(NEExtraModels.COMPUTATION_COOLING_CONTROLLER_GLASS);
        for (ECOTier tier : List.of(ECOTier.L4, ECOTier.L6, ECOTier.L9)) {
            models.add(ECOComputationModels.getCableConnectedModel(tier));
            models.add(ECOComputationModels.getCableDisconnectedModel(tier));
        }
        for (var item : List.of(NEItems.ECO_COMPUTATION_CELL_L4, NEItems.ECO_COMPUTATION_CELL_L6,
                NEItems.ECO_COMPUTATION_CELL_L9)) {
            models.add(ECOComputationModels.getNormalModel(item.get()));
            models.add(ECOComputationModels.getFormedModel(item.get()));
        }
        var manager = client.getModelManager();
        List<ResourceLocation> missing = models.stream()
            .filter(id -> manager.getModel(id) == manager.getMissingModel()).toList();
        LOG.info("MULTIBLOCK_ALIGNMENT extraModels={} missing={}", models.size(), missing);
        require(missing.isEmpty(), "unregistered multiblock models: " + missing);
        for (var block : List.of(NEBlocks.COMPUTATION_COOLING_CONTROLLER_L4,
                NEBlocks.COMPUTATION_COOLING_CONTROLLER_L6, NEBlocks.COMPUTATION_COOLING_CONTROLLER_L9)) {
            for (BlockState state : block.get().getStateDefinition().getPossibleStates()) {
                if (state.getValue(NEBlock.FORMED)) {
                    require(client.getBlockRenderer().getBlockModel(state) instanceof CoolingControllerBakedModel,
                        "formed cooling model was not wrapped: " + state);
                }
            }
        }
        for (MultiBlockDefinition definition : NEMultiBlocks.DEFINITIONS) {
            if (definition == NEMultiBlocks.LARGE_INTEGRATED_WORKING_STATION) continue;
            for (Direction front : FACINGS) {
                for (boolean mirrored : List.of(false, true)) {
                    for (int repeats : List.of(1, 3)) FIXTURES.add(new Fixture(definition, front, mirrored, repeats));
                }
            }
        }
        require(FIXTURES.size() == 144, "expected nine tiered structures and 144 fixtures");
        LOG.info("MULTIBLOCK_ALIGNMENT pre-world definitionMax={} config={}/{}/{}",
            NEMultiBlocks.STORAGE_SYSTEM_L4.getExpandMax(), NEConfig.craftingSystemMaxLength,
            NEConfig.computationSystemMaxLength, NEConfig.storageSystemMaxLength);
    }

    static void tick(Minecraft client) {
        if (finished) return;
        if (failure != null) throw new IllegalStateException("Multiblock alignment fixture failed", failure);
        if (++ticks > 600) throw new IllegalStateException("Multiblock alignment timed out at " + specimen);
        if (specimen == FIXTURES.size()) {
            finished = true;
            LOG.info("MULTIBLOCK_ALIGNMENT PASSED fixtures={} formation, dismantling, repair and client models", specimen);
            client.options.pauseOnLostFocus = previousPauseOnLostFocus;
            client.options.hideGui = previousHideGui;
            client.options.fov().set(previousFov);
            client.stop();
            return;
        }
        var fixture = FIXTURES.get(specimen);
        if (!submitted && !members.isEmpty()) {
            if (!clearing) {
                clearing = true;
                client.getSingleplayerServer().execute(() -> {
                    var level = client.getSingleplayerServer().overworld();
                    for (BlockPos pos : members.keySet()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    cleared = true;
                });
            }
            if (!cleared) return;
            var remaining = members.keySet().stream().filter(pos -> !client.level.getBlockState(pos).isAir()).findFirst();
            if (remaining.isPresent()) {
                if (ticks == 100) {
                    var pos = remaining.orElseThrow();
                    LOG.warn("MULTIBLOCK_ALIGNMENT clearing pos={} client={}", pos, client.level.getBlockState(pos));
                    client.getSingleplayerServer().execute(() -> {
                        var level = client.getSingleplayerServer().overworld();
                        LOG.warn("MULTIBLOCK_ALIGNMENT clearing server={} tick={} chunkStatus={} clientPlayer={} trackingPlayers={} serverPlayers={}",
                            level.getBlockState(pos), client.getSingleplayerServer().getTickCount(),
                            level.getChunkAt(pos).getFullStatus(), client.player.position(),
                            level.getChunkSource().chunkMap.getPlayers(new net.minecraft.world.level.ChunkPos(pos), false).size(),
                            level.players().stream().map(player -> player.position()).toList());
                    });
                }
                return;
            }
            // Observe the teardown packet before placing the next specimen at the same coordinates.
            members = Map.of();
            clearing = false;
            cleared = false;
        }
        if (!submitted) {
            submitted = true;
            if (specimen == 0) {
                previousPauseOnLostFocus = client.options.pauseOnLostFocus;
                previousHideGui = client.options.hideGui;
                previousFov = client.options.fov().get();
            }
            client.options.pauseOnLostFocus = false;
            client.options.hideGui = true;
            client.options.fov().set(45);
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            var playerId = client.player.getUUID();
            client.getSingleplayerServer().execute(() -> {
                try {
                    var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                    var level = player.serverLevel();
                    if (specimen == 0) {
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                        level.setDayTime(6000);
                        level.setWeatherParameters(6000, 0, false, false);
                        player.setGameMode(GameType.SPECTATOR);
                        for (BlockPos floor : BlockPos.betweenClosed(-5, 178, -5, 21, 178, 21)) {
                            level.setBlock(floor, Blocks.SMOOTH_STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                    prepare(level, fixture);
                    Vec3 target = Vec3.atCenterOf(CONTROLLER).add(0, 0, 0.5);
                    Vec3 eye = target.add(Vec3.atLowerCornerOf(fixture.front().getNormal()).scale(9))
                        .add(Vec3.atLowerCornerOf(fixture.front().getClockWise().getNormal()).scale(4)).add(0, 5, 0);
                    Vec3 delta = target.subtract(eye);
                    player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z,
                        (float) Math.toDegrees(Math.atan2(-delta.x, delta.z)),
                        (float) -Math.toDegrees(Math.atan2(delta.y, delta.horizontalDistance())));
                    prepared = true;
                } catch (Throwable error) {
                    failure = error;
                }
            });
            return;
        }
        if (!prepared || ticks < 8) return;
        for (var member : members.entrySet()) {
            BlockState state = client.level.getBlockState(member.getKey());
            if (state != member.getValue()) {
                if (ticks == 100) {
                    LOG.warn("MULTIBLOCK_ALIGNMENT waiting pos={} expected={} client={}",
                        member.getKey(), member.getValue(), state);
                    client.getSingleplayerServer().execute(() -> LOG.warn("MULTIBLOCK_ALIGNMENT current server={}",
                        client.getSingleplayerServer().overworld().getBlockState(member.getKey())));
                }
                return;
            }
        }
        for (var cell : installedCells.entrySet()) {
            if (!(client.level.getBlockEntity(cell.getKey()) instanceof ICellHost host)) return;
            ItemStack actual = host.getCellStack() == null ? ItemStack.EMPTY : host.getCellStack();
            if (actual.isEmpty() != cell.getValue().isEmpty() || actual.getItem() != cell.getValue().getItem()) return;
            if (host instanceof ECOComputationDriveBlockEntity drive && (!drive.isFormed() || drive.getTier() == null)) return;
        }
        // Allow section uploads to reach the framebuffer for the visual samples.
        boolean capture = fixture.front() == Direction.NORTH && fixture.repeats() == 3;
        if (capture && ticks < 30) return;
        if (capture) {
            String name = "multiblock-" + System.getProperty("neoecoae.multiblockAlignmentSmoke") + "-"
                + fixture.id() + (fixture.mirrored() ? "-mirrored" : "-normal") + ".png";
            Screenshot.grab(client.gameDirectory, name, client.getMainRenderTarget(),
                message -> LOG.info("MULTIBLOCK_ALIGNMENT screenshot={}", message.getString()));
        }
        LOG.info("MULTIBLOCK_ALIGNMENT fixture={} id={} front={} mirrored={} repeats={} blocks={}",
            specimen, fixture.id(), fixture.front(), fixture.mirrored(), fixture.repeats(), members.size());
        specimen++;
        ticks = 0;
        submitted = false;
        prepared = false;
    }

    private static void prepare(ServerLevel level, Fixture fixture) {
        var context = new MultiBlockPlanContext(fixture.repeats());
        fixture.definition().createLevel(context);
        Map<BlockPos, BlockState> next = new LinkedHashMap<>();
        for (var planned : context.getPlannedBlocks()) {
            BlockPos pos = MultiBlockRotation.localToWorld(planned.relativePos(), CONTROLLER, fixture.front(), fixture.mirrored());
            BlockState state = MultiBlockRotation.rotateState(planned.targetState(), fixture.front(), fixture.mirrored());
            next.put(pos, state);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
        var host = (NEBlockEntity<?, ?>) level.getBlockEntity(CONTROLLER);
        if (specimen == 0) LOG.info("MULTIBLOCK_ALIGNMENT world config crafting={} computation={} storage={} definitionMax={}",
                NEConfig.craftingSystemMaxLength, NEConfig.computationSystemMaxLength, NEConfig.storageSystemMaxLength,
                fixture.definition().getExpandMax());
        int configuredLength = fixture.id().startsWith("storage_") ? NEConfig.storageSystemMaxLength
            : fixture.id().startsWith("computation_") ? NEConfig.computationSystemMaxLength : NEConfig.craftingSystemMaxLength;
        require(fixture.definition().getExpandMax() == configuredLength - 4, "preview retained an obsolete server limit");
        host.rebuildMultiblock();
        require(host.isFormed(), "structure did not form: " + fixture);
        BlockPos casing = next.keySet().stream().filter(pos -> !pos.equals(CONTROLLER)).findFirst().orElseThrow();
        level.setBlock(casing, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        require(!host.isFormed(), "removed member did not disconnect structure: " + fixture);
        level.setBlock(casing, next.get(casing), Block.UPDATE_ALL);
        host.rebuildMultiblock();
        require(host.isFormed(), "repaired structure did not reform: " + fixture);
        int drive = 0;
        for (BlockPos pos : next.keySet()) {
            if (level.getBlockEntity(pos) instanceof ECODriveBlockEntity storage && drive++ % 2 == 0) {
                storage.setCellStack(new ItemStack(NEItems.ECO_ITEM_CELL_16M.get()));
            } else if (level.getBlockEntity(pos) instanceof ECOComputationDriveBlockEntity computation && drive++ % 2 == 0) {
                computation.setCellStack(new ItemStack(NEItems.ECO_COMPUTATION_CELL_L4.get()));
            }
        }
        next.replaceAll((pos, state) -> level.getBlockState(pos));
        members = next;
        Map<BlockPos, ItemStack> cells = new LinkedHashMap<>();
        for (BlockPos pos : next.keySet()) {
            if (level.getBlockEntity(pos) instanceof ICellHost cellHost) {
                cells.put(pos, cellHost.getCellStack() == null ? ItemStack.EMPTY : cellHost.getCellStack().copy());
            }
        }
        installedCells = cells;
        level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
            new net.minecraft.world.phys.AABB(CONTROLLER).inflate(16)).forEach(net.minecraft.world.entity.Entity::discard);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record Fixture(MultiBlockDefinition definition, Direction front, boolean mirrored, int repeats) {
        String id() { return BuiltInRegistries.BLOCK.getKey(definition.getOwner().value()).getPath(); }
    }
}
