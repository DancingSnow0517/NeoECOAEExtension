package cn.dancingsnow.neoecoae.blocks.entity.storage;

import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.all.NERegistries;
import cn.dancingsnow.neoecoae.all.NETags;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.blocks.storage.ECOStorageSystemBlock;
import cn.dancingsnow.neoecoae.blocks.entity.ECOMachineInterfaceBlockEntity;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostActionUI;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostHugeStackList;
import cn.dancingsnow.neoecoae.gui.storage.StorageHostPanelUI;
import cn.dancingsnow.neoecoae.gui.common.HostText;
import cn.dancingsnow.neoecoae.gui.storage.StoragePriority;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteDomainState;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorage;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageDomains;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageEngine;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMember;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOInfiniteStorageMigrationDiagnostics;
import cn.dancingsnow.neoecoae.impl.storage.infinite.ECOStorageHostMode;
import cn.dancingsnow.neoecoae.impl.storage.infinite.HugeAmount;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEStorageCluster;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.util.ServerTaskUtil;
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
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.hooks.ticking.TickHandler;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import net.minecraft.ChatFormatting;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

public class ECOStorageSystemBlockEntity extends AbstractStorageBlockEntity<ECOStorageSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, InternalInventoryHost, IStorageProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ECOStorageSystemBlockEntity.class);
    private static final int MAX_ORPHANED_KEY_TEXT_LENGTH = 180;
    private static final Set<ECOStorageSystemBlockEntity> ACTIVE_CONTROLLERS =
        Collections.newSetFromMap(new WeakHashMap<>());
    private static final int INFINITE_COMPONENT_REQUIRED = 64;
    private static final int LEGACY_INFINITE_MEMBER_REQUIRED = 16;
    private static final int INFINITE_MEMBER_REQUIRED = 12;
    private static final int STORAGE_INTERFACE_TRANSFER_KEYS_PER_TICK = 64;
    private static final long STORAGE_INTERFACE_TRANSFER_LIMIT = Long.MAX_VALUE;
    private static volatile Map<AEKeyType, Integer> registeredCellTypesByKeyType;
    private static final long PERFORMANCE_SAMPLE_WINDOW_TICKS = 20L * 3L;
    private static final long INFINITE_RESTORE_MARGIN_NUMERATOR = 95L;
    private static final long INFINITE_RESTORE_MARGIN_DENOMINATOR = 100L;
    private static final String INFINITE_COMPONENT_INVENTORY_PERSIST_KEY = "infiniteComponentInventory";
    private static final String LEGACY_COMPONENT_INVENTORY_PERSIST_KEY = "componentInventory";
    private static final String CONTROLLER_DOMAIN_TAG = "neoecoae_infinite_controller_domain";
    private static final String CONTROLLER_MODE_TAG = "neoecoae_infinite_controller_mode";
    private static final String CONTROLLER_MEMBER_REQUIREMENT_TAG = "neoecoae_infinite_controller_member_requirement";

    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Persisted
    @DescSynced
    private int selectedBuildLength = 11;
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
    // Zero denotes a legacy domain, which retains its original 16-matrix requirement.
    @Persisted
    @DescSynced
    private int infiniteMemberRequirement;
    @Persisted(key = INFINITE_COMPONENT_INVENTORY_PERSIST_KEY)
    @DescSynced
    private final AppEngInternalInventory infiniteComponentInventory = new AppEngInternalInventory(this, 1, INFINITE_COMPONENT_REQUIRED);
    private final IItemHandlerModifiable infiniteComponentItemHandler =
        new InfiniteComponentItemHandler((IItemHandlerModifiable) infiniteComponentInventory.toItemHandler());
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
    private transient Set<UUID> loggedConflictingMemberDomains = Set.of();
    @Nullable private transient UUID loggedMissingMemberDomain;
    @Nullable private transient ECOInfiniteDomainState lastInfiniteDomainState;
    private transient long lastInfiniteStorageRevision = Long.MIN_VALUE;
    private transient boolean storageProviderRefreshQueued;
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
        synchronized (ACTIVE_CONTROLLERS) {
            ACTIVE_CONTROLLERS.add(this);
        }
        getMainNode().setIdlePowerUsage(256 + (1 << (1 + 4 * tier.getTier())));
    }

    /** Refreshes AE2's mounted inventory after an administrator recovers this domain. */
    public static void refreshRecoveredDomain(ServerLevel level, UUID domainId) {
        List<ECOStorageSystemBlockEntity> controllers;
        synchronized (ACTIVE_CONTROLLERS) {
            controllers = List.copyOf(ACTIVE_CONTROLLERS);
        }
        for (ECOStorageSystemBlockEntity controller : controllers) {
            if (controller.level == level && domainId.equals(controller.infiniteDomainId)) {
                controller.refreshAfterDomainRecovery();
            }
        }
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

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        if (isServerStopping()) {
            return;
        }
        super.onMainNodeStateChanged(reason);
        if (reason == IGridNodeListener.State.POWER || reason == IGridNodeListener.State.GRID_BOOT) {
            queueStorageProviderRefresh();
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        long startNanos = System.nanoTime();
        try {
            updateInfiniteStorageMode();
            invalidateInfiniteStorageCacheIfChanged();
            if (level instanceof ServerLevel serverLevel && infiniteDomainId != null) {
                ECOInfiniteStorageDomains.pollPersistence(serverLevel, infiniteDomainId, level.getGameTime());
            }
            ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
            if (storageInterface != null) {
                storageInterface.recordStorageInterfaceTransfer(transferStorageInterfaceContents(storageInterface));
            }
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
        recoverInfiniteDomainOnUiOpen(holder.player);
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

    /** Reopens a closed or safely reloadable quarantined domain when it is inspected. */
    private void recoverInfiniteDomainOnUiOpen(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || infiniteDomainId == null) {
            return;
        }
        ECOInfiniteStorageEngine engine = ECOInfiniteStorageDomains.openExisting(serverLevel, infiniteDomainId);
        ECOInfiniteDomainState state = engine.getState();
        if (state != ECOInfiniteDomainState.CLOSED && state != ECOInfiniteDomainState.QUARANTINED) {
            return;
        }
        engine = ECOInfiniteStorageDomains.recover(serverLevel, infiniteDomainId);
        if (engine.getState() == ECOInfiniteDomainState.READY && engine.isHealthy()) {
            refreshRecoveredDomain(serverLevel, infiniteDomainId);
            reportRecoveredDomainContents(player, engine);
        } else {
            player.sendSystemMessage(Component.literal(
                "无限存储自动恢复失败：" + engine.getFailureReason().orElse(engine.getState().name())
            ).withStyle(ChatFormatting.RED));
        }
    }

    private void reportRecoveredDomainContents(Player player, ECOInfiniteStorageEngine engine) {
        List<ECOInfiniteStorageEngine.OrphanedStack> entries = engine.getOrphanedStacks().stream()
            .sorted(Comparator.comparing(entry -> describeEncodedKey(entry.encodedKey()).id()))
            .toList();
        if (entries.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                "无限存储已自动恢复；未检测到可定位的缺失模组物品。"
            ).withStyle(ChatFormatting.YELLOW));
            return;
        }

        player.sendSystemMessage(Component.literal(
            "无限存储已自动恢复，但检测到以下无法解析的物品条目："
        ).withStyle(ChatFormatting.YELLOW));
        for (ECOInfiniteStorageEngine.OrphanedStack entry : entries) {
            EncodedKeyDescription description = describeEncodedKey(entry.encodedKey());
            player.sendSystemMessage(Component.literal(
                " - [" + description.type() + "] " + description.id() + " x " + entry.amount()
            ).withStyle(ChatFormatting.RED));
        }
    }

    private static EncodedKeyDescription describeEncodedKey(CompoundTag encodedKey) {
        String type = encodedKey.getString("#");
        String id = encodedKey.getString("id");
        if (type.isBlank()) {
            type = "unknown";
        }
        if (id.isBlank()) {
            id = abbreviate(encodedKey.toString());
        }
        return new EncodedKeyDescription(type, id);
    }

    private static String abbreviate(String text) {
        return text.length() <= MAX_ORPHANED_KEY_TEXT_LENGTH
            ? text
            : text.substring(0, MAX_ORPHANED_KEY_TEXT_LENGTH - 3) + "...";
    }

    private record EncodedKeyDescription(String type, String id) {
    }

    private static void titleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.NONE).textColor(0x3f3d52).textShadow(false);
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
                        () -> getStorageUiSnapshot().storageTypeTotals(id).infiniteTypes(),
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
            this::getInfiniteDomainStatus,
            this::isInfiniteDomainFailed,
            this::shouldDisplayInfiniteStorageControls,
            this::canExtractInfiniteComponents,
            infiniteComponentItemHandler,
            () -> level.registryAccess(),
            this::getHugeStackUiEntries
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

    private boolean shouldDisplayInfiniteStorageControls() {
        return true;
    }

    private Component getInfiniteDomainStatus() {
        if (infiniteDomainId == null) {
            return Component.empty();
        }
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            return Component.translatable("gui.neoecoae.storage.status.domain_unavailable");
        }
        return switch (engine.getState()) {
            case LOADING -> Component.translatable("gui.neoecoae.storage.status.domain_loading");
            case MIGRATING_V1 -> Component.translatable("gui.neoecoae.storage.status.domain_migrating_v1");
            case QUARANTINED -> Component.translatable("gui.neoecoae.storage.status.domain_quarantined")
                .append(engine.getFailureReason().map(reason -> ": " + reason).orElse(""));
            case CLOSED -> Component.translatable("gui.neoecoae.storage.status.domain_closed");
            case READY -> engine.hasUnacknowledgedOrphanedEntries()
                ? Component.translatable(
                    "gui.neoecoae.storage.status.domain_orphaned",
                    engine.getOrphanedTypes()
                )
                : hostMode == ECOStorageHostMode.FORMED_INFINITE && !hasCompleteInfiniteRestoreSet()
                    ? Component.translatable(
                        "gui.neoecoae.storage.status.domain_missing_restore_parts",
                        Math.max(0, getInfiniteMemberRequirement() - countInfiniteMembers()),
                        getInfiniteMemberRequirement(),
                        Math.max(0, INFINITE_COMPONENT_REQUIRED - getInfiniteComponentCount()),
                        INFINITE_COMPONENT_REQUIRED
                    )
                : hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE
                    ? Component.translatable("gui.neoecoae.storage.status.domain_migrating_matrices")
                    : Component.empty();
        };
    }

    private boolean isInfiniteDomainFailed() {
        if (infiniteDomainId == null) {
            return false;
        }
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        return engine == null
            || engine.getState() == ECOInfiniteDomainState.QUARANTINED
            || engine.getState() == ECOInfiniteDomainState.CLOSED;
    }

    @Override
    public void saveChangedInventory(AppEngInternalInventory inv) {
        saveChanges();
    }

    @Override
    public void onChangeInventory(AppEngInternalInventory inv, int slot) {
        updateInfiniteStorageMode();
        onInfiniteRestorePartsChanged();
        saveChanges();
    }

    /** Re-evaluate the controller mount after a migrated matrix or infinite component is restored. */
    public void onInfiniteRestorePartsChanged() {
        if (level == null || level.isClientSide) {
            return;
        }
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        refreshDriveStorageProviders();
        setChanged();
        markForUpdate();
    }

    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null
                || engine.getState() != ECOInfiniteDomainState.READY
                || !engine.isHealthy()
                || !canUseHostDomainStorage()
                || isStorageInterfaceTransferMode()) {
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
            int cellTypeId = NERegistries.CELL_TYPE.getId(inv.getCellType());
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

            if (cellTypeId >= 0) {
                storageTypes.merge(
                    cellTypeId,
                    new StorageTypeTotals(
                        usedTypes,
                        totalTypes,
                        usedBytes,
                        totalBytes,
                        inv.hasInfiniteTypeCapacity()
                    ),
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
        Map<AEKeyType, Integer> cellTypesByKeyType = getRegisteredCellTypesByKeyType();
        for (ECOInfiniteStorageEngine.TypeStats stats : engine.getTypeStats()) {
            int cellTypeId = cellTypesByKeyType.getOrDefault(
                stats.keyType(),
                NERegistries.CELL_TYPE.getId(NECellTypes.OTHER.get())
            );
            BigInteger usedBytes = infiniteUsedBytes(stats);
            storageTypes.merge(
                cellTypeId,
                new StorageTypeTotals(
                    stats.storedTypes(),
                    0L,
                    usedBytes.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue(),
                    0L,
                    usedBytes,
                    true
                ),
                StorageTypeTotals::add
            );
        }
    }

    private static Map<AEKeyType, Integer> getRegisteredCellTypesByKeyType() {
        Map<AEKeyType, Integer> cached = registeredCellTypesByKeyType;
        if (cached != null) {
            return cached;
        }
        synchronized (ECOStorageSystemBlockEntity.class) {
            cached = registeredCellTypesByKeyType;
            if (cached != null) {
                return cached;
            }

            Map<AEKeyType, CellTypeCandidate> candidates = new HashMap<>();
            BuiltInRegistries.ITEM.stream().forEach(item -> {
                if (!(item instanceof IECOStorageCellItem cellItem)) {
                    return;
                }
                int cellTypeId = NERegistries.CELL_TYPE.getId(cellItem.getCellType());
                Set<AEKeyType> keyTypes = cellItem.getKeyTypes();
                // Universal/omni cells are a fallback for storage, not a specific UI category.
                if (cellTypeId < 0 || keyTypes.size() != 1) {
                    return;
                }
                CellTypeCandidate candidate = new CellTypeCandidate(cellTypeId, keyTypes.size());
                for (AEKeyType keyType : keyTypes) {
                    candidates.merge(keyType, candidate, CellTypeCandidate::moreSpecific);
                }
            });

            Map<AEKeyType, Integer> result = new HashMap<>();
            candidates.forEach((keyType, candidate) -> result.put(keyType, candidate.cellTypeId()));
            registeredCellTypesByKeyType = cached = Map.copyOf(result);
            return cached;
        }
    }

    private record CellTypeCandidate(int cellTypeId, int supportedKeyTypes) {
        private CellTypeCandidate moreSpecific(CellTypeCandidate other) {
            if (other.supportedKeyTypes < supportedKeyTypes) {
                return other;
            }
            if (other.supportedKeyTypes > supportedKeyTypes) {
                return this;
            }
            return other.cellTypeId < cellTypeId ? other : this;
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

    private record StorageTypeTotals(
        long usedTypes,
        long totalTypes,
        long usedBytes,
        long totalBytes,
        BigInteger displayUsedBytes,
        boolean infiniteTypes
    ) {
        private static final StorageTypeTotals EMPTY =
            new StorageTypeTotals(0L, 0L, 0L, 0L, BigInteger.ZERO, false);

        private StorageTypeTotals(
            long usedTypes,
            long totalTypes,
            long usedBytes,
            long totalBytes,
            boolean infiniteTypes
        ) {
            this(
                usedTypes,
                totalTypes,
                usedBytes,
                totalBytes,
                BigInteger.valueOf(Math.max(0L, usedBytes)),
                infiniteTypes
            );
        }

        private String infiniteBytesText() {
            return HostText.fitHugeAmount(displayUsedBytes, 62);
        }

        private String infiniteBytesTooltipText() {
            return HostText.compactStorageBytes(displayUsedBytes);
        }

        private StorageTypeTotals add(StorageTypeTotals other) {
            return new StorageTypeTotals(
                saturatedAdd(usedTypes, other.usedTypes),
                saturatedAdd(totalTypes, other.totalTypes),
                saturatedAdd(usedBytes, other.usedBytes),
                saturatedAdd(totalBytes, other.totalBytes),
                displayUsedBytes.add(other.displayUsedBytes),
                infiniteTypes || other.infiniteTypes
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
            delta -> changeStoragePriority(holder.player, delta),
            () -> level.registryAccess(),
            this::getHugeStackUiEntries
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
        invalidateStorageCache();
    }

    /**
     * A powered-down AE2 network keeps provider instances while its visible inventory cache can be stale.
     * Invalidate that cache after an infinite-domain mutation so it is rebuilt from the live SavedData engine.
     */
    private void invalidateInfiniteStorageCacheIfChanged() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        long revision = engine != null && engine.isLoaded() ? engine.getRevision() : Long.MIN_VALUE;
        if (revision == lastInfiniteStorageRevision) {
            return;
        }
        lastInfiniteStorageRevision = revision;
        invalidateStorageCache();
    }

    private void invalidateStorageCache() {
        getMainNode().ifPresent(grid -> grid.getStorageService().invalidateCache());
    }

    private void refreshAfterDomainRecovery() {
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        lastInfiniteDomainState = ECOInfiniteDomainState.READY;
        lastInfiniteStorageRevision = Long.MIN_VALUE;
        getMainNode().ifPresent(grid -> grid.getStorageService().invalidateCache());
        if (getMainNode().isOnline()) {
            IStorageProvider.requestUpdate(getMainNode());
        }
    }

    /** Defers a remount until AE2 has completed the current power/pathing transition. */
    private void queueStorageProviderRefresh() {
        if (!(level instanceof ServerLevel serverLevel) || storageProviderRefreshQueued) {
            return;
        }
        storageProviderRefreshQueued = true;
        ServerTaskUtil.executeIfServerRunning(serverLevel, () -> {
            storageProviderRefreshQueued = false;
            if (!isServerStopping() && !isRemoved() && getMainNode().isOnline()) {
                IStorageProvider.requestUpdate(getMainNode());
                invalidateStorageCache();
            }
        });
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
        tag.putInt(CONTROLLER_MEMBER_REQUIREMENT_TAG, getInfiniteMemberRequirement());
        drop.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public void restoreInfiniteDomainFromItem(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID(CONTROLLER_DOMAIN_TAG)) {
            return;
        }
        infiniteDomainId = tag.getUUID(CONTROLLER_DOMAIN_TAG);
        hostMode = ECOStorageHostMode.fromId(tag.getString(CONTROLLER_MODE_TAG));
        infiniteMemberRequirement = tag.getInt(CONTROLLER_MEMBER_REQUIREMENT_TAG);
        setChanged();
    }

    public boolean isInfiniteMode() {
        return hostMode.isInfiniteState();
    }

    public boolean isMigratingToInfinite() {
        return hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE;
    }

    public boolean isFormedInfiniteMode() {
        return hostMode == ECOStorageHostMode.FORMED_INFINITE && hasCompleteInfiniteRestoreSet();
    }

    private int getInfiniteMigrationProgressPercent() {
        if (!hostMode.isInfiniteState()) {
            return 0;
        }
        return Math.clamp(Math.round(countInfiniteMembers() * 100.0F / getInfiniteMemberRequirement()), 0, 100);
    }

    public boolean canUseHostDomainStorage() {
        return formed && isFormedInfiniteMode() && infiniteDomainId != null;
    }

    public boolean isInfiniteMemberCell(@Nullable ItemStack stack) {
        return stack != null && ECOInfiniteStorageMember.isMember(stack);
    }

    public void onStorageInterfaceModeChanged() {
        if (level == null || level.isClientSide) return;
        refreshDriveStorageProviders();
        setChanged();
        markForUpdate();
    }

    boolean isStorageInterfaceTransferMode() {
        ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface = getStorageInterface();
        return formed && storageInterface != null && storageInterface.isStorageTransferMode();
    }

    @Nullable
    private ECOMachineInterfaceBlockEntity<NEStorageCluster> getStorageInterface() {
        return cluster == null ? null : cluster.getTheInterface();
    }

    private long transferStorageInterfaceContents(ECOMachineInterfaceBlockEntity<NEStorageCluster> storageInterface) {
        if (!storageInterface.isStorageTransferMode() || !storageInterface.isTargetOnline()) {
            return 0L;
        }
        var grid = storageInterface.getMainNode().getGrid();
        if (grid == null) return 0L;
        MEStorage network = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.ofMachine(storageInterface);
        long moved;
        if (canUseHostDomainStorage()) {
            ECOInfiniteStorageEngine engine = getInfiniteEngine();
            if (engine == null) return 0L;
            MEStorage domain = new ECOInfiniteStorage(engine, getBlockState().getBlock().getName());
            moved = storageInterface.isStorageInputMode()
                ? transferLimited(network, domain, source, true)
                : transferLimited(domain, network, source, false);
        } else if (hostMode == ECOStorageHostMode.FORMED_NORMAL) {
            moved = transferFiniteStorageContents(
                network,
                grid.getEnergyService(),
                source,
                storageInterface.isStorageInputMode()
            );
        } else {
            return 0L;
        }
        if (moved > 0L) {
            storageUiSnapshotGameTime = Long.MIN_VALUE;
            setChanged();
            markForUpdate();
        }
        return moved;
    }

    private long transferFiniteStorageContents(
        MEStorage network,
        IEnergySource energy,
        IActionSource source,
        boolean inputMode
    ) {
        List<IECOStorageCell> cells = getFiniteStorageInterfaceCells();
        if (cells.isEmpty()) return 0L;
        return inputMode
            ? transferNetworkToFiniteCells(network, cells, energy, source)
            : transferFiniteCellsToNetwork(cells, network, energy, source);
    }

    private List<IECOStorageCell> getFiniteStorageInterfaceCells() {
        if (cluster == null) return List.of();
        List<IECOStorageCell> cells = new ArrayList<>();
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            IECOStorageCell cell = drive.getCellInventory();
            if (cell != null
                && tier.compareTo(cell.getTier()) >= 0
                && !isInfiniteMemberCell(drive.getCellStack())) {
                cells.add(cell);
            }
        }
        return cells;
    }

    private static long transferNetworkToFiniteCells(
        MEStorage network,
        List<IECOStorageCell> cells,
        IEnergySource energy,
        IActionSource source
    ) {
        KeyCounter available = new KeyCounter();
        network.getAvailableStacks(available);
        long remaining = STORAGE_INTERFACE_TRANSFER_LIMIT;
        int visited = 0;
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (isEffectivelyInfiniteSource(network, entry.getKey(), entry.getLongValue(), source)) continue;
            if (remaining <= 0L || visited++ >= STORAGE_INTERFACE_TRANSFER_KEYS_PER_TICK) break;
            long keyRemaining = Math.min(entry.getLongValue(), remaining);
            if (keyRemaining <= 0L) continue;
            for (IECOStorageCell cell : cells) {
                long moved = transferPowered(network, cell, entry.getKey(), keyRemaining, energy, source);
                remaining -= moved;
                keyRemaining -= moved;
                if (remaining <= 0L || keyRemaining <= 0L) break;
            }
        }
        return STORAGE_INTERFACE_TRANSFER_LIMIT - remaining;
    }

    private static long transferFiniteCellsToNetwork(
        List<IECOStorageCell> cells,
        MEStorage network,
        IEnergySource energy,
        IActionSource source
    ) {
        long remaining = STORAGE_INTERFACE_TRANSFER_LIMIT;
        int visited = 0;
        for (IECOStorageCell cell : cells) {
            KeyCounter available = new KeyCounter();
            cell.getAvailableStacks(available);
            for (Object2LongMap.Entry<AEKey> entry : available) {
                if (remaining <= 0L || visited++ >= STORAGE_INTERFACE_TRANSFER_KEYS_PER_TICK) {
                    return STORAGE_INTERFACE_TRANSFER_LIMIT - remaining;
                }
                long requested = Math.min(entry.getLongValue(), remaining);
                if (requested <= 0L) continue;
                remaining -= transferPowered(cell, network, entry.getKey(), requested, energy, source);
            }
        }
        return STORAGE_INTERFACE_TRANSFER_LIMIT - remaining;
    }

    private static long transferPowered(
        MEStorage from,
        MEStorage to,
        AEKey key,
        long amount,
        IEnergySource energy,
        IActionSource source
    ) {
        long extractable = from.extract(key, amount, Actionable.SIMULATE, source);
        if (extractable <= 0L) return 0L;
        long accepted = to.insert(key, extractable, Actionable.SIMULATE, source);
        if (accepted <= 0L) return 0L;
        long extracted = from.extract(key, accepted, Actionable.MODULATE, source);
        long inserted = StorageHelper.poweredInsert(energy, to, key, extracted, source);
        if (inserted < extracted) {
            from.insert(key, extracted - inserted, Actionable.MODULATE, source);
        }
        return inserted;
    }

    private static long transferLimited(MEStorage from, MEStorage to, IActionSource source, boolean skipInfiniteSourceEntries) {
        KeyCounter available = new KeyCounter();
        from.getAvailableStacks(available);
        long remaining = STORAGE_INTERFACE_TRANSFER_LIMIT;
        int visited = 0;
        for (Object2LongMap.Entry<AEKey> entry : available) {
            if (skipInfiniteSourceEntries
                && isEffectivelyInfiniteSource(from, entry.getKey(), entry.getLongValue(), source)) continue;
            if (remaining <= 0L || visited++ >= STORAGE_INTERFACE_TRANSFER_KEYS_PER_TICK) break;
            long amount = Math.min(entry.getLongValue(), remaining);
            if (amount <= 0L) continue;
            AEKey key = entry.getKey();
            long extractable = from.extract(key, amount, Actionable.SIMULATE, source);
            long accepted = to.insert(key, extractable, Actionable.SIMULATE, source);
            if (accepted <= 0L) continue;
            long extracted = from.extract(key, accepted, Actionable.MODULATE, source);
            long inserted = to.insert(key, extracted, Actionable.MODULATE, source);
            if (inserted < extracted) from.insert(key, extracted - inserted, Actionable.MODULATE, source);
            remaining -= inserted;
        }
        return STORAGE_INTERFACE_TRANSFER_LIMIT - remaining;
    }

    private static boolean isEffectivelyInfiniteSource(
        MEStorage storage,
        AEKey key,
        long visibleAmount,
        IActionSource source
    ) {
        if (storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source) == Long.MAX_VALUE) {
            return true;
        }

        long amountPerUnit = Math.max(1L, key.getAmountPerUnit());
        long conventionalInfiniteAmount = saturatedMultiply(Integer.MAX_VALUE, amountPerUnit);
        return visibleAmount >= conventionalInfiniteAmount;
    }

    private static long saturatedMultiply(long left, long right) {
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    private void updateInfiniteStorageMode() {
        if (level == null || level.isClientSide || isServerStopping()) {
            return;
        }
        ECOStorageHostMode previous = hostMode;
        UUID previousDomainId = infiniteDomainId;
        pollInfiniteStorageLoad();
        if (!formed || cluster == null) {
            if (!hostMode.isInfiniteState()) {
                hostMode = ECOStorageHostMode.UNFORMED;
            }
            syncInfiniteModeChanges(previous, previousDomainId);
            return;
        }
        recoverInfiniteDomainFromMembers();
        if (hostMode == ECOStorageHostMode.UNFORMED) {
            hostMode = ECOStorageHostMode.FORMED_NORMAL;
        }
        if (hostMode.isInfiniteState() && !hasRequiredInfiniteComponents()) {
            restoreInfiniteDomainToNormalStorageIfPossible();
            syncInfiniteModeChanges(previous, previousDomainId);
            return;
        }
        if (hostMode == ECOStorageHostMode.FORMED_NORMAL && canStartInfiniteMigration()) {
            infiniteMemberRequirement = INFINITE_MEMBER_REQUIRED;
            UUID domainId = ensureInfiniteDomainId();
            ServerLevel serverLevel = (ServerLevel) level;
            ECOInfiniteStorageEngine engine = ECOInfiniteStorageDomains.exists(serverLevel, domainId)
                ? ECOInfiniteStorageDomains.openExisting(serverLevel, domainId)
                : ECOInfiniteStorageDomains.create(serverLevel, domainId);
            if (engine.getState() == ECOInfiniteDomainState.READY && engine.isHealthy()) {
                hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
            } else {
                LOGGER.error(
                    "Unable to create infinite-storage domain {} at {}: {}",
                    domainId,
                    worldPosition,
                    engine.getFailureReason().orElse(engine.getState().name())
                );
            }
        }
        if (hostMode == ECOStorageHostMode.MIGRATING_TO_INFINITE) {
            runInfiniteMigrationStep();
        }
        syncInfiniteModeChanges(previous, previousDomainId);
        pollInfiniteStorageLoad();
    }

    private void pollInfiniteStorageLoad() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null) {
            lastInfiniteDomainState = null;
            return;
        }
        boolean completed = engine.tickLoad();
        ECOInfiniteDomainState currentState = engine.getState();
        if (completed || currentState != lastInfiniteDomainState) {
            // Notify AE2 to call mountInventories() so the terminal shows items immediately.
            storageUiSnapshotGameTime = Long.MIN_VALUE;
            refreshDriveStorageProviders();
        }
        lastInfiniteDomainState = currentState;
    }

    private void syncInfiniteModeChanges(ECOStorageHostMode previous, @Nullable UUID previousDomainId) {
        if (previous != hostMode || !Objects.equals(previousDomainId, infiniteDomainId)) {
            storageUiSnapshotGameTime = Long.MIN_VALUE;
            refreshDriveStorageProviders();
            setChanged();
            markForUpdate();
        }
    }

    private void recoverInfiniteDomainFromMembers() {
        if (infiniteDomainId != null || !(level instanceof ServerLevel serverLevel) || cluster == null) {
            return;
        }

        Set<UUID> memberDomains = new HashSet<>();
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ECOInfiniteStorageMember.getDomainId(drive.getCellStack()).ifPresent(memberDomains::add);
        }
        if (memberDomains.size() > 1) {
            if (!memberDomains.equals(loggedConflictingMemberDomains)) {
                LOGGER.error(
                    "Unable to recover ECO infinite storage domain at {} because its members reference multiple domains: {}",
                    worldPosition,
                    memberDomains
                );
                loggedConflictingMemberDomains = Set.copyOf(memberDomains);
            }
            return;
        }
        loggedConflictingMemberDomains = Set.of();
        if (memberDomains.isEmpty()) {
            loggedMissingMemberDomain = null;
            return;
        }

        UUID recoveredDomainId = memberDomains.iterator().next();
        if (!ECOInfiniteStorageDomains.exists(serverLevel, recoveredDomainId)) {
            if (!recoveredDomainId.equals(loggedMissingMemberDomain)) {
                LOGGER.error(
                    "Unable to recover ECO infinite storage domain {} at {} because its persisted data is missing",
                    recoveredDomainId,
                    worldPosition
                );
                loggedMissingMemberDomain = recoveredDomainId;
            }
            return;
        }

        loggedMissingMemberDomain = null;
        infiniteDomainId = recoveredDomainId;
        if (!hostMode.isInfiniteState()) {
            hostMode = ECOStorageHostMode.MIGRATING_TO_INFINITE;
        }
        LOGGER.warn(
            "Recovered ECO infinite storage domain {} at {} from its storage matrix members after controller data loss",
            recoveredDomainId,
            worldPosition
        );
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

    /**
     * The controller item keeps the domain ID, but the domain is unavailable until its migrated
     * matrices and required infinite components are restored to this host.
     */
    private boolean hasCompleteInfiniteRestoreSet() {
        return countInfiniteMembers() >= getInfiniteMemberRequirement() && hasRequiredInfiniteComponents();
    }

    private int getInfiniteComponentCount() {
        ItemStack stack = infiniteComponentInventory.getStackInSlot(0);
        return isInfiniteComponent(stack) ? Math.min(stack.getCount(), INFINITE_COMPONENT_REQUIRED) : 0;
    }

    private void runInfiniteMigrationStep() {
        if (!(level instanceof ServerLevel serverLevel) || cluster == null) {
            return;
        }
        UUID domainId = ensureInfiniteDomainId();
        ECOInfiniteStorageEngine engine = ECOInfiniteStorageDomains.openExisting(serverLevel, domainId);
        if (engine.getState() != ECOInfiniteDomainState.READY || !engine.isHealthy()) {
            return;
        }
        boolean hasPending = false;
        for (ECODriveBlockEntity drive : cluster.getDrives()) {
            ItemStack stack = drive.getCellStack();
            IECOStorageCell cell = drive.getCellInventory();
            if (stack == null || stack.isEmpty() || cell == null || cell.getTier() != ECOTier.L9) {
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
        if (!hasPending && countInfiniteMembers() >= getInfiniteMemberRequirement()) {
            hostMode = ECOStorageHostMode.FORMED_INFINITE;
        }
    }

    private int getInfiniteMemberRequirement() {
        return infiniteMemberRequirement > 0 ? infiniteMemberRequirement : LEGACY_INFINITE_MEMBER_REQUIRED;
    }

    private void migrateDriveToDomain(ECODriveBlockEntity drive, IECOStorageCell cell, ECOInfiniteStorageEngine engine, UUID domainId) {
        ItemStack sourceStack = drive.getCellStack();
        if (sourceStack == null || sourceStack.isEmpty()) {
            return;
        }
        // OmniCells keeps its inventory in world SavedData. Flush the delegate before taking the
        // migration snapshot so the transfer receipt describes the latest durable source state.
        cell.persist();
        // The matrix identity must survive conversion to a member and later retries. A block position is not
        // unique when a domain is moved between dimensions or a drive is replaced.
        ECOInfiniteStorageMember.ensureMatrixId(sourceStack);
        drive.setChanged();
        KeyCounter available = new KeyCounter();
        cell.getAvailableStacks(available);
        List<ECOInfiniteStorageEngine.HugeStack> pending = new ArrayList<>();
        for (Object2LongMap.Entry<AEKey> entry : available) {
            long amount = entry.getLongValue();
            if (amount > 0L) {
                UUID legacyTransactionId = migrationTransactionId(
                    domainId,
                    drive,
                    entry.getKey(),
                    amount,
                    "to-domain"
                );
                if (!engine.hasLegacyTransferReceipt(legacyTransactionId)
                        && !engine.hasTransferReceipt(legacyTransactionId)) {
                    pending.add(new ECOInfiniteStorageEngine.HugeStack(entry.getKey(), HugeAmount.of(amount)));
                }
            }
        }
        UUID transactionId = matrixMigrationTransactionId(domainId, drive);
        UUID legacyTransactionId = legacyMatrixMigrationTransactionId(domainId, drive);
        ECOInfiniteStorageMigrationDiagnostics.log(
            "migration-snapshot:" + domainId + ':' + transactionId,
            "stage=migration_snapshot position=" + drive.getBlockPos() + " domain=" + domainId
                + " transaction=" + transactionId + " matrix="
                + ECOInfiniteStorageMember.getMatrixId(sourceStack).orElse(null)
                + " keyCount=" + pending.size() + " contents=" + describeMigrationContents(pending)
        );
        boolean applied;
        if (pending.isEmpty()) {
            applied = true;
        } else if (engine.hasTransferReceipt(legacyTransactionId)) {
            // Preserve a batch receipt written by the pre-v3 implementation when its digest matches.
            applied = engine.applyTransferOnce(legacyTransactionId, pending);
        } else {
            applied = engine.applyTransferOnce(transactionId, pending);
        }
        if (!applied) {
            ECOInfiniteStorageMigrationDiagnostics.log(
                "migration-rejected:" + domainId + ':' + transactionId,
                "stage=migration_result reason=target_rejected position=" + drive.getBlockPos()
                    + " domain=" + domainId + " transaction=" + transactionId
                    + " keyCount=" + pending.size()
            );
            LOGGER.error(
                "Unable to durably migrate ECO storage matrix at {} into infinite domain {}; keeping the source cell",
                drive.getBlockPos(),
                domainId
            );
            return;
        }
        if (!drainMigratedSource(cell, pending)) {
            LOGGER.error(
                "Unable to durably clear ECO storage matrix at {}; keeping the source cell",
                drive.getBlockPos()
            );
            return;
        }
        drive.convertCellToInfiniteMember(domainId);
        IStorageProvider.requestUpdate(drive.getMainNode());
        storageUiSnapshotGameTime = Long.MIN_VALUE;
        setChanged();
        markForUpdate();
    }

    /**
     * Drain exactly the snapshot that was transferred. Using the same delegate instance is important for
     * world-backed cells such as OmniCells; constructing a second handler can observe a different cache.
     */
    private boolean drainMigratedSource(IECOStorageCell cell, List<ECOInfiniteStorageEngine.HugeStack> pending) {
        if (pending.isEmpty()) {
            return true;
        }
        List<ECOInfiniteStorageEngine.HugeStack> drained = new ArrayList<>();
        for (ECOInfiniteStorageEngine.HugeStack stack : pending) {
            long expected = stack.amount().toLongSaturated();
            long removed = cell.extract(stack.key(), expected, Actionable.MODULATE, IActionSource.empty());
            if (removed != expected) {
                for (ECOInfiniteStorageEngine.HugeStack rollback : drained) {
                    cell.insert(rollback.key(), rollback.amount().toLongSaturated(), Actionable.MODULATE, IActionSource.empty());
                }
                cell.persist();
                return false;
            }
            drained.add(new ECOInfiniteStorageEngine.HugeStack(stack.key(), HugeAmount.of(removed)));
        }
        cell.persist();
        KeyCounter remaining = new KeyCounter();
        cell.getAvailableStacks(remaining);
        if (!remaining.isEmpty()) {
            for (ECOInfiniteStorageEngine.HugeStack rollback : drained) {
                cell.insert(rollback.key(), rollback.amount().toLongSaturated(), Actionable.MODULATE, IActionSource.empty());
            }
            cell.persist();
            return false;
        }
        return true;
    }

    private static String describeMigrationContents(List<ECOInfiniteStorageEngine.HugeStack> contents) {
        int previewLimit = 8;
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < contents.size() && index < previewLimit; index++) {
            ECOInfiniteStorageEngine.HugeStack stack = contents.get(index);
            if (index > 0) {
                result.append(", ");
            }
            result.append(stack.key()).append('=').append(stack.amount());
        }
        if (contents.size() > previewLimit) {
            result.append(", ... +").append(contents.size() - previewLimit).append(" keys");
        }
        return result.append(']').toString();
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
        if (!engine.isLoaded()) {
            return RestorePlan.blocked("infinite storage domain is still loading");
        }
        if (!engine.isHealthy()) {
            return RestorePlan.blocked("infinite storage domain is degraded and requires recovery");
        }
        if (engine.hasOrphanedEntries()) {
            return RestorePlan.blocked("infinite storage contains entries from missing mods");
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
        Set<UUID> expectedReceiptIds = new HashSet<>();
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
                expectedReceiptIds.add(transactionId);
                ECODriveBlockEntity.RestoreReceipt receipt =
                    target.drive().getRestoreReceiptDetails(transactionId);
                if (receipt == null && target.drive().hasRestoreReceipt(transactionId)) {
                    return RestorePlan.blocked("a target matrix contains an unverifiable restore receipt");
                }
                if (receipt != null) {
                    IECOStorageCell actualCell = target.drive().getCellInventory();
                    if (actualCell == null
                            || receipt.amount() > remaining
                            || !restoreReceiptMatches(actualCell, key, receipt)) {
                        return RestorePlan.blocked("a target matrix no longer matches its restore receipt");
                    }
                    remaining -= receipt.amount();
                }
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
        for (RestoreTarget target : targets) {
            if (target.drive().hasUnexpectedRestoreReceipts(expectedReceiptIds)) {
                return RestorePlan.blocked("a target matrix has receipts for different source contents");
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
            if (simulatedCell != null && simulatedCell.getTier() == ECOTier.L9) {
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
            used = saturatedAdd(used, cell.getUsedBytes());
            total = saturatedAdd(total, cell.getTotalBytes());
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
        if (engine == null || !engine.isLoaded() || engine.isEmpty()) {
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
                ECODriveBlockEntity.RestoreReceipt receipt =
                    target.drive().getRestoreReceiptDetails(transactionId);
                if (receipt == null && target.drive().hasRestoreReceipt(transactionId)) {
                    LOGGER.error("Unverifiable restore receipt in matrix {}", target.drive().getBlockPos());
                    return;
                }
                long inserted;
                if (receipt != null) {
                    if (receipt.amount() > remaining || !restoreReceiptMatches(cell, key, receipt)) {
                        LOGGER.error("Restore receipt no longer matches matrix {}", target.drive().getBlockPos());
                        return;
                    }
                    inserted = receipt.amount();
                } else {
                    inserted = insertForRestore(cell, key, remaining, Actionable.MODULATE, source);
                    if (inserted > 0L) {
                        long postAmount = getCellStoredAmount(cell, key);
                        if (postAmount < inserted) {
                            LOGGER.error("Unable to verify restore write in matrix {}", target.drive().getBlockPos());
                            return;
                        }
                        target.drive().putRestoreReceipt(transactionId, inserted, postAmount);
                    }
                }
                remaining -= inserted;
                if (remaining <= 0L) {
                    break;
                }
            }
            if (remaining > 0L) {
                LOGGER.warn("ECO infinite storage restore changed during execution; keeping domain {} mounted", infiniteDomainId);
                engine.flushAndAwait();
                return;
            }
        }
        serverLevel.getChunkSource().save(true);
        if (!verifyRestoredContents(plan.targets(), expectedFinalAmounts)) {
            LOGGER.error(
                "Unable to verify restored ECO storage contents for domain {}; keeping the domain mounted",
                infiniteDomainId
            );
            engine.flushAndAwait();
            return;
        }
        if (!verifyRestoreReceipts(plan.targets(), pending)) {
            LOGGER.error(
                "Unable to verify restored ECO storage receipts for domain {}; keeping the source domain",
                infiniteDomainId
            );
            return;
        }
        for (Object2LongMap.Entry<AEKey> entry : pending) {
            long amount = engine.getAmount(entry.getKey()).toLongSaturated();
            if (amount > 0L) {
                engine.extract(entry.getKey(), amount, Actionable.MODULATE);
            }
        }
        engine.flushAndAwait();
        if (!engine.isHealthy()) {
            LOGGER.error("Unable to durably finish restoring infinite domain {}; keeping it mounted", infiniteDomainId);
            return;
        }
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
                alreadyRestored = saturatedAdd(alreadyRestored, target.drive().getRestoreReceipt(transactionId));
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

    private boolean verifyRestoreReceipts(List<RestoreTarget> targets, KeyCounter restored) {
        for (Object2LongMap.Entry<AEKey> entry : restored) {
            AEKey key = entry.getKey();
            long expected = entry.getLongValue();
            long receipted = 0L;
            for (RestoreTarget target : targets) {
                UUID transactionId = migrationTransactionId(
                    infiniteDomainId,
                    target.drive(),
                    key,
                    expected,
                    "from-domain"
                );
                ECODriveBlockEntity.RestoreReceipt receipt =
                    target.drive().getRestoreReceiptDetails(transactionId);
                if (receipt == null) {
                    if (target.drive().hasRestoreReceipt(transactionId)) {
                        return false;
                    }
                    continue;
                }
                IECOStorageCell cell = target.drive().getCellInventory();
                if (cell == null || !restoreReceiptMatches(cell, key, receipt)) {
                    return false;
                }
                try {
                    receipted = Math.addExact(receipted, receipt.amount());
                } catch (ArithmeticException e) {
                    return false;
                }
            }
            if (receipted != expected) {
                return false;
            }
        }
        return true;
    }

    private boolean restoreReceiptMatches(
        IECOStorageCell cell,
        AEKey key,
        ECODriveBlockEntity.RestoreReceipt receipt
    ) {
        return receipt.amount() > 0L && getCellStoredAmount(cell, key) == receipt.postAmount();
    }

    private long getCellStoredAmount(IECOStorageCell cell, AEKey key) {
        KeyCounter contents = new KeyCounter();
        cell.getAvailableStacks(contents);
        return contents.get(key);
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

    private UUID matrixMigrationTransactionId(UUID domainId, ECODriveBlockEntity drive) {
        UUID matrixId = ECOInfiniteStorageMember.getMatrixId(drive.getCellStack())
            .orElseThrow(() -> new IllegalStateException("Infinite-storage matrix has no persistent identity"));
        String dimension = level.dimension().location().toString();
        String value = domainId + ":to-domain-v3:" + dimension + ":" + matrixId;
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private UUID legacyMatrixMigrationTransactionId(UUID domainId, ECODriveBlockEntity drive) {
        String value = domainId + ":to-domain-v2:" + drive.getBlockPos().asLong();
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private void exitInfiniteModeIfSafe() {
        ECOInfiniteStorageEngine engine = getInfiniteEngine();
        if (engine == null || !engine.isLoaded() || !engine.isHealthy() || !engine.isEmpty()) {
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
        loadLegacyInfiniteComponentInventory(data, registries);
        hostMode = ECOStorageHostMode.fromId(data.getString("infiniteHostMode"));
        infiniteDomainId = data.hasUUID("infiniteDomainId") ? data.getUUID("infiniteDomainId") : null;
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

    @Override
    public void onChunkUnloaded() {
        synchronized (ACTIVE_CONTROLLERS) {
            ACTIVE_CONTROLLERS.remove(this);
        }
        closeInfiniteEngine();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        synchronized (ACTIVE_CONTROLLERS) {
            ACTIVE_CONTROLLERS.remove(this);
        }
        closeInfiniteEngine();
        super.setRemoved();
    }

    private void closeInfiniteEngine() {
        if (level instanceof ServerLevel serverLevel && infiniteDomainId != null) {
            ECOInfiniteStorageDomains.close(serverLevel, infiniteDomainId);
        }
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

    private void increaseBuildLength(Player player) {
        if (!canPlayerInteract(player)) return;
        if (buildInProgress) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        setChanged();
        markForUpdate();
    }

    private void decreaseBuildLength(Player player) {
        if (!canPlayerInteract(player)) return;
        if (buildInProgress) {
            return;
        }
        selectedBuildLength = Math.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        setChanged();
        markForUpdate();
    }

    private void autoBuild(Player player) {
        if (!canPlayerInteract(player)) return;
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
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan, serverPlayer)) {
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
        if (!canPlayerInteract(player)) return;
        if (buildInProgress) {
            return;
        }
        this.mirrorBuild = mirrorBuild;
        setChanged();
        markForUpdate();
    }

    private boolean canPlayerInteract(Player player) {
        return level != null && ECOStorageSystemBlock.isPlayerCloseEnough(level, worldPosition, player);
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
