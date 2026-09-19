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
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.crafting.amount.NEMath;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;

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

    ECOStorageHostStatistics(ECOStorageSystemBlockEntity host) {
        this.host = host;
    }

    void invalidate() {
        storageUiSnapshotGameTime = Long.MIN_VALUE;
    }

    List<StorageHostUI.CellEntry> getCellEntries() {
        return getStorageUiSnapshot().cellEntries();
    }

    List<StorageHostUI.StorageTypeLine> createStorageTypeLines() {
        List<StorageHostUI.StorageTypeLine> lines = NERegistries.CELL_TYPE.stream()
            .map(cellType -> {
                int id = NERegistries.CELL_TYPE.getId(cellType);
                return new StorageHostUI.StorageTypeLine(
                    cellType,
                    id,
                    cellType::desc,
                    () -> !host.isFormedInfiniteMode() && cellType.visible(),
                    () -> getStorageValue(id, StorageValue.USED_TYPES),
                    () -> getStorageValue(id, StorageValue.TOTAL_TYPES),
                    () -> getStorageValue(id, StorageValue.USED_BYTES),
                    () -> getStorageValue(id, StorageValue.TOTAL_BYTES),
                    () -> getStorageUiSnapshot().storageTypeTotals(id).infiniteBytesText()
                );
            })
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (AEKeyType keyType : AEKeyTypes.getAll()) {
            int id = infiniteUiTypeId(keyType);
            if (id != Integer.MIN_VALUE) {
                lines.add(infiniteStorageTypeLine(keyType, id));
            }
        }
        return List.copyOf(lines);
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
            () -> getStorageUiSnapshot().storageTypeTotals(id).infiniteBytesText());
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
        driveUiSnapshots.keySet().retainAll(host.getCluster().getDrives());
        for (ECODriveBlockEntity drive : host.getCluster().getDrives()) {
            DriveUiSnapshot view = driveUiSnapshot(drive);
            if (view == null) continue;
            if (view.infiniteResource()) continue;
            int cellTypeId = view.type();
            boolean supported = view.inventory() != null && host.getTier().compareTo(view.inventory().getTier()) >= 0;
            if (supported) {
                energyConsumePerTick = NEMath.saturatingAdd(
                    energyConsumePerTick,
                    Math.max(0L, Math.round(view.inventory().getIdleDrain()))
                );
                cellEntries.add(new StorageHostUI.CellEntry(
                    cellTypeId,
                    view.tier(),
                    legacyCellKind(view.keyTypes()),
                    view.member() ? 0L : view.usedTypes(),
                    view.member() ? -1L : view.totalTypes(),
                    view.member() ? 0L : view.usedBytes(),
                    view.member() ? -1L : view.totalBytes(),
                    view.member()
                ));
            }
            if (view.member()) {
                continue;
            }

            long usedTypes = view.usedTypes();
            long totalTypes = view.totalTypes();
            long usedBytes = view.usedBytes();
            long totalBytes = view.totalBytes();
            if (cellTypeId >= 0) {
                storageTypes.merge(
                    cellTypeId,
                    new StorageTypeTotals(usedTypes, totalTypes, usedBytes, totalBytes),
                    StorageTypeTotals::add
                );
            }
        }
        if (host.isFormedInfiniteMode()) {
            addInfiniteStorageTypes(storageTypes);
        }

        cellEntries.sort((left, right) -> {
            int bytes = Long.compare(right.usedBytes(), left.usedBytes());
            if (bytes != 0) return bytes;
            int types = Long.compare(right.usedTypes(), left.usedTypes());
            if (types != 0) return types;
            int tiers = Integer.compare(right.tier(), left.tier());
            if (tiers != 0) return tiers;
            return Integer.compare(left.typeId(), right.typeId());
        });

        return new StorageUiSnapshot(
            storedEnergy,
            maxEnergy,
            energyConsumePerTick,
            Map.copyOf(storageTypes),
            List.copyOf(cellEntries)
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
            int type = NERegistries.CELL_TYPE.getId(inventory.getCellType());
            List<AEKeyType> keyTypes = new ArrayList<>();
            if (type >= 0 && drive.getCellStack().getItem() instanceof IECOStorageCellItem item) {
                for (AEKeyType keyType : item.getKeyTypes()) keyTypes.add(keyType);
            }
            DriveUiSnapshot next = new DriveUiSnapshot(inventory, revision, tick, type, inventory.getTier().getTier(),
                List.copyOf(keyTypes), member,
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
                NEMath.saturatingAdd(totalTypes, other.totalTypes),
                NEMath.saturatingAdd(usedBytes, other.usedBytes),
                NEMath.saturatingAdd(totalBytes, other.totalBytes),
                displayUsedBytes.add(other.displayUsedBytes)
            );
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
