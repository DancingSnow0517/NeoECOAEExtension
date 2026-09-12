package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.NeoECOAE;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostActionUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageMegaPanelUI;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.impl.storage.StorageByteAccounting;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOFiniteStorageDomain;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageSourceSafety;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageSourceAdapterRegistry;
import cn.dancingsnow.neoecoae.impl.storage.transfer.ECOTransferScheduler;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageData;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageTransfer;
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
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RPCMethod;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation ECO_MEGA_BULK_CELL_ID = NeoECOAE.id("eco_mega_long_bulk_cell");
    private static final ResourceLocation ECO_MEGA_UPGRADE_CARD_ID = NeoECOAE.id("eco_mega_upgrade_card");
    private static final int ECO_MEGA_SLOTS_PER_PAGE = 25;
    private static final int ECO_MEGA_PAGE_COUNT = 2;

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
    private final IItemHandlerModifiable ecoMegaUpgradeItemHandler = new EcoMegaUpgradeItemHandler();
    private final IItemHandlerModifiable ecoMegaFilterItemHandler = new EcoMegaFilterItemHandler();
    @DescSynced
    private int selectedEcoMegaBulkCell;
    @DescSynced
    private int selectedEcoMegaPage;
    @DescSynced
    private boolean buildInProgress;
    private final MultiBlockBuildController buildController = new MultiBlockBuildController(this);
    private transient StorageUiSnapshot storageUiSnapshot = StorageUiSnapshot.EMPTY;
    private transient long storageUiSnapshotGameTime = Long.MIN_VALUE;
    private long storageUiRevision = Long.MIN_VALUE;
    private long extractionCheckTick = Long.MIN_VALUE;
    private String extractionCheckReason;
    private final Map<ECODriveBlockEntity, DriveUiSnapshot> driveUiSnapshots = new HashMap<>();
    private record DriveUiSnapshot(IECOStorageCell inventory, long revision, long tick, int type, int tier,
        List<AEKeyType> keyTypes, boolean member, long usedTypes, long totalTypes, long usedBytes, long totalBytes,
        boolean infiniteResource) {}
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
    @Nullable
    private transient MinecraftServer mountedInfiniteServer;
    @Nullable
    private transient UUID mountedInfiniteDomainId;
    @Nullable
    private transient ECOInfiniteStorageEngine mountedInfiniteEngine;
    private final ECOInfiniteStorageTransfer infiniteTransfer = new ECOInfiniteStorageTransfer();
    private final java.util.Set<UUID> durableInfiniteSourceSeals = new java.util.HashSet<>();
    private int migrationDriveCursor;
    private RestorePlan activeRestorePlan;
    private final java.util.ArrayDeque<AEKey> restoreQueue = new java.util.ArrayDeque<>();
    private long currentStorageBudget = STORAGE_INTERFACE_TRANSFER_NANOS_PER_TICK;
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
    private transient boolean finiteDomainLeaseDurable;
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
            if (!runStorageStage("migration", this::updateInfiniteStorageMode)) return;
            ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
            if (storageInterface != null) {
                if (!runStorageStage("transfer", () -> {
                    updateFiniteTransferDomain(storageInterface);
                    storageInterface.recordStorageInterfaceTransfer(transferStorageInterfaceContents(storageInterface));
                })) return;
            } else if (finiteTransferDomain != null) {
                if (!runStorageStage("materialization", this::materializeFiniteTransferDomain)) return;
            }
            runStorageStage("construction", () -> buildController.tick(level));
        } finally {
            long elapsed = System.nanoTime() - startNanos;
            cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageTickBudget.spent(server, elapsed);
            recordPerformanceSample(elapsed);
        }
    }

    private boolean runStorageStage(String stage, Runnable action) {
        long tick = level == null ? 0L : level.getGameTime();
        if (tick < stageRetryTicks.getOrDefault(stage, Long.MIN_VALUE)) return false;
        if (currentStorageBudget <= 0L) return false;
        long start = System.nanoTime();
        try {
            action.run();
            storageFaults.recovered(stage);
            return true;
        } catch (RuntimeException e) {
            stageRetryTicks.put(stage, tick + 200L);
            storageFaults.report(stage, worldPosition + ": " + e, tick, e);
            return false;
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

        UIElement root = StorageHostUI.create(new StorageHostUI.Config(
            () -> getItemFromBlockEntity().getDescription(),
            this::getStoredEnergy,
            this::getMaxEnergy,
            this::getEnergyConsumePerTick,
            this::getTotalUsedBytesText,
            () -> getStorageUiSnapshot().cellEntries(),
            createStorageTypeLines(),
            this::isFormedInfiniteMode,
            this::isMigratingToInfinite,
            this::canExtractInfiniteComponents,
            infiniteComponentItemHandler
        ));
        root.addChild(StorageMegaPanelUI.create(
            this,
            ecoMegaUpgradeItemHandler,
            ecoMegaFilterItemHandler,
            actionUI.bulkMarkingButton()
        ));
        actionUI.addTo(root);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private List<StorageHostUI.StorageTypeLine> createStorageTypeLines() {
        return NERegistries.CELL_TYPE.stream()
            .map(cellType -> {
                int id = NERegistries.CELL_TYPE.getId(cellType);
                return new StorageHostUI.StorageTypeLine(
                    cellType,
                    id,
                    () -> getStorageValue(id, StorageValue.USED_TYPES),
                    () -> getStorageValue(id, StorageValue.TOTAL_TYPES),
                    () -> getStorageValue(id, StorageValue.USED_BYTES),
                    () -> getStorageValue(id, StorageValue.TOTAL_BYTES),
                    () -> getStorageUiSnapshot().storageTypeTotals(id).infiniteBytesText()
                );
            })
            .toList();
    }

    public boolean hasEcoMegaUpgradeCard() {
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        int driveIndex = getSelectedEcoMegaBulkCell();
        return driveIndex >= 0 && driveIndex < drives.size()
            && hasEcoMegaUpgradeCard(drives.get(driveIndex).getCellStack());
    }

    private boolean hasEcoMegaUpgradeCard(@Nullable ItemStack cellStack) {
        if (cellStack == null || cellStack.isEmpty()
            || !(cellStack.getItem() instanceof cn.dancingsnow.neoecoae.items.ECOStorageCellItem cellItem)) {
            return false;
        }
        for (ItemStack upgrade : cellItem.getUpgrades(cellStack)) {
            if (!upgrade.isEmpty()
                && ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(upgrade.getItem()))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private appeng.api.upgrades.IUpgradeInventory getSelectedEcoMegaUpgradeInventory() {
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        int driveIndex = getSelectedEcoMegaBulkCell();
        if (driveIndex < 0 || driveIndex >= drives.size()) {
            return null;
        }
        ItemStack cellStack = drives.get(driveIndex).getCellStack();
        if (cellStack == null || cellStack.isEmpty()
            || !(cellStack.getItem() instanceof cn.dancingsnow.neoecoae.items.ECOStorageCellItem cellItem)) {
            return null;
        }
        return cellItem.getUpgrades(cellStack);
    }

    private void onSelectedEcoMegaUpgradeChanged() {
        if (!hasEcoMegaUpgradeCard()) {
            selectedEcoMegaPage = 0;
        }
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        int driveIndex = getSelectedEcoMegaBulkCell();
        if (driveIndex >= 0 && driveIndex < drives.size()) {
            drives.get(driveIndex).onCellConfigurationChanged();
        }
        notifyStorageConfigurationChanged();
        setChanged();
        markForUpdate();
    }

    public boolean hasEcoMegaBulkCell() {
        return !getEcoMegaBulkDrives().isEmpty();
    }

    public int getEcoMegaBulkCellCount() {
        return getEcoMegaBulkDrives().size();
    }

    public int getSelectedEcoMegaBulkCell() {
        int count = getEcoMegaBulkCellCount();
        selectedEcoMegaBulkCell = count == 0 ? 0 : Math.clamp(selectedEcoMegaBulkCell, 0, count - 1);
        return selectedEcoMegaBulkCell;
    }

    public int getSelectedEcoMegaPage() {
        if (!hasEcoMegaUpgradeCard()) {
            selectedEcoMegaPage = 0;
        }
        return selectedEcoMegaPage;
    }

    public void changeSelectedEcoMegaBulkCell(int delta) {
        int count = getEcoMegaBulkCellCount();
        if (count <= 0) {
            selectedEcoMegaBulkCell = 0;
            selectedEcoMegaPage = 0;
            return;
        }
        selectedEcoMegaBulkCell = Math.floorMod(getSelectedEcoMegaBulkCell() + delta, count);
        selectedEcoMegaPage = 0;
        setChanged();
        markForUpdate();
    }

    public void changeSelectedEcoMegaPage(int delta) {
        if (!hasEcoMegaUpgradeCard()) {
            selectedEcoMegaPage = 0;
            return;
        }
        selectedEcoMegaPage = Math.floorMod(getSelectedEcoMegaPage() + delta, ECO_MEGA_PAGE_COUNT);
        setChanged();
        markForUpdate();
    }

    public void setEcoMegaFilterFromClient(int visualSlot, ItemStack stack) {
        int driveIndex = getSelectedEcoMegaBulkCell();
        int page = getSelectedEcoMegaPage();
        if (level != null && level.isClientSide) {
            rpcToServer("setEcoMegaFilter", driveIndex, page, visualSlot, stack);
            return;
        }
        setEcoMegaFilterDirect(driveIndex, page, visualSlot, stack);
    }

    @RPCMethod
    public void setEcoMegaFilter(
        RPCSender sender,
        int driveIndex,
        int page,
        int visualSlot,
        ItemStack stack
    ) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        ServerPlayer player = sender.asPlayer();
        if (player == null || player.level() != serverLevel || !canPlayerInteract(player)) {
            return;
        }
        EcoMegaFilterResult result = setEcoMegaFilterDirect(driveIndex, page, visualSlot, stack);
        if (result == EcoMegaFilterResult.NOT_COMPRESSIBLE) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "gui.neoecoae.storage.mega_filter.not_compressible"), true);
        } else if (result == EcoMegaFilterResult.DUPLICATE_CHAIN) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "gui.neoecoae.storage.mega_filter.duplicate_chain"), true);
        }
    }

    private EcoMegaFilterResult setEcoMegaFilterDirect(int driveIndex, int page, int visualSlot, ItemStack stack) {
        if (visualSlot < 0 || visualSlot >= ECO_MEGA_SLOTS_PER_PAGE
            || page < 0 || page >= ECO_MEGA_PAGE_COUNT) {
            return EcoMegaFilterResult.INVALID_TARGET;
        }
        List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
        if (driveIndex < 0 || driveIndex >= drives.size()
            || page > 0 && !hasEcoMegaUpgradeCard(drives.get(driveIndex).getCellStack())) {
            return EcoMegaFilterResult.INVALID_TARGET;
        }
        ItemStack normalized = ItemStack.EMPTY;
        if (stack != null && !stack.isEmpty()) {
            normalized = StorageBulkMarkingIntegration.normalizeMarker(stack);
            if (normalized.isEmpty()) {
                return EcoMegaFilterResult.NOT_COMPRESSIBLE;
            }
            if (hasDuplicateEcoMegaMarker(drives, driveIndex, page, visualSlot, normalized)) {
                return EcoMegaFilterResult.DUPLICATE_CHAIN;
            }
        }
        ECODriveBlockEntity drive = drives.get(driveIndex);
        ItemStack cellStack = drive.getCellStack();
        if (cellStack == null || cellStack.isEmpty()
            || !(cellStack.getItem() instanceof cn.dancingsnow.neoecoae.items.ECOStorageCellItem cellItem)) {
            return EcoMegaFilterResult.INVALID_TARGET;
        }
        AEItemKey key = normalized.isEmpty() ? null : AEItemKey.of(normalized);
        cellItem.getConfigInventory(cellStack).setStack(
            page * ECO_MEGA_SLOTS_PER_PAGE + visualSlot,
            key == null ? null : new GenericStack(key, 0L)
        );
        drive.onCellConfigurationChanged();
        notifyStorageConfigurationChanged();
        return EcoMegaFilterResult.SUCCESS;
    }

    private enum EcoMegaFilterResult {
        SUCCESS,
        NOT_COMPRESSIBLE,
        DUPLICATE_CHAIN,
        INVALID_TARGET
    }

    private boolean hasDuplicateEcoMegaMarker(
        List<ECODriveBlockEntity> drives,
        int driveIndex,
        int page,
        int visualSlot,
        ItemStack candidate
    ) {
        for (int index = 0; index < drives.size(); index++) {
            ItemStack cellStack = drives.get(index).getCellStack();
            if (cellStack == null || cellStack.isEmpty()
                || !(cellStack.getItem() instanceof cn.dancingsnow.neoecoae.items.ECOStorageCellItem cellItem)) {
                continue;
            }
            var config = cellItem.getConfigInventory(cellStack);
            int activeSlots = hasEcoMegaUpgradeCard(cellStack)
                ? ECO_MEGA_SLOTS_PER_PAGE * ECO_MEGA_PAGE_COUNT
                : ECO_MEGA_SLOTS_PER_PAGE;
            for (int slot = 0; slot < Math.min(config.size(), activeSlots); slot++) {
                if (index == driveIndex
                    && slot == page * ECO_MEGA_SLOTS_PER_PAGE + visualSlot) {
                    continue;
                }
                AEKey configured = config.getKey(slot);
                if (configured instanceof AEItemKey itemKey
                    && StorageBulkMarkingIntegration.isSameMarkerChain(candidate, itemKey.toStack())) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<ECODriveBlockEntity> getEcoMegaBulkDrives() {
        return getStorageDrivesForIntegration().stream()
            .filter(drive -> {
                ItemStack stack = drive.getCellStack();
                return stack != null && !stack.isEmpty()
                    && ECO_MEGA_BULK_CELL_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            })
            .sorted(java.util.Comparator.comparingLong(drive -> drive.getBlockPos().asLong()))
            .toList();
    }

    private String getTotalUsedBytesText() {
        StorageUiSnapshot snapshot = getStorageUiSnapshot();
        BigInteger used = BigInteger.ZERO;
        if (isFormedInfiniteMode()) {
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
        storageMounts.mount(
            new ECOInfiniteStorage(engine, getBlockState().getBlock().getName(), this::canInsertIntoInfiniteDomain),
            storagePriority
        );
    }

    @SuppressWarnings("UnstableApiUsage")
    private long getStoredEnergy() {
        return getStorageUiSnapshot().storedEnergy();
    }

    @SuppressWarnings("UnstableApiUsage")
    private long getMaxEnergy() {
        return getStorageUiSnapshot().maxEnergy();
    }

    private long getEnergyConsumePerTick() {
        return getStorageUiSnapshot().energyConsumePerTick();
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

        long energyConsumePerTick = 256L + (1L << (1 + 4 * tier.getTier()));
        Map<Integer, StorageTypeTotals> storageTypes = new HashMap<>();
        Map<AEKeyType, Integer> cellTypesByKeyType = new HashMap<>();
        Map<Integer, Long> infiniteCapacityByCellType = new HashMap<>();
        List<StorageHostUI.CellEntry> cellEntries = new ArrayList<>();
        driveUiSnapshots.keySet().retainAll(cluster.getDrives());
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            DriveUiSnapshot view = driveUiSnapshot(drive);
            if (view == null) continue;
            if (view.infiniteResource()) continue;
            int cellTypeId = view.type();
            for (AEKeyType keyType : view.keyTypes()) cellTypesByKeyType.putIfAbsent(keyType, cellTypeId);
            boolean supported = view.inventory() != null && tier.compareTo(view.inventory().getTier()) >= 0;
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
                if (cellTypeId >= 0) {
                    infiniteCapacityByCellType.merge(
                        cellTypeId, Math.max(0L, view.totalBytes()), NEMath::saturatingAdd);
                }
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
        if (isFormedInfiniteMode()) {
            addInfiniteStorageTypes(storageTypes, cellTypesByKeyType, infiniteCapacityByCellType);
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
        long tick = level.getGameTime();
        String component = "drive statistics " + drive.getBlockPos();
        try {
            IECOStorageCell inventory = drive.getCellInventory();
            if (inventory == null) { driveUiSnapshots.remove(drive); return null; }
            long revision = inventory instanceof ECOStorageCell cell ? cell.contentRevision() : -1L;
            boolean member = isInfiniteMemberCell(drive.getCellStack());
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
            storageFaults.recovered(component);
            return next;
        } catch (RuntimeException e) {
            storageFaults.report(component, e.toString(), tick, e);
            return previous;
        }
    }

    private void addInfiniteStorageTypes(
        Map<Integer, StorageTypeTotals> storageTypes,
        Map<AEKeyType, Integer> cellTypesByKeyType,
        Map<Integer, Long> capacityByCellType
    ) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            return;
        }
        capacityByCellType.forEach((cellTypeId, capacity) -> storageTypes.merge(
            cellTypeId,
            new StorageTypeTotals(0L, 0L, 0L, capacity),
            StorageTypeTotals::add
        ));
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
            delta -> changeStoragePriority(holder.player, delta),
            StorageBulkMarkingIntegration::isAvailable,
            () -> NEConfig.megaBulkAutoMarkThreshold,
            () -> autoMarkBulkCells(holder.player)
        ));
    }

    private void autoMarkBulkCells(Player player) {
        if (!canPlayerInteract(player)) {
            return;
        }
        StorageBulkMarkingIntegration.MarkResult result = StorageBulkMarkingIntegration.autoMark(
            this, NEConfig.megaBulkAutoMarkThreshold);
        String key = switch (result.status()) {
            case SUCCESS -> "gui.neoecoae.storage.bulk_mark.result.success";
            case NO_BULK_CELL -> "gui.neoecoae.storage.bulk_mark.result.no_bulk_cell";
            case BUSY -> "gui.neoecoae.storage.bulk_mark.result.busy";
            case INVALID_THRESHOLD -> "gui.neoecoae.storage.bulk_mark.result.invalid_threshold";
            case UNAVAILABLE -> "gui.neoecoae.storage.bulk_mark.result.unavailable";
        };
        net.minecraft.network.chat.Component message = result.status() == StorageBulkMarkingIntegration.Status.SUCCESS
            ? net.minecraft.network.chat.Component.translatable(
                key, result.added(), result.alreadyMarked(), result.noSpace(), result.transferred())
            : net.minecraft.network.chat.Component.translatable(key);
        player.displayClientMessage(message, true);
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

    /** Read-only local-storage snapshot for optional integrations. */
    public KeyCounter collectLocalStorageStacksForIntegration() {
        KeyCounter result = new KeyCounter();
        MEStorage storage = getStorageInterfaceHostStorage();
        if (storage != null) {
            storage.getAvailableStacks(result);
        }
        return result;
    }

    /** Stable snapshot of the drives belonging to this storage host. */
    public List<ECODriveBlockEntity> getStorageDrivesForIntegration() {
        return cluster == null ? List.of() : List.copyOf(cluster.getDrives());
    }

    /** Invalidates host UI data and refreshes AE2 mounts after an integration changes cell configuration. */
    public void notifyStorageConfigurationChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        setChanged();
        markForUpdate();
        refreshDriveStorageProviders();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        if (!materializeFiniteTransferDomain()) {
            LOGGER.error("Refusing to release storage-controller contents at {} while finite recovery is unresolved", pos);
            return;
        }
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
        releaseMountedInfiniteEngine();
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
        if (hostMode == ECOStorageHostMode.FORMED_INFINITE) {
            return 100;
        }
        if (hostMode != ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            return 0;
        }
        int migrated = countInfiniteMembers();
        int totalTargets = migrated + countPendingInfiniteMigrationTargets();
        return totalTargets == 0
            ? 100
            : Math.clamp(Math.round(migrated * 100.0F / totalTargets), 0, 100);
    }

    public boolean canUseHostDomainStorage() {
        return formed && hostMode.isInfiniteState() && infiniteDomainId != null;
    }

    public boolean canInsertIntoInfiniteDomain() {
        return formed && hostMode == ECOStorageHostMode.FORMED_INFINITE && infiniteDomainId != null
            && !infiniteExitRequested && activeRestorePlan == null
            && (mountedInfiniteEngine == null || !mountedInfiniteEngine.hasPendingRestore());
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
        long tick = level == null ? 0L : level.getGameTime();
        if (tick < stageRetryTicks.getOrDefault("materialization", Long.MIN_VALUE)) return false;
        resetFiniteTransferScheduler();
        if (!finiteTransferDomain.materializePhaseA()) {
            LOGGER.error("Unable to materialize finite storage transfer domain at {}; drives remain locked", worldPosition);
            stageRetryTicks.put("materialization", NEMath.saturatingAdd(tick, 200L));
            setChanged();
            return false;
        }
        // Phase A must make both the MATERIALIZING recovery snapshot and every Drive component durable before the
        // controller is allowed to relinquish recovery ownership.
        setChanged();
        try {
            if (level instanceof ServerLevel serverLevel) serverLevel.getChunkSource().save(true);
        } catch (RuntimeException e) {
            LOGGER.error("Unable to persist finite storage handoff at {}; recovery lease retained", worldPosition, e);
            stageRetryTicks.put("materialization", NEMath.saturatingAdd(tick, 200L));
            return false;
        }
        if (!finiteTransferDomain.verifyMaterialized()) {
            LOGGER.error("Unable to verify finite storage transfer domain at {}; recovery lease retained", worldPosition);
            stageRetryTicks.put("materialization", NEMath.saturatingAdd(tick, 200L));
            return false;
        }
        finiteTransferDomain = null;
        pendingFiniteTransferDomain = null;
        finiteDomainRestoreFailed = false;
        finiteDomainLeaseDurable = false;
        stageRetryTicks.remove("materialization");
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
                getBlockState().getBlock().getName(), actionSource, pendingFiniteTransferDomain);
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
                    ECOFiniteStorageDomain.RestoreResult result = finiteTransferDomain.restore(
                        pendingFiniteTransferDomain, level.registryAccess(), actionSource);
                    pendingFiniteTransferDomain = null;
                    if (result == ECOFiniteStorageDomain.RestoreResult.ALREADY_MATERIALIZED) {
                        finiteTransferDomain = null;
                        finiteDomainRestoreFailed = false;
                        refreshDriveStorageProviders();
                        setChanged();
                        return;
                    }
                    finiteDomainLeaseDurable = true;
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
            if (pendingFiniteTransferDomain == null && !finiteDomainLeaseDurable) {
                try {
                    if (level instanceof ServerLevel serverLevel) serverLevel.getChunkSource().save(true);
                    finiteDomainLeaseDurable = true;
                } catch (RuntimeException e) {
                    LOGGER.error("Unable to persist finite storage ownership lease at {}; transfer remains disabled",
                        worldPosition, e);
                    return;
                }
            }
        }
        if (!finiteDomainLeaseDurable && !finiteDomainRestoreFailed) {
            try {
                setChanged();
                if (level instanceof ServerLevel serverLevel) serverLevel.getChunkSource().save(true);
                finiteDomainLeaseDurable = true;
            } catch (RuntimeException e) {
                LOGGER.error("Unable to persist finite storage ownership lease at {}; will retry", worldPosition, e);
                return;
            }
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
        if (finiteTransferDomain != null && (finiteDomainRestoreFailed || !finiteDomainLeaseDurable)) return 0L;
        IActionSource source = IActionSource.ofMachine(storageInterface);
        long moved;
        if (!isInfiniteMode() && finiteTransferDomain != null && !finiteDomainRestoreFailed
            && finiteDomainLeaseDurable) {
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
                cachedInfiniteStorage = engine == null ? null : new ECOInfiniteStorage(
                    engine,
                    getBlockState().getBlock().getName(),
                    this::canInsertIntoInfiniteDomain
                );
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
            && finiteTransferDomain == null
            && pendingFiniteTransferDomain == null
            && !finiteDomainRestoreFailed
            && !isStorageInterfaceTransferMode()
            && hasRequiredInfiniteComponents()
            && countInfiniteMigrationSources() >= 12
            && !hasForeignInfiniteMembers();
    }

    private int countInfiniteMigrationSources() {
        return countInfiniteMembers() + countPendingInfiniteMigrationTargets();
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

    private int countPendingInfiniteMigrationTargets() {
        if (cluster == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack != null
                && !stack.isEmpty()
                && ECOInfiniteStorageTransfer.isEligible(cell)
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
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            return;
        }
        if (!engine.isHealthy()) {
            return;
        }
        sealInfiniteTransferSources(serverLevel, domainId);
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
                if (stack == null || stack.isEmpty() || !ECOInfiniteStorageTransfer.isEligible(cell)) continue;
                hasPending = true;
                if (!durableInfiniteSourceSeals.contains(ECOInfiniteStorageMember.getMigrationId(stack))) continue;
                migrateDriveToDomain(drive, cell, engine, domainId);
                storageFaults.recovered(stage);
                break;
            } catch (RuntimeException e) {
                hasPending = true;
                stageRetryTicks.put(stage, tick + 200L);
                storageFaults.report(stage, e.toString(), tick, e);
            }
        }
        if (!hasPending) {
            hostMode = ECOStorageHostMode.FORMED_INFINITE;
        }
    }

    private void migrateDriveToDomain(ECODriveBlockEntity drive, IECOStorageCell cell, ECOInfiniteStorageEngine engine, UUID domainId) {
        if (!(cell instanceof IECOStorageMigrationCell migrationCell)) {
            throw new IllegalStateException("Cell handler does not support resumable migration");
        }
        UUID migration = ECOInfiniteStorageMember.beginMigration(drive.getCellStack(), domainId);
        boolean finished = infiniteTransfer.step(drive.getCellStack(), migrationCell, domainId, engine,
            level.registryAccess(), () -> {
                if (!durableInfiniteSourceSeals.contains(migration)) throw new IllegalStateException("Source seal is not durable");
            }, () -> drive.convertCellToInfiniteMember(domainId),
            (key, amount) -> migrationTransactionId(domainId, drive, key, amount, "to-domain"),
            NEConfig.storageTransferKeysPerTick, currentStorageBudget);
        if (!finished) return;
        durableInfiniteSourceSeals.remove(migration);
        IStorageProvider.requestUpdate(drive.getMainNode());
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        setChanged();
        markForUpdate();
    }

    private void sealInfiniteTransferSources(ServerLevel serverLevel, UUID domainId) {
        java.util.Set<UUID> prepared = new java.util.HashSet<>();
        long started = System.nanoTime();
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (prepared.size() >= NEConfig.storageTransferKeysPerTick
                || (!prepared.isEmpty() && System.nanoTime() - started >= currentStorageBudget)) break;
            String stage = "migration drive " + drive.getBlockPos();
            if (level.getGameTime() < stageRetryTicks.getOrDefault(stage, Long.MIN_VALUE)) continue;
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack == null || stack.isEmpty() || ECOInfiniteStorageMember.isMember(stack)
                || !ECOInfiniteStorageTransfer.isEligible(cell)) continue;
            try {
                UUID migration = ECOInfiniteStorageMember.beginMigration(stack, domainId);
                if (durableInfiniteSourceSeals.contains(migration)) continue;
                ((IECOStorageMigrationCell) cell).persistMigrationContents(serverLevel);
                drive.setChanged();
                IStorageProvider.requestUpdate(drive.getMainNode());
                prepared.add(migration);
            } catch (RuntimeException e) {
                stageRetryTicks.put(stage, level.getGameTime() + 200L);
                storageFaults.report(stage, e.toString(), level.getGameTime(), e);
            }
        }
        if (!prepared.isEmpty()) {
            // Save a batch of seals together, rather than saving the dimension separately for every source disk.
            serverLevel.getChunkSource().save(true);
            durableInfiniteSourceSeals.addAll(prepared);
        }
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
            var savedPlan = engine.restorePlan(key);
            if (!savedPlan.isEmpty()) {
                for (RestoreTarget target : targets) {
                    var goal = savedPlan.get(target.identity);
                    if (goal == null) continue;
                    long current = target.simulatedContents().get(key);
                    if (current < goal.before() || current > goal.after()) {
                        return RestorePlan.blocked("restore target contents differ from the saved transfer plan");
                    }
                    target.addSimulated(key, goal.after() - current);
                }
                continue;
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
            if (simulatedCell instanceof IECOStorageMigrationCell
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
        if (cell instanceof IECOStorageMigrationCell migrationCell) {
            return migrationCell.simulateInsertForMigration(
                key, amount, target.simulatedContents(), target.simulatedTypes, target.simulatedAmount);
        }
        // Unknown handlers must still be probed without mutation. Such handlers are allowed to return a conservative
        // capacity; the real restore below remains authoritative and verifies the final aggregate.
        return insertForRestore(cell, key, amount, Actionable.SIMULATE, source);
    }

    private long getUsedBytesForRestore(RestoreTarget target) {
        IECOStorageCell cell = target.simulatedCell();
        if (cell instanceof IECOStorageMigrationCell migrationCell) {
            return migrationCell.getUsedBytesForMigration(target.simulatedContents());
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
        if (activeRestorePlan == null) {
            KeyCounter pending = new KeyCounter();
            engine.getRestoreStacks(pending);
            Map<AEKey, UUID> transactions = new HashMap<>();
            Map<AEKey, Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts>> goals = new HashMap<>();
            for (var entry : pending) {
                AEKey key = entry.getKey();
                UUID transaction = engine.restoreTransaction(key);
                transactions.put(key, transaction == null ? UUID.randomUUID() : transaction);
                var savedPlan = engine.restorePlan(key);
                if (!savedPlan.isEmpty()) {
                    goals.put(key, savedPlan);
                    continue;
                }
                Map<UUID, ECOInfiniteStorageEngine.RestoreTargetAmounts> amounts = new HashMap<>();
                for (RestoreTarget target : plan.targets()) {
                    var cell = (IECOStorageMigrationCell) target.drive().getCellInventory();
                    long before = cell.getMigrationAmount(key);
                    long after = target.simulatedContents().get(key);
                    UUID oldReceipt = migrationTransactionId(infiniteDomainId, target.drive(), key,
                        entry.getLongValue(), "from-domain");
                    long transferred = Math.addExact(target.drive().getRestoreReceipt(oldReceipt), after - before);
                    amounts.put(target.identity, new ECOInfiniteStorageEngine.RestoreTargetAmounts(before, after, transferred));
                }
                goals.put(key, amounts);
            }
            // Freeze all source keys before any target changes. The durable target quantities also make a crash
            // between an external cell's SavedData write and its chunk receipt recoverable without reinsertion.
            if (!engine.reserveRestores(transactions, targetIds, goals)) return;
            restoreQueue.clear();
            for (var entry : pending) restoreQueue.addLast(entry.getKey());
            activeRestorePlan = plan;
            IStorageProvider.requestUpdate(getMainNode());
        }
        while (!restoreQueue.isEmpty() && engine.getRestoreAmount(restoreQueue.peekFirst()).isZero()) restoreQueue.removeFirst();
        Map<AEKey, UUID> completed = new HashMap<>();
        java.util.Set<IECOStorageMigrationCell> changedCells = new java.util.HashSet<>();
        IActionSource source = IActionSource.ofMachine(this);
        long started = System.nanoTime();
        for (AEKey key : restoreQueue) {
            if (completed.size() >= NEConfig.storageTransferKeysPerTick
                || (!completed.isEmpty() && System.nanoTime() - started >= currentStorageBudget)) break;
            var goals = engine.restorePlan(key);
            for (RestoreTarget target : plan.targets()) {
                var goal = goals.get(target.identity);
                if (goal == null) continue;
                var cell = (IECOStorageMigrationCell) target.drive().getCellInventory();
                if (!ECOInfiniteStorageTransfer.restoreTarget(cell, key, goal, source)) {
                    storageFaults.report("restore", "Waiting for compatible target capacity", level.getGameTime());
                    return;
                }
                UUID receipt = migrationTransactionId(infiniteDomainId, target.drive(), key,
                    engine.getRestoreAmount(key).toLongSaturated(), "from-domain");
                target.drive().putRestoreReceipt(receipt, goal.transferred());
                changedCells.add(cell);
            }
            completed.put(key, engine.restoreTransaction(key));
        }
        if (!completed.isEmpty()) {
            for (var cell : changedCells) cell.persistMigrationContents(serverLevel);
            serverLevel.getChunkSource().save(true);
            if (!engine.finishRestores(completed)) return;
            restoreQueue.removeIf(completed::containsKey);
            storageFaults.recovered("restore");
            IStorageProvider.requestUpdate(getMainNode());
        }
        if (restoreQueue.isEmpty()) {
            activeRestorePlan = null;
            exitInfiniteModeIfSafe();
        }
    }

    private long insertForRestore(
        IECOStorageCell cell,
        AEKey key,
        long amount,
        Actionable mode,
        IActionSource source
    ) {
        if (cell instanceof IECOStorageMigrationCell migrationCell) {
            return migrationCell.insertForMigration(key, amount, mode, source);
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
            // Retain transfer receipts: an old source chunk must not import the same inventory again.
            releaseMountedInfiniteEngine();
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
        if (nextCluster == null) {
            releaseMountedInfiniteEngine();
        }
        super.updateCluster(nextCluster);
    }

    @Override
    public void onChunkUnloaded() {
        materializeFiniteTransferDomain();
        releaseMountedInfiniteEngine();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        materializeFiniteTransferDomain();
        releaseMountedInfiniteEngine();
        super.setRemoved();
    }

    @Nullable
    private ECOInfiniteStorageEngine getInfiniteEngine() {
        if (!(level instanceof ServerLevel serverLevel) || infiniteDomainId == null) {
            releaseMountedInfiniteEngine();
            return null;
        }
        MinecraftServer server = serverLevel.getServer();
        if (mountedInfiniteEngine == null || mountedInfiniteServer != server
                || !infiniteDomainId.equals(mountedInfiniteDomainId)) {
            releaseMountedInfiniteEngine();
            mountedInfiniteServer = server;
            mountedInfiniteDomainId = infiniteDomainId;
            mountedInfiniteEngine = ECOInfiniteStorageDomains.acquire(serverLevel, infiniteDomainId);
        }
        return mountedInfiniteEngine;
    }

    private void releaseMountedInfiniteEngine() {
        infiniteTransfer.reset();
        durableInfiniteSourceSeals.clear();
        if (mountedInfiniteServer != null && mountedInfiniteDomainId != null) {
            ECOInfiniteStorageDomains.release(mountedInfiniteServer, mountedInfiniteDomainId);
        }
        mountedInfiniteServer = null;
        mountedInfiniteDomainId = null;
        mountedInfiniteEngine = null;
        cachedStorageEngine = null;
        cachedInfiniteStorage = null;
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
        finiteDomainLeaseDurable = false;
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
            if (amount <= 0L) return;
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

    private final class EcoMegaUpgradeItemHandler implements IItemHandlerModifiable {
        private ItemStack clientDisplayStack = ItemStack.EMPTY;

        private boolean isClientHandler() {
            return level != null && level.isClientSide;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot != 0) {
                return ItemStack.EMPTY;
            }
            if (isClientHandler()) {
                return clientDisplayStack;
            }
            var upgrades = getSelectedEcoMegaUpgradeInventory();
            if (upgrades != null) {
                for (ItemStack stack : upgrades) {
                    if (!stack.isEmpty()
                        && ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                        return stack;
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            if (slot != 0 || !stack.isEmpty()
                && !ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return;
            }
            if (isClientHandler()) {
                clientDisplayStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
                return;
            }
            ItemStack current = getStackInSlot(0);
            if (!current.isEmpty()) {
                extractItem(0, 1, false);
            }
            if (!stack.isEmpty()) {
                insertItem(0, stack.copyWithCount(1), false);
            }
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()
                || !ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                return stack;
            }
            if (isClientHandler()) {
                if (!clientDisplayStack.isEmpty()) {
                    return stack;
                }
                if (!simulate) {
                    clientDisplayStack = stack.copyWithCount(1);
                }
                if (stack.getCount() == 1) {
                    return ItemStack.EMPTY;
                }
                ItemStack remainder = stack.copy();
                remainder.shrink(1);
                return remainder;
            }
            var upgrades = getSelectedEcoMegaUpgradeInventory();
            if (upgrades == null) {
                return stack;
            }
            ItemStack remainder = upgrades.addItems(stack.copyWithCount(1), simulate);
            if (!simulate && remainder.isEmpty()) {
                onSelectedEcoMegaUpgradeChanged();
            }
            if (stack.getCount() <= 1 || !remainder.isEmpty()) {
                return remainder.isEmpty() ? ItemStack.EMPTY : stack;
            }
            ItemStack result = stack.copy();
            result.shrink(1);
            return result;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            if (isClientHandler()) {
                if (clientDisplayStack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                ItemStack extracted = clientDisplayStack.copyWithCount(1);
                if (!simulate) {
                    clientDisplayStack = ItemStack.EMPTY;
                }
                return extracted;
            }
            var upgrades = getSelectedEcoMegaUpgradeInventory();
            if (upgrades == null) {
                return ItemStack.EMPTY;
            }
            for (int upgradeSlot = 0; upgradeSlot < upgrades.size(); upgradeSlot++) {
                ItemStack stack = upgrades.getStackInSlot(upgradeSlot);
                if (!stack.isEmpty()
                    && ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    ItemStack extracted = upgrades.extractItem(upgradeSlot, 1, simulate);
                    if (!simulate && !extracted.isEmpty()) {
                        onSelectedEcoMegaUpgradeChanged();
                    }
                    return extracted;
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == 0 ? 1 : 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && !stack.isEmpty()
                && ECO_MEGA_UPGRADE_CARD_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
    }

    private final class EcoMegaFilterItemHandler implements IItemHandlerModifiable {
        @Override
        public int getSlots() {
            return ECO_MEGA_SLOTS_PER_PAGE;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= ECO_MEGA_SLOTS_PER_PAGE) {
                return ItemStack.EMPTY;
            }
            List<ECODriveBlockEntity> drives = getEcoMegaBulkDrives();
            int driveIndex = getSelectedEcoMegaBulkCell();
            if (driveIndex < 0 || driveIndex >= drives.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack cellStack = drives.get(driveIndex).getCellStack();
            if (cellStack == null || cellStack.isEmpty()
                || !(cellStack.getItem() instanceof cn.dancingsnow.neoecoae.items.ECOStorageCellItem cellItem)) {
                return ItemStack.EMPTY;
            }
            AEKey key = cellItem.getConfigInventory(cellStack).getKey(
                getSelectedEcoMegaPage() * ECO_MEGA_SLOTS_PER_PAGE + slot);
            return key instanceof AEItemKey itemKey ? itemKey.toStack() : ItemStack.EMPTY;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            setEcoMegaFilterDirect(getSelectedEcoMegaBulkCell(), getSelectedEcoMegaPage(), slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 0;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < ECO_MEGA_SLOTS_PER_PAGE && !stack.isEmpty();
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
            if (slot != 0 || (!stack.isEmpty() && !isInfiniteComponent(stack))) {
                return;
            }
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
            if (slot != 0 || stack.isEmpty() || !isInfiniteComponent(stack)) {
                return stack;
            }
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
            return slot == 0 && isInfiniteComponent(stack) && delegate.isItemValid(slot, stack);
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
