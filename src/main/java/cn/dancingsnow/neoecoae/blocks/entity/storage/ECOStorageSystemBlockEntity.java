package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageMigrationCell;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostActionUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageMegaPanelUI;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageTransfer;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
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
import appeng.api.stacks.AEKey;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
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

    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;

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
    private transient boolean infiniteComponentsDirty = true;
    private transient boolean targetInfiniteMode;
    private transient boolean updatingInfiniteMode;
    private transient long infiniteModeCheckTick = Long.MIN_VALUE;
    private transient long infiniteBackendGeneration;
    @Persisted
    @DescSynced
    @Nullable
    private UUID infiniteDomainId;
    private final java.util.Set<UUID> infiniteMemberIds = new java.util.HashSet<>();

    @Persisted(key = INFINITE_COMPONENT_INVENTORY_PERSIST_KEY)
    // Keep the component inventory persisted and exposed to the regular UI container, but do not include
    // every ItemStack in LDLib's descriptive/advanced-data synchronization packet. A populated infinite
    // component inventory can exceed the packet's fixed 2392-byte buffer before the UI is even opened.
    private final AppEngInternalInventory infiniteComponentInventory = new AppEngInternalInventory(this, 1, INFINITE_COMPONENT_REQUIRED);
    // Keep LDLib-managed fields on the block entity; delegate domain-specific behavior and caches.
    private final ECOStorageHostStatistics statistics = new ECOStorageHostStatistics(this);
    private final ECOStorageMegaController megaController = new ECOStorageMegaController(this);
    private final ECOStorageInterfaceTransfer interfaceTransfer = new ECOStorageInterfaceTransfer(this);
    private final ECOStorageInfiniteRestore infiniteRestore = new ECOStorageInfiniteRestore(this);
    private final IItemHandlerModifiable infiniteComponentItemHandler =
        infiniteRestore.componentItemHandler((IItemHandlerModifiable) infiniteComponentInventory.toItemHandler());

    @DescSynced
    private int selectedEcoMegaBulkCell;
    @DescSynced
    private int selectedEcoMegaPage;
    @DescSynced
    private boolean buildInProgress;
    private final MultiBlockBuildController buildController = new MultiBlockBuildController(this);

    private final cn.dancingsnow.neoecoae.impl.storage.StorageFaults storageFaults =
        new cn.dancingsnow.neoecoae.impl.storage.StorageFaults();
    private final Map<String, Long> stageRetryTicks = new HashMap<>();

    @Nullable
    private transient MinecraftServer mountedInfiniteServer;
    @Nullable
    private transient UUID mountedInfiniteDomainId;
    @Nullable
    private transient ECOInfiniteStorageEngine mountedInfiniteEngine;
    private final ECOInfiniteStorageTransfer infiniteTransfer = new ECOInfiniteStorageTransfer();
    private final java.util.Set<UUID> durableInfiniteSourceSeals = new java.util.HashSet<>();
    private int migrationDriveCursor;

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
                var interfaceMode = getStorageInterface();
                var mode = interfaceMode == null ? cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode.STORAGE
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
        Object server = level.getServer();
        currentStorageBudget = cn.dancingsnow.neoecoae.impl.storage.transfer.ECOStorageTickBudget.allowance(
            server, this, level.getGameTime(), NEConfig.storageTransferNanosPerTick);
        if (currentStorageBudget <= 0L) return;
        try (var cellBatch = cn.dancingsnow.neoecoae.impl.storage.ECOCellMutationBatch.open()) {
            if (!runStorageStage("migration", this::updateInfiniteStorageMode)) return;
            ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
            if (storageInterface != null) {
                if (!runStorageStage("transfer", () -> {
                    interfaceTransfer.updateFiniteTransferDomain(storageInterface);
                    storageInterface.recordStorageInterfaceTransfer(interfaceTransfer.transferStorageInterfaceContents(storageInterface));
                })) return;
            } else if (isFiniteTransferDomainLocked()) {
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
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        StorageHostActionUI.Elements actionUI = createActionUI(holder);

        UIElement root = StorageHostUI.create(new StorageHostUI.Config(
            () -> getItemFromBlockEntity().getDescription(),
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
            infiniteComponentItemHandler
        ));
        root.addChild(StorageMegaPanelUI.create(
            this,
            megaController.upgradeItemHandler(),
            megaController.filterItemHandler(),
            actionUI.bulkMarkingButton()
        ));
        actionUI.addTo(root);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
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

    private net.minecraft.network.chat.Component storageDiagnostics() {
        var text = net.minecraft.network.chat.Component.empty();
        interfaceTransfer.appendDiagnostics(text);
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
        infiniteComponentsDirty = true;
        infiniteRestore.invalidateExtractionCheck();
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
            storagePriority
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
            () -> storagePriority,
            priority -> setStoragePriority(holder.player, priority),
            delta -> changeStoragePriority(holder.player, delta),
            StorageBulkMarkingIntegration::isAvailable,
            () -> NEConfig.megaBulkAutoMarkThreshold,
            () -> megaController.autoMarkBulkCells(holder.player)
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

    void refreshDriveStorageProviders() {
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
        MEStorage storage = interfaceTransfer.getStorageInterfaceHostStorage();
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
        invalidateStorageStatistics();
        infiniteRestore.invalidateExtractionCheck();
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
        rememberInfiniteMembers();
        saveInfiniteMembers(tag);
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
        loadInfiniteMembers(tag);
        infiniteDomainId = tag.getUUID(CONTROLLER_DOMAIN_TAG);
        hostMode = ECOStorageHostMode.fromId(tag.getString(CONTROLLER_MODE_TAG));
        setChanged();
    }

    public String getInfiniteDomainText() {
        return isInfiniteMode() && infiniteDomainId != null ? infiniteDomainId.toString() : "";
    }

    public boolean canInsertStorageCell(ItemStack stack) {
        if (!isInfiniteMode()) return !ECOInfiniteStorageMember.isSealed(stack);
        if (ECOInfiniteStorageMember.isMember(stack)) {
            if (infiniteDomainId == null || !ECOInfiniteStorageMember.isMemberOf(stack, infiniteDomainId)) return false;
            var identity = ECOInfiniteStorageMember.getIdentity(stack);
            return cluster == null || identity.isEmpty() || cluster.getDrives().stream().noneMatch(drive ->
                identity.equals(ECOInfiniteStorageMember.getIdentity(drive.getCellStack())));

        }
        if (ECOInfiniteStorageMember.isMigrating(stack)) return false;
        IECOStorageCell cell = cn.dancingsnow.neoecoae.api.storage.ECOStorageCells.getCellInventory(stack, null);
        return cell != null && !cell.isInfiniteStorageEligible();
    }

    /** Remember identities, not slots: moving a member must not change the required roster. */
    public void rememberInfiniteMembers() {
        if (level == null || level.isClientSide || cluster == null || infiniteDomainId == null) return;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), infiniteDomainId)) {
                if (infiniteMemberIds.add(ECOInfiniteStorageMember.identity(drive.getCellStack()))) {
                    drive.setChanged();
                    setChanged();
                }
            }
        }
    }

    public int getMissingInfiniteMembers() {
        if (!isInfiniteMode()) return 0;
        rememberInfiniteMembers();
        java.util.Set<UUID> missing = new java.util.HashSet<>(infiniteMemberIds);
        if (cluster != null && infiniteDomainId != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), infiniteDomainId)) {
                    ECOInfiniteStorageMember.getIdentity(drive.getCellStack()).ifPresent(missing::remove);
                }
            }
        }
        return missing.size();
    }

    private void saveInfiniteMembers(CompoundTag tag) {
        net.minecraft.nbt.ListTag ids = new net.minecraft.nbt.ListTag();
        for (UUID id : infiniteMemberIds) ids.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        tag.put("infiniteMemberIds", ids);
    }

    private void loadInfiniteMembers(CompoundTag tag) {
        infiniteMemberIds.clear();
        for (var value : tag.getList("infiniteMemberIds", net.minecraft.nbt.Tag.TAG_STRING)) {
            infiniteMemberIds.add(UUID.fromString(value.getAsString()));
        }
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
        return formed && !isRemoved() && !isServerStopping()
            && hostMode == ECOStorageHostMode.FORMED_INFINITE && infiniteDomainId != null
            && !infiniteExitRequested && !infiniteRestore.isRestoring()
            && mountedInfiniteEngine != null && mountedInfiniteEngine.isHealthy()
            && !mountedInfiniteEngine.hasPendingRestore();
    }

    MEStorage createInfiniteStorageView(ECOInfiniteStorageEngine engine) {
        long generation = infiniteBackendGeneration;
        return new ECOInfiniteStorage(engine, getBlockState().getBlock().getName(),
            () -> generation == infiniteBackendGeneration && engine == mountedInfiniteEngine
                && canInsertIntoInfiniteDomain());
    }

    public boolean canUseNormalStorage() {
        return formed && !isRemoved() && !isServerStopping() && !hostMode.isInfiniteState();
    }

    public boolean isInfiniteMemberCell(@Nullable ItemStack stack) {
        return stack != null && ECOInfiniteStorageMember.isMember(stack);
    }

    public void onStorageInterfaceModeChanged() {
        if (level == null || level.isClientSide) return;
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (level.getBlockState(worldPosition).hasProperty(ECOStorageSystemBlock.STORAGE_MODE)) {
            var mode = storageInterface == null ? cn.dancingsnow.neoecoae.impl.storage.ECOStorageInterfaceMode.STORAGE
                : storageInterface.getStorageInterfaceMode();
            level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition)
                .setValue(ECOStorageSystemBlock.STORAGE_MODE, mode));
        }
        if (storageInterface != null) {
            interfaceTransfer.updateFiniteTransferDomain(storageInterface);
        }
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

    private void updateInfiniteStorageMode() {
        if (level == null || level.isClientSide || isServerStopping() || updatingInfiniteMode
            || infiniteModeCheckTick == level.getGameTime()) {
            return;
        }
        infiniteModeCheckTick = level.getGameTime();
        updatingInfiniteMode = true;
        ECOStorageHostMode previous = hostMode;
        try {
            if (infiniteComponentsDirty) {
                infiniteComponentsDirty = false;
                targetInfiniteMode = hasRequiredInfiniteComponents();
                // Keep a completed exit latched until the components have actually been removed.
                if (!targetInfiniteMode && !hostMode.isInfiniteState()) infiniteExitRequested = false;
            }
            processInfiniteStorageMode();
        } finally {
            updatingInfiniteMode = false;
            syncInfiniteModeChanges(previous);
        }
    }

    private void processInfiniteStorageMode() {
        rememberInfiniteMembers();
        if (!formed || cluster == null) {
            if (!hostMode.isInfiniteState()) {
                hostMode = ECOStorageHostMode.UNFORMED;
            }
            return;
        }
        if (hostMode == ECOStorageHostMode.UNFORMED) {
            hostMode = ECOStorageHostMode.FORMED_NORMAL;
        }
        if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            // Finish the sealed migration before considering an exit, including after reload.
            runInfiniteMigrationStep();
            return;
        }
        ECOInfiniteStorageEngine restoringEngine = getInfiniteEngine();
        if (hostMode == ECOStorageHostMode.RESTORING_TO_NORMAL || infiniteRestore.isRestoring()
            || (restoringEngine != null && restoringEngine.hasPendingRestore())
            || (infiniteExitRequested && hostMode.isInfiniteState())) {
            hostMode = ECOStorageHostMode.RESTORING_TO_NORMAL;
            infiniteRestore.restoreInfiniteDomainToNormalStorageIfPossible();
            return;
        }
        if (hostMode.isInfiniteState() && !targetInfiniteMode) {
            hostMode = ECOStorageHostMode.RESTORING_TO_NORMAL;
            infiniteRestore.restoreInfiniteDomainToNormalStorageIfPossible();
            return;
        }
        if (hostMode == ECOStorageHostMode.FORMED_NORMAL && canStartInfiniteMigration()) {
            ensureInfiniteDomainId();
            // Opening the destination must succeed before changing ownership of any source.
            ECOInfiniteStorageEngine engine = getInfiniteEngine();
            if (engine == null || !engine.isHealthy()) return;
            hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
        }
        if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            runInfiniteMigrationStep();
        }
    }

    private void syncInfiniteModeChanges(ECOStorageHostMode previous) {
        if (previous != hostMode) {
            infiniteBackendGeneration++;
            interfaceTransfer.invalidateInfiniteStorageView();
            infiniteRestore.invalidateExtractionCheck();
            invalidateStorageStatistics();
            refreshDriveStorageProviders();
            setChanged();
            markForUpdate();
        }
    }

    private boolean canStartInfiniteMigration() {
        return !infiniteExitRequested && tier == ECOTier.L9
            && formed
            && cluster != null
            && !interfaceTransfer.blocksInfiniteMigration()
            && !isStorageInterfaceTransferMode()
            && targetInfiniteMode
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

    boolean hasRequiredInfiniteComponents(ItemStack stack) {
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
        rememberInfiniteMembers();
        durableInfiniteSourceSeals.remove(migration);
        IStorageProvider.requestUpdate(drive.getMainNode());
        invalidateStorageStatistics();
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

    UUID migrationTransactionId(UUID domainId, ECODriveBlockEntity drive, AEKey key, long amount, String direction) {
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

    void exitInfiniteModeIfSafe() {
        if (getMissingInfiniteMembers() > 0) return;
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
        infiniteMemberIds.clear();
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
        interfaceTransfer.resetFiniteTransferScheduler();
    }

    @Override
    public void updateCluster(@Nullable NEStorageCluster nextCluster) {
        rememberInfiniteMembers();
        if (nextCluster == null && isFiniteTransferDomainLocked()) {
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
    ECOInfiniteStorageEngine getInfiniteEngine() {
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
        interfaceTransfer.invalidateInfiniteStorageView();
    }

    static boolean isInfiniteComponent(ItemStack stack) {
        return !stack.isEmpty() && stack.is(NETags.Items.INFINITE_CELL_COMPONENTS);
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        interfaceTransfer.saveHaltedTransfers(data, registries);
        data.putString("infiniteHostMode", hostMode.id());
        if (infiniteDomainId != null) {
            data.putUUID("infiniteDomainId", infiniteDomainId);
        }
        saveInfiniteMembers(data);
        interfaceTransfer.saveDomain(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        interfaceTransfer.loadHaltedTransfers(data, registries);
        loadLegacyInfiniteComponentInventory(data, registries);
        hostMode = ECOStorageHostMode.fromId(data.getString("infiniteHostMode"));
        infiniteDomainId = data.hasUUID("infiniteDomainId") ? data.getUUID("infiniteDomainId") : null;
        loadInfiniteMembers(data);
        interfaceTransfer.loadDomain(data);
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
        return infiniteRestore.blockedInfiniteComponentExtractionReason();
    }

    // Package-local collaboration points; public integration and RPC entry points remain on this entity.
    void invalidateStorageStatistics() {
        statistics.invalidate();
    }

    cn.dancingsnow.neoecoae.impl.storage.StorageFaults storageFaults() {
        return storageFaults;
    }

    Map<String, Long> storageStageRetryTicks() {
        return stageRetryTicks;
    }

    long currentStorageBudget() {
        return currentStorageBudget;
    }

    ECOStorageHostMode storageHostMode() {
        return hostMode;
    }

    boolean isInfiniteExitRequested() {
        return infiniteExitRequested;
    }

    void requestInfiniteExit() {
        infiniteExitRequested = true;
    }

    @Nullable
    UUID infiniteDomainId() {
        return infiniteDomainId;
    }

    AppEngInternalInventory infiniteComponentInventory() {
        return infiniteComponentInventory;
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

    public boolean isFiniteTransferDomainLocked() {
        return interfaceTransfer.isFiniteTransferDomainLocked();
    }

    public boolean materializeFiniteTransferDomain() {
        return interfaceTransfer.materializeFiniteTransferDomain();
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
