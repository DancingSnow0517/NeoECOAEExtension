package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.StorageHostActionUI;
import cn.dancingsnow.neoecoae.gui.StorageHostPanelUI;
import cn.dancingsnow.neoecoae.gui.StoragePriority;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
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
import appeng.hooks.ticking.TickHandler;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost, IStorageProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOStorageSystemBlockEntity.class);
    private static final int INFINITE_COMPONENT_REQUIRED = 64;
    private static final int INFINITE_MEMBER_REQUIRED = 16;
    private static final long INFINITE_FLUSH_BUDGET_NANOS = 1_000_000L;
    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Persisted
    @DescSynced
    private int selectedBuildLength = 1;
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
    @Persisted
    @DescSynced
    private final AppEngInternalInventory componentInventory = new AppEngInternalInventory(this, 1, INFINITE_COMPONENT_REQUIRED);
    private final IItemHandlerModifiable componentItemHandler = (IItemHandlerModifiable) componentInventory.toItemHandler();
    @DescSynced
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;
    private transient StorageUiSnapshot storageUiSnapshot = StorageUiSnapshot.EMPTY;
    private transient long storageUiSnapshotGameTime = Long.MIN_VALUE;
    @Getter
    @DescSynced
    private long performanceAverageNanos = 0L;
    private long performanceWindowStartTick = Long.MIN_VALUE;
    private long performanceWindowNanos = 0L;
    @Setter
    private boolean mirrored;

    public ECOStorageSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
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
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                    );
                }
            }
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        long startNanos = System.nanoTime();
        try {
            updateInfiniteStorageMode();
            flushInfiniteEngineBudgeted();
            if (!(level instanceof ServerLevel serverLevel) || !buildInProgress || buildSession == null) {
                return;
            }

            ServerPlayer buildPlayer = buildPlayerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
            if (buildPlayer == null) {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                setChanged();
                markForUpdate();
                return;
            }

            switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
                case WAITING, ADVANCED -> {
                }
                case COMPLETED -> {
                    buildSession = null;
                    buildPlayerId = null;
                    buildInProgress = false;
                    rebuildMultiblock();
                    setChanged();
                    markForUpdate();
                }
                case BLOCKED -> {
                    buildSession = null;
                    buildPlayerId = null;
                    buildInProgress = false;
                    setChanged();
                    markForUpdate();
                }
            }
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
                        () -> getStorageValue(id, StorageValue.TOTAL_BYTES)
                    );
                })
                .toList(),
            this::isMigratingToInfinite,
            this::getInfiniteMigrationProgressPercent,
            () -> NEConfig.storageHostComponentSlots,
            componentItemHandler
        );
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
        if (engine == null || !canUseHostDomainStorage()) {
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
            storedEnergy = saturatedAdd(storedEnergy, (long) energyCell.getAECurrentPower());
            maxEnergy = saturatedAdd(maxEnergy, (long) energyCell.getAEMaxPower());
        }

        long maxLoadUsedBytes = 0L;
        long maxLoadTotalBytes = 0L;
        int idleMatrices = 0;
        double bestLoadRatio = -1.0D;
        Map<Integer, StorageTypeTotals> storageTypes = new HashMap<>();
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell inv = drive.getCellInventory();
            if (inv == null) {
                continue;
            }
            if (isInfiniteMemberCell(drive.getCellStack())) {
                idleMatrices++;
                continue;
            }

            long usedTypes = inv.getStoredItemTypes();
            long totalTypes = inv.getTotalItemTypes();
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

            int cellTypeId = NERegistries.CELL_TYPE.getId(inv.getCellType());
            if (cellTypeId >= 0) {
                storageTypes.merge(
                    cellTypeId,
                    new StorageTypeTotals(usedTypes, totalTypes, usedBytes, totalBytes),
                    StorageTypeTotals::add
                );
            }
        }
        if (isFormedInfiniteMode()) {
            addInfiniteStorageTypes(storageTypes);
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

    private void addInfiniteStorageTypes(Map<Integer, StorageTypeTotals> storageTypes) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            return;
        }
        for (ECOInfiniteStorageEngine.TypeStats stats : engine.getTypeStats()) {
            int cellTypeId = cellTypeIdForKeyType(stats.keyType());
            if (cellTypeId < 0) {
                continue;
            }
            storageTypes.merge(
                cellTypeId,
                new StorageTypeTotals(
                    stats.storedTypes(),
                    0L,
                    stats.storedAmount().toLongSaturated(),
                    0L
                ),
                StorageTypeTotals::add
            );
        }
    }

    private static int cellTypeIdForKeyType(AEKeyType keyType) {
        if (keyType == AEKeyType.items()) {
            return NERegistries.CELL_TYPE.getId(NECellTypes.ITEM.get());
        }
        if (keyType == AEKeyType.fluids()) {
            return NERegistries.CELL_TYPE.getId(NECellTypes.FLUID.get());
        }
        return -1;
    }

    private static long saturatedAdd(long left, long right) {
        long safeRight = Math.max(0L, right);
        long result = left + safeRight;
        return result < 0L ? Long.MAX_VALUE : result;
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

    private record StorageTypeTotals(long usedTypes, long totalTypes, long usedBytes, long totalBytes) {
        private static final StorageTypeTotals EMPTY = new StorageTypeTotals(0L, 0L, 0L, 0L);

        private StorageTypeTotals add(StorageTypeTotals other) {
            return new StorageTypeTotals(
                saturatedAdd(usedTypes, other.usedTypes),
                saturatedAdd(totalTypes, other.totalTypes),
                saturatedAdd(usedBytes, other.usedBytes),
                saturatedAdd(totalBytes, other.totalBytes)
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
            mirror -> setMirrorBuild(holder.player, mirror),
            () -> decreaseBuildLength(holder.player),
            () -> increaseBuildLength(holder.player),
            () -> autoBuild(holder.player),
            () -> formed,
            () -> buildInProgress,
            this::createLocalPreviewPlan,
            () -> storagePriority,
            priority -> setStoragePriority(holder.player, priority),
            delta -> changeStoragePriority(holder.player, delta)
        ));
    }

    private void changeStoragePriority(Player player, int delta) {
        setStoragePriority(player, StoragePriority.adjust(storagePriority, delta));
    }

    private void setStoragePriority(Player player, int priority) {
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
        ItemStack component = componentInventory.getStackInSlot(0);
        if (!component.isEmpty()) {
            drops.add(component);
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
        if (hostMode.isInfiniteState() && (!NEConfig.isInfiniteStorageEnabled() || !hasRequiredInfiniteComponents())) {
            restoreInfiniteDomainToNormalStorage();
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
            && NEConfig.isInfiniteStorageEnabled()
            && formed
            && cluster != null
            && hasRequiredInfiniteComponents()
            && countEligibleInfiniteMatrices() >= INFINITE_MEMBER_REQUIRED;
    }

    private boolean hasRequiredInfiniteComponents() {
        ItemStack stack = componentInventory.getStackInSlot(0);
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
        boolean hasPending = false;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack == null || stack.isEmpty() || cell == null || cell.getTier() != ECOTier.L9) {
                continue;
            }
            if (ECOInfiniteStorageMember.isMemberOf(stack, domainId)) {
                continue;
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
                engine.insert(entry.getKey(), amount, Actionable.MODULATE);
            }
        }
        if (cell instanceof ECOStorageCell storageCell) {
            storageCell.clearAllStoredStacks();
        }
        drive.convertCellToInfiniteMember(domainId);
        IStorageProvider.requestUpdate(drive.getMainNode());
        engine.flushBudgeted(0L);
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        setChanged();
        markForUpdate();
    }

    private void restoreInfiniteDomainToNormalStorage() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || engine.isEmpty()) {
            exitInfiniteModeIfSafe();
            return;
        }
        if (cluster == null || infiniteDomainId == null) {
            return;
        }
        KeyCounter pending = new KeyCounter();
        engine.getAvailableStacks(pending);
        List<ECODriveBlockEntity> restoreTargets = prepareNormalRestoreTargets(infiniteDomainId);
        boolean restoredAll = true;
        IActionSource source = IActionSource.ofMachine(this);
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            AEKey key = entry.getKey();
            HugeAmount amount = engine.getAmount(key);
            if (amount.compareTo(HugeAmount.of(Long.MAX_VALUE)) > 0) {
                restoredAll = false;
                continue;
            }
            long remaining = amount.toLongSaturated();
            for (ECODriveBlockEntity drive : restoreTargets) {
                IECOStorageCell cell = drive.getCellInventory();
                if (cell == null) {
                    continue;
                }
                long inserted = cell.insert(key, remaining, Actionable.MODULATE, source);
                remaining -= inserted;
                if (remaining <= 0L) {
                    break;
                }
            }
            long restored = amount.toLongSaturated() - remaining;
            if (restored > 0L) {
                engine.extract(key, restored, Actionable.MODULATE);
            }
            if (remaining > 0L) {
                restoredAll = false;
            }
        }
        engine.flushBudgeted(0L);
        if (restoredAll && engine.isEmpty()) {
            exitInfiniteModeIfSafe();
        } else {
            LOGGER.warn("Unable to fully restore ECO infinite storage domain {}; keeping it mounted to avoid data loss", infiniteDomainId);
        }
    }

    private List<ECODriveBlockEntity> prepareNormalRestoreTargets(UUID domainId) {
        List<ECODriveBlockEntity> targets = new ArrayList<>();
        if (cluster == null) {
            return targets;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (ECOInfiniteStorageMember.isMemberOf(drive.getCellStack(), domainId)) {
                drive.convertInfiniteMemberToNormalStorage(domainId);
            }
            IECOStorageCell cell = drive.getCellInventory();
            if (cell != null && cell.getTier() == ECOTier.L9) {
                targets.add(drive);
            }
        }
        return targets;
    }

    private void exitInfiniteModeIfSafe() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine != null && !engine.isEmpty()) {
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
            ECOInfiniteStorageDomains.close(serverLevel, domainId);
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

    @Nullable
    private ECOInfiniteStorageEngine getInfiniteEngine() {
        if (!(level instanceof ServerLevel serverLevel) || infiniteDomainId == null) {
            return null;
        }
        return ECOInfiniteStorageDomains.get(serverLevel, infiniteDomainId);
    }

    private void flushInfiniteEngineBudgeted() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine != null) {
            engine.flushBudgeted(INFINITE_FLUSH_BUDGET_NANOS);
        }
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
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        hostMode = ECOStorageHostMode.fromId(data.getString("infiniteHostMode"));
        infiniteDomainId = data.hasUUID("infiniteDomainId") ? data.getUUID("infiniteDomainId") : null;
    }

    @Override
    public void onChunkUnloaded() {
        closeInfiniteEngine();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        closeInfiniteEngine();
        super.setRemoved();
    }

    private void closeInfiniteEngine() {
        if (level instanceof ServerLevel serverLevel && infiniteDomainId != null) {
            ECOInfiniteStorageDomains.close(serverLevel, infiniteDomainId);
        }
    }

    private void increaseBuildLength(Player player) {
        if (buildInProgress) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        setChanged();
        markForUpdate();
    }

    private void decreaseBuildLength(Player player) {
        if (buildInProgress) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        setChanged();
        markForUpdate();
    }

    private void autoBuild(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (formed) {
            return;
        }
        if (buildInProgress) {
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(serverLevel, worldPosition, getBlockState(), definition, selectedBuildLength, mirrorBuild);
        if (!plan.getConflictPositions().isEmpty()) {
            return;
        }
        if (!serverPlayer.isCreative() && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            rebuildMultiblock();
            serverPlayer.closeContainer();
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan)) {
                return;
            }
            rebuildMultiblock();
            serverPlayer.closeContainer();
            return;
        }
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        buildInProgress = true;
        setChanged();
        markForUpdate();
        serverPlayer.closeContainer();
    }

    private @Nullable MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getStorageSystemDefinition(tier);
    }

    private int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    private int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    private void setMirrorBuild(Player player, boolean mirrorBuild) {
        if (buildInProgress) {
            return;
        }
        this.mirrorBuild = mirrorBuild;
        setChanged();
        markForUpdate();
    }

    private @Nullable MultiBlockPlacementPlan createLocalPreviewPlan() {
        if (level == null || formed) {
            return null;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            return null;
        }
        int buildLength = Math.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        return MultiBlockPlacementService.preview(level, worldPosition, getBlockState(), definition, buildLength, mirrorBuild);
    }
}
