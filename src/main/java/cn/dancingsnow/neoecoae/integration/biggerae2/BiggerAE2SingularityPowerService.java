package cn.dancingsnow.neoecoae.integration.biggerae2;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECODriveBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageSystemBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import java.util.IdentityHashMap;
import java.util.Map;

/** Counts usable installed matrices independently of power, so an overloaded grid stays offline. */
public final class BiggerAE2SingularityPowerService implements IGridService, IGridServiceProvider {
    public static final double BASE_IDLE_DRAIN = 1_024;
    private static final int[] IDLE_DRAIN_PER_CELL = {0, 1_024, 4_096, 16_384, 65_535, 262_144, 1_048_576};
    private final Map<IGridNode, ECODriveBlockEntity> nodes = new IdentityHashMap<>();
    private final Map<ECOStorageSystemBlockEntity, Integer> hostCounts = new IdentityHashMap<>();
    private int cellCount;
    private double additionalIdleDrain;

    public BiggerAE2SingularityPowerService() {}

    public static boolean isSingularity(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty()
            && (stack.getItem() instanceof BiggerAE2BulkStorageCellItem
                || stack.getItem() instanceof BiggerAE2StorageCellItem item && item.isSingularity());
    }

    /** Every installed matrix uses the same tier; six or more matrices keep the final per-cell rate. */
    public static double idleDrainPerCell(int count) {
        if (count <= 0) return 0;
        return IDLE_DRAIN_PER_CELL[Math.min(count, IDLE_DRAIN_PER_CELL.length - 1)];
    }

    /** Total matrix drain, including the base drain already charged by each physical drive. */
    public static double totalIdleDrain(int count) {
        return count <= 0 ? 0 : count * idleDrainPerCell(count);
    }

    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        if (node.getOwner() instanceof ECODriveBlockEntity drive) nodes.put(node, drive);
    }

    @Override
    public void removeNode(IGridNode node) {
        nodes.remove(node);
    }

    @Override
    public void onServerStartTick() {
        refresh();
    }

    /** Also called immediately before AE2 pays idle power, to include changes during the tick. */
    public void refresh() {
        cellCount = 0;
        hostCounts.clear();
        if (nodes.isEmpty()) {
            additionalIdleDrain = 0;
            return;
        }
        ReferenceSet<ECODriveBlockEntity> seen = new ReferenceOpenHashSet<>();
        for (ECODriveBlockEntity drive : nodes.values()) {
            if (!seen.add(drive)) continue;
            var stack = drive.getCellStack();
            if (!isSingularity(stack)) continue;
            var host = drive.getStorageController();
            var item = (ECOStorageCellItem) stack.getItem();
            if (host == null || host.isStorageInterfaceTransferMode()
                || host.getTier().compareTo(item.getTier()) < 0) continue;
            cellCount++;
            hostCounts.merge(host, 1, Integer::sum);
        }
        additionalIdleDrain = totalIdleDrain(cellCount) - cellCount * BASE_IDLE_DRAIN;
    }

    public int getCellCount() {
        return cellCount;
    }

    public double getAdditionalIdleDrain() {
        return additionalIdleDrain;
    }

    /** Every controller pays the shared per-cell surcharge for its own installed matrices. */
    public double getAdditionalIdleDrain(ECOStorageSystemBlockEntity host) {
        return cellCount == 0 ? 0
            : hostCounts.getOrDefault(host, 0) * (idleDrainPerCell(cellCount) - BASE_IDLE_DRAIN);
    }
}
