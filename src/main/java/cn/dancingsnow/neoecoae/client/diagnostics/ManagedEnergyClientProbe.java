package cn.dancingsnow.neoecoae.client.diagnostics;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity;
import cn.dancingsnow.neoecoae.platform.sync.NEManagedBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in live regression for energy fields, neighboring casings and a full save/reopen cycle. */
final class ManagedEnergyClientProbe {
    private static final Logger LOG = LoggerFactory.getLogger("neoecoae.energySmoke");
    private static final List<BlockPos> positions = new ArrayList<>();
    private static int specimen;
    private static int stage;
    private static int ticks;
    private static volatile boolean serverDone;
    private static volatile Throwable serverFailure;
    private static boolean previousPauseOnLostFocus;
    private static String worldId;

    private ManagedEnergyClientProbe() {}

    static void tick(Minecraft client) {
        if (serverFailure != null) throw new IllegalStateException("Managed energy server regression", serverFailure);
        if (++ticks > 2400) throw new IllegalStateException("Managed energy regression timed out at " + specimen + ":" + stage);
        if (client.screen instanceof net.minecraft.client.gui.screens.PauseScreen) client.setScreen(null);
        List<Block> blocks = List.of(NEBlocks.ENERGY_CELL_L4.get(), NEBlocks.ENERGY_CELL_L6.get(),
            NEBlocks.ENERGY_CELL_L9.get(), NEBlocks.STORAGE_CASING.get());
        switch (stage) {
            case 0 -> {
                if (specimen == 0) {
                    previousPauseOnLostFocus = client.options.pauseOnLostFocus;
                    client.options.pauseOnLostFocus = false;
                    verifyManagedFields();
                }
                var origin = client.player.blockPosition();
                var position = specimen < 3 ? origin.offset(2, 0, specimen) : origin.offset(1, 0, 2);
                positions.add(position);
                var block = blocks.get(specimen);
                var playerId = client.player.getUUID();
                serverDone = false;
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var player = client.getSingleplayerServer().getPlayerList().getPlayer(playerId);
                        var level = player.serverLevel();
                        level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(position.below(), Blocks.BEDROCK.defaultBlockState());
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(block));
                        player.inventoryMenu.broadcastChanges();
                        serverDone = true;
                    } catch (Throwable failure) {
                        serverFailure = failure;
                    }
                });
                advance();
            }
            case 1 -> {
                var block = blocks.get(specimen);
                var position = positions.get(specimen);
                if (!serverDone || ticks < 10 || !client.player.getMainHandItem().is(block.asItem())
                        || !client.level.isEmptyBlock(position)) return;
                var hit = new BlockHitResult(Vec3.atCenterOf(position.below()).add(0, 0.5, 0),
                    Direction.UP, position.below(), false);
                require(client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit).consumesAction(),
                    "client placement rejected for " + block);
                require(client.level.getBlockEntity(position) != null && client.level.getBlockState(position).is(block),
                    "client prediction did not create " + block);
                LOG.info("ENERGY_SMOKE predicted placement: {}", BuiltInRegistries.BLOCK.getKey(block));
                advance();
            }
            case 2 -> {
                if (ticks < 20) return;
                var position = positions.get(specimen);
                int index = specimen;
                serverDone = false;
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var level = client.getSingleplayerServer().overworld();
                        var entity = level.getBlockEntity(position);
                        require(entity != null && entity.getBlockState().is(blocks.get(index)), "server placement missing at " + position);
                        if (entity instanceof ECOEnergyCellBlockEntity energy) verifyEnergy(energy, index);
                        LOG.info("ENERGY_SMOKE server placement PASS: {}", BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()));
                        serverDone = true;
                    } catch (Throwable failure) {
                        serverFailure = failure;
                    }
                });
                advance();
            }
            case 3 -> {
                if (!serverDone || ticks < 20) return;
                specimen++;
                stage = specimen == blocks.size() ? 4 : 0;
                ticks = 0;
            }
            case 4 -> {
                // clearLevel waits for the integrated server to finish saving and release its world lock.
                worldId = client.getSingleplayerServer().getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
                LOG.info("ENERGY_SMOKE saving and reopening world: {}", worldId);
                client.level.disconnect();
                client.clearLevel(new TitleScreen());
                client.setScreen(new TitleScreen());
                advance();
            }
            case 5 -> {
                if (!(client.screen instanceof TitleScreen) || client.getOverlay() != null) return;
                client.createWorldOpenFlows().loadLevel(new TitleScreen(), worldId);
                advance();
            }
            case 6 -> {
                if (client.level == null || client.player == null || ticks < 200) return;
                serverDone = false;
                client.getSingleplayerServer().execute(() -> {
                    try {
                        var level = client.getSingleplayerServer().overworld();
                        for (int index = 0; index < blocks.size(); index++) {
                            var entity = level.getBlockEntity(positions.get(index));
                            require(entity != null && entity.getBlockState().is(blocks.get(index)), "reopened world lost block " + index);
                            if (entity instanceof ECOEnergyCellBlockEntity energy) {
                                require(energy.getAECurrentPower() == expectedEnergy(index), "reopened world lost energy " + index);
                            }
                        }
                        LOG.info("ENERGY_SMOKE reopened world PASS: all 3 energy amounts and neighboring casing retained");
                        serverDone = true;
                    } catch (Throwable failure) {
                        serverFailure = failure;
                    }
                });
                advance();
            }
            case 7 -> {
                if (!serverDone || ticks < 20) return;
                LOG.info("ENERGY_SMOKE PASS: L4/L6/L9 predicted placement, charge/discharge, NBT and world reopen");
                client.options.pauseOnLostFocus = previousPauseOnLostFocus;
                client.stop();
                advance();
            }
            case 8 -> { }
            default -> throw new IllegalStateException("Unknown energy regression stage " + stage);
        }
    }

    private static void verifyManagedFields() {
        int blocks = 0;
        int fields = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            if (!BuiltInRegistries.BLOCK.getKey(block).getNamespace().equals("neoecoae") || !(block instanceof EntityBlock factory)) continue;
            var entity = factory.newBlockEntity(BlockPos.ZERO, block.defaultBlockState());
            if (!(entity instanceof NEManagedBlockEntity managed)) continue;
            managed.getSyncStorage().init();
            blocks++;
            fields += managed.getFieldHolder().getFields().length;
        }
        require(blocks >= 3, "managed block audit did not cover the energy cells");
        LOG.info("ENERGY_SMOKE managed field audit PASS: {} block variants, {} field references initialized with live mixins", blocks, fields);
    }

    private static void verifyEnergy(ECOEnergyCellBlockEntity energy, int index) {
        double expected = expectedEnergy(index);
        double inserted = expected + 34.5;
        require(energy.injectAEPower(inserted, Actionable.SIMULATE) == 0, "simulated charge rejected");
        require(energy.getAECurrentPower() == 0, "simulated charge changed energy");
        require(energy.injectAEPower(inserted, Actionable.MODULATE) == 0, "charge rejected");
        require(energy.extractAEPower(34.5, Actionable.SIMULATE, PowerMultiplier.ONE) == 34.5, "simulated discharge rejected");
        require(energy.getAECurrentPower() == inserted, "simulated discharge changed energy");
        require(energy.extractAEPower(34.5, Actionable.MODULATE, PowerMultiplier.ONE) == 34.5, "discharge rejected");
        require(energy.getAECurrentPower() == expected, "charge/discharge changed the balance");
        var data = energy.saveWithFullMetadata();
        require(data.contains("energyStored", Tag.TAG_DOUBLE) && data.getDouble("energyStored") == expected,
            "stored energy missing from the existing double NBT field");
        var restored = BlockEntity.loadStatic(energy.getBlockPos(), energy.getBlockState(), data);
        require(restored instanceof ECOEnergyCellBlockEntity, "energy entity NBT reload failed");
        var copy = (ECOEnergyCellBlockEntity) restored;
        require(copy.getAECurrentPower() == expected && copy.getAEMaxPower() == energy.getAEMaxPower(),
            "energy or tier capacity changed across NBT reload");
        LOG.info("ENERGY_SMOKE energy round trip PASS: tier={}, amount={}, capacity={}", index, expected, energy.getAEMaxPower());
    }

    private static double expectedEnergy(int index) {
        return 1200.25 + index * 1000.25;
    }

    private static void advance() {
        stage++;
        ticks = 0;
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
