package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.impl.storage.StorageByteAccounting;
import cn.dancingsnow.neoecoae.integration.biggerae2.BiggerAE2SingularityPowerService;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.cells.CellState;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Collects and caches the storage host's energy, cell and infinite-domain UI statistics. */
final class ECOStorageHostStatistics {
    private transient StorageUiSnapshot storageUiSnapshot = StorageUiSnapshot.EMPTY;
    private transient long storageUiSnapshotGameTime = Long.MIN_VALUE;
    private long storageUiRevision = Long.MIN_VALUE;
    private final Map<ECODriveBlockEntity, DriveUiSnapshot> driveUiSnapshots = new HashMap<>();
    private record DriveUiSnapshot(IECOStorageCell inventory, long revision, long tick, int type, int tier,
        List<AEKeyType> keyTypes, boolean member, long usedTypes, long totalTypes, long usedBytes, long totalBytes,
        boolean infiniteResource) {}

    private final ECOStorageSystemBlockEntity host;
    private final Registry<ECOCellType> cellTypes;

    ECOStorageHostStatistics(ECOStorageSystemBlockEntity host) {
        this(host, NERegistries.CELL_TYPE);
    }

    ECOStorageHostStatistics(ECOStorageSystemBlockEntity host, Registry<ECOCellType> cellTypes) {
        this.host = host;
        this.cellTypes = cellTypes;
    }

    void invalidate() {
        storageUiSnapshotGameTime = Long.MIN_VALUE;
    }

    List<StorageHostUI.CellEntry> getCellEntries() {
        return getStorageUiSnapshot().cellEntries();
    }

    List<StorageHostUI.StorageTypeLine> createStorageTypeLines() {
        List<StorageHostUI.StorageTypeLine> lines = cellTypes.stream()
            .map(cellType -> {
                int id = cellTypes.getId(cellType);
                return new StorageHostUI.StorageTypeLine(
                    cellType,
                    id,
                    cellType::desc,
                    () -> !host.isFormedInfiniteMode() && cellType.visible() && normalUiTypeId(id) == id,
                    () -> getStorageValue(id, StorageValue.USED_TYPES),
                    () -> getStorageValue(id, StorageValue.TOTAL_TYPES),
                    () -> getStorageValue(id, StorageValue.USED_BYTES),
                    () -> getStorageValue(id, StorageValue.TOTAL_BYTES),
                    () -> getStorageUiSnapshot().storageTypeTotals(id).infiniteBytesText(),
                    cellTypes.getKey(cellType),
                    () -> getStorageUiSnapshot().storageTypeTotals(id).displayUsedBytes()
                );
            })
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            int id = infiniteUiTypeId(keyType);
            if (id != Integer.MIN_VALUE) {
                lines.add(infiniteStorageTypeLine(keyType, id));
            }
        }
        return lines;
    }

    private StorageHostUI.StorageTypeLine infiniteStorageTypeLine(AEKeyType keyType, int id) {
        java.util.function.Supplier<net.minecraft.network.chat.Component> name = keyType::getDescription;
        return new StorageHostUI.StorageTypeLine(
            new ECOCellType(name.get(), 1, true), id,
            name, host::isFormedInfiniteMode,
            () -> getStorageValue(id, StorageValue.USED_TYPES),
            () -> getStorageValue(id, StorageValue.TOTAL_TYPES),
            () -> getStorageValue(id, StorageValue.USED_BYTES),
            () -> getStorageValue(id, StorageValue.TOTAL_BYTES),
            () -> getStorageUiSnapshot().storageTypeTotals(id).infiniteBytesText(), keyType.getId(),
            () -> getStorageUiSnapshot().storageTypeTotals(id).displayUsedBytes());
    }

    String getTotalUsedBytesText() {
        StorageUiSnapshot snapshot = getStorageUiSnapshot();
        BigInteger used = BigInteger.ZERO;
        if (host.isFormedInfiniteMode()) {
            for (StorageTypeTotals totals : snapshot.storageTypes().values()) {
                used = used.add(totals.displayUsedBytes());
            }
        } else {
            for (StorageHostUI.CellEntry entry : snapshot.cellEntries()) {
                used = used.add(BigInteger.valueOf(Math.max(0L, entry.usedBytes())));
            }
        }
        return HostText.ae2Amount(used);
    }

    @SuppressWarnings("UnstableApiUsage")
    long getStoredEnergy() {
        return getStorageUiSnapshot().storedEnergy();
    }

    @SuppressWarnings("UnstableApiUsage")
    long getMaxEnergy() {
        return getStorageUiSnapshot().maxEnergy();
    }

    long getEnergyConsumePerTick() {
        return getStorageUiSnapshot().energyConsumePerTick();
    }

    private long getStorageValue(int cellTypeId, StorageValue value) {
        StorageTypeTotals totals = getStorageUiSnapshot().storageTypeTotals(cellTypeId);
        return switch (value) {
            case USED_TYPES -> totals.usedTypes();
            case TOTAL_TYPES -> totals.totalTypes();
            case USED_BYTES -> totals.usedBytes();
            case TOTAL_BYTES -> totals.totalBytes();
        };
    }

    private StorageUiSnapshot getStorageUiSnapshot() {
        long gameTime = host.getLevel() == null ? Long.MIN_VALUE : host.getLevel().getGameTime();
        ECOInfiniteStorageEngine engine = host.getInfiniteEngine();
        long revision = engine == null ? 0L : engine.revision();
        if (storageUiSnapshotGameTime == Long.MIN_VALUE || gameTime - storageUiSnapshotGameTime >= 20L
            || (revision != storageUiRevision && gameTime - storageUiSnapshotGameTime >= 5L)) {
            storageUiSnapshotGameTime = gameTime;
            try {
                storageUiSnapshot = collectStorageUiSnapshot();
                storageUiRevision = revision;
                host.storageFaults().recovered("statistics");
            } catch (RuntimeException e) {
                host.storageFaults().report("statistics", e.toString(), gameTime, e);
            }
        }
        return storageUiSnapshot;
    }

    @SuppressWarnings("UnstableApiUsage")
    private StorageUiSnapshot collectStorageUiSnapshot() {
        if (host.getCluster() == null) {
            driveUiSnapshots.clear();
            return StorageUiSnapshot.EMPTY;
        }

        long storedEnergy = 0L;
        long maxEnergy = 0L;
        for (ECOEnergyCellBlockEntity energyCell : host.getCluster().getEnergyCells()) {
            storedEnergy = NEMath.saturatingAdd(storedEnergy, (long) energyCell.getAECurrentPower());
            maxEnergy = NEMath.saturatingAdd(maxEnergy, (long) energyCell.getAEMaxPower());
        }

        long energyConsumePerTick = 256L + (1L << (1 + 4 * host.getTier().getTier()));
        Map<Integer, StorageTypeTotals> storageTypes = new HashMap<>();
        List<StorageHostUI.CellEntry> cellEntries = new ArrayList<>();
        boolean hasSingularity = false;
        driveUiSnapshots.keySet().retainAll(host.getCluster().getDrives());
        for (ECODriveBlockEntity drive : host.getStorageUiDrives()) {
            if (drive == null) {
                cellEntries.add(StorageHostUI.CellEntry.EMPTY);
                continue;
            }
            DriveUiSnapshot view = driveUiSnapshot(drive);
            var stack = drive.getCellStack();
            if (view == null) {
                cellEntries.add(StorageHostUI.CellEntry.EMPTY.atDrive(drive.getBlockPos(),
                    stack == null ? ItemStack.EMPTY : stack, drive.getCellExtractionBlockReasonText(), drive.getCellInteractionId()));
                continue;
            }
            int cellTypeId = view.type();
            boolean supported = view.inventory() != null && host.getTier().compareTo(view.inventory().getTier()) >= 0;
            if (supported) {
                hasSingularity |= BiggerAE2SingularityPowerService.isSingularity(stack);
                energyConsumePerTick = NEMath.saturatingAdd(
                    energyConsumePerTick,
                    Math.max(0L, Math.round(view.inventory().getIdleDrain()))
                );
            }
            // Keep every physical drive position, including empty and unsupported cells.
            cellEntries.add(new StorageHostUI.CellEntry(
                    cellTypeId,
                    view.tier(),
                    legacyCellKind(view.keyTypes()),
                    !supported || view.member() ? 0L : view.usedTypes(),
                    !supported ? 0L : view.member() ? -1L : view.totalTypes(),
                    !supported || view.member() ? 0L : view.usedBytes(),
                    !supported ? 0L : view.member() ? -1L : view.totalBytes(),
                    supported && view.member(),
                    supported ? view.inventory().getStatus() : CellState.ABSENT,
                    supported && drive.isOnline()
            ).withPanelTier(view.inventory().getTier())
                .atDrive(drive.getBlockPos(), stack, drive.getCellExtractionBlockReasonText(), drive.getCellInteractionId()));
            if (view.infiniteResource()) continue;
            if (view.member()) {
                continue;
            }

            long usedTypes = view.usedTypes();
            long totalTypes = view.totalTypes();
            long usedBytes = view.usedBytes();
            long totalBytes = view.totalBytes();
            if (cellTypeId >= 0) {
                storageTypes.merge(
                    normalUiTypeId(cellTypeId),
                    new StorageTypeTotals(usedTypes, totalTypes, usedBytes, totalBytes),
                    StorageTypeTotals::add
                );
            }
        }
        if (host.isFormedInfiniteMode()) {
            addInfiniteStorageTypes(storageTypes);
        }

        if (hasSingularity) {
            var grid = host.getMainNode().getGrid();
            if (grid != null) {
                double surcharge = grid.getService(BiggerAE2SingularityPowerService.class).getAdditionalIdleDrain(host);
                energyConsumePerTick = NEMath.saturatingAdd(energyConsumePerTick, Math.round(surcharge));
            }
        }

        return new StorageUiSnapshot(
            storedEnergy,
            maxEnergy,
            energyConsumePerTick,
            storageTypes,
            cellEntries
        );
    }

    private static int legacyCellKind(List<AEKeyType> keyTypes) {
        if (keyTypes.contains(AEKeyType.items())) {
            return StorageHostUI.CellEntry.KIND_ITEM;
        }
        if (keyTypes.contains(AEKeyType.fluids())) {
            return StorageHostUI.CellEntry.KIND_FLUID;
        }
        return keyTypes.isEmpty() ? StorageHostUI.CellEntry.KIND_EMPTY : StorageHostUI.CellEntry.KIND_OTHER;
    }

    private DriveUiSnapshot driveUiSnapshot(ECODriveBlockEntity drive) {
        DriveUiSnapshot previous = driveUiSnapshots.get(drive);
        long tick = host.getLevel().getGameTime();
        String component = "drive statistics " + drive.getBlockPos();
        try {
            IECOStorageCell inventory = drive.getCellInventory();
            if (inventory == null) { driveUiSnapshots.remove(drive); return null; }
            long revision = inventory instanceof ECOStorageCell cell ? cell.contentRevision() : -1L;
            boolean member = host.isInfiniteMemberCell(drive.getCellStack());
            boolean infiniteResource = inventory instanceof ECOInfiniteResourceCell;
            if (previous != null && previous.inventory() == inventory && previous.revision() == revision
                && previous.member() == member && previous.infiniteResource() == infiniteResource
                && tick - previous.tick() < 20L) return previous;
            int type = cellTypes.getId(inventory.getCellType());
            List<AEKeyType> keyTypes = new ArrayList<>();
            if (type >= 0 && drive.getCellStack().getItem() instanceof IECOStorageCellItem item) {
                for (AEKeyType keyType : item.getKeyTypes()) keyTypes.add(keyType);
            }
            DriveUiSnapshot next = new DriveUiSnapshot(inventory, revision, tick, type, inventory.getTier().getTier(),
                keyTypes, member,
                member ? 0L : inventory.getStoredItemTypes(), member ? 0L : inventory.hasInfiniteTypeCapacity() ? -1L : inventory.getTotalItemTypes(),
                member || infiniteResource ? 0L : inventory.getUsedBytes(),
                member || infiniteResource ? 0L : inventory.getTotalBytes(), infiniteResource);
            driveUiSnapshots.put(drive, next);
            host.storageFaults().recovered(component);
            return next;
        } catch (RuntimeException e) {
            host.storageFaults().report(component, e.toString(), tick, e);
            return previous;
        }
    }

    private int normalUiTypeId(int cellTypeId) {
        ResourceLocation key = cellTypes.getKey(cellTypes.byId(cellTypeId));
        if (key == null || !key.getNamespace().equals("neoecoae")) return cellTypeId;
        String baseType = switch (key.getPath()) {
            case "mega_item", "bigger_ae2_item" -> "items";
            case "mega_fluid", "bigger_ae2_fluid" -> "fluids";
            case "mega_energy", "bigger_ae2_fe" -> "flux";
            case "mega_chemical", "bigger_ae2_chemical" -> "mekanism";
            case "mega_air" -> "air";
            case "mega_experience", "bigger_ae2_experience" -> "experience";
            case "mega_soul" -> "soul";
            case "mega_mana" -> "mana";
            case "mega_source", "bigger_ae2_source" -> "source";
            case "mega_matter" -> "matter";
            case "mega_stress" -> "stress";
            case "mega_data" -> "data";
            default -> null;
        };
        if (baseType == null) return cellTypeId;
        ECOCellType base = cellTypes.get(ResourceLocation.fromNamespaceAndPath("neoecoae", baseType));
        // Keep the original group if an optional channel's ordinary cell type is unavailable.
        return base == null ? cellTypeId : cellTypes.getId(base);
    }

    private void addInfiniteStorageTypes(Map<Integer, StorageTypeTotals> storageTypes) {
        ECOInfiniteStorageEngine engine = host.getInfiniteEngine();
        if (engine == null) {
            return;
        }
        for (ECOInfiniteStorageEngine.TypeStats stats : engine.getTypeStats()) {
            int cellTypeId = infiniteUiTypeId(stats.keyType());
            if (cellTypeId == Integer.MIN_VALUE) {
                continue;
            }
            BigInteger usedBytes = infiniteUsedBytes(stats);
            storageTypes.merge(
                cellTypeId,
                new StorageTypeTotals(
                    stats.storedTypes(),
                    0L,
                    usedBytes.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(),
                    0L,
                    usedBytes
                ),
                StorageTypeTotals::add
            );
        }
    }

    private static BigInteger infiniteUsedBytes(ECOInfiniteStorageEngine.TypeStats stats) {
        long bytesPerType = 1L << (12 + ECOTier.L9.getTier());
        return StorageByteAccounting.usedBytes(
            stats.storedTypes(), stats.storedAmount().toBigInteger(), stats.keyType().getAmountPerByte(), bytesPerType);
    }

    private record StorageUiSnapshot(
        long storedEnergy,
        long maxEnergy,
        long energyConsumePerTick,
        Map<Integer, StorageTypeTotals> storageTypes,
        List<StorageHostUI.CellEntry> cellEntries
    ) {
        private static final StorageUiSnapshot EMPTY =
            new StorageUiSnapshot(0L, 0L, 0L, Map.of(), List.of());

        private StorageTypeTotals storageTypeTotals(int cellTypeId) {
            return storageTypes.getOrDefault(cellTypeId, StorageTypeTotals.EMPTY);
        }
    }

    private record StorageTypeTotals(
        long usedTypes,
        long totalTypes,
        long usedBytes,
        long totalBytes,
        BigInteger displayUsedBytes
    ) {
        private static final StorageTypeTotals EMPTY = new StorageTypeTotals(0L, 0L, 0L, 0L, BigInteger.ZERO);

        private StorageTypeTotals(long usedTypes, long totalTypes, long usedBytes, long totalBytes) {
            this(usedTypes, totalTypes, usedBytes, totalBytes, BigInteger.valueOf(Math.max(0L, usedBytes)));
        }

        private String infiniteBytesText() {
            return HostText.ae2Amount(displayUsedBytes);
        }

        private StorageTypeTotals add(StorageTypeTotals other) {
            return new StorageTypeTotals(
                NEMath.saturatingAdd(usedTypes, other.usedTypes),
                addCapacity(totalTypes, other.totalTypes),
                NEMath.saturatingAdd(usedBytes, other.usedBytes),
                addCapacity(totalBytes, other.totalBytes),
                displayUsedBytes.add(other.displayUsedBytes)
            );
        }

        private static long addCapacity(long left, long right) {
            return left < 0L || right < 0L ? -1L : NEMath.saturatingAdd(left, right);
        }
    }

    private enum StorageValue {
        USED_TYPES,
        TOTAL_TYPES,
        USED_BYTES,
        TOTAL_BYTES
    }

    private static int infiniteUiTypeId(AEKeyType keyType) {
        int index = 0;
        for (AEKeyType registered : AEKeyTypes.getAll()) {
            if (registered == keyType) {
                return -1 - index;
            }
            index++;
        }
        return Integer.MIN_VALUE;
    }
}
