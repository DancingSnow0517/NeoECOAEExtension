package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.api.multiblock.ECOMachineKind;
import cn.dancingsnow.neoecoae.api.multiblock.ECOMultiblockDefinitions;
import cn.dancingsnow.neoecoae.api.power.ECOPowerRole;

import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.hooks.ticking.TickHandler;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.api.storage.IECOConfigurableCellDrive;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCellPriorities;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCellPriorities.Target;
import cn.dancingsnow.neoecoae.api.storage.IECOStoragePriorityHost;
import cn.dancingsnow.neoecoae.api.storage.IECOConfigurableCellHost;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostActionUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.menu.HostDiskMenu;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.gui.storage.StorageCellPriorityUI;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.grid.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEStorageClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.annotation.RPCMethod;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

public class ECOStorageSystemBlockEntity extends NEBlockEntity<NEStorageCluster, ECOStorageSystemBlockEntity>
        implements ISyncPersistRPCBlockEntity, InternalInventoryHost, IStorageProvider, MultiBlockBuildController.Host,
        IECOStoragePriorityHost, IECOConfigurableCellHost {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOStorageSystemBlockEntity.class);
    private static final int INFINITE_COMPONENT_REQUIRED = 64;
    private static final long STORAGE_INTERFACE_TRANSFER_NANOS_PER_TICK = 2_000_000L;

    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;

    private static final String INFINITE_COMPONENT_INVENTORY_PERSIST_KEY = "infiniteComponentInventory";
    private static final String LEGACY_COMPONENT_INVENTORY_PERSIST_KEY = "componentInventory";
    private static final String CONTROLLER_DOMAIN_TAG = "neoecoae_infinite_controller_domain";
    private static final String CONTROLLER_CLAIM_TAG = "neoecoae_infinite_controller_claim";
    private static final String PERSISTENT_CLAIM_TAG = "neoecoae:infinite_claim";
    private static final String CURRENT_CLAIM_TAG = "infiniteClaimToken";
    private static final String PENDING_CLAIM_TAG = "pendingInfiniteClaimToken";

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
    private final ECOStorageCellPriorities cellPriorities = new ECOStorageCellPriorities(
        this::getConfigurableCellDrives,
        player -> level != null && player.level() == level && isFormed() && canPlayerInteract(player));
    @DescSynced
    private ECOStorageHostMode hostMode = ECOStorageHostMode.UNFORMED;
    @DescSynced
    @Nullable
    private UUID infiniteDomainId;
    @Nullable
    private UUID infiniteClaimToken;
    @Nullable
    private UUID pendingInfiniteClaimToken;
    private final java.util.Set<UUID> legacyMigrationSources = new java.util.HashSet<>();
    private CompoundTag legacyRoster = new CompoundTag();
    private boolean malformedLegacyRoster;

    @Persisted(key = INFINITE_COMPONENT_INVENTORY_PERSIST_KEY)
    @com.lowdragmc.lowdraglib2.syncdata.annotation.LazyManaged
    // Inventory callbacks mark it dirty on the server; the UI container synchronizes its slot separately.
    private final AppEngInternalInventory infiniteComponentInventory = new AppEngInternalInventory(this, 1, INFINITE_COMPONENT_REQUIRED) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !isInfiniteComponentSlotReadOnly() && isInfiniteComponent(stack) && super.isItemValid(slot, stack);
        }
    };
    // Keep LDLib-managed fields on the block entity; delegate domain-specific behavior and caches.
    private final ECOStorageHostStatistics statistics = new ECOStorageHostStatistics(this);
    private final ECOStorageMegaController megaController = new ECOStorageMegaController(this);
    private final ECOBulkCraftingProvider bulkCrafting = new ECOBulkCraftingProvider(this);
    private final ECOStorageInterfaceTransfer interfaceTransfer = new ECOStorageInterfaceTransfer(this);
    private final ECOInfiniteStorageModeController infiniteModeController =
            new ECOInfiniteStorageModeController(this);
    private final IItemHandlerModifiable infiniteComponentItemHandler = (IItemHandlerModifiable) infiniteComponentInventory.toItemHandler();

    @DescSynced
    private int selectedEcoMegaBulkCell;
    @DescSynced
    private int selectedEcoMegaPage;
    @DescSynced
    private boolean buildInProgress;
    private final MultiBlockBuildController buildController = new MultiBlockBuildController(this);

    private final ECOStorageStageRunner stageRunner = new ECOStorageStageRunner(this);

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
        getMainNode().addService(appeng.api.networking.crafting.ICraftingProvider.class, bulkCrafting);
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
        infiniteModeController.membersChanged();
        applyIdlePower(ECOPowerRole.STORAGE_CONTROLLER, 256 + (1 << (1 + 4 * tier.getTier())), 0);
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
                var interfaceMode = getStorageInterface();
                var mode = interfaceMode == null ? cn.dancingsnow.neoecoae.grid.storage.transfer.ECOStorageInterfaceMode.STORAGE
                        : interfaceMode.getStorageInterfaceMode();
                BlockState newState = state.setValue(ECOStorageSystemBlock.MIRRORED, formed && mirrored)
                        .setValue(ECOStorageSystemBlock.STORAGE_MODE, mode);
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
        try (var cellBatch = cn.dancingsnow.neoecoae.grid.storage.cell.ECOCellMutationBatch.open()) {
            if (!stageRunner.run("domain", infiniteModeController::update)) return;
            ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
            if (storageInterface != null) {
                if (!stageRunner.run("transfer", () -> {
                    storageInterface.recordStorageInterfaceTransfer(interfaceTransfer.transferStorageInterfaceContents(storageInterface));
                })) return;
            }
            stageRunner.run("construction", () -> buildController.tick(level));
            stageRunner.run("bulk_crafting", bulkCrafting::tick);
        } finally {
            long elapsed = System.nanoTime() - startNanos;
            recordPerformanceSample(elapsed);
        }
    }

    public Collection<cn.dancingsnow.neoecoae.grid.storage.StorageFaults.Fault> storageFailures() {
        return stageRunner.failures();
    }

    public String storageDiagnosticText() {
        return storageDiagnostics().getString();
    }

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
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        StorageHostActionUI.Elements actionUI = createActionUI(holder);
        var cellPriorityWindow = new StorageCellPriorityUI(holder.player, cellPriorities);
        int builtInCount = getBuiltInInfiniteComponentCount();
        ItemStack builtInComponents = builtInCount > 0
                ? new ItemStack(NEItems.ECO_INFINITE_CELL_COMPONENT.get(), Math.min(builtInCount, INFINITE_COMPONENT_REQUIRED))
                : ItemStack.EMPTY;

        UIElement root = StorageHostUI.create(new StorageHostUI.Config(
                () -> getItemFromBlockEntity().getDescription(),
                () -> getTier().getTier(),
                this::isFormed,
                statistics::getStoredEnergy,
                statistics::getMaxEnergy,
                statistics::getEnergyConsumePerTick,
                statistics::getTotalUsedBytesText,
                statistics::getCellEntries,
                statistics.createStorageTypeLines(),
                this::isFormedInfiniteMode,
                this::isMigratingToInfinite,
                this::canExtractInfiniteComponents,
                this::getInfiniteDomainText,
                this::getMissingInfiniteMembers,
                infiniteComponentItemHandler,
                holder.player,
                () -> new ItemStack(getBlockState().getBlock()),
                this::getInfiniteMigrationProgressPercent,
                () -> {
                    var block = getInfiniteMigrationBlock();
                    return block == null ? net.minecraft.network.chat.Component.empty() : block.toComponent();
                },
                new StorageHostUI.CellActions(
                    (position, expected) -> markAndMigrateBulkCell(holder.player, position, expected),
                    cellPriorityWindow::openPriority,
                    position -> holder.player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                            "gui.neoecoae.storage.bulk_cell.compression_display.reserved"), true),
                    position -> previewBulkCellMigration(holder.player, position),
                    (position, expected) -> openCellConfiguration(holder.player, position, expected)
                )
        ), this::getTier, this::getBlockState, HostDiskMenu.disks(holder), builtInComponents);
        actionUI.addTo(root);
        root.addChild(cellPriorityWindow.window());
        return ECOModularUI.create(root, holder.player);
    }

    @RPCMethod
    public void selectCellPriority(RPCSender sender, BlockPos position, UUID expected) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel)) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null || player.level() != serverLevel) return;
        cellPriorities.select(player, new Target(position, expected));
    }

    @RPCMethod
    public void setCellPriority(RPCSender sender, BlockPos position, UUID expected, int priority) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel)) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null || player.level() != serverLevel) return;
        cellPriorities.setPriority(player, new Target(position, expected), priority);
    }

    @RPCMethod
    public void changeCellPriority(RPCSender sender, BlockPos position, UUID expected, int delta) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel)) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null || player.level() != serverLevel) return;
        cellPriorities.changePriority(player, new Target(position, expected), delta);
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity getCellWorkbenchBlockEntity() {
        return this;
    }

    private CompoundTag previewBulkCellMigration(Player player, BlockPos position) {
        CompoundTag preview = new CompoundTag();
        if (level == null || level.isClientSide || !canPlayerInteract(player)) return preview;
        for (var drive : getStorageDrivesForIntegration()) {
            if (!drive.getBlockPos().equals(position) || drive.isRemoved()) continue;
            var items = StorageBulkMarkingIntegration.previewMarkAndMigrateCell(
                // A selected-cell action is an explicit migration request; do not hide a
                // recognised chain merely because its current stock is below the global
                // convenience auto-mark threshold.
                this, position, 0L);
            preview.putUUID("cell_id", drive.getCellInteractionId());
            preview.putInt("total", items.size());
            var entries = new net.minecraft.nbt.ListTag();
            items.stream().limit(6).forEach(stack -> entries.add(stack.save(player.registryAccess())));
            preview.put("items", entries);
            return preview;
        }
        return preview;
    }

    private void markAndMigrateBulkCell(Player player, BlockPos position, UUID expected) {
        if (!canPlayerInteract(player)) return;
        // A confirmation belongs to the installation that was previewed, even if the drive was swapped meanwhile.
        if (expected == null || getStorageDrivesForIntegration().stream().noneMatch(drive ->
            !drive.isRemoved() && drive.getBlockPos().equals(position)
                && expected.equals(drive.getCellInteractionId()))) return;
        StorageBulkMarkingIntegration.MarkResult result =
            StorageBulkMarkingIntegration.markAndMigrateCell(
                // The confirmation above previews every positive-stock recognised chain.
                this, position, 0L);
        String key = switch (result.status()) {
            case SUCCESS -> "gui.neoecoae.storage.bulk_mark.result.success";
            case NO_BULK_CELL -> "gui.neoecoae.storage.bulk_mark.result.no_bulk_cell";
            case BUSY -> "gui.neoecoae.storage.bulk_mark.result.busy";
            case INVALID_THRESHOLD -> "gui.neoecoae.storage.bulk_mark.result.invalid_threshold";
            case UNAVAILABLE -> "gui.neoecoae.storage.bulk_mark.result.unavailable";
        };
        player.displayClientMessage(result.status() == StorageBulkMarkingIntegration.Status.SUCCESS
            ? net.minecraft.network.chat.Component.translatable(key, result.added(), result.alreadyMarked(),
                result.noSpace(), result.transferred())
            : net.minecraft.network.chat.Component.translatable(key), true);
    }

    public List<ECODriveBlockEntity> getStorageUiDrives() {
        if (!isFormed() || cluster == null) return List.of();
        var orientation = OrientationStrategies.horizontalFacing();
        var expansion = orientation.getSide(getBlockState(), mirrored ? RelativeSide.RIGHT : RelativeSide.LEFT);
        var top = orientation.getSide(getBlockState(), RelativeSide.TOP);
        return StorageDriveLayout.ordered(cluster.getDrives(), getBlockPos(), expansion, top);
    }

    public boolean hasEcoMegaUpgradeCard() {
        return megaController.hasEcoMegaUpgradeCard();
    }

    public boolean hasEcoMegaBulkCell() {
        return megaController.hasEcoMegaBulkCell();
    }

    public int getEcoMegaBulkCellCount() {
        return megaController.getEcoMegaBulkCellCount();
    }

    public int getSelectedEcoMegaBulkCell() {
        return megaController.getSelectedEcoMegaBulkCell();
    }

    public int getEcoMegaPageCount() {
        return megaController.getEcoMegaPageCount();
    }

    public boolean isEcoMegaFilterSlotAvailable(int visualSlot) {
        return megaController.isEcoMegaFilterSlotAvailable(visualSlot);
    }

    public int getSelectedEcoMegaPage() {
        return megaController.getSelectedEcoMegaPage();
    }

    public void changeSelectedEcoMegaBulkCell(int delta) {
        megaController.changeSelectedEcoMegaBulkCell(delta);
    }

    public void changeSelectedEcoMegaPage(int delta) {
        megaController.changeSelectedEcoMegaPage(delta);
    }

    public void setEcoMegaFilterFromClient(int visualSlot, ItemStack stack) {
        int driveIndex = getSelectedEcoMegaBulkCell();
        int page = getSelectedEcoMegaPage();
        if (level != null && level.isClientSide) {
            rpcToServer("setEcoMegaFilter", driveIndex, page, visualSlot, stack);
            return;
        }
        megaController.setEcoMegaFilterDirect(driveIndex, page, visualSlot, stack);
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
        ECOStorageMegaController.EcoMegaFilterResult result = megaController.setEcoMegaFilterDirect(driveIndex, page, visualSlot, stack);
        if (result == ECOStorageMegaController.EcoMegaFilterResult.NOT_COMPRESSIBLE) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "gui.neoecoae.storage.mega_filter.not_compressible"), true);
        } else if (result == ECOStorageMegaController.EcoMegaFilterResult.DUPLICATE_CHAIN) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "gui.neoecoae.storage.mega_filter.duplicate_chain"), true);
        }
    }

    public void cycleEcoMegaCutoffFromClient(int visualSlot, int delta) {
        if (level != null && level.isClientSide) {
            rpcToServer("cycleEcoMegaCutoff", getSelectedEcoMegaBulkCell(), getSelectedEcoMegaPage(), visualSlot, delta);
        }
    }

    @RPCMethod
    public void cycleEcoMegaCutoff(RPCSender sender, int driveIndex, int page, int visualSlot, int delta) {
        if (sender.isServer() || !(level instanceof ServerLevel serverLevel)) return;
        ServerPlayer player = sender.asPlayer();
        if (player == null || player.level() != serverLevel || !canPlayerInteract(player)) return;
        var selected = megaController.cycleCutoff(driveIndex, page, visualSlot, delta);
        if (selected != null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "gui.neoecoae.storage.mega_filter.cutoff", selected.toStack().getHoverName()), true);
        }
    }

    private net.minecraft.network.chat.Component storageDiagnostics() {
        var text = net.minecraft.network.chat.Component.empty();
        for (var fault : stageRunner.failures()) {
            text.append(fault.component() + " [" + fault.id() + "]\n" + fault.reason() + "\n");
        }
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine instanceof cn.dancingsnow.neoecoae.grid.storage.infinite.persist.SavedDataInfiniteStorageEngine saved) {
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
        infiniteModeController.membersChanged();
        invalidateStorageStatistics();
        saveChanges();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !canUseHostDomainStorage() || isStorageInterfaceTransferMode()) {
            return;
        }
        storageMounts.mount(
                createInfiniteStorageView(engine),
                StoragePriority.mountPriority(storagePriority, false)
        );
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
                this::getBuildDefinition,
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
        setStoragePriority(priority);
    }

    /** {@inheritDoc} */
    @Override
    public void setStoragePriority(int priority) {
        if (level == null || level.isClientSide()) return;
        if (storagePriority == priority) {
            return;
        }
        storagePriority = priority;
        setChanged();
        markForUpdate();
        refreshBulkCraftingPublication();
        refreshDriveStorageProviders();
    }

    void refreshDriveStorageProviders() {
        if (!(level instanceof ServerLevel) || isServerStopping()) return;
        var grids = new java.util.HashSet<appeng.api.networking.IGrid>();
        if (cluster != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                var node = drive.getMainNode();
                if (node.getGrid() != null) grids.add(node.getGrid());
                IStorageProvider.requestUpdate(node);
            }
        }
        if (getMainNode().getGrid() != null) grids.add(getMainNode().getGrid());
        IStorageProvider.requestUpdate(getMainNode());
        grids.forEach(grid -> grid.getStorageService().invalidateCache());
    }

    /**
     * Read-only local-storage snapshot for optional integrations.
     */
    public KeyCounter collectLocalStorageStacksForIntegration() {
        KeyCounter result = new KeyCounter();
        MEStorage storage = interfaceTransfer.getStorageInterfaceHostStorage();
        if (storage != null) {
            storage.getAvailableStacks(result);
        }
        return result;
    }

    /**
     * Stable snapshot of the drives belonging to this storage host.
     */
    public List<ECODriveBlockEntity> getStorageDrivesForIntegration() {
        return cluster == null ? List.of() : cluster.getDrives();
    }

    void bulkDriveChanged(ECODriveBlockEntity drive, boolean removed) {
        if (!(level instanceof ServerLevel) || isServerStopping()) return;
        bulkCrafting.driveChanged(drive, removed);
        bulkCrafting.refreshPublication();
    }

    public void refreshBulkCraftingPublication() {
        if (level instanceof ServerLevel && !isRemoved() && !isServerStopping()) bulkCrafting.refreshPublication();
    }

    void queueBulkCraftingPublication() {
        var grid = getMainNode().getGrid();
        if (grid != null && grid.getService(cn.dancingsnow.neoecoae.api.IECOPatternStorageService.class)
                instanceof cn.dancingsnow.neoecoae.grid.pattern.PatternCatalog catalog) catalog.enqueueBulkPublication(this);
    }

    @Override
    public void onMainNodeStateChanged(appeng.api.networking.IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (level instanceof ServerLevel && !isServerStopping()) queueBulkCraftingPublication();
    }

    /** Drives eligible for the shared cell-priority API; addons may expose their own drive implementations. */
    public List<? extends IECOConfigurableCellDrive> getConfigurableCellDrives() {
        return getStorageDrivesForIntegration();
    }

    /**
     * Invalidates host UI data and refreshes AE2 mounts after an integration changes cell configuration.
     */
    public void notifyStorageConfigurationChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        interfaceTransfer.invalidateHostStorage();
        refreshStorageConfiguration();
    }

    void refreshStorageConfiguration() {
        invalidateInfiniteMembers();
        invalidateStorageStatistics();
        setChanged();
        markForUpdate();
        refreshDriveStorageProviders();
    }

    void driveStorageAvailabilityChanged() {
        infiniteModeController.membersChanged();
        interfaceTransfer.invalidateHostStorage();
        invalidateStorageStatistics();
        refreshDriveStorageProviders();
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        ItemStack infiniteComponent = infiniteComponentInventory.getStackInSlot(0);
        if (!infiniteComponent.isEmpty()) {
            drops.add(infiniteComponent);
        }
    }

    @Deprecated
    public void applyInfiniteDomainToControllerDrop(ItemStack drop) { }

    public void restoreInfiniteDomainFromItem(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!(level instanceof ServerLevel serverLevel) || !tag.hasUUID(CONTROLLER_DOMAIN_TAG)
                || infiniteDomainId != null || !tier.supportsFeature(cn.dancingsnow.neoecoae.api.capability.ECOFeature.INFINITE_STORAGE_HOST)) return;
        var claims = cn.dancingsnow.neoecoae.grid.storage.infinite.ECOInfiniteStorageDomains.hostClaims(serverLevel.getServer());
        UUID id = tag.getUUID(CONTROLLER_DOMAIN_TAG);
        if (!claims.readable() || claims.locationOf(id) != null
                || claims.domainAt(net.minecraft.core.GlobalPos.of(level.dimension(), worldPosition)) != null) return;
        loadLegacyRoster(tag);
        setInfiniteDomainId(id);
        infiniteClaimToken = tag.hasUUID(CONTROLLER_CLAIM_TAG) ? tag.getUUID(CONTROLLER_CLAIM_TAG) : null;
        infiniteModeController.membersChanged();
    }

    public String getInfiniteDomainText() {
        return infiniteDomainId != null ? infiniteDomainId.toString() : "";
    }

    /** Returns a translated refusal, or null after successfully reattaching the saved domain. */
    @Nullable
    public net.minecraft.network.chat.Component recoverInfiniteDomain(UUID domainId) {
        return infiniteModeController.recoverDomain(domainId);
    }

    public boolean canInsertStorageCell(ItemStack stack) {
        return cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.isCellHandled(stack);
    }

    public void invalidateInfiniteMembers() { infiniteModeController.membersChanged(); }

    public int getMissingInfiniteMembers() { return infiniteModeController.missingDisks(); }

    private void loadLegacyRoster(CompoundTag tag) {
        legacyRoster = new CompoundTag();
        legacyMigrationSources.clear();
        malformedLegacyRoster = false;
        for (String name : List.of("infiniteMemberIds", "infiniteMigrationSourceIds")) {
            var entry = tag.get(name);
            if (entry == null) continue;
            legacyRoster.put(name, entry.copy());
            if (!(entry instanceof net.minecraft.nbt.ListTag values)) {
                malformedLegacyRoster = true;
                continue;
            }
            for (var value : values) {
                try {
                    UUID id = UUID.fromString(value.getAsString());
                    if (name.equals("infiniteMigrationSourceIds")) legacyMigrationSources.add(id);
                } catch (IllegalArgumentException invalid) {
                    malformedLegacyRoster = true;
                }
            }
        }
    }

    boolean hasLegacyRoster() { return !legacyRoster.isEmpty(); }

    java.util.Set<UUID> legacyMigrationSources() { return legacyMigrationSources; }

    boolean hasMalformedLegacyRoster() { return malformedLegacyRoster; }

    void clearLegacyRoster() {
        if (!hasLegacyRoster()) return;
        legacyMigrationSources.clear();
        legacyRoster = new CompoundTag();
        malformedLegacyRoster = false;
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

    /** @deprecated Infinite mode no longer migrates disks. */
    @Deprecated
    public int getInfiniteMigrationProgressPercent() { return isFormedInfiniteMode() ? 100 : 0; }

    /** @deprecated Infinite mode no longer migrates disks. */
    @Deprecated
    @Nullable
    public ECOInfiniteStorageEngine.MigrationBlock getInfiniteMigrationBlock() { return null; }

    public boolean canUseHostDomainStorage() { return infiniteModeController.canAccess(); }

    public boolean canInsertIntoInfiniteDomain() { return canUseHostDomainStorage(); }


    MEStorage createInfiniteStorageView(ECOInfiniteStorageEngine engine) {
        return infiniteModeController.createStorageView(engine);
    }

    public boolean canUseNormalStorage() {
        return formed && !isRemoved() && !isServerStopping() && !hostMode.isInfiniteState();
    }

    public boolean isInfiniteMemberCell(@Nullable ItemStack stack) {
        return stack != null && cluster != null && cluster.getDrives().stream()
                .anyMatch(drive -> infiniteModeController.occupies(drive) && drive.getInstalledCellStack() == stack);
    }

    public boolean isInfiniteDiskOccupied(ECODriveBlockEntity drive) { return infiniteModeController.occupies(drive); }

    @Override
    public void disconnect(boolean update) {
        infiniteModeController.topologyChanging();
        super.disconnect(update);
    }

    public void onStorageInterfaceModeChanged() {
        if (level == null || level.isClientSide) return;
        infiniteModeController.interfaceChanged();
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (level.getBlockState(worldPosition).hasProperty(ECOStorageSystemBlock.STORAGE_MODE)) {
            var mode = storageInterface == null ? cn.dancingsnow.neoecoae.grid.storage.transfer.ECOStorageInterfaceMode.STORAGE
                    : storageInterface.getStorageInterfaceMode();
            level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition)
                    .setValue(ECOStorageSystemBlock.STORAGE_MODE, mode));
        }
        refreshBulkCraftingPublication();
        refreshDriveStorageProviders();
        setChanged();
        markForUpdate();
    }

    public boolean isStorageInterfaceTransferMode() {
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        return formed && storageInterface != null && storageInterface.isStorageTransferMode();
    }

    @Nullable
    private ECOMachineInterfaceBlockEntity<NEStorageCluster> getStorageInterface() {
        return cluster == null ? null : cluster.getTheInterface();
    }


    boolean hasRequiredInfiniteComponents(ItemStack stack) {
        return isInfiniteComponent(stack) && stack.getCount() >= INFINITE_COMPONENT_REQUIRED;
    }

    @Override
    public void updateCluster(@Nullable NEStorageCluster nextCluster) {
        infiniteModeController.topologyChanging();
        interfaceTransfer.invalidateHostStorage();
        bulkCrafting.topologyChanged();
        super.updateCluster(nextCluster);
        infiniteModeController.membersChanged();
        refreshBulkCraftingPublication();
    }

    @Override
    public void onChunkUnloaded() {
        bulkCrafting.detach();
        infiniteModeController.release();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        bulkCrafting.detach();
        infiniteModeController.release();
        super.setRemoved();
    }

    @Nullable
    ECOInfiniteStorageEngine getInfiniteEngine() {
        return infiniteModeController.getEngine();
    }

    static boolean isInfiniteComponent(ItemStack stack) {
        return !stack.isEmpty() && stack.is(NETags.Items.INFINITE_CELL_COMPONENTS);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        if (infiniteDomainId != null && (infiniteClaimToken != null || pendingInfiniteClaimToken != null)) {
            CompoundTag claim = new CompoundTag();
            claim.putUUID("domain", infiniteDomainId);
            if (infiniteClaimToken != null) claim.putUUID(CURRENT_CLAIM_TAG, infiniteClaimToken);
            if (pendingInfiniteClaimToken != null) claim.putUUID(PENDING_CLAIM_TAG, pendingInfiniteClaimToken);
            getPersistentData().put(PERSISTENT_CLAIM_TAG, claim);
        } else {
            getPersistentData().remove(PERSISTENT_CLAIM_TAG);
        }
        super.saveAdditional(data, registries);
        if (infiniteDomainId != null) {
            data.putUUID("infiniteDomainId", infiniteDomainId);
        }
        for (String key : legacyRoster.getAllKeys()) data.put(key, legacyRoster.get(key).copy());
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        loadLegacyInfiniteComponentInventory(data, registries);
        hostMode = ECOStorageHostMode.UNFORMED;
        setInfiniteDomainId(data.hasUUID("infiniteDomainId") ? data.getUUID("infiniteDomainId") : null);
        CompoundTag claim = getPersistentData().getCompound(PERSISTENT_CLAIM_TAG);
        if (!claim.hasUUID("domain") || !claim.getUUID("domain").equals(infiniteDomainId)) claim = data;
        infiniteClaimToken = claim.hasUUID(CURRENT_CLAIM_TAG) ? claim.getUUID(CURRENT_CLAIM_TAG) : null;
        pendingInfiniteClaimToken = claim.hasUUID(PENDING_CLAIM_TAG) ? claim.getUUID(PENDING_CLAIM_TAG) : null;
        loadLegacyRoster(data);
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
        return !isInfiniteComponentSlotReadOnly();
    }

    protected boolean hasBuiltInInfiniteComponents() {
        return false;
    }

    protected int getBuiltInInfiniteComponentCount() {
        return 0;
    }

    private boolean isInfiniteComponentSlotReadOnly() {
        return getBuiltInInfiniteComponentCount() > 0 || hasBuiltInInfiniteComponents();
    }

    @Nullable
    public String blockedInfiniteComponentExtractionReason() {
        return null;
    }

    // Package-local collaboration points; public integration and RPC entry points remain on this entity.
    void invalidateStorageStatistics() {
        statistics.invalidate();
    }

    cn.dancingsnow.neoecoae.grid.storage.StorageFaults storageFaults() {
        return stageRunner.faults();
    }

    ECOStorageHostMode storageHostMode() {
        return hostMode;
    }

    void setStorageHostMode(ECOStorageHostMode mode) {
        if (hostMode == mode) return;
        hostMode = mode;
        interfaceTransfer.invalidateInfiniteStorageView();
        refreshBulkCraftingPublication();
    }


    boolean hasRequiredInfiniteComponents() {
        int builtInCount = getBuiltInInfiniteComponentCount();
        return builtInCount > 0 ? builtInCount >= INFINITE_COMPONENT_REQUIRED
                : hasRequiredInfiniteComponents(infiniteComponentInventory.getStackInSlot(0));
    }




    @Nullable
    UUID infiniteDomainId() {
        return infiniteDomainId;
    }

    @Nullable
    UUID infiniteClaimToken() {
        return infiniteClaimToken;
    }

    @Nullable
    UUID pendingInfiniteClaimToken() {
        return pendingInfiniteClaimToken;
    }


    void setInfiniteDomainId(@Nullable UUID domainId) {
        if (java.util.Objects.equals(infiniteDomainId, domainId)) return;
        infiniteDomainId = domainId;
        infiniteClaimToken = null;
        pendingInfiniteClaimToken = null;
        setChanged();
    }




    ECOStorageInterfaceTransfer storageInterfaceTransfer() {
        return interfaceTransfer;
    }

    boolean isStorageServerStopping() {
        return isServerStopping();
    }

    int selectedEcoMegaBulkCell() {
        return selectedEcoMegaBulkCell;
    }

    void selectEcoMegaBulkCell(int index) {
        selectedEcoMegaBulkCell = index;
    }

    int selectedEcoMegaPage() {
        return selectedEcoMegaPage;
    }

    void selectEcoMegaPage(int page) {
        selectedEcoMegaPage = page;
    }

    @Override
    public @Nullable MultiBlockDefinition getBuildDefinition() {
        return ECOMultiblockDefinitions.resolve(this,
            ECOMachineKind.STORAGE, tier,
            () -> NEMultiBlocks.getStorageSystemDefinition(tier));
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
    public Level getBuildLevel() {
        return level;
    }

    @Override
    public BlockPos getBuildPosition() {
        return worldPosition;
    }

    @Override
    public BlockState getBuildState() {
        return getBlockState();
    }

    @Override
    public int getSelectedBuildLength() {
        return selectedBuildLength;
    }

    @Override
    public void setSelectedBuildLength(int length) {
        selectedBuildLength = length;
    }

    @Override
    public boolean isMirrorBuild() {
        return mirrorBuild;
    }

    @Override
    public void setMirrorBuild(boolean mirrorBuild) {
        this.mirrorBuild = mirrorBuild;
    }

    @Override
    public boolean isBuildInProgress() {
        return buildInProgress;
    }

    @Override
    public void setBuildInProgress(boolean buildInProgress) {
        this.buildInProgress = buildInProgress;
    }

    @Override
    public boolean isFormed() {
        return formed;
    }

    @Override
    public void rebuildAfterBuild() {
        rebuildMultiblock();
    }

    @Override
    public void buildStateChanged() {
        setChanged();
        markForUpdate();
    }
}
