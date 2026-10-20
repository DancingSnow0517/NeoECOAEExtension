package cn.dancingsnow.neoecoae.dev;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationDriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOEnergyCellBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockContext;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Builds real, powered machines in an isolated copied world for English marketing captures. */
@EventBusSubscriber(modid = "neoecoae")
public final class MachinePromoScene {
    public static final int REPEATS = 4;
    public static final String[] FAMILIES = {"crafting", "computation", "storage"};
    public static final ECOTier[] TIERS = {ECOTier.L4, ECOTier.L6, ECOTier.L9};
    public static final BlockPos[] ORIGINS = new BlockPos[9];
    public static final List<List<BlockPos>> BLOCKS = new ArrayList<>();
    public static volatile boolean ready;
    public static volatile int requestedUi = -1;
    public static volatile int openedUi = -1;
    public static volatile boolean closeUi;
    private static int ticks;
    private static int switching = -1;
    private static int openAt;

    public static String key(int index) {
        return FAMILIES[index / 3] + "-" + TIERS[index % 3].name().toLowerCase(java.util.Locale.ROOT);
    }

    public static MultiBlockDefinition definition(int index) {
        ECOTier tier = TIERS[index % 3];
        return switch (index / 3) {
            case 0 -> NEMultiBlocks.getCraftingSystemDefinition(tier);
            case 1 -> NEMultiBlocks.getComputationSystemDefinition(tier);
            default -> NEMultiBlocks.getStorageSystemDefinition(tier);
        };
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        var player = server.getPlayerList().getPlayers().getFirst();
        ServerLevel level = server.overworld();
        ticks++;
        if (ticks == 1) {
            level.setDayTime(6000);
            level.setWeatherParameters(100000, 0, false, false);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            player.setGameMode(GameType.CREATIVE);
            player.getInventory().clearContent();
            for (int i = 0; i < 9; i++) {
                BlockPos origin = new BlockPos(1600 + (i % 3) * 32, 100, 1600 + (i / 3) * 32);
                ORIGINS[i] = origin;
                for (int cx = (origin.getX() - 10) >> 4; cx <= (origin.getX() + 10) >> 4; cx++) {
                    for (int cz = (origin.getZ() - 10) >> 4; cz <= (origin.getZ() + 10) >> 4; cz++) {
                        level.setChunkForced(cx, cz, true);
                        level.getChunk(cx, cz);
                    }
                }
                for (BlockPos p : BlockPos.betweenClosed(origin.offset(-8, -1, -3), origin.offset(7, 5, 7))) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
                PlacedContext context = new PlacedContext(level, origin);
                definition(i).createLevel(context);
                BLOCKS.add(List.copyOf(context.positions));
                level.setBlockAndUpdate(origin.offset(5, 0, 5), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            }
            player.teleportTo(level, 1601.5, 101, 1597, 0, 0);
            System.out.println("MACHINE_PROMO_BUILT nine machines; sections=" + REPEATS);
        }
        if (ticks == 40) {
            for (int i = 0; i < 9; i++) host(level, i).rebuildMultiblock();
        }
        if (ticks == 65) {
            for (int i = 0; i < 9; i++) {
                var host = host(level, i);
                if (!host.isFormed()) throw new IllegalStateException("Promo machine failed to form: " + key(i));
                ECOTier tier = TIERS[i % 3];
                int drive = 0;
                for (BlockPos pos : BLOCKS.get(i)) {
                    var be = level.getBlockEntity(pos);
                    if (be instanceof ECOComputationDriveBlockEntity computation) {
                        computation.setCellStack(computationCell(tier));
                        computation.markForUpdate();
                    } else if (be instanceof ECODriveBlockEntity storage) {
                        boolean fluid = drive++ % 2 == 1;
                        ItemStack cell = storageCell(tier, fluid);
                        var inventory = ECOStorageCells.getCellInventory(cell, null);
                        AEKey resource = fluid ? AEFluidKey.of(Fluids.WATER) : AEItemKey.of(Items.IRON_INGOT);
                        long amount = inventory.getTotalBytes() * resource.getType().getAmountPerByte() / 4;
                        inventory.insert(resource, amount, Actionable.MODULATE, IActionSource.empty());
                        storage.setCellStack(cell);
                        storage.markForUpdate();
                    } else if (be instanceof ECOEnergyCellBlockEntity energy) {
                        energy.injectAEPower(energy.getTier().getPowerStorageSize() * 0.75, Actionable.MODULATE);
                    }
                }
                var supply = (AENetworkedBlockEntity) level.getBlockEntity(ORIGINS[i].offset(5, 0, 5));
                GridHelper.createConnection(host.getMainNode().getNode(), supply.getMainNode().getNode());
                System.out.println("MACHINE_PROMO_FORMED " + key(i));
            }
        }
        if (ticks == 150) {
            for (int i = 0; i < 9; i++) {
                var host = host(level, i);
                if (!host.isFormed() || !host.getMainNode().isOnline()) {
                    throw new IllegalStateException("Promo machine not online: " + key(i));
                }
                if (host instanceof ECOComputationSystemBlockEntity computation) {
                    System.out.println("MACHINE_PROMO_CAPACITY " + key(i) + " bytes=" + computation.getCluster().getTotalStorage()
                        + " threads=" + computation.getCluster().getMaxThreads());
                } else if (host instanceof ECOStorageSystemBlockEntity storage) {
                    System.out.println("MACHINE_PROMO_STORAGE " + key(i) + " cells=" + storage.getStorageUiDrives().size());
                }
            }
            ready = true;
            System.out.println("MACHINE_PROMO_READY");
        }
        if (!ready) return;
        if (closeUi) {
            closeUi = false;
            player.closeContainer();
            openedUi = -1;
        }
        if (requestedUi >= 0 && requestedUi != switching) {
            switching = requestedUi;
            player.closeContainer();
            player.getInventory().clearContent();
            BlockPos controller = ORIGINS[switching].offset(1, 1, 0);
            player.teleportTo(level, controller.getX() + 0.5, controller.getY(), controller.getZ() - 2.5, 0, 0);
            openAt = ticks + 20;
        }
        if (switching >= 0 && ticks == openAt) {
            BlockUIMenuType.openUI(player, ORIGINS[switching].offset(1, 1, 0));
            openedUi = switching;
            System.out.println("MACHINE_PROMO_UI " + key(switching));
        }
    }

    private static NEBlockEntity<?, ?> host(ServerLevel level, int index) {
        return (NEBlockEntity<?, ?>) level.getBlockEntity(ORIGINS[index].offset(1, 1, 0));
    }

    private static ItemStack computationCell(ECOTier tier) {
        return switch (tier) {
            case L4 -> NEItems.ECO_COMPUTATION_CELL_L4.asStack();
            case L6 -> NEItems.ECO_COMPUTATION_CELL_L6.asStack();
            case L9 -> NEItems.ECO_COMPUTATION_CELL_L9.asStack();
        };
    }

    private static ItemStack storageCell(ECOTier tier, boolean fluid) {
        return switch (tier) {
            case L4 -> fluid ? NEItems.ECO_FLUID_CELL_16M.asStack() : NEItems.ECO_ITEM_CELL_16M.asStack();
            case L6 -> fluid ? NEItems.ECO_FLUID_CELL_64M.asStack() : NEItems.ECO_ITEM_CELL_64M.asStack();
            case L9 -> fluid ? NEItems.ECO_FLUID_CELL_256M.asStack() : NEItems.ECO_ITEM_CELL_256M.asStack();
        };
    }

    private static final class PlacedContext extends MultiBlockContext {
        private final ServerLevel level;
        private final BlockPos origin;
        private final List<BlockPos> positions = new ArrayList<>();

        private PlacedContext(ServerLevel level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
            this.repeats = REPEATS;
        }

        @Override public void setBlock(BlockPos pos, BlockState state) {
            BlockPos world = origin.offset(pos);
            level.setBlockAndUpdate(world, state);
            positions.add(world);
        }
        @Override public Level getLevel() { return level; }
        @Override public List<BlockPos> allBlocks() { return positions; }
        @Override public boolean isFormed() { return false; }
    }
}
