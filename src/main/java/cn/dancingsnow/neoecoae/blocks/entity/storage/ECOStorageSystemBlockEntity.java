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
    @Getter
    @DescSynced
    private long performanceAverageNanos = 0L;
    private long performanceWindowStartTick = Long.MIN_VALUE;
    private long performanceWindowNanos = 0L;
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
        try {
            updateInfiniteStorageMode();
            ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
            if (storageInterface != null) {
                updateFiniteTransferDomain(storageInterface);
                storageInterface.recordStorageInterfaceTransfer(transferStorageInterfaceContents(storageInterface));
            } else if (finiteTransferDomain != null) {
                materializeFiniteTransferDomain();
            }
            buildController.tick(level);
        } finally {
            recordPerformanceSample(System.nanoTime() - startNanos);
        }
    }

    private void recordPerformanceSample(long elapsedNanos) {
        if (elapsedNanos < 0L) {
            return;
        }
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
            this::getInfiniteDomainStatus
        );
    }

    private List<StorageHostHugeStackList.Entry> getHugeStackUiEntries() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !isFormedInfiniteMode()) {
            return List.of();
        }
        return engine.getHugeStacks().stream()
            .map(stack -> new StorageHostHugeStackList.Entry(stack.key(), stack.amount().toString()))
            .toList();
    }

    private ECOInfiniteStorageData.DomainStatus getInfiniteDomainStatus() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            return ECOInfiniteStorageData.DomainStatus.HEALTHY;
        }
        return engine.status();
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        updateInfiniteStorageMode();
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
        if (storageUiSnapshotGameTime != gameTime) {
            storageUiSnapshot = collectStorageUiSnapshot();
            storageUiSnapshotGameTime = gameTime;
        }
        return storageUiSnapshot;
    }

    @SuppressWarnings("UnstableApiUsage")
    private StorageUiSnapshot collectStorageUiSnapshot() {
        if (cluster == null) {
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
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell inv = drive.getCellInventory();
            if (inv == null) {
                continue;
            }
            int cellTypeId = NERegistries.CELL_TYPE.getId(inv.getCellType());
            if (cellTypeId >= 0 && drive.getCellStack().getItem() instanceof IECOStorageCellItem cellItem) {
                for (AEKeyType keyType : cellItem.getKeyTypes()) {
                    cellTypesByKeyType.putIfAbsent(keyType, cellTypeId);
                }
            }
            if (isInfiniteMemberCell(drive.getCellStack())) {
                idleMatrices++;
                continue;
            }

            long usedTypes = inv.getStoredItemTypes();
            long totalTypes = inv.hasInfiniteTypeCapacity() ? -1L : inv.getTotalItemTypes();
            long usedBytes = inv.getUsedBytes();
            long totalBytes = inv.getTotalBytes();
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
            finiteTransferScheduler.stop();
            finiteTransferScheduler = null;
        }
    }

    @Nullable
    private ECOMachineInterfaceBlockEntity<NEStorageCluster> getStorageInterface() {
        return cluster == null ? null : cluster.getTheInterface();
    }

    private long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
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
                    STORAGE_INTERFACE_TRANSFER_KEYS_PER_TICK,
                    STORAGE_INTERFACE_TRANSFER_NANOS_PER_TICK,
                    NEConfig.storageTransferRate,
                    this::onFiniteDomainMutation
                );
                finiteTransferScheduler.start(level.getGameTime());
            }
            moved = finiteTransferScheduler.tick(level.getGameTime());
        } else {
            moved = storageInterface.isStorageInputMode()
                ? transferLimited(network, hostStorage, source, true)
                : transferLimited(hostStorage, network, source, false);
        }
        if (moved > 0L) {
            storageUiSnapshotGameTime = Long.MIN_VALUE;
            setChanged();
            markForUpdate();
        }
        return moved;
    }

    private void onFiniteDomainMutation() {
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        setChanged();
    }

    @Nullable
    private MEStorage getStorageInterfaceHostStorage() {
        if (isFormedInfiniteMode()) {
            ECOInfiniteStorageEngine engine = getInfiniteEngine();
            return engine == null ? null : new ECOInfiniteStorage(engine, getBlockState().getBlock().getName());
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
        return cells.isEmpty() ? null : new CombinedStorage(cells, getBlockState().getBlock().getName());
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

    private static long transferLimited(
        MEStorage from,
        MEStorage to,
        IActionSource source,
        boolean skipInfiniteSourceEntries
    ) {
        KeyCounter available = new KeyCounter();
        from.getAvailableStacks(available);
        long total = 0L;
        int visited = 0;
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (skipInfiniteSourceEntries
                && isEffectivelyInfiniteSource(from, entry.getKey(), entry.getLongValue(), source)) {
                continue;
            }
            if (visited++ >= STORAGE_INTERFACE_TRANSFER_KEYS_PER_TICK) break;
            long amount = entry.getLongValue();
            if (amount <= 0L) continue;
            AEKey key = entry.getKey();
            long extractable = from.extract(key, amount, Actionable.SIMULATE, source);
            long accepted = to.insert(key, extractable, Actionable.SIMULATE, source);
            if (accepted <= 0L) continue;
            long extracted = from.extract(key, accepted, Actionable.MODULATE, source);
            long inserted = to.insert(key, extracted, Actionable.MODULATE, source);
            if (inserted < extracted) from.insert(key, extracted - inserted, Actionable.MODULATE, source);
            total = total > Long.MAX_VALUE - inserted ? Long.MAX_VALUE : total + inserted;
        }
        return total;
    }

    private static boolean isEffectivelyInfiniteSource(
        MEStorage storage,
        AEKey key,
        long visibleAmount,
        IActionSource source
    ) {
        long amountPerUnit = Math.max(1L, key.getAmountPerUnit());
        long conventionalInfiniteAmount = NEMath.saturatingMultiply(Integer.MAX_VALUE, amountPerUnit);
        if (visibleAmount < conventionalInfiniteAmount) {
            return false;
        }
        // MEStorage is aggregated. Probe only keys already at the conventional infinity threshold.
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE;
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
        if (hostMode.isInfiniteState() && !hasRequiredInfiniteComponents()) {
            restoreInfiniteDomainToNormalStorageIfPossible();
            syncInfiniteModeChanges(previous);
            return;
        }
        if (hostMode == ECOStorageHostMode.FORMED_NORMAL && canStartInfiniteMigration()) {
            ensureInfiniteDomainId();
            hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
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
        return tier == ECOTier.L9
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
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack == null || stack.isEmpty() || cell == null || cell.getTier() != ECOTier.L9
                || !cell.isInfiniteStorageEligible()) {
                continue;
            }
            if (ECOInfiniteStorageMember.isMember(stack)) {
                if (ECOInfiniteStorageMember.isMemberOf(stack, domainId)) {
                    continue;
                }
                LOGGER.error(
                    "Foreign ECO infinite storage member at {} blocks migration into domain {}",
                    drive.getBlockPos(),
                    domainId
                );
                restoreInfiniteDomainToNormalStorage();
                return;
            }
            hasPending = true;
            migrateDriveToDomain(drive, cell, engine, domainId);
            break;
        }
        if (!hasPending && countInfiniteMembers() >= INFINITE_MEMBER_REQUIRED) {
            hostMode = ECOStorageHostMode.FORMED_INFINITE;
        }
    }

    private void migrateDriveToDomain(ECODriveBlockEntity drive, IECOStorageCell cell, ECOInfiniteStorageEngine engine, UUID domainId) {
        KeyCounter available = new KeyCounter();
        cell.getAvailableStacks(available);
        for (Object2LongMap.Entry<AEKey> entry : available) {
            long amount = entry.getLongValue();
            if (amount > 0L) {
                UUID transactionId = migrationTransactionId(domainId, drive, entry.getKey(), amount, "to-domain");
                long inserted = engine.insertOnce(transactionId, entry.getKey(), amount);
                if (inserted != amount) {
                    LOGGER.error(
                        "Unable to migrate ECO storage matrix at {} into infinite domain {}: inserted {} of {}",
                        drive.getBlockPos(),
                        domainId,
                        inserted,
                        amount
                    );
                    engine.commit();
                    return;
                }
            }
        }
        engine.commit();
        if (cell instanceof ECOStorageCell storageCell) {
            storageCell.clearAllStoredStacks();
        }
        drive.convertCellToInfiniteMember(domainId);
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
        }
    }

    private RestorePlan createInfiniteRestorePlan(boolean enforceMargin) {
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
        if (!engine.getHugeStacks().isEmpty()) {
            return RestorePlan.blocked("domain contains stacks larger than a normal storage cell can hold");
        }

        List<RestoreTarget> targets = createRestoreTargets(infiniteDomainId);
        if (targets.isEmpty()) {
            return RestorePlan.blocked("no L9 storage matrices are available");
        }

        KeyCounter pending = new KeyCounter();
        engine.getAvailableStacks(pending);
        IActionSource source = IActionSource.ofMachine(this);
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            AEKey key = entry.getKey();
            HugeAmount amount = engine.getAmount(key);
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
                long inserted = insertForRestore(target.simulatedCell(), key, remaining, Actionable.MODULATE, source);
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
            if (ECOInfiniteStorageMember.isMember(stack)
                && !ECOInfiniteStorageMember.isMemberOf(stack, domainId)) {
                continue;
            }
            ItemStack simulationStack = stack.copy();
            ECOInfiniteStorageMember.clearMember(simulationStack);
            IECOStorageCell simulatedCell = ECOStorageCells.getCellInventory(simulationStack, null);
            if (simulatedCell != null && simulatedCell.getTier() == ECOTier.L9
                && simulatedCell.isInfiniteStorageEligible()) {
                targets.add(new RestoreTarget(drive, simulatedCell));
            }
        }
        return targets;
    }

    private boolean restoreTargetsHaveMargin(List<RestoreTarget> targets) {
        long used = 0L;
        long total = 0L;
        for (RestoreTarget target : targets) {
            IECOStorageCell cell = target.simulatedCell();
            used = NEMath.saturatingAdd(used, cell.getUsedBytes());
            total = NEMath.saturatingAdd(total, cell.getTotalBytes());
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

        KeyCounter pending = new KeyCounter();
        engine.getAvailableStacks(pending);
        IActionSource source = IActionSource.ofMachine(this);
        Map<AEKey, BigInteger> expectedFinalAmounts = expectedFinalRestoreAmounts(plan.targets(), pending);
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            AEKey key = entry.getKey();
            long remaining = engine.getAmount(key).toLongSaturated();
            long original = remaining;
            for (RestoreTarget target : plan.targets()) {
                IECOStorageCell cell = target.drive().getCellInventory();
                if (cell == null) {
                    continue;
                }
                UUID transactionId = migrationTransactionId(infiniteDomainId, target.drive(), key, original, "from-domain");
                long inserted = Math.min(remaining, target.drive().getRestoreReceipt(transactionId));
                if (inserted <= 0L) {
                    inserted = insertForRestore(cell, key, remaining, Actionable.MODULATE, source);
                    target.drive().putRestoreReceipt(transactionId, inserted);
                }
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
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            long amount = engine.getAmount(entry.getKey()).toLongSaturated();
            if (amount > 0L) {
                engine.extract(entry.getKey(), amount, Actionable.MODULATE);
            }
        }
        engine.commit();
        if (!engine.isEmpty()) {
            LOGGER.warn("Unable to fully restore ECO infinite storage domain {}; keeping it mounted to avoid data loss", infiniteDomainId);
            return;
        }
        exitInfiniteModeIfSafe();
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
            if (cell != null) {
                cell.getAvailableStacks(restored);
            }
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
        return cell.insert(key, amount, mode, source);
    }

    private UUID migrationTransactionId(UUID domainId, ECODriveBlockEntity drive, AEKey key, long amount, String direction) {
        String value = domainId + ":" + direction + ":" + drive.getBlockPos().asLong() + ":"
            + key.toTagGeneric(level.registryAccess()) + ":" + amount;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private void exitInfiniteModeIfSafe() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !engine.canExitOrRestore() || !engine.isEmpty()) {
            return;
        }
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
            // The transition is complete, so the receipts of the migrations that made up this run are no longer
            // needed. Dropping them keeps a host that is toggled repeatedly from accumulating them forever.
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
        RestorePlan plan = createInfiniteRestorePlan(true);
        return plan.canRestore() ? null : plan.reason();
    }

    private record RestoreTarget(ECODriveBlockEntity drive, IECOStorageCell simulatedCell) {
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
