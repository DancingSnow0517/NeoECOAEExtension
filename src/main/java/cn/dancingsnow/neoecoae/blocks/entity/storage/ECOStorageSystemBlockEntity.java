package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostActionUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostHugeStackList;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostPanelUI;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOFiniteStorageDomain;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageSourceSafety;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageSourceAdapterRegistry;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferScheduler;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.util.NEMath;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.hooks.ticking.TickHandler;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ECOStorageSystemBlockEntity extends NEBlockEntity<NEStorageCluster, ECOStorageSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost, IStorageProvider, MultiBlockBuildController.Host {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOStorageSystemBlockEntity.class);
    private static final int INFINITE_COMPONENT_REQUIRED = 64;
    private static final int INFINITE_MEMBER_REQUIRED = 12;
    private static final int STORAGE_INTERFACE_TRANSFER_KEYS_PER_TICK = 64;
    private static final long STORAGE_INTERFACE_TRANSFER_NANOS_PER_TICK = 2_000_000L;
    private static final String FINITE_TRANSFER_DOMAIN_TAG = "finiteTransferDomain";
    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;
    private static final long INFINITE_RESTORE_MARGIN_NUMERATOR = 95L;
    private static final long INFINITE_RESTORE_MARGIN_DENOMINATOR = 100L;
    private static final String INFINITE_COMPONENT_INVENTORY_PERSIST_KEY = "infiniteComponentInventory";
    private static final String LEGACY_COMPONENT_INVENTORY_PERSIST_KEY = "componentInventory";
    private static final String CONTROLLER_DOMAIN_TAG = "neoecoae_infinite_controller_domain";
    private static final String CONTROLLER_MODE_TAG = "neoecoae_infinite_controller_mode";

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Persisted
    @DescSynced
    private int selectedBuildLength = NEConfig.storageSystemMaxLength - 4;
    @Persisted
    @DescSynced
    private boolean mirrorBuild;
    @Getter
    @Persisted
    @DescSynced
    private int storagePriority;
    @Persisted
    @DescSynced
    private ECOStorageHostMode hostMode = ECOStorageHostMode.UNFORMED;
    @Persisted
    private boolean infiniteExitRequested;
    @Persisted
    @DescSynced
    @Nullable
    private UUID infiniteDomainId;
    @Persisted(key = INFINITE_COMPONENT_INVENTORY_PERSIST_KEY)
    @DescSynced
    private final AppEngInternalInventory infiniteComponentInventory = new AppEngInternalInventory(this, 1, INFINITE_COMPONENT_REQUIRED);
    private final IItemHandlerModifiable infiniteComponentItemHandler =
        new InfiniteComponentItemHandler((IItemHandlerModifiable) infiniteComponentInventory.toItemHandler());
    @DescSynced
    private boolean buildInProgress;
    private final MultiBlockBuildController buildController = new MultiBlockBuildController(this);
    private transient StorageUiSnapshot storageUiSnapshot = StorageUiSnapshot.EMPTY;
    private transient long storageUiSnapshotGameTime = Long.MIN_VALUE;
    private long storageUiRevision = Long.MIN_VALUE;
    private long hugeUiRevision = Long.MIN_VALUE;
    private long hugeUiTick = Long.MIN_VALUE;
    private ECOInfiniteStorageEngine hugeUiEngine;
    private long extractionCheckTick = Long.MIN_VALUE;
    private String extractionCheckReason;
    private List<StorageHostHugeStackList.Entry> hugeUiEntries = List.of();
    private final Map<ECODriveBlockEntity, DriveUiSnapshot> driveUiSnapshots = new HashMap<>();
    private record DriveUiSnapshot(IECOStorageCell inventory, long revision, long tick, int type,
        List<AEKeyType> keyTypes, boolean member, long usedTypes, long totalTypes, long usedBytes, long totalBytes) {}
    private final cn.dancingsnow.neoecoae.impl.storage.StorageFaults storageFaults =
        new cn.dancingsnow.neoecoae.impl.storage.StorageFaults();
    private final Map<String, Long> stageRetryTicks = new HashMap<>();
    private final java.util.Set<AEKey> haltedTransferKeys = new java.util.HashSet<>();
    private boolean unresolvedTransferHalt;
    private final net.minecraft.nbt.ListTag unresolvedHaltedKeys = new net.minecraft.nbt.ListTag();
    private final cn.dancingsnow.neoecoae.impl.storage.transfer.ECOGenericTransfer genericTransfer =
        new cn.dancingsnow.neoecoae.impl.storage.transfer.ECOGenericTransfer();
    private ECOInfiniteStorageEngine cachedStorageEngine;
    private MEStorage cachedInfiniteStorage;
    private final Map<UUID, MigrationCursor> migrationCursors = new HashMap<>();
    private int migrationDriveCursor;
    private RestorePlan activeRestorePlan;
    private final java.util.ArrayDeque<AEKey> restoreQueue = new java.util.ArrayDeque<>();
    private long currentStorageBudget = STORAGE_INTERFACE_TRANSFER_NANOS_PER_TICK;
    private static final class MigrationCursor {
        private final java.util.Iterator<Object2LongMap.Entry<AEKey>> entries;
        private Object2LongMap.Entry<AEKey> pending;
        private MigrationCursor(java.util.Iterator<Object2LongMap.Entry<AEKey>> entries) { this.entries = entries; }
    }
    @Getter
    @DescSynced
    private long performanceAverageNanos = 0L;
    private long performanceWindowStartTick = Long.MIN_VALUE;
    private long performanceWindowNanos = 0L;
    private final long[] performanceSamples = new long[256];
    private int performanceSampleCount;
    private int performanceSampleCursor;
    @Getter
    private long performanceP95Nanos;
    @Getter
    private long performanceMaxNanos;
    @Nullable
    private transient ECOFiniteStorageDomain finiteTransferDomain;
    @Nullable
    private transient ECOTransferScheduler finiteTransferScheduler;
    @Nullable
    private transient CompoundTag pendingFiniteTransferDomain;
    private transient boolean finiteDomainRestoreFailed;
    private final transient ECOStorageSourceAdapterRegistry sourceAdapterRegistry =
        new ECOStorageSourceAdapterRegistry();
    // Transient derived state rebuilt by the calculator; the BlockState property is render-only persistence.
    @Setter
    private boolean mirrored;

    public ECOStorageSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState, NEStorageClusterCalculator::new);
        this.tier = tier;
        getMainNode().addService(IStorageProvider.class, this);
    }

    public static ECOStorageSystemBlockEntity createL4(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L4);
    }

    public static ECOStorageSystemBlockEntity createL6(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L6);
    }

    public static ECOStorageSystemBlockEntity createL9(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState
    ) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L9);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(256 + (1 << (1 + 4 * tier.getTier())));
    }

    @Override
    public void updateState(boolean updateExposed) {
        if (isServerStopping()) {
            return;
        }
        super.updateState(updateExposed);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOStorageSystemBlock.MIRRORED)) {
                BlockState newState = state.setValue(ECOStorageSystemBlock.MIRRORED, formed && mirrored);
                if (newState != state) {
                    level.setBlock(
                        worldPosition,
                        newState,
                        Block.UPDATE_CLIENTS
                    );
                }
            }
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        long startNanos = System.nanoTime();
        Object server = level.getServer();
        currentStorageBudget = cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageTickBudget.allowance(
            server, this, level.getGameTime(), NEConfig.storageTransferNanosPerTick);
        if (currentStorageBudget <= 0L) return;
        try (var cellBatch = cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch.open()) {
            runStorageStage("migration", this::updateInfiniteStorageMode);
            ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
            if (storageInterface != null) {
                runStorageStage("transfer", () -> {
                    updateFiniteTransferDomain(storageInterface);
                    storageInterface.recordStorageInterfaceTransfer(transferStorageInterfaceContents(storageInterface));
                });
            } else if (finiteTransferDomain != null) {
                runStorageStage("materialization", this::materializeFiniteTransferDomain);
            }
            runStorageStage("construction", () -> buildController.tick(level));
        } finally {
            long elapsed = System.nanoTime() - startNanos;
            cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageTickBudget.spent(server, elapsed);
            recordPerformanceSample(elapsed);
        }
    }

    private void runStorageStage(String stage, Runnable action) {
        long tick = level == null ? 0L : level.getGameTime();
        if (tick < stageRetryTicks.getOrDefault(stage, Long.MIN_VALUE)) return;
        if (currentStorageBudget <= 0L) return;
        long start = System.nanoTime();
        try {
            action.run();
            storageFaults.recovered(stage);
        } catch (RuntimeException e) {
            stageRetryTicks.put(stage, tick + 200L);
            storageFaults.report(stage, worldPosition + ": " + e, tick, e);
        } finally {
            currentStorageBudget = Math.max(0L, currentStorageBudget - (System.nanoTime() - start));
        }
    }

    public List<cn.dancingsnow.neoecoae.impl.storage.StorageFaults.Fault> storageFailures() {
        return storageFaults.snapshot();
    }

    public String storageDiagnosticText() { return storageDiagnostics().getString(); }

    private void recordPerformanceSample(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            return;
        }
        performanceSamples[performanceSampleCursor++ % performanceSamples.length] = elapsedNanos;
        performanceSampleCount = Math.min(performanceSamples.length, performanceSampleCount + 1);
        long currentTick = TickHandler.instance().getCurrentTick();
        if (performanceWindowStartTick == Long.MIN_VALUE) {
            performanceWindowStartTick = currentTick;
        }
        performanceWindowNanos += elapsedNanos;
        long elapsedTicks = currentTick - performanceWindowStartTick;
        if (elapsedTicks < PERFORMANCE_SAMPLE_WINDOW_TICKS) {
            return;
        }
        long nextAverageNanos = performanceWindowNanos / Math.max(1L, elapsedTicks);
        long[] ordered = java.util.Arrays.copyOf(performanceSamples, performanceSampleCount);
        java.util.Arrays.sort(ordered);
        performanceP95Nanos = ordered[Math.max(0, (int) Math.ceil(ordered.length * 0.95D) - 1)];
        performanceMaxNanos = ordered[ordered.length - 1];
        performanceSampleCount = 0;
        performanceSampleCursor = 0;
        performanceWindowStartTick = currentTick;
        performanceWindowNanos = 0L;
        if (performanceAverageNanos == nextAverageNanos) {
            return;
        }
        performanceAverageNanos = nextAverageNanos;
        setChanged();
        markForUpdate();
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        StorageHostActionUI.Elements actionUI = createActionUI(holder);

        UIElement root = new UIElement().layout(layout -> {
            layout.width(344);
            layout.height(232);
            layout.gapAll(0);
        }).addClass("panel_bg");

        root.addChild(new TextElement()
            .setText(getItemFromBlockEntity().getDescription())
            .textStyle(ECOStorageSystemBlockEntity::titleTextStyle)
            .layout(layout -> {
                layout.positionType(TaffyPosition.ABSOLUTE);
                layout.left(8);
                layout.top(8);
            }));

        UIElement panels = new UIElement().layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(6);
            layout.top(24);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.STRETCH);
            layout.gapAll(4);
        });
        StorageHostPanelUI.Config storagePanelConfig = createStoragePanelConfig();
        panels.addChild(StorageHostPanelUI.createLeftPanel(storagePanelConfig));
        panels.addChild(StorageHostPanelUI.createRightPanel(storagePanelConfig));

        root.addChild(panels);
        actionUI.addTo(root);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static void titleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }

    private StorageHostPanelUI.Config createStoragePanelConfig() {
        return new StorageHostPanelUI.Config(
            this::getStoredEnergy,
            this::getMaxEnergy,
            this::getMaxLoadUsedBytes,
            this::getMaxLoadTotalBytes,
            this::getIdleMatrixCount,
            this::getPerformanceAverageNanos,
            NERegistries.CELL_TYPE.stream()
                .map(cellType -> {
                    int id = NERegistries.CELL_TYPE.getId(cellType);
                    return new StorageHostPanelUI.StorageTypeLine(
                        cellType,
                        id,
                        () -> getStorageValue(id, StorageValue.USED_TYPES),
                        () -> getStorageValue(id, StorageValue.TOTAL_TYPES),
                        () -> getStorageValue(id, StorageValue.USED_BYTES),
                        () -> getStorageValue(id, StorageValue.TOTAL_BYTES),
                        () -> getStorageUiSnapshot().storageTypeTotals(id).infiniteBytesText(),
                        () -> getStorageUiSnapshot().storageTypeTotals(id).infiniteBytesTooltipText(),
                        () -> getStorageUiSnapshot().storageTypeTotals(id).displayUsedBytes().toString()
                    );
                })
                .toList(),
            this::isMigratingToInfinite,
            this::getInfiniteMigrationProgressPercent,
            this::canExtractInfiniteComponents,
            infiniteComponentItemHandler,
            () -> level.registryAccess(),
            this::getHugeStackUiEntries,
            this::getInfiniteDomainStatus,
            this::storageDiagnostics
        );
    }

    private List<StorageHostHugeStackList.Entry> getHugeStackUiEntries() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !isFormedInfiniteMode()) {
            hugeUiEngine = null;
            return List.of();
        }
        long tick = level.getGameTime();
        if (hugeUiEngine != engine || hugeUiTick == Long.MIN_VALUE || (engine.revision() != hugeUiRevision && tick - hugeUiTick >= 20L)) {
            hugeUiEngine = engine;
            hugeUiTick = tick;
            try {
                hugeUiEntries = engine.getLargestStacks(128).stream()
                    .map(stack -> new StorageHostHugeStackList.Entry(stack.key(), stack.amount().toString()))
                    .toList();
                hugeUiRevision = engine.revision();
            } catch (RuntimeException e) {
                storageFaults.report("large quantity display", e.toString(), tick);
            }
        }
        return hugeUiEntries;
    }

    private ECOInfiniteStorageData.DomainStatus getInfiniteDomainStatus() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            return ECOInfiniteStorageData.DomainStatus.HEALTHY;
        }
        return engine.status();
    }

    private net.minecraft.network.chat.Component storageDiagnostics() {
        var text = net.minecraft.network.chat.Component.empty();
        if (!haltedTransferKeys.isEmpty() || unresolvedTransferHalt) {
            text.append("Transfer requires review: " + haltedTransferKeys.size() + " keys\n");
        }
        for (var fault : storageFaults.snapshot()) {
            text.append(fault.component() + " [" + fault.id() + "]\n" + fault.reason() + "\n");
        }
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine instanceof cn.dancingsnow.neoecoae.impl.storage.infinite.SavedDataInfiniteStorageEngine saved) {
            text.append(saved.persistenceSummary() + "\n");
            for (String failure : saved.failures()) text.append(failure + "\n");
        }
        return text;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        if (!hasRequiredInfiniteComponents()) infiniteExitRequested = false;
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        saveChanges();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !canUseHostDomainStorage() || isStorageInterfaceTransferMode()) {
            return;
        }
        storageMounts.mount(new ECOInfiniteStorage(engine, getBlockState().getBlock().getName()), storagePriority);
    }

    @SuppressWarnings("UnstableApiUsage")
    private long getStoredEnergy() {
        return getStorageUiSnapshot().storedEnergy();
    }

    @SuppressWarnings("UnstableApiUsage")
    private long getMaxEnergy() {
        return getStorageUiSnapshot().maxEnergy();
    }

    private long getMaxLoadUsedBytes() {
        return getStorageUiSnapshot().maxLoadUsedBytes();
    }

    private long getMaxLoadTotalBytes() {
        return getStorageUiSnapshot().maxLoadTotalBytes();
    }

    private int getIdleMatrixCount() {
        return getStorageUiSnapshot().idleMatrices();
    }

    private long getStorageValue(int cellTypeId, StorageValue value) {
        if (cellTypeId < 0) {
            return 0;
        }
        StorageTypeTotals totals = getStorageUiSnapshot().storageTypeTotals(cellTypeId);
        return switch (value) {
            case USED_TYPES -> totals.usedTypes();
            case TOTAL_TYPES -> totals.totalTypes();
            case USED_BYTES -> totals.usedBytes();
            case TOTAL_BYTES -> totals.totalBytes();
        };
    }

    private StorageUiSnapshot getStorageUiSnapshot() {
        long gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        long revision = engine == null ? 0L : engine.revision();
        if (storageUiSnapshotGameTime == Long.MIN_VALUE || gameTime - storageUiSnapshotGameTime >= 20L
            || (revision != storageUiRevision && gameTime - storageUiSnapshotGameTime >= 5L)) {
            storageUiSnapshotGameTime = gameTime;
            try {
                storageUiSnapshot = collectStorageUiSnapshot();
                storageUiRevision = revision;
                storageFaults.recovered("statistics");
            } catch (RuntimeException e) {
                storageFaults.report("statistics", e.toString(), gameTime, e);
            }
        }
        return storageUiSnapshot;
    }

    @SuppressWarnings("UnstableApiUsage")
    private StorageUiSnapshot collectStorageUiSnapshot() {
        if (cluster == null) {
            driveUiSnapshots.clear();
            return StorageUiSnapshot.EMPTY;
        }

        long storedEnergy = 0L;
        long maxEnergy = 0L;
        for (ECOEnergyCellBlockEntity energyCell : cluster.getEnergyCells()) {
            storedEnergy = NEMath.saturatingAdd(storedEnergy, (long) energyCell.getAECurrentPower());
            maxEnergy = NEMath.saturatingAdd(maxEnergy, (long) energyCell.getAEMaxPower());
        }

        long maxLoadUsedBytes = 0L;
        long maxLoadTotalBytes = 0L;
        int idleMatrices = 0;
        double bestLoadRatio = -1.0D;
        Map<Integer, StorageTypeTotals> storageTypes = new HashMap<>();
        Map<AEKeyType, Integer> cellTypesByKeyType = new HashMap<>();
        driveUiSnapshots.keySet().retainAll(cluster.getDrives());
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            DriveUiSnapshot view = driveUiSnapshot(drive);
            if (view == null) continue;
            int cellTypeId = view.type();
            for (AEKeyType keyType : view.keyTypes()) cellTypesByKeyType.putIfAbsent(keyType, cellTypeId);
            if (view.member()) {
                idleMatrices++;
                continue;
            }

            long usedTypes = view.usedTypes();
            long totalTypes = view.totalTypes();
            long usedBytes = view.usedBytes();
            long totalBytes = view.totalBytes();
            if (usedBytes <= 0L && usedTypes <= 0L) {
                idleMatrices++;
            }
            if (totalBytes > 0L) {
                double ratio = (double) usedBytes / (double) totalBytes;
                if (ratio > bestLoadRatio) {
                    bestLoadRatio = ratio;
                    maxLoadUsedBytes = usedBytes;
                    maxLoadTotalBytes = totalBytes;
                }
            }

            if (cellTypeId >= 0) {
                storageTypes.merge(
                    cellTypeId,
                    new StorageTypeTotals(usedTypes, totalTypes, usedBytes, totalBytes),
                    StorageTypeTotals::add
                );
            }
        }
        if (isFormedInfiniteMode()) {
            addInfiniteStorageTypes(storageTypes, cellTypesByKeyType);
        }

        return new StorageUiSnapshot(
            storedEnergy,
            maxEnergy,
            isFormedInfiniteMode() ? Long.MAX_VALUE : maxLoadUsedBytes,
            isFormedInfiniteMode() ? Long.MAX_VALUE : maxLoadTotalBytes,
            idleMatrices,
            Map.copyOf(storageTypes)
        );
    }

    private DriveUiSnapshot driveUiSnapshot(ECODriveBlockEntity drive) {
        DriveUiSnapshot previous = driveUiSnapshots.get(drive);
        long tick = level.getGameTime();
        String component = "drive statistics " + drive.getBlockPos();
        try {
            IECOStorageCell inventory = drive.getCellInventory();
            if (inventory == null) { driveUiSnapshots.remove(drive); return null; }
            long revision = inventory instanceof ECOStorageCell cell ? cell.contentRevision() : -1L;
            boolean member = isInfiniteMemberCell(drive.getCellStack());
            if (previous != null && previous.inventory() == inventory && previous.revision() == revision
                && previous.member() == member && tick - previous.tick() < 20L) return previous;
            int type = NERegistries.CELL_TYPE.getId(inventory.getCellType());
            List<AEKeyType> keyTypes = new ArrayList<>();
            if (type >= 0 && drive.getCellStack().getItem() instanceof IECOStorageCellItem item) {
                for (AEKeyType keyType : item.getKeyTypes()) keyTypes.add(keyType);
            }
            DriveUiSnapshot next = new DriveUiSnapshot(inventory, revision, tick, type, List.copyOf(keyTypes), member,
                member ? 0L : inventory.getStoredItemTypes(), member ? 0L : inventory.hasInfiniteTypeCapacity() ? -1L : inventory.getTotalItemTypes(),
                member ? 0L : inventory.getUsedBytes(), member ? 0L : inventory.getTotalBytes());
            driveUiSnapshots.put(drive, next);
            storageFaults.recovered(component);
            return next;
        } catch (RuntimeException e) {
            storageFaults.report(component, e.toString(), tick, e);
            return previous;
        }
    }

    private void addInfiniteStorageTypes(
        Map<Integer, StorageTypeTotals> storageTypes,
        Map<AEKeyType, Integer> cellTypesByKeyType
    ) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            return;
        }
        for (ECOInfiniteStorageEngine.TypeStats stats : engine.getTypeStats()) {
            int cellTypeId = cellTypesByKeyType.getOrDefault(stats.keyType(), -1);
            if (cellTypeId < 0) {
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
        BigInteger amount = stats.storedAmount().toBigInteger();
        BigInteger amountPerByte = BigInteger.valueOf(stats.keyType().getAmountPerByte());
        BigInteger[] division = amount.divideAndRemainder(amountPerByte);
        BigInteger contentBytes = division[0].add(division[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
        long bytesPerType = 1L << (12 + ECOTier.L9.getTier());
        return contentBytes.add(BigInteger.valueOf(stats.storedTypes()).multiply(BigInteger.valueOf(bytesPerType)));
    }

    private record StorageUiSnapshot(
        long storedEnergy,
        long maxEnergy,
        long maxLoadUsedBytes,
        long maxLoadTotalBytes,
        int idleMatrices,
        Map<Integer, StorageTypeTotals> storageTypes
    ) {
        private static final StorageUiSnapshot EMPTY =
            new StorageUiSnapshot(0L, 0L, 0L, 0L, 0, Map.of());

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
            return HostText.fitHugeAmount(displayUsedBytes, 62);
        }

        private String infiniteBytesTooltipText() {
            return HostText.compactStorageBytes(displayUsedBytes);
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

    private StorageHostActionUI.Elements createActionUI(BlockUIMenuType.BlockUIHolder holder) {
        return StorageHostActionUI.create(new StorageHostActionUI.Config(
            holder.player,
            () -> selectedBuildLength,
            () -> mirrorBuild,
            mirror -> buildController.setMirrorBuild(holder.player, mirror),
            () -> buildController.decreaseBuildLength(holder.player),
            () -> buildController.increaseBuildLength(holder.player),
            () -> buildController.autoBuild(holder.player),
            () -> formed,
            () -> buildInProgress,
            buildController::createLocalPreviewPlan,
            () -> storagePriority,
            priority -> setStoragePriority(holder.player, priority),
            delta -> changeStoragePriority(holder.player, delta)
        ));
    }

    private void changeStoragePriority(Player player, int delta) {
        if (!canPlayerInteract(player)) return;
        setStoragePriority(player, StoragePriority.adjust(storagePriority, delta));
    }

    private void setStoragePriority(Player player, int priority) {
        if (!canPlayerInteract(player)) return;
        if (storagePriority == priority) {
            return;
        }
        storagePriority = priority;
        setChanged();
        markForUpdate();
        refreshDriveStorageProviders();
    }

    private void refreshDriveStorageProviders() {
        if (cluster == null) {
            return;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IStorageProvider.requestUpdate(drive.getMainNode());
        }
        IStorageProvider.requestUpdate(getMainNode());
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack infiniteComponent = infiniteComponentInventory.getStackInSlot(0);
        if (!infiniteComponent.isEmpty()) {
            drops.add(infiniteComponent);
        }
    }

    public void applyInfiniteDomainToControllerDrop(ItemStack drop) {
        if (infiniteDomainId == null || drop.isEmpty()) {
            return;
        }
        CompoundTag tag = drop.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID(CONTROLLER_DOMAIN_TAG, infiniteDomainId);
        tag.putString(CONTROLLER_MODE_TAG, hostMode.id());
        drop.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public void restoreInfiniteDomainFromItem(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(CONTROLLER_DOMAIN_TAG)) {
            return;
        }
        infiniteDomainId = tag.getUUID(CONTROLLER_DOMAIN_TAG);
        hostMode = ECOStorageHostMode.fromId(tag.getString(CONTROLLER_MODE_TAG));
        setChanged();
    }

    public boolean isInfiniteMode() {
        return hostMode.isInfiniteState();
    }

    public boolean isMigratingToInfinite() {
        return hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE;
    }

    public boolean isFormedInfiniteMode() {
        return hostMode == ECOStorageHostMode.FORMED_INFINITE;
    }

    private int getInfiniteMigrationProgressPercent() {
        if (!hostMode.isInfiniteState()) {
            return 0;
        }
        return Math.clamp(Math.round(countInfiniteMembers() * 100.0F / INFINITE_MEMBER_REQUIRED), 0, 100);
    }

    public boolean canUseHostDomainStorage() {
        return formed && hostMode.isInfiniteState() && infiniteDomainId != null;
    }

    public boolean isInfiniteMemberCell(@Nullable ItemStack stack) {
        return stack != null && ECOInfiniteStorageMember.isMember(stack);
    }

    public void onStorageInterfaceModeChanged() {
        if (level == null || level.isClientSide) return;
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (storageInterface != null) {
            updateFiniteTransferDomain(storageInterface);
        }
        refreshDriveStorageProviders();
        setChanged();
        markForUpdate();
    }

    public boolean isStorageInterfaceTransferMode() {
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        return formed && storageInterface != null && storageInterface.isStorageTransferMode();
    }

    public boolean isFiniteTransferDomainLocked() {
        return finiteTransferDomain != null;
    }

    public boolean materializeFiniteTransferDomain() {
        if (finiteTransferDomain == null) return true;
        if (finiteDomainRestoreFailed) {
            LOGGER.error("Finite storage transfer domain at {} cannot materialize because restore failed", worldPosition);
            return false;
        }
        resetFiniteTransferScheduler();
        boolean materialized = finiteTransferDomain.materialize(IActionSource.ofMachine(this));
        if (!materialized) {
            LOGGER.error("Unable to materialize finite storage transfer domain at {}; drives remain locked", worldPosition);
            setChanged();
            return false;
        }
        finiteTransferDomain = null;
        pendingFiniteTransferDomain = null;
        finiteDomainRestoreFailed = false;
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        refreshDriveStorageProviders();
        setChanged();
        markForUpdate();
        return true;
    }

    private void updateFiniteTransferDomain(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (isInfiniteMode() || !formed) {
            materializeFiniteTransferDomain();
            return;
        }
        boolean transferRequested = storageInterface.isStorageTransferMode();
        if (!transferRequested && pendingFiniteTransferDomain == null) {
            materializeFiniteTransferDomain();
            return;
        }
        if (cluster == null) return;
        IActionSource actionSource = IActionSource.ofMachine(storageInterface);
        if (finiteTransferDomain == null) {
            finiteTransferDomain = ECOFiniteStorageDomain.create(
                cluster.getDrives().stream()
                    .filter(drive -> !isInfiniteMemberCell(drive.getCellStack()))
                    .toList(),
                tier, storageInterface.getStorageInterfaceMode(),
                getBlockState().getBlock().getName(), actionSource);
            long eligibleCells = cluster.getDrives().stream()
                .filter(drive -> !isInfiniteMemberCell(drive.getCellStack()))
                .map(ECODriveBlockEntity::getCellInventory)
                .filter(java.util.Objects::nonNull)
                .filter(cell -> tier.compareTo(cell.getTier()) >= 0)
                .count();
            if (finiteTransferDomain.shardCount() != eligibleCells) {
                // Optional/external cell handlers keep using their standard MEStorage path until they expose the
                // controller-domain mutation contract. Mixing both ownership models would make materialization unsafe.
                if (pendingFiniteTransferDomain != null) {
                    finiteDomainRestoreFailed = true;
                    LOGGER.error("Finite storage transfer domain at {} cannot restore because its cell handler set changed",
                        worldPosition);
                    return;
                }
                materializeFiniteTransferDomain();
                return;
            }
            if (pendingFiniteTransferDomain != null) {
                try {
                    finiteTransferDomain.restore(pendingFiniteTransferDomain, level.registryAccess(), actionSource);
                    pendingFiniteTransferDomain = null;
                } catch (RuntimeException e) {
                    finiteDomainRestoreFailed = true;
                    LOGGER.error("Unable to restore finite storage transfer domain at {}; drives remain locked",
                        worldPosition, e);
                    return;
                }
            }
            storageUiSnapshotGameTime = Long.MIN_VALUE;
            refreshDriveStorageProviders();
            setChanged();
        }
        if (finiteTransferDomain.state() == ECOFiniteStorageDomain.State.MATERIALIZING) {
            materializeFiniteTransferDomain();
            return;
        }
        if (!transferRequested) {
            materializeFiniteTransferDomain();
            return;
        }
        if (finiteTransferDomain.mode() != storageInterface.getStorageInterfaceMode()) {
            finiteTransferDomain.setMode(storageInterface.getStorageInterfaceMode());
            resetFiniteTransferScheduler();
        }
    }

    private void resetFiniteTransferScheduler() {
        if (finiteTransferScheduler != null) {
            haltedTransferKeys.addAll(finiteTransferScheduler.haltedKeys());
            finiteTransferScheduler.stop();
            finiteTransferScheduler = null;
        }
    }

    @Nullable
    private ECOMachineInterfaceBlockEntity<NEStorageCluster> getStorageInterface() {
        return cluster == null ? null : cluster.getTheInterface();
    }

    private long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (unresolvedTransferHalt) return 0L;
        if (!formed || !storageInterface.isStorageTransferMode()) return 0L;
        if (!storageInterface.isTargetOnline()) return 0L;
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) return 0L;
        MEStorage network = grid.getStorageService().getInventory();
        MEStorage hostStorage = getStorageInterfaceHostStorage();
        if (hostStorage == null) return 0L;
        IActionSource source = IActionSource.ofMachine(storageInterface);
        long moved;
        if (!isInfiniteMode() && finiteTransferDomain != null && !finiteDomainRestoreFailed) {
            if (finiteTransferScheduler == null) {
                finiteTransferScheduler = new ECOTransferScheduler(
                    finiteTransferDomain,
                    grid,
                    network,
                    source,
                    sourceAdapterRegistry,
                    NEConfig.storageTransferKeysPerTick,
                    NEConfig.storageTransferNanosPerTick,
                    NEConfig.storageTransferRate,
                    this::onFiniteDomainMutation
                );
                finiteTransferScheduler.start(level.getGameTime());
                finiteTransferScheduler.restoreHalted(haltedTransferKeys);
            }
            moved = finiteTransferScheduler.tick(level.getGameTime(), currentStorageBudget);
        } else {
            genericTransfer.restoreHalted(haltedTransferKeys);
            moved = storageInterface.isStorageInputMode()
                ? genericTransfer.tick(network, hostStorage, source, true, level.getGameTime(),
                    NEConfig.storageTransferKeysPerTick, currentStorageBudget,
                    NEConfig.storageTransferRate, reason -> storageFaults.report("generic transfer", reason, level.getGameTime()))
                : genericTransfer.tick(hostStorage, network, source, false, level.getGameTime(),
                    NEConfig.storageTransferKeysPerTick, currentStorageBudget,
                    NEConfig.storageTransferRate, reason -> storageFaults.report("generic transfer", reason, level.getGameTime()));
        }
        int previousHalted = haltedTransferKeys.size();
        haltedTransferKeys.addAll(genericTransfer.haltedKeys());
        if (finiteTransferScheduler != null) haltedTransferKeys.addAll(finiteTransferScheduler.haltedKeys());
        if (haltedTransferKeys.size() != previousHalted) setChanged();
        if (moved > 0L) {
            setChanged();
            markForUpdate();
        }
        return moved;
    }

    private void onFiniteDomainMutation() {
        setChanged();
    }

    private CombinedStorage cachedCombinedStorage;

    @Nullable
    private MEStorage getStorageInterfaceHostStorage() {
        if (canUseHostDomainStorage()) {
            ECOInfiniteStorageEngine engine = getInfiniteEngine();
            if (engine != cachedStorageEngine) {
                cachedStorageEngine = engine;
                cachedInfiniteStorage = engine == null ? null : new ECOInfiniteStorage(engine, getBlockState().getBlock().getName());
            }
            return cachedInfiniteStorage;
        }
        if (finiteTransferDomain != null && !finiteDomainRestoreFailed) {
            return finiteTransferDomain;
        }
        if (cluster == null) return null;

        List<MEStorage> cells = new ArrayList<>();
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell cell = drive.getCellInventory();
            if (cell != null
                && tier.compareTo(cell.getTier()) >= 0
                && !isInfiniteMemberCell(drive.getCellStack())) {
                cells.add(cell);
            }
        }
        if (cells.isEmpty()) { cachedCombinedStorage = null; return null; }
        if (cachedCombinedStorage == null || !cachedCombinedStorage.inventories().equals(cells)) {
            cachedCombinedStorage = new CombinedStorage(cells, getBlockState().getBlock().getName());
        }
        return cachedCombinedStorage;
    }

    private record CombinedStorage(List<MEStorage> inventories, net.minecraft.network.chat.Component description)
        implements MEStorage {
        private CombinedStorage {
            inventories = List.copyOf(inventories);
        }

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long inserted = 0L;
            for (MEStorage inventory : inventories) {
                if (inserted >= amount) break;
                inserted += inventory.insert(key, amount - inserted, mode, source);
            }
            return inserted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = 0L;
            for (MEStorage inventory : inventories) {
                if (extracted >= amount) break;
                extracted += inventory.extract(key, amount - extracted, mode, source);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter out) {
            for (MEStorage inventory : inventories) {
                inventory.getAvailableStacks(out);
            }
        }

        @Override
        public net.minecraft.network.chat.Component getDescription() {
            return description;
        }
    }

    private void updateInfiniteStorageMode() {
        if (level == null || level.isClientSide || isServerStopping()) {
            return;
        }
        ECOStorageHostMode previous = hostMode;
        if (!formed || cluster == null) {
            if (!hostMode.isInfiniteState()) {
                hostMode = ECOStorageHostMode.UNFORMED;
            }
            syncInfiniteModeChanges(previous);
            return;
        }
        if (hostMode == ECOStorageHostMode.UNFORMED) {
            hostMode = ECOStorageHostMode.FORMED_NORMAL;
        }
        if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            runInfiniteMigrationStep();
            syncInfiniteModeChanges(previous);
            return;
        }
        ECOInfiniteStorageEngine restoringEngine = getInfiniteEngine();
        if (activeRestorePlan != null || (restoringEngine != null && restoringEngine.hasPendingRestore())
            || (infiniteExitRequested && hostMode.isInfiniteState())) {
            restoreInfiniteDomainToNormalStorageIfPossible();
            syncInfiniteModeChanges(previous);
            return;
        }
        if (hostMode.isInfiniteState() && !hasRequiredInfiniteComponents()) {
            restoreInfiniteDomainToNormalStorageIfPossible();
            syncInfiniteModeChanges(previous);
            return;
        }
        if (hostMode == ECOStorageHostMode.FORMED_NORMAL && canStartInfiniteMigration()) {
            ensureInfiniteDomainId();
            hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
            syncInfiniteModeChanges(previous);
        }
        if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            runInfiniteMigrationStep();
        }
        syncInfiniteModeChanges(previous);
    }

    private void syncInfiniteModeChanges(ECOStorageHostMode previous) {
        if (previous != hostMode) {
            storageUiSnapshotGameTime = Long.MIN_VALUE;
            refreshDriveStorageProviders();
            setChanged();
            markForUpdate();
        }
    }

    private boolean canStartInfiniteMigration() {
        return !infiniteExitRequested && tier == ECOTier.L9
            && formed
            && cluster != null
            && hasRequiredInfiniteComponents()
            && !hasForeignInfiniteMembers()
            && countEligibleInfiniteMatrices() >= INFINITE_MEMBER_REQUIRED;
    }

    private boolean hasForeignInfiniteMembers() {
        if (cluster == null) {
            return false;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (ECOInfiniteStorageMember.isMember(stack)
                && (infiniteDomainId == null || !ECOInfiniteStorageMember.isMemberOf(stack, infiniteDomainId))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRequiredInfiniteComponents() {
        ItemStack stack = infiniteComponentInventory.getStackInSlot(0);
        return hasRequiredInfiniteComponents(stack);
    }

    private boolean hasRequiredInfiniteComponents(ItemStack stack) {
        return isInfiniteComponent(stack) && stack.getCount() >= INFINITE_COMPONENT_REQUIRED;
    }

    private int countEligibleInfiniteMatrices() {
        if (cluster == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack != null
                && !stack.isEmpty()
                && cell != null
                && cell.getTier() == ECOTier.L9
                && cell.isInfiniteStorageEligible()
                && !ECOInfiniteStorageMember.isMember(stack)) {
                count++;
            }
        }
        return count;
    }

    private int countInfiniteMembers() {
        if (cluster == null || infiniteDomainId == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), infiniteDomainId)) {
                count++;
            }
        }
        return count;
    }

    private void runInfiniteMigrationStep() {
        if (!(level instanceof ServerLevel serverLevel) || cluster == null) {
            return;
        }
        UUID domainId = ensureInfiniteDomainId();
        ECOInfiniteStorageEngine engine = ECOInfiniteStorageDomains.get(serverLevel, domainId);
        if (!engine.isHealthy()) {
            return;
        }
        boolean hasPending = false;
        List<ECODriveBlockEntity> drives = new ArrayList<>(cluster.getDrives());
        for (int visited = 0; visited < drives.size(); visited++) {
            ECODriveBlockEntity drive = drives.get(Math.floorMod(migrationDriveCursor++, drives.size()));
            String stage = "migration drive " + drive.getBlockPos();
            long tick = level.getGameTime();
            if (tick < stageRetryTicks.getOrDefault(stage, Long.MIN_VALUE)) {
                hasPending = true;
                continue;
            }
            try {
                ItemStack stack = drive.getCellStack();
                if (ECOInfiniteStorageMember.isMember(stack)) {
                    if (ECOInfiniteStorageMember.isMemberOf(stack, domainId)) continue;
                    hasPending = true;
                    storageFaults.report(stage, "Foreign infinite storage member", tick);
                    continue;
                }
                IECOStorageCell cell = drive.getCellInventory();
                if (stack == null || stack.isEmpty() || cell == null || cell.getTier() != ECOTier.L9
                    || !cell.isInfiniteStorageEligible()) continue;
                hasPending = true;
                migrateDriveToDomain(drive, cell, engine, domainId);
                storageFaults.recovered(stage);
                break;
            } catch (RuntimeException e) {
                hasPending = true;
                stageRetryTicks.put(stage, tick + 200L);
                storageFaults.report(stage, e.toString(), tick, e);
            }
        }
        if (!hasPending && countInfiniteMembers() >= INFINITE_MEMBER_REQUIRED) {
            hostMode = ECOStorageHostMode.FORMED_INFINITE;
        }
    }

    private void migrateDriveToDomain(ECODriveBlockEntity drive, IECOStorageCell cell, ECOInfiniteStorageEngine engine, UUID domainId) {
        if (!(cell instanceof ECOStorageCell)
            && !(cell instanceof cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalStorageCell)) {
            throw new IllegalStateException("Cell handler does not support resumable migration");
        }
        UUID migration = ECOInfiniteStorageMember.beginMigration(drive.getCellStack(), domainId);
        MigrationCursor cursor = migrationCursors.get(migration);
        if (cursor == null) {
            java.util.Iterator<Object2LongMap.Entry<AEKey>> entries;
            if (cell instanceof ECOStorageCell storageCell) entries = storageCell.migrationEntries();
            else if (cell instanceof cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalStorageCell universal) {
                KeyCounter available = new KeyCounter();
                universal.getMigrationStacks(available);
                entries = available.iterator();
            } else throw new IllegalStateException("Cell handler does not support resumable migration");
            drive.setChanged();
            IStorageProvider.requestUpdate(drive.getMainNode());
            // The source seal must reach its chunk before the domain can expose a second copy.
            ((ServerLevel) level).getChunkSource().save(true);
            cursor = new MigrationCursor(entries);
            migrationCursors.put(migration, cursor);
        }
        long start = System.nanoTime();
        int processed = 0;
        while ((cursor.pending != null || cursor.entries.hasNext()) && processed++ < 64) {
            if (System.nanoTime() - start >= currentStorageBudget) break;
            if (cursor.pending == null) cursor.pending = cursor.entries.next();
            AEKey key = cursor.pending.getKey();
            long amount = cursor.pending.getLongValue();
            if (amount > 0L) {
                UUID legacyReceipt = migrationTransactionId(domainId, drive, key, amount, "to-domain");
                UUID transaction = engine.hasMigrationReceipt(legacyReceipt) ? legacyReceipt
                    : UUID.nameUUIDFromBytes((migration + ":" + key.toTagGeneric(level.registryAccess())).getBytes(StandardCharsets.UTF_8));
                if (engine.insertOnce(transaction, key, amount) != amount) return;
            }
            cursor.pending = null;
        }
        if (cursor.pending != null || cursor.entries.hasNext()) return;
        if (!engine.commit().successful()) {
            return;
        }
        if (cell instanceof ECOStorageCell storageCell) {
            storageCell.clearAllStoredStacks();
        }
        drive.convertCellToInfiniteMember(domainId);
        migrationCursors.remove(migration);
        IStorageProvider.requestUpdate(drive.getMainNode());
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        setChanged();
        markForUpdate();
    }

    private void restoreInfiniteDomainToNormalStorage() {
        RestorePlan plan = createInfiniteRestorePlan(false);
        if (!plan.canRestore()) {
            LOGGER.warn(
                "Unable to restore ECO infinite storage domain {}: {}",
                infiniteDomainId,
                plan.reason()
            );
            return;
        }
        restoreInfiniteDomainToNormalStorage(plan);
    }

    private void restoreInfiniteDomainToNormalStorageIfPossible() {
        RestorePlan plan = createInfiniteRestorePlan(true);
        if (plan.canRestore()) {
            restoreInfiniteDomainToNormalStorage(plan);
        } else {
            storageFaults.report("restore", plan.reason(), level.getGameTime());
        }
    }

    private RestorePlan createInfiniteRestorePlan(boolean enforceMargin) {
        if (activeRestorePlan != null) return activeRestorePlan;
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            return RestorePlan.blocked("missing infinite storage engine");
        }
        if (!engine.canExitOrRestore()) {
            return RestorePlan.blocked(
                "infinite storage domain is " + engine.status() + " and cannot be restored to normal storage");
        }
        if (engine.isEmpty()) {
            return RestorePlan.allowed(List.of());
        }
        if (cluster == null || infiniteDomainId == null) {
            return RestorePlan.blocked("missing storage cluster or infinite domain");
        }
        if (engine.hasHugeStacks()) {
            return RestorePlan.blocked("domain contains stacks larger than a normal storage cell can hold");
        }

        List<RestoreTarget> targets = createRestoreTargets(infiniteDomainId);
        if (targets.isEmpty()) {
            return RestorePlan.blocked("no L9 storage matrices are available");
        }
        java.util.Set<UUID> targetIds = new java.util.HashSet<>();
        for (RestoreTarget target : targets) {
            if (!targetIds.add(target.identity)) return RestorePlan.blocked("duplicate restore target identity");
        }

        KeyCounter pending = new KeyCounter();
        engine.getRestoreStacks(pending);
        IActionSource source = IActionSource.ofMachine(this);
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            AEKey key = entry.getKey();
            if (!targetIds.containsAll(engine.restoreTargetIds(key))) {
                return RestorePlan.blocked("an original restore target is missing; return its sealed matrix to resume");
            }
            HugeAmount amount = engine.getRestoreAmount(key);
            if (amount.compareTo(HugeAmount.of(Long.MAX_VALUE)) > 0) {
                return RestorePlan.blocked("domain contains stacks larger than a normal storage cell can hold");
            }
            long remaining = amount.toLongSaturated();
            for (RestoreTarget target : targets) {
                UUID transactionId = migrationTransactionId(infiniteDomainId, target.drive(), key,
                    amount.toLongSaturated(), "from-domain");
                long alreadyRestored = Math.min(remaining, target.drive().getRestoreReceipt(transactionId));
                remaining -= alreadyRestored;
                if (remaining <= 0L) {
                    break;
                }
                long inserted = simulateInsertForRestore(target, key, remaining, source);
                if (inserted > 0L) {
                    target.addSimulated(key, inserted);
                }
                remaining -= inserted;
                if (remaining <= 0L) {
                    break;
                }
            }
            if (remaining > 0L) {
                return RestorePlan.blocked("normal storage matrices do not have enough compatible capacity");
            }
        }
        if (enforceMargin && !restoreTargetsHaveMargin(targets)) {
            return RestorePlan.blocked("normal storage matrices would exceed the reserve margin");
        }
        return RestorePlan.allowed(targets);
    }

    private List<RestoreTarget> createRestoreTargets(UUID domainId) {
        List<RestoreTarget> targets = new ArrayList<>();
        if (cluster == null) {
            return targets;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!ECOInfiniteStorageMember.isMemberOf(stack, domainId)) {
                continue;
            }
            ItemStack simulationStack = stack.copy();
            ECOInfiniteStorageMember.clearMember(simulationStack);
            IECOStorageCell simulatedCell = ECOStorageCells.getCellInventory(simulationStack, null);
            if ((simulatedCell instanceof ECOStorageCell
                || simulatedCell instanceof cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalStorageCell)
                && simulatedCell.getTier() == ECOTier.L9
                && simulatedCell.isInfiniteStorageEligible()) {
                KeyCounter simulatedContents = new KeyCounter();
                simulatedCell.getAvailableStacks(simulatedContents);
                targets.add(new RestoreTarget(drive, simulatedCell, simulatedContents));
            }
        }
        return targets;
    }

    private boolean restoreTargetsHaveMargin(List<RestoreTarget> targets) {
        long used = 0L;
        long total = 0L;
        for (RestoreTarget target : targets) {
            used = NEMath.saturatingAdd(used, getUsedBytesForRestore(target));
            total = NEMath.saturatingAdd(total, target.simulatedCell().getTotalBytes());
        }
        if (total <= 0L) {
            return false;
        }
        long reserved = Math.max(
            1L,
            total / INFINITE_RESTORE_MARGIN_DENOMINATOR
                * (INFINITE_RESTORE_MARGIN_DENOMINATOR - INFINITE_RESTORE_MARGIN_NUMERATOR)
        );
        return used <= total - reserved;
    }

    private long simulateInsertForRestore(
        RestoreTarget target,
        AEKey key,
        long amount,
        IActionSource source
    ) {
        IECOStorageCell cell = target.simulatedCell();
        if (cell instanceof ECOStorageCell storageCell) {
            return storageCell.simulateInsertForMigration(key, amount, target.simulatedContents().get(key),
                target.simulatedTypes, target.simulatedAmount);
        }
        if (cell instanceof cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalStorageCell universalCell) {
            return universalCell.simulateInsertForMigration(key, amount, target.simulatedContents());
        }
        // Unknown handlers must still be probed without mutation. Such handlers are allowed to return a conservative
        // capacity; the real restore below remains authoritative and verifies the final aggregate.
        return insertForRestore(cell, key, amount, Actionable.SIMULATE, source);
    }

    private long getUsedBytesForRestore(RestoreTarget target) {
        IECOStorageCell cell = target.simulatedCell();
        if (cell instanceof ECOStorageCell storageCell) {
            return storageCell.getUsedBytesForMigration(target.simulatedContents());
        }
        if (cell instanceof cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalStorageCell universalCell) {
            return universalCell.getUsedBytesForMigration(target.simulatedContents());
        }
        return cell.getUsedBytes();
    }

    private void restoreInfiniteDomainToNormalStorage(RestorePlan plan) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || engine.isEmpty()) {
            exitInfiniteModeIfSafe();
            return;
        }
        if (infiniteDomainId == null) {
            return;
        }
        if (!engine.canExitOrRestore()) return;
        java.util.Set<UUID> targetIds = new java.util.HashSet<>();
        for (RestoreTarget target : plan.targets()) {
            ECODriveBlockEntity drive = target.drive();
            if (drive.isRemoved() || cluster == null || !cluster.getDrives().contains(drive)
                || serverLevel.getBlockEntity(drive.getBlockPos()) != drive
                || !ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), infiniteDomainId)
                || !target.identity.equals(ECOInfiniteStorageMember.identity(drive.getCellStack()))
                || !targetIds.add(target.identity)) {
                activeRestorePlan = null;
                restoreQueue.clear();
                storageFaults.report("restore", "Restore target changed; waiting for original sealed matrices", level.getGameTime());
                return;
            }
        }
        KeyCounter pending = new KeyCounter();
        if (activeRestorePlan == null) {
            engine.getRestoreStacks(pending);
            for (var entry : pending) restoreQueue.addLast(entry.getKey());
            activeRestorePlan = plan;
        }
        pending.clear();
        while (!restoreQueue.isEmpty() && engine.getRestoreAmount(restoreQueue.peekFirst()).isZero()) restoreQueue.removeFirst();
        if (restoreQueue.isEmpty()) {
            activeRestorePlan = null;
            exitInfiniteModeIfSafe();
            return;
        }
        AEKey restoringKey = restoreQueue.peekFirst();
        long restoringAmount = engine.getRestoreAmount(restoringKey).toLongSaturated();
        pending.add(restoringKey, restoringAmount);
        UUID restoreId = engine.restoreTransaction(restoringKey);
        if (restoreId == null) restoreId = UUID.randomUUID();
        if (!engine.reserveRestore(restoringKey, restoreId, targetIds)) return;
        IStorageProvider.requestUpdate(getMainNode());
        IActionSource source = IActionSource.ofMachine(this);
        Map<AEKey, BigInteger> expectedFinalAmounts = expectedFinalRestoreAmounts(plan.targets(), pending);
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            AEKey key = entry.getKey();
            long remaining = engine.getRestoreAmount(key).toLongSaturated();
            long original = remaining;
            for (RestoreTarget target : plan.targets()) {
                IECOStorageCell cell = target.drive().getCellInventory();
                if (cell == null || !ECOInfiniteStorageMember.isMemberOf(target.drive().getCellStack(), infiniteDomainId)) {
                    continue;
                }
                UUID transactionId = migrationTransactionId(infiniteDomainId, target.drive(), key, original, "from-domain");
                long inserted = Math.min(remaining, target.drive().getRestoreReceipt(transactionId));
                if (inserted <= 0L) {
                    try {
                        inserted = insertForRestore(cell, key, remaining, Actionable.MODULATE, source);
                    } catch (RuntimeException e) {
                        engine.failRestore(key, "Restore outcome uncertain at " + target.drive().getBlockPos() + ": " + e);
                        throw e;
                    }
                    target.drive().putRestoreReceipt(transactionId, inserted);
                }
                cell.persist();
                remaining -= inserted;
                if (remaining <= 0L) {
                    break;
                }
            }
            if (remaining > 0L) {
                LOGGER.warn("ECO infinite storage restore changed during execution; keeping domain {} mounted", infiniteDomainId);
                engine.commit();
                return;
            }
        }
        serverLevel.getChunkSource().save(true);
        if (!verifyRestoredContents(plan.targets(), expectedFinalAmounts)) {
            LOGGER.error(
                "Unable to verify restored ECO storage contents for domain {}; keeping the domain mounted",
                infiniteDomainId
            );
            engine.commit();
            return;
        }
        if (!engine.finishRestore(restoringKey, restoreId)) return;
        storageFaults.recovered("restore");
        IStorageProvider.requestUpdate(getMainNode());
        restoreQueue.removeFirst();
        if (restoreQueue.isEmpty()) {
            activeRestorePlan = null;
            exitInfiniteModeIfSafe();
        }
    }

    private Map<AEKey, BigInteger> expectedFinalRestoreAmounts(List<RestoreTarget> targets, KeyCounter pending) {
        KeyCounter baseline = collectRestoreTargetContents(targets);
        Map<AEKey, BigInteger> expected = new HashMap<>();
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            AEKey key = entry.getKey();
            long domainAmount = entry.getLongValue();
            long alreadyRestored = 0L;
            for (RestoreTarget target : targets) {
                UUID transactionId = migrationTransactionId(infiniteDomainId, target.drive(), key, domainAmount, "from-domain");
                alreadyRestored = NEMath.saturatingAdd(alreadyRestored, target.drive().getRestoreReceipt(transactionId));
            }
            long outstanding = Math.max(0L, domainAmount - Math.min(domainAmount, alreadyRestored));
            expected.put(key, BigInteger.valueOf(baseline.get(key)).add(BigInteger.valueOf(outstanding)));
        }
        return expected;
    }

    private boolean verifyRestoredContents(List<RestoreTarget> targets, Map<AEKey, BigInteger> expected) {
        KeyCounter restored = collectRestoreTargetContents(targets);
        for (Map.Entry<AEKey, BigInteger> entry : expected.entrySet()) {
            if (!BigInteger.valueOf(restored.get(entry.getKey())).equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private KeyCounter collectRestoreTargetContents(List<RestoreTarget> targets) {
        KeyCounter restored = new KeyCounter();
        for (RestoreTarget target : targets) {
            IECOStorageCell cell = target.drive().getCellInventory();
            if (cell instanceof ECOStorageCell storageCell) storageCell.getMigrationStacks(restored);
            else if (cell instanceof cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalStorageCell universal) {
                universal.getMigrationStacks(restored);
            } else if (cell != null) cell.getAvailableStacks(restored);
        }
        return restored;
    }

    private long insertForRestore(
        IECOStorageCell cell,
        AEKey key,
        long amount,
        Actionable mode,
        IActionSource source
    ) {
        if (cell instanceof ECOStorageCell storageCell) {
            return storageCell.insertForMigration(key, amount, mode);
        }
        if (cell instanceof cn.dancingsnow.neoecoae.integration.ae2omnicells.ECOUniversalStorageCell universal) {
            return universal.insertForMigration(key, amount, mode, source);
        }
        return cell.insert(key, amount, mode, source);
    }

    private UUID migrationTransactionId(UUID domainId, ECODriveBlockEntity drive, AEKey key, long amount, String direction) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        UUID restore = engine == null ? null : engine.restoreTransaction(key);
        if ("from-domain".equals(direction) && restore != null) {
            UUID identity = ECOInfiniteStorageMember.identity(drive.getCellStack());
            drive.setChanged();
            return UUID.nameUUIDFromBytes((restore + ":" + identity).getBytes(StandardCharsets.UTF_8));
        }
        String value = domainId + ":" + direction + ":" + drive.getBlockPos().asLong() + ":"
            + key.toTagGeneric(level.registryAccess()) + ":" + amount;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private void exitInfiniteModeIfSafe() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !engine.canExitOrRestore() || !engine.isEmpty()) {
            return;
        }
        if (!engine.commit().successful()) return;
        UUID domainId = infiniteDomainId;
        if (cluster != null && domainId != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), domainId)) {
                    drive.convertInfiniteMemberToNormalStorage(domainId);
                    IStorageProvider.requestUpdate(drive.getMainNode());
                }
            }
        }
        hostMode = formed ? ECOStorageHostMode.FORMED_NORMAL : ECOStorageHostMode.UNFORMED;
        if (level instanceof ServerLevel serverLevel && domainId != null) {
            // Journal receipts remain as ownership tombstones for stale source chunks.
            engine.clearMigrationReceipts();
            engine.commit();
            ECOInfiniteStorageDomains.release(serverLevel.getServer(), domainId);
        }
        infiniteDomainId = null;
        refreshDriveStorageProviders();
        setChanged();
        markForUpdate();
    }

    private UUID ensureInfiniteDomainId() {
        if (infiniteDomainId == null) {
            infiniteDomainId = UUID.randomUUID();
            setChanged();
        }
        return infiniteDomainId;
    }

    @Override
    protected void onMainNodeGridChanged() {
        // The scheduler captures the grid inventory and optional source bindings; rebuild both for a new epoch.
        resetFiniteTransferScheduler();
    }

    @Override
    public void updateCluster(@Nullable NEStorageCluster nextCluster) {
        if (nextCluster == null && finiteTransferDomain != null) {
            materializeFiniteTransferDomain();
        }
        super.updateCluster(nextCluster);
    }

    @Override
    public void onChunkUnloaded() {
        materializeFiniteTransferDomain();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        materializeFiniteTransferDomain();
        super.setRemoved();
    }

    @Nullable
    private ECOInfiniteStorageEngine getInfiniteEngine() {
        if (!(level instanceof ServerLevel serverLevel) || infiniteDomainId == null) {
            return null;
        }
        return ECOInfiniteStorageDomains.get(serverLevel, infiniteDomainId);
    }

    private static boolean isInfiniteComponent(ItemStack stack) {
        return !stack.isEmpty() && stack.is(NETags.Items.INFINITE_CELL_COMPONENTS);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        net.minecraft.nbt.ListTag halted = new net.minecraft.nbt.ListTag();
        halted.addAll(unresolvedHaltedKeys);
        for (AEKey key : haltedTransferKeys) {
            try { halted.add(key.toTagGeneric(registries)); }
            catch (RuntimeException e) { unresolvedTransferHalt = true; }
        }
        data.put("haltedStorageTransfers", halted);
        data.putBoolean("unresolvedStorageTransfer", unresolvedTransferHalt);
        data.putString("infiniteHostMode", hostMode.id());
        if (infiniteDomainId != null) {
            data.putUUID("infiniteDomainId", infiniteDomainId);
        }
        if (finiteTransferDomain != null) {
            data.put(FINITE_TRANSFER_DOMAIN_TAG, finiteTransferDomain.save(registries));
        } else if (pendingFiniteTransferDomain != null) {
            data.put(FINITE_TRANSFER_DOMAIN_TAG, pendingFiniteTransferDomain.copy());
        }
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        haltedTransferKeys.clear();
        unresolvedHaltedKeys.clear();
        unresolvedTransferHalt = data.getBoolean("unresolvedStorageTransfer");
        for (var raw : data.getList("haltedStorageTransfers", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            try {
                AEKey key = AEKey.fromTagGeneric(registries, (CompoundTag) raw);
                if (key == null) { unresolvedTransferHalt = true; unresolvedHaltedKeys.add(raw.copy()); }
                else haltedTransferKeys.add(key);
            } catch (RuntimeException e) { unresolvedTransferHalt = true; unresolvedHaltedKeys.add(raw.copy()); }
        }
        loadLegacyInfiniteComponentInventory(data, registries);
        hostMode = ECOStorageHostMode.fromId(data.getString("infiniteHostMode"));
        infiniteDomainId = data.hasUUID("infiniteDomainId") ? data.getUUID("infiniteDomainId") : null;
        pendingFiniteTransferDomain = data.contains(FINITE_TRANSFER_DOMAIN_TAG, net.minecraft.nbt.Tag.TAG_COMPOUND)
            ? data.getCompound(FINITE_TRANSFER_DOMAIN_TAG).copy()
            : null;
        finiteDomainRestoreFailed = false;
    }

    private void loadLegacyInfiniteComponentInventory(CompoundTag data, HolderLookup.Provider registries) {
        if (!infiniteComponentInventory.getStackInSlot(0).isEmpty()) {
            return;
        }
        CompoundTag managed = data.getCompound("managed");
        if (!managed.contains(LEGACY_COMPONENT_INVENTORY_PERSIST_KEY)) {
            return;
        }
        infiniteComponentInventory.readFromNBT(
            managed.getCompound(LEGACY_COMPONENT_INVENTORY_PERSIST_KEY),
            "inventory",
            registries
        );
    }

    public boolean canExtractInfiniteComponents() {
        return blockedInfiniteComponentExtractionReason() == null;
    }

    @Nullable
    public String blockedInfiniteComponentExtractionReason() {
        ItemStack stack = infiniteComponentInventory.getStackInSlot(0);
        if (!hasRequiredInfiniteComponents(stack) || !hostMode.isInfiniteState()) {
            return null;
        }
        long tick = level == null ? 0L : level.getGameTime();
        if (extractionCheckTick == Long.MIN_VALUE || tick - extractionCheckTick >= 20L) {
            extractionCheckTick = tick;
            RestorePlan plan = createInfiniteRestorePlan(true);
            extractionCheckReason = plan.canRestore() ? null : plan.reason();
        }
        return extractionCheckReason;
    }

    private static final class RestoreTarget {
        private final ECODriveBlockEntity drive;
        private final IECOStorageCell simulatedCell;
        private final KeyCounter simulatedContents;
        private final UUID identity;
        private long simulatedTypes;
        private long simulatedAmount;
        private RestoreTarget(ECODriveBlockEntity drive, IECOStorageCell cell, KeyCounter contents) {
            this.drive = drive;
            this.simulatedCell = cell;
            this.simulatedContents = contents;
            this.identity = ECOInfiniteStorageMember.identity(drive.getCellStack());
            drive.setChanged();
            for (var entry : contents) {
                if (entry.getLongValue() > 0L) {
                    simulatedTypes++;
                    simulatedAmount = NEMath.saturatingAdd(simulatedAmount, entry.getLongValue());
                }
            }
        }
        private ECODriveBlockEntity drive() { return drive; }
        private IECOStorageCell simulatedCell() { return simulatedCell; }
        private KeyCounter simulatedContents() { return simulatedContents; }
        private void addSimulated(AEKey key, long amount) {
            if (simulatedContents.get(key) == 0L) simulatedTypes++;
            simulatedAmount = NEMath.saturatingAdd(simulatedAmount, amount);
            simulatedContents.add(key, amount);
        }
    }

    private record RestorePlan(boolean canRestore, List<RestoreTarget> targets, String reason) {
        private static RestorePlan allowed(List<RestoreTarget> targets) {
            return new RestorePlan(true, List.copyOf(targets), "");
        }

        private static RestorePlan blocked(String reason) {
            return new RestorePlan(false, List.of(), reason);
        }
    }

    private final class InfiniteComponentItemHandler implements IItemHandlerModifiable {
        private final IItemHandlerModifiable delegate;

        private InfiniteComponentItemHandler(IItemHandlerModifiable delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot == 0) {
                ItemStack current = delegate.getStackInSlot(slot);
                if (hostMode.isInfiniteState()
                    && hasRequiredInfiniteComponents(current)
                    && !hasRequiredInfiniteComponents(stack)) {
                    RestorePlan plan = createInfiniteRestorePlan(true);
                    if (!plan.canRestore()) {
                        return;
                    }
                    infiniteExitRequested = true;
                    setChanged();
                    restoreInfiniteDomainToNormalStorage(plan);
                    if (hostMode.isInfiniteState()) {
                        return;
                    }
                }
            }
            delegate.setStackInSlot(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = delegate.getStackInSlot(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            if (!hostMode.isInfiniteState() || !hasRequiredInfiniteComponents(stack)) {
                return delegate.extractItem(slot, amount, simulate);
            }

            RestorePlan plan = createInfiniteRestorePlan(true);
            if (!plan.canRestore()) {
                return ItemStack.EMPTY;
            }
            if (!simulate) {
                infiniteExitRequested = true;
                setChanged();
                restoreInfiniteDomainToNormalStorage(plan);
                if (hostMode.isInfiniteState()) {
                    return ItemStack.EMPTY;
                }
            }
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }
    }

    @Override
    public @Nullable MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getStorageSystemDefinition(tier);
    }

    @Override
    public int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    @Override
    public int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    @Override
    public boolean canPlayerInteract(Player player) {
        return level != null && ECOStorageSystemBlock.isPlayerCloseEnough(level, worldPosition, player);
    }

    @Override
    public Level getBuildLevel() { return level; }

    @Override
    public BlockPos getBuildPosition() { return worldPosition; }

    @Override
    public BlockState getBuildState() { return getBlockState(); }

    @Override
    public int getSelectedBuildLength() { return selectedBuildLength; }

    @Override
    public void setSelectedBuildLength(int length) { selectedBuildLength = length; }

    @Override
    public boolean isMirrorBuild() { return mirrorBuild; }

    @Override
    public void setMirrorBuild(boolean mirrorBuild) { this.mirrorBuild = mirrorBuild; }

    @Override
    public boolean isBuildInProgress() { return buildInProgress; }

    @Override
    public void setBuildInProgress(boolean buildInProgress) { this.buildInProgress = buildInProgress; }

    @Override
    public boolean isFormed() { return formed; }

    @Override
    public void rebuildAfterBuild() { rebuildMultiblock(); }

    @Override
    public void buildStateChanged() {
        setChanged();
        markForUpdate();
    }
}
