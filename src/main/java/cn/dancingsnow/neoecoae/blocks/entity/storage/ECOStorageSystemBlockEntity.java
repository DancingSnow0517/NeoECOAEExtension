package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.helpers.IPriorityHost;
import appeng.menu.ISubMenu;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.ldlib.NELDLibUis;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageHugeStackState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiMatrixState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiState;
import cn.dancingsnow.neoecoae.gui.ldlib.state.NEStorageUiTypeState;
import cn.dancingsnow.neoecoae.gui.ldlib.support.NEBlockEntityUIHolder;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.multiblock.BuildPreviewState;
import cn.dancingsnow.neoecoae.multiblock.INEMultiblockBuildHost;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity>
        implements IGridTickable, IStorageProvider, INEMultiblockBuildHost, IPriorityHost, NEBlockEntityUIHolder {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final int STORAGE_INTERFACE_EXPORT_KEYS_PER_TICK = 64;
    private static final int INFINITE_COMPONENT_REQUIRED = 64;
    private static final int INFINITE_MEMBER_REQUIRED = 16;
    private static final long INFINITE_FLUSH_BUDGET_NANOS = 1_000_000L;

    @Getter
    private final IECOTier tier;

    private long[] usedTypes;
    private long[] totalTypes;
    private long[] usedBytes;
    private long[] totalBytes;
    private boolean storageStatsDirty = true;

    /** Storage priority for AE2 network insertion/extraction ordering. */
    private int priority = 0;

    private ECOStorageHostMode hostMode = ECOStorageHostMode.UNFORMED;

    @Nullable private UUID infiniteDomainId;

    private boolean hostStorageMounted;
    private boolean infiniteRestoreWarningLogged;

    private final ItemStackHandler infiniteComponentHandler = new ItemStackHandler(1) {
        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return Math.min(super.getStackLimit(slot, stack), INFINITE_COMPONENT_REQUIRED);
        }

        @Override
        public int getSlotLimit(int slot) {
            return INFINITE_COMPONENT_REQUIRED;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isInfiniteComponent(stack);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!canTakeInfiniteStorageComponent()) {
                return ItemStack.EMPTY;
            }
            return super.extractItem(slot, amount, simulate);
        }

        @Override
        protected void onContentsChanged(int slot) {
            onInfiniteComponentSlotChanged();
        }
    };

    /** Shared preview/build state, delegates NBT sync to {@link BuildPreviewState}. */
    private final BuildPreviewState buildPreview = new BuildPreviewState();

    private long storedEnergy;
    private long maxEnergy;

    public ECOStorageSystemBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState, IECOTier tier) {
        super(type, pos, blockState);
        this.tier = tier;
        resetStorageInfos();

        getMainNode().addService(IGridTickable.class, this);
        getMainNode().addService(IStorageProvider.class, this);
    }

    public static ECOStorageSystemBlockEntity createL4(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L4);
    }

    public static ECOStorageSystemBlockEntity createL6(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L6);
    }

    public static ECOStorageSystemBlockEntity createL9(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        return new ECOStorageSystemBlockEntity(type, pos, blockState, ECOTier.L9);
    }

    @Override
    public void onReady() {
        super.onReady();
        getMainNode().setIdlePowerUsage(256 + (1 << (1 + 4 * tier.getTier())));
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            markStorageStatsDirty();
            updateInfos();
        }
    }

    public void onStorageClusterFormed() {
        if (level == null || level.isClientSide || !formed || cluster == null) {
            return;
        }
        updateInfiniteStorageMode();
        markStorageStatsDirty();
        updateInfos();
        requestProviderUpdates();
        IStorageProvider.requestUpdate(getMainNode());
        setChanged();
        markForUpdate();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(20, 20, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        updateInfiniteStorageMode();
        flushInfiniteEngineBudgeted();
        long exported = exportStorageInterfaceContents();
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (storageInterface != null) {
            storageInterface.recordStorageInterfaceExport(exported);
        }
        updateInfos();
        return TickRateModulation.URGENT;
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !canUseHostDomainStorage()) {
            setHostStorageMounted(false);
            return;
        }
        storageMounts.mount(
                new ECOInfiniteStorage(engine, getBlockState().getBlock().getName()), priority);
        setHostStorageMounted(true);
    }

    private void resetStorageInfos() {
        int typeCount = getCellTypeCount();
        usedTypes = new long[typeCount];
        totalTypes = new long[typeCount];
        usedBytes = new long[typeCount];
        totalBytes = new long[typeCount];
        storedEnergy = 0;
        maxEnergy = 0;
        _synUsedTypes = 0;
        _synTotalTypes = 0;
        _synUsedBytes = 0;
        _synTotalBytes = 0;
    }

    /**
     * Core stats recalculation from cluster drives and energy cells.
     * Updates _syn* scalars and per-type arrays but does NOT mark dirty
     * or sync to client. Safe to call on server only.
     */
    @SuppressWarnings("UnstableApiUsage")
    private void recalculateStorageStats() {
        if (cluster != null) {
            storedEnergy = 0;
            maxEnergy = 0;
            for (ECOEnergyCellBlockEntity energyCell : cluster.getEnergyCells()) {
                storedEnergy += (long) energyCell.getAECurrentPower();
                maxEnergy += (long) energyCell.getAEMaxPower();
            }

            int typeCount = getCellTypeCount();
            usedTypes = new long[typeCount];
            totalTypes = new long[typeCount];
            usedBytes = new long[typeCount];
            totalBytes = new long[typeCount];

            // Aggregate scalars - always populated regardless of registry-id lookup
            long aggUsedTypes = 0, aggTotalTypes = 0, aggUsedBytes = 0, aggTotalBytes = 0;

            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                IECOStorageCell inv = drive.getCellInventory();
                if (inv == null) continue;
                if (isInfiniteMemberCell(drive.getCellStack())) continue;

                long st = inv.getStoredItemTypes();
                long tt = inv.getTotalItemTypes();
                long ub = inv.getUsedBytes();
                long tb = inv.getTotalBytes();

                aggUsedTypes += st;
                aggTotalTypes += tt;
                aggUsedBytes += ub;
                aggTotalBytes += tb;

                // Per-cell-type arrays - best-effort, may skip if id lookup fails
                ECOCellType cellType = inv.getCellType();
                var reg = NERegistries.cellTypeRegistry();
                int id = reg != null ? reg.getId(cellType) : -1;
                if (id >= 0 && id < typeCount) {
                    usedTypes[id] += st;
                    totalTypes[id] += tt;
                    usedBytes[id] += ub;
                    totalBytes[id] += tb;
                }
            }

            ECOInfiniteStorageEngine engine = getInfiniteEngine();
            if (engine != null && canUseHostDomainStorage()) {
                long domainTypes = engine.getStoredTypes();
                long domainAmount = engine.getStoredAmount().toLongSaturated();
                aggUsedTypes = saturatedAdd(aggUsedTypes, domainTypes);
                aggTotalTypes = Long.MAX_VALUE;
                aggUsedBytes = saturatedAdd(aggUsedBytes, domainAmount);
                aggTotalBytes = Long.MAX_VALUE;
            }

            _synUsedTypes = aggUsedTypes;
            _synTotalTypes = aggTotalTypes;
            _synUsedBytes = aggUsedBytes;
            _synTotalBytes = aggTotalBytes;
        } else {
            resetStorageInfos();
        }
    }

    @SuppressWarnings("UnstableApiUsage")
    private void updateInfos() {
        if (ensureStorageStatsCurrent()) {
            setChanged();
        }
    }

    private boolean ensureStorageStatsCurrent() {
        if (!storageStatsDirty) {
            return false;
        }
        recalculateStorageStats();
        storageStatsDirty = false;
        return true;
    }

    private void updateInfiniteStorageMode() {
        if (level == null || level.isClientSide) {
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
        if (hostMode.isInfiniteState() && !NEConfig.isInfiniteStorageEnabled()) {
            restoreInfiniteDomainToNormalStorage();
            syncInfiniteModeChanges(previous);
            return;
        }
        if (hostMode == ECOStorageHostMode.FORMED_NORMAL && canStartInfiniteMigration()) {
            ensureInfiniteDomainId();
            infiniteRestoreWarningLogged = false;
            hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
        }
        if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            runInfiniteMigrationStep();
        }
        syncInfiniteModeChanges(previous);
    }

    private void syncInfiniteModeChanges(ECOStorageHostMode previous) {
        if (previous != hostMode) {
            markStorageStatsDirty();
            requestProviderUpdates();
            IStorageProvider.requestUpdate(getMainNode());
            setChanged();
            markForUpdate();
        }
    }

    private boolean canStartInfiniteMigration() {
        return tier == ECOTier.L9
                && NEConfig.isInfiniteStorageEnabled()
                && formed
                && cluster != null
                && infiniteComponentHandler.getStackInSlot(0).getCount() >= INFINITE_COMPONENT_REQUIRED
                && countEligibleInfiniteMatrices() >= INFINITE_MEMBER_REQUIRED;
    }

    private int countEligibleInfiniteMatrices() {
        if (cluster == null) {
            return 0;
        }
        int count = 0;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell cell = drive.getCellInventory();
            if (cell != null && cell.getTier() == ECOTier.L9) {
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
        boolean migratedAny = false;
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
            migratedAny = true;
            break;
        }
        if (!hasPending && countInfiniteMembers() >= INFINITE_MEMBER_REQUIRED) {
            hostMode = ECOStorageHostMode.FORMED_INFINITE;
        }
        if (migratedAny) {
            markStorageStatsDirty();
            requestProviderUpdates();
            IStorageProvider.requestUpdate(getMainNode());
            setChanged();
            markForUpdate();
        }
    }

    private void migrateDriveToDomain(
            ECODriveBlockEntity drive, IECOStorageCell cell, ECOInfiniteStorageEngine engine, UUID domainId) {
        KeyCounter available = new KeyCounter();
        cell.getAvailableStacks(available);
        for (Object2LongMap.Entry<AEKey> entry : available) {
            long amount = entry.getLongValue();
            if (amount > 0L) {
                engine.insert(entry.getKey(), amount, Actionable.MODULATE);
            }
        }
        engine.flushBudgeted(0L);
        ItemStack stack = drive.getCellStack();
        if (stack != null && !stack.isEmpty()) {
            ECOInfiniteStorageMember.clearStoredContents(stack);
            ECOInfiniteStorageMember.markMember(stack, domainId);
        }
        drive.invalidateCellInventoryForHostChange();
        drive.requestStorageProviderUpdate();
        drive.scheduleRenderUpdate();
        drive.setChanged();
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
        boolean changed = false;
        boolean restoredAll = true;

        for (Object2LongMap.Entry<AEKey> entry : pending) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (amount <= 0L) {
                continue;
            }
            if (engine.getAmount(key).compareTo(HugeAmount.of(Long.MAX_VALUE)) > 0) {
                restoredAll = false;
                logInfiniteRestoreWarning(
                        "Unable to restore key {} from ECO infinite domain {} because its amount exceeds normal cell storage format",
                        key,
                        infiniteDomainId);
                continue;
            }
            long restored = forceRestoreToNormalMember(key, amount);
            if (restored <= 0L) {
                restoredAll = false;
                continue;
            }
            long extracted = engine.extract(key, restored, Actionable.MODULATE);
            changed = true;
            if (extracted < amount) {
                restoredAll = false;
            }
        }

        if (changed) {
            engine.flushBudgeted(0L);
            markStorageStatsDirty();
            requestProviderUpdates();
            IStorageProvider.requestUpdate(getMainNode());
            setChanged();
            markForUpdate();
        }
        if (restoredAll && engine.isEmpty()) {
            infiniteRestoreWarningLogged = false;
            exitInfiniteModeIfSafe();
        } else if (!restoredAll) {
            logInfiniteRestoreWarning(
                    "Unable to fully restore ECO infinite storage domain {} into normal storage; keeping infinite domain mounted to avoid data loss",
                    infiniteDomainId);
        }
    }

    private void logInfiniteRestoreWarning(String message, Object... args) {
        if (infiniteRestoreWarningLogged) {
            return;
        }
        infiniteRestoreWarningLogged = true;
        LOGGER.warn(message, args);
    }

    private long forceRestoreToNormalMember(AEKey key, long amount) {
        if (cluster == null || infiniteDomainId == null) {
            return 0L;
        }
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            if (!ECOInfiniteStorageMember.isMemberOf(stack, infiniteDomainId)) {
                continue;
            }
            IECOStorageCell cell = drive.getCellInventory();
            if (cell instanceof ECOStorageCell ecoCell) {
                long restored = ecoCell.forceInsertOverflow(key, amount, Actionable.MODULATE);
                if (restored > 0L) {
                    drive.invalidateCellInventoryForHostChange();
                    drive.requestStorageProviderUpdate();
                    drive.scheduleRenderUpdate();
                    drive.setChanged();
                    return restored;
                }
            }
        }
        return 0L;
    }

    private UUID ensureInfiniteDomainId() {
        if (infiniteDomainId == null) {
            infiniteDomainId = UUID.randomUUID();
            setChanged();
        }
        return infiniteDomainId;
    }

    @Nullable private ECOInfiniteStorageEngine getInfiniteEngine() {
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

    public boolean canUseHostDomainStorage() {
        return formed && hostMode.isInfiniteState() && infiniteDomainId != null;
    }

    public boolean isInfiniteMode() {
        return hostMode.isInfiniteState();
    }

    public boolean isInfiniteSlotVisible() {
        return tier == ECOTier.L9 && NEConfig.isInfiniteStorageEnabled();
    }

    public boolean canTakeInfiniteStorageComponent() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        return !hostMode.isInfiniteState() || engine == null || engine.isEmpty();
    }

    public IItemHandler getInfiniteComponentItemHandler() {
        return infiniteComponentHandler;
    }

    public void onInfiniteComponentSlotChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (hostMode.isInfiniteState()
                && canTakeInfiniteStorageComponent()
                && infiniteComponentHandler.getStackInSlot(0).isEmpty()) {
            exitInfiniteModeIfSafe();
        } else {
            updateInfiniteStorageMode();
        }
        markStorageStatsDirty();
        setChanged();
        markForUpdate();
    }

    private void exitInfiniteModeIfSafe() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine != null && !engine.isEmpty()) {
            return;
        }
        UUID domainId = infiniteDomainId;
        if (cluster != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                ItemStack stack = drive.getCellStack();
                if (stack != null
                        && !stack.isEmpty()
                        && (domainId == null || ECOInfiniteStorageMember.isMemberOf(stack, domainId))) {
                    ECOInfiniteStorageMember.clearMember(stack);
                    drive.invalidateCellInventoryForHostChange();
                    drive.requestStorageProviderUpdate();
                    drive.scheduleRenderUpdate();
                }
            }
        }
        hostMode = formed ? ECOStorageHostMode.FORMED_NORMAL : ECOStorageHostMode.UNFORMED;
        if (level instanceof ServerLevel serverLevel && domainId != null) {
            ECOInfiniteStorageDomains.close(serverLevel, domainId);
        }
        infiniteDomainId = null;
        requestProviderUpdates();
        IStorageProvider.requestUpdate(getMainNode());
    }

    public boolean isInfiniteMemberCell(@Nullable ItemStack stack) {
        return stack != null && ECOInfiniteStorageMember.isMember(stack);
    }

    public boolean canExtractDriveCell(ECODriveBlockEntity drive) {
        ItemStack stack = drive.getCellStack();
        if (!isInfiniteMemberCell(stack)) {
            return true;
        }
        return false;
    }

    private void setHostStorageMounted(boolean mounted) {
        if (hostStorageMounted == mounted) {
            return;
        }
        hostStorageMounted = mounted;
        markStorageStatsDirty();
        setChanged();
        markForUpdate();
    }

    private static boolean isInfiniteComponent(ItemStack stack) {
        return !stack.isEmpty() && stack.is(NEItems.ECO_INFINITE_CELL_COMPONENT.get());
    }

    /**
     * Creates a snapshot of current storage stats for S2C UI sync.
     * <p>
     * Stats are grouped by ECOCellType registry key so the screen can display
     * separate rows for Items, Fluids, and future cell types.
     * </p>
     */
    public NEStorageUiState createStorageUiState() {
        if (level != null && !level.isClientSide) {
            ensureStorageStatsCurrent();
        }

        List<NEStorageUiTypeState> typeStates;
        List<NEStorageUiMatrixState> matrixStates;
        if (cluster != null) {
            // Group by cell type key; LinkedHashMap preserves insertion order
            Map<ResourceLocation, NEStorageUiTypeState> grouped = new LinkedHashMap<>();
            Map<AEKeyType, CellTypePresentation> keyTypePresentations = new HashMap<>();
            matrixStates = new ArrayList<>(cluster.getDrives().size());
            IOrientationStrategy strategy = OrientationStrategies.horizontalFacing();
            Direction top = strategy.getSide(getBlockState(), RelativeSide.TOP);
            Direction left = strategy.getSide(getBlockState(), RelativeSide.RIGHT);
            Direction right = cluster.isMirrored() ? left : left.getOpposite();

            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                BlockPos offset = drive.getBlockPos().subtract(worldPosition);
                int row = 1 - directionDistance(offset, top);
                int column = directionDistance(offset, right) - 1;
                ItemStack cellStack = drive.getCellStack();
                IECOStorageCell inv = drive.getCellInventory();
                if (inv == null || cellStack.isEmpty()) {
                    matrixStates.add(
                            new NEStorageUiMatrixState(row, column, ItemStack.EMPTY, 0, 0L, 0L, 0L, 0L, false));
                    continue;
                }
                ECOCellType cellType = inv.getCellType();
                ResourceLocation typeId = getCellTypeKey(cellType);
                String displayName = cellType.desc().getString();
                if (inv instanceof ECOStorageCell ecoCell) {
                    keyTypePresentations.putIfAbsent(
                            ecoCell.getKeyType(), new CellTypePresentation(typeId, displayName));
                }
                boolean infiniteMember = isInfiniteMemberCell(cellStack);
                if (infiniteMember) {
                    int matrixTier = Math.max(0, Math.min(3, inv.getTier().getTier()));
                    matrixStates.add(new NEStorageUiMatrixState(
                            row,
                            column,
                            new ItemStack(cellStack.getItem()),
                            matrixTier,
                            0L,
                            Long.MAX_VALUE,
                            0L,
                            Long.MAX_VALUE,
                            true));
                    continue;
                }

                long st = inv.getStoredItemTypes();
                long tt = inv.getTotalItemTypes();
                long ub = inv.getUsedBytes();
                long tb = inv.getTotalBytes();
                int matrixTier = Math.max(0, Math.min(3, inv.getTier().getTier()));
                matrixStates.add(new NEStorageUiMatrixState(
                        row, column, new ItemStack(cellStack.getItem()), matrixTier, st, tt, ub, tb, false));

                mergeStorageTypeState(grouped, typeId, displayName, st, tt, ub, tb, Long.toString(Math.max(0L, ub)));
            }
            ECOInfiniteStorageEngine engine = getInfiniteEngine();
            if (engine != null && canUseHostDomainStorage()) {
                mergeInfiniteDomainTypeStates(grouped, keyTypePresentations, engine);
            }
            typeStates = new ArrayList<>(grouped.values());
            // Stable ordering: Items first, Fluids second, others by typeId string
            typeStates.sort(
                    java.util.Comparator.comparingInt((NEStorageUiTypeState s) -> storageTypeSortPriority(s.typeId()))
                            .thenComparing(s -> s.typeId().toString()));
            matrixStates.sort(Comparator.comparingInt(NEStorageUiMatrixState::row)
                    .thenComparingInt(NEStorageUiMatrixState::column));
        } else {
            typeStates = new ArrayList<>();
            matrixStates = List.of();
        }

        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        return new NEStorageUiState(
                worldPosition,
                typeStates,
                matrixStates,
                createHugeStackStates(engine),
                storedEnergy,
                maxEnergy,
                formed,
                isInfiniteSlotVisible(),
                isInfiniteMode(),
                infiniteComponentHandler.getStackInSlot(0).getCount(),
                canTakeInfiniteStorageComponent(),
                engine == null || engine.isEmpty());
    }

    private static List<NEStorageHugeStackState> createHugeStackStates(@Nullable ECOInfiniteStorageEngine engine) {
        if (engine == null) {
            return List.of();
        }
        List<NEStorageHugeStackState> stacks = new ArrayList<>();
        for (ECOInfiniteStorageEngine.HugeStack stack : engine.getHugeStacks()) {
            stacks.add(new NEStorageHugeStackState(stack.key(), stack.amount().toString()));
        }
        return List.copyOf(stacks);
    }

    private static void mergeInfiniteDomainTypeStates(
            Map<ResourceLocation, NEStorageUiTypeState> grouped,
            Map<AEKeyType, CellTypePresentation> keyTypePresentations,
            ECOInfiniteStorageEngine engine) {
        String fallbackDisplayName =
                Component.translatable("gui.neoecoae.storage.infinite_domain").getString();
        for (ECOInfiniteStorageEngine.TypeStats stats : engine.getTypeStats()) {
            long amount = stats.storedAmount().toLongSaturated();
            CellTypePresentation presentation = keyTypePresentations.get(stats.keyType());
            ResourceLocation typeId = presentation == null ? NeoECOAE.id("infinite") : presentation.typeId();
            String displayName = presentation == null ? fallbackDisplayName : presentation.displayName();
            mergeStorageTypeState(
                    grouped,
                    typeId,
                    displayName,
                    stats.storedTypes(),
                    Long.MAX_VALUE,
                    amount,
                    Long.MAX_VALUE,
                    stats.storedAmount().toString());
        }
    }

    private static void mergeStorageTypeState(
            Map<ResourceLocation, NEStorageUiTypeState> grouped,
            ResourceLocation typeId,
            String displayName,
            long usedTypes,
            long totalTypes,
            long usedBytes,
            long totalBytes,
            String usedAmount) {
        NEStorageUiTypeState existing = grouped.get(typeId);
        if (existing == null) {
            grouped.put(
                    typeId,
                    new NEStorageUiTypeState(
                            typeId, displayName, usedTypes, totalTypes, usedBytes, totalBytes, safeAmount(usedAmount)));
            return;
        }
        grouped.put(
                typeId,
                new NEStorageUiTypeState(
                        typeId,
                        displayName,
                        saturatedAdd(existing.usedTypes(), usedTypes),
                        saturatedAdd(existing.totalTypes(), totalTypes),
                        saturatedAdd(existing.usedBytes(), usedBytes),
                        saturatedAdd(existing.totalBytes(), totalBytes),
                        addAmounts(existing.safeUsedAmount(), usedAmount)));
    }

    private static String addAmounts(String left, String right) {
        return safeBigInteger(left).add(safeBigInteger(right)).toString();
    }

    private static String safeAmount(String value) {
        return safeBigInteger(value).toString();
    }

    private static BigInteger safeBigInteger(String value) {
        try {
            return value == null || value.isBlank() ? BigInteger.ZERO : new BigInteger(value);
        } catch (RuntimeException ignored) {
            return BigInteger.ZERO;
        }
    }

    private record CellTypePresentation(ResourceLocation typeId, String displayName) {}

    private static int directionDistance(BlockPos offset, Direction direction) {
        return offset.getX() * direction.getStepX()
                + offset.getY() * direction.getStepY()
                + offset.getZ() * direction.getStepZ();
    }

    @Override
    public ModularUI createUI(Player player) {
        return NELDLibUis.createStorageController(this, player);
    }

    /**
     * Returns the stable identity key for a cell type.
     * Uses the {@code id} field embedded in {@link ECOCellType} directly,
     * avoiding {@code Registry.getKey()} which is unreliable for custom
     * Registrate-built registries.
     */
    private static ResourceLocation getCellTypeKey(ECOCellType cellType) {
        ResourceLocation id = cellType.id();
        return id != null ? id : ResourceLocation.fromNamespaceAndPath(NeoECOAE.MOD_ID, "unknown");
    }

    /**
     * Returns a sort priority for stable UI ordering.
     * Items (0) always first, Fluids (1) second, other types (100+) sorted
     * by their full typeId string.
     */
    private static int storageTypeSortPriority(ResourceLocation id) {
        if (id.equals(NeoECOAE.id("items"))) {
            return 0;
        }
        if (id.equals(NeoECOAE.id("fluids"))) {
            return 1;
        }
        if (id.equals(NeoECOAE.id("infinite"))) {
            return 2;
        }
        return 100;
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        tickBuild(level);
        updateInfiniteStorageMode();
        flushInfiniteEngineBudgeted();
    }

    public long getStoredEnergy() {
        return storedEnergy;
    }

    public boolean isFormed() {
        return formed;
    }

    public long getMaxEnergy() {
        return maxEnergy;
    }

    // Scalar synced fields - written directly to avoid long[] array sync issues on client
    private long _synUsedTypes;
    private long _synTotalTypes;
    private long _synUsedBytes;
    private long _synTotalBytes;

    public long getTotalUsedBytes() {
        return _synUsedBytes;
    }

    public long getTotalBytes() {
        return _synTotalBytes;
    }

    public long getTotalUsedTypes() {
        return _synUsedTypes;
    }

    public long getTotalTypes() {
        return _synTotalTypes;
    }

    public Component getPreviewStatusComponent() {
        return buildPreviewStatusComponent();
    }

    // INEMultiblockBuildHost implementation

    @Override
    public BlockPos getHostPos() {
        return worldPosition;
    }

    @Override
    public BlockState getHostBlockState() {
        return getBlockState();
    }

    @Override
    public MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getStorageSystemDefinition(tier);
    }

    // Legacy public accessors

    public int getSelectedBuildLength() {
        return buildPreview.selectedBuildLength;
    }

    public int getPreviewMissingBlocks() {
        return buildPreview.previewMissingBlocks;
    }

    public int getPreviewConflictBlocks() {
        return buildPreview.previewConflictBlocks;
    }

    public int getPreviewReusedBlocks() {
        return buildPreview.previewReusedBlocks;
    }

    public int getPreviewRequiredItems() {
        return buildPreview.previewRequiredItems;
    }

    /**
     * Called by Drive block entities to notify the controller that storage
     * stats should be recalculated (cell inserted, removed, or content changed).
     * Only executes on the server side.
     */
    public void refreshStorageUiState() {
        if (level == null || level.isClientSide) {
            return;
        }
        markStorageStatsDirty();
    }

    /**
     * Marks the cached storage stats (per-type used/total bytes and types)
     * as stale. The next call to {@link #ensureStorageStatsCurrent()} will
     * recalculate from cluster drives and trigger a UI state push.
     */
    public void markStorageStatsDirty() {
        storageStatsDirty = true;
    }

    public boolean isStorageInterfaceOutputMode() {
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        return storageInterface != null && storageInterface.isStorageOutputMode();
    }

    public void onStorageInterfaceModeChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        markStorageStatsDirty();
        requestProviderUpdates();
        markForUpdate();
        setChanged();
    }

    @Nullable private ECOMachineInterfaceBlockEntity<NEStorageCluster> getStorageInterface() {
        return cluster == null ? null : cluster.getTheInterface();
    }

    private long exportStorageInterfaceContents() {
        if (!isStorageInterfaceOutputMode() || !formed || cluster == null) {
            return 0L;
        }
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        if (storageInterface == null || !storageInterface.getMainNode().isOnline()) {
            return 0L;
        }
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) {
            return 0L;
        }

        MEStorage target = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(storageInterface);
        long exported = 0L;
        int remainingKeys = STORAGE_INTERFACE_EXPORT_KEYS_PER_TICK;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            if (remainingKeys <= 0) {
                break;
            }
            IECOStorageCell cell = drive.getCellInventory();
            if (cell == null || !canExportDriveCell(drive)) {
                continue;
            }
            ExportResult result = exportFromStorageLimited(cell, target, source, remainingKeys);
            exported = saturatedAdd(exported, result.exported());
            remainingKeys -= result.keysVisited();
        }
        if (exported > 0L) {
            markStorageStatsDirty();
            setChanged();
            markForUpdate();
        }
        return exported;
    }

    private boolean canExportDriveCell(ECODriveBlockEntity drive) {
        if (!formed || cluster == null) {
            return false;
        }
        ItemStack stack = drive.getCellStack();
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (isInfiniteMemberCell(stack)) {
            return false;
        }
        IECOStorageCell cell = drive.getCellInventory();
        return cell != null && tier.compareTo(cell.getTier()) >= 0;
    }

    private ExportResult exportFromStorageLimited(
            MEStorage sourceStorage, MEStorage targetStorage, IActionSource source, int maxKeys) {
        if (maxKeys <= 0) {
            return new ExportResult(0L, 0);
        }
        appeng.api.stacks.KeyCounter available = new appeng.api.stacks.KeyCounter();
        sourceStorage.getAvailableStacks(available);
        long exported = 0L;
        int keysVisited = 0;
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (keysVisited >= maxKeys) {
                break;
            }
            long amount = entry.getLongValue();
            if (amount <= 0L) {
                continue;
            }
            keysVisited++;
            long moved = exportKey(sourceStorage, targetStorage, source, entry.getKey(), amount);
            if (moved > 0L) {
                exported = saturatedAdd(exported, moved);
            }
        }
        return new ExportResult(exported, keysVisited);
    }

    private long exportKey(
            MEStorage sourceStorage, MEStorage targetStorage, IActionSource source, AEKey key, long availableAmount) {
        long request = Math.max(0L, availableAmount);
        if (request <= 0L) {
            return 0L;
        }
        long accepted = targetStorage.insert(key, request, Actionable.SIMULATE, source);
        if (accepted <= 0L) {
            return 0L;
        }
        long extracted = sourceStorage.extract(key, Math.min(request, accepted), Actionable.MODULATE, source);
        if (extracted <= 0L) {
            return 0L;
        }
        long inserted = targetStorage.insert(key, extracted, Actionable.MODULATE, source);
        if (inserted < extracted) {
            long remainder = extracted - Math.max(0L, inserted);
            sourceStorage.insert(key, remainder, Actionable.MODULATE, source);
        }
        return Math.max(0L, inserted);
    }

    private void requestProviderUpdates() {
        if (cluster != null) {
            for (ECODriveBlockEntity drive : cluster.getDrives()) {
                drive.requestStorageProviderUpdate();
                drive.scheduleRenderUpdate();
            }
        }
    }

    // IPriorityHost implementation

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int newValue) {
        this.priority = newValue;
        setChanged();
        requestProviderUpdates();
        IStorageProvider.requestUpdate(getMainNode());
        markForUpdate();
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

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(getBlockState().getBlock().asItem());
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player instanceof ServerPlayer serverPlayer && level != null && !level.isClientSide && !isRemoved()) {
            BlockEntityUIFactory.INSTANCE.openUI(this, serverPlayer);
        }
    }

    private int getCellTypeCount() {
        var reg = NERegistries.cellTypeRegistry();
        return Math.max(reg != null ? reg.size() : 1, 1);
    }

    private record ExportResult(long exported, int keysVisited) {}

    private static long saturatedAdd(long left, long right) {
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE || right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    // increaseBuildLength / decreaseBuildLength are provided by INEMultiblockBuildHost default

    @Override
    public BuildPreviewState getBuildPreview() {
        return buildPreview;
    }

    @Override
    public void markPreviewDirty() {
        setChanged();
        markForUpdate();
    }

    // buildPreviewStatusComponent() is provided by INEMultiblockBuildHost default

    // NBT persistence
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("selectedBuildLength", getSelectedBuildLength());
        tag.putInt("priority", priority);
        tag.putString("infiniteHostMode", hostMode.id());
        if (infiniteDomainId != null) {
            tag.putUUID("infiniteDomainId", infiniteDomainId);
        }
        tag.put("infiniteComponentSlot", infiniteComponentHandler.serializeNBT());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        buildPreview.selectedBuildLength = Math.max(1, tag.getInt("selectedBuildLength"));
        priority = tag.getInt("priority");
        hostMode = ECOStorageHostMode.fromId(tag.getString("infiniteHostMode"));
        infiniteDomainId = tag.hasUUID("infiniteDomainId") ? tag.getUUID("infiniteDomainId") : null;
        if (tag.contains("infiniteComponentSlot")) {
            infiniteComponentHandler.deserializeNBT(tag.getCompound("infiniteComponentSlot"));
        }
        buildPreview.buildInProgress = false;
        buildPreview.resetPreview(BuildPreviewState.DEFAULT_STATUS_KEY);
    }

    // UI sync (Layer 1: chunk-load NBT)
    // getUpdateTag/handleUpdateTag/getUpdatePacket are provided by NEBlockEntity.
    // We only need to override writeUiSyncTag/readUiSyncTag.

    @Override
    protected void writeUiSyncTag(CompoundTag tag) {
        tag.putLong("neo_storedEnergy", storedEnergy);
        tag.putLong("neo_maxEnergy", maxEnergy);
        tag.putBoolean("neo_formed", formed);
        tag.putLong("neo_usedTypes_s", _synUsedTypes);
        tag.putLong("neo_totalTypes_s", _synTotalTypes);
        tag.putLong("neo_usedBytes_s", _synUsedBytes);
        tag.putLong("neo_totalBytes_s", _synTotalBytes);
        tag.putString("neo_infiniteHostMode", hostMode.id());
        tag.putInt(
                "neo_infiniteComponents",
                infiniteComponentHandler.getStackInSlot(0).getCount());
        tag.putBoolean("neo_infiniteCanTake", canTakeInfiniteStorageComponent());
        if (usedTypes != null) tag.putLongArray("neo_usedTypes", usedTypes);
        if (totalTypes != null) tag.putLongArray("neo_totalTypes", totalTypes);
        if (usedBytes != null) tag.putLongArray("neo_usedBytes", usedBytes);
        if (totalBytes != null) tag.putLongArray("neo_totalBytes", totalBytes);
        buildPreview.writeToTag(tag);
    }

    @Override
    protected void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_storedEnergy")) storedEnergy = tag.getLong("neo_storedEnergy");
        if (tag.contains("neo_maxEnergy")) maxEnergy = tag.getLong("neo_maxEnergy");
        if (tag.contains("neo_formed")) formed = tag.getBoolean("neo_formed");
        if (tag.contains("neo_usedTypes_s")) _synUsedTypes = tag.getLong("neo_usedTypes_s");
        if (tag.contains("neo_totalTypes_s")) _synTotalTypes = tag.getLong("neo_totalTypes_s");
        if (tag.contains("neo_usedBytes_s")) _synUsedBytes = tag.getLong("neo_usedBytes_s");
        if (tag.contains("neo_totalBytes_s")) _synTotalBytes = tag.getLong("neo_totalBytes_s");
        if (tag.contains("neo_infiniteHostMode"))
            hostMode = ECOStorageHostMode.fromId(tag.getString("neo_infiniteHostMode"));
        if (tag.contains("neo_usedTypes")) usedTypes = tag.getLongArray("neo_usedTypes");
        if (tag.contains("neo_totalTypes")) totalTypes = tag.getLongArray("neo_totalTypes");
        if (tag.contains("neo_usedBytes")) usedBytes = tag.getLongArray("neo_usedBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLongArray("neo_totalBytes");
        buildPreview.readFromTag(tag);
    }
}
