package cn.dancingsnow.neoecoae.integration.xei.multiblock;

import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.network.CraftingCapabilitySnapshot;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationDrive;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationParallelCore;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationThreadingCore;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingParallelCore;
import cn.dancingsnow.neoecoae.blocks.crafting.ECOCraftingWorker;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationOverclock;
import cn.dancingsnow.neoecoae.blocks.storage.ECODriveBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOEnergyCellBlock;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import cn.dancingsnow.neoecoae.crafting.display.format.NEByteFormatter;
import cn.dancingsnow.neoecoae.crafting.execution.worker.ECOCraftingThread;
import cn.dancingsnow.neoecoae.multiblock.placement.RequiredItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Locale;

/** Describes one physical preview. It does not create block entities or inspect a live world. */
final class MultiBlockPreviewInfo {
    static final String PREFIX = "gui.neoecoae.multiblock.preview.info.";

    private MultiBlockPreviewInfo() {}

    record Row(Component label, Component value, Component help) {}

    static List<Row> rows(Block owner, List<RequiredItem> items) {
        long parallel = 0, threads = 0, drives = 0, workers = 0, ft = 0, storage = 0, energyCells = 0;
        IECOTier computationTier = null, craftingTier = null;
        for (var item : items) {
            if (!(item.stack().getItem() instanceof BlockItem blockItem)) continue;
            Block block = blockItem.getBlock();
            long count = item.count();
            if (block instanceof ECOComputationParallelCore core) {
                parallel = NEMath.saturatingAdd(parallel, NEMath.saturatingMultiply(count, core.getTier().getCPUAccelerators()));
                computationTier = core.getTier();
            } else if (block instanceof ECOComputationThreadingCore core) {
                threads = NEMath.saturatingAdd(threads, NEMath.saturatingMultiply(count, core.getTier().getCPUThreads()));
            } else if (block instanceof ECOComputationDrive) {
                drives += count;
            } else if (block instanceof ECOCraftingParallelCore core) {
                craftingTier = core.getTier();
                ft = NEMath.saturatingAdd(ft, NEMath.saturatingMultiply(count,
                    (long) core.getTier().getCrafterParallel() + core.getTier().getOverclockedCrafterParallel()));
            } else if (block instanceof ECOCraftingWorker) {
                workers += count;
            } else if (block instanceof ECODriveBlock) {
                storage += count;
            } else if (block instanceof ECOEnergyCellBlock) {
                energyCells += count;
            }
        }
        if (computationTier != null) {
            return computation(computationTier, parallel, drives, threads);
        }
        if (craftingTier != null) {
            return crafting(craftingTier, workers, ft);
        }
        if (owner instanceof ECOStorageSystemBlock) {
            String id = BuiltInRegistries.BLOCK.getKey(owner).getPath();
            IECOTier tier = id.endsWith("_l9") ? ECOTier.L9 : id.endsWith("_l6") ? ECOTier.L6 : ECOTier.L4;
            long power = NEMath.saturatingMultiply(energyCells, tier.getPowerStorageSize());
            return List.of(row("slots", number(storage), "storage_help"),
                row("energy", compact(power) + " AE", "energy_help"),
                row("capacity", Component.translatable(PREFIX + "by_cells"), "storage_help"));
        }
        // A workstation has no repeating section and no C/F/S component cores.
        if (owner instanceof cn.dancingsnow.neoecoae.blocks.ECOIntegratedWorkingStation
            || owner instanceof cn.dancingsnow.neoecoae.blocks.ECOLargeIntegratedWorkingStation) {
            return List.of(row("parallel", number(LargeWorkstationOverclock.BASE_PARALLELISM), "workstation_help"),
                row("max_parallel", number(LargeWorkstationOverclock.forCoolingTier(9).maxParallelism()), "workstation_help"),
                row("structure", "3×2×2", "scope"));
        }
        long blocks = items.stream().mapToLong(RequiredItem::count).sum();
        return List.of(row("blocks", number(blocks), "scope"), row("materials", number(items.size()), "scope"));
    }

    static List<Row> computation(IECOTier tier, long parallel, long drives, long threads) {
        long bytes = NEMath.saturatingMultiply(drives, tier.getCPUTotalBytes());
        return List.of(row("parallel", number(Math.min(Integer.MAX_VALUE, parallel)), "computation_help"),
            row("memory", NEByteFormatter.format(bytes), "memory_help"),
            row("threads", number(threads), "computation_help"));
    }

    static CraftingCapabilitySnapshot craftingCapacity(IECOTier tier, long workers, long ft) {
        return CraftingCapabilitySnapshot.calculate(new CraftingCapabilitySnapshot.Input(
            (int) Math.min(Integer.MAX_VALUE, workers), 0, 0, 0,
            NEMath.saturatingMultiply(CraftingCapabilitySnapshot.BASE_BATCH_PER_FX, tier.getOverclockedCrafterQueueMultiply()),
            ft, 0, true, true, tier.getOverclockedCrafterPowerMultiply(), false,
            new CraftingCapabilitySnapshot.CoolantState(true, 0, 0, CraftingCapabilitySnapshot.MAX_OVERCLOCK)));
    }

    static long craftingTicks(CraftingCapabilitySnapshot capacity) {
        int progress = ECOCraftingThread.calculateProgressPerTick(capacity.effectiveOverclock());
        return (ECOCraftingThread.MAX_PROGRESS + progress - 1L) / progress;
    }

    static List<Row> crafting(IECOTier tier, long workers, long ft) {
        var capacity = craftingCapacity(tier, workers, ft);
        long total = capacity.totalBatchCapacity().finiteValue();
        String speed = compact(total / (double) craftingTicks(capacity)) + "/t";
        return List.of(row("speed", speed, "speed_help"),
            new Row(Component.translatable(PREFIX + "batch"), Component.literal(number(total)),
                Component.translatable(PREFIX + "batch_help", number(capacity.batchPerFx().finiteValue()), number(workers))),
            row("workers", number(workers), "speed_help"));
    }

    private static Row row(String label, String value, String help) {
        return row(label, Component.literal(value), help);
    }

    private static Row row(String label, Component value, String help) {
        return new Row(Component.translatable(PREFIX + label), value, Component.translatable(PREFIX + help));
    }

    private static String number(long value) { return String.format(Locale.ROOT, "%,d", value); }

    private static String compact(double value) {
        String[] units = {"", "k", "M", "G", "T", "P", "E"};
        int unit = 0;
        while (value >= 1000 && unit < units.length - 1) { value /= 1000; unit++; }
        return String.format(Locale.ROOT, value == Math.floor(value) ? "%.0f%s" : "%.1f%s", value, units[unit]);
    }
}
