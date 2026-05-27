package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.client.gui.Icon;
import appeng.core.localization.Tooltips;
import appeng.api.networking.IGridNodeListener;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.gui.AETextures;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.NETextures;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.gui.util.WindowDragHelper;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyDisplay;
import dev.vfyjxf.taffy.style.TaffyPosition;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

public class ECOComputationSystemBlockEntity extends AbstractComputationBlockEntity<ECOComputationSystemBlockEntity> implements ISyncPersistRPCBlockEntity {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    private int usedThread;
    @DescSynced
    private int totalThread;
    @DescSynced
    private int parallelCount;
    @DescSynced
    private long availableBytes;
    @DescSynced
    private long totalBytes;
    @Persisted
    @DescSynced
    private int selectedBuildLength = 1;
    @DescSynced
    private int previewMissingBlocks;
    @DescSynced
    private int previewConflictBlocks;
    @DescSynced
    private int previewReusedBlocks;
    @DescSynced
    private int previewRequiredItems;
    @DescSynced
    private String previewStatusKey = "gui.neoecoae.multiblock.status.idle";
    @DescSynced
    private int previewStatusArg1;
    @DescSynced
    private int previewStatusArg2;
    @DescSynced
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;

    public ECOComputationSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState);
        this.tier = tier;
    }

    @Override
    public void updateState(boolean updateExposed) {
        super.updateState(updateExposed);
        if (updateExposed) {
            updateInfos();
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (reason != IGridNodeListener.State.GRID_BOOT && cluster != null && getMainNode().isActive()) {
            cluster.updateGridForChangedCpu(cluster);
        }
    }

    public void updateInfos() {
        if (cluster != null) {
            availableBytes = cluster.getAvailableStorage();
            totalBytes = 0;
            for (ECOComputationDriveBlockEntity drive : cluster.getUpperDrives()) {
                ItemStack cellStack = drive.getCellStack();
                if (cellStack != null && cellStack.getItem() instanceof ECOComputationCellItem cellItem) {
                    totalBytes += cellItem.getTier().getCPUTotalBytes();
                }
            }
            for (ECOComputationDriveBlockEntity drive : cluster.getLowerDrives()) {
                ItemStack cellStack = drive.getCellStack();
                if (cellStack != null && cellStack.getItem() instanceof ECOComputationCellItem cellItem) {
                    totalBytes += cellItem.getTier().getCPUTotalBytes();
                }
            }

            usedThread = cluster.getActiveCPUs().size();
            totalThread = cluster.getMaxThreads();
            parallelCount = cluster.getParallelCores().stream().mapToInt(e -> e.getTier().getCPUAccelerators()).sum();
        } else {
            totalThread = 0;
            parallelCount = 0;
            availableBytes = 0;
            totalBytes = 0;
        }
        setChanged();
        syncUiToClient();
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel serverLevel) || !buildInProgress || buildSession == null) {
            return;
        }

        ServerPlayer buildPlayer = buildPlayerId == null ? null : serverLevel.getServer().getPlayerList().getPlayer(buildPlayerId);
        if (buildPlayer == null) {
            int remainingBlocks = buildSession.getRemainingBlockCount();
            buildSession = null;
            buildPlayerId = null;
            buildInProgress = false;
            syncPreview(remainingBlocks, 0, previewReusedBlocks, previewRequiredItems, "gui.neoecoae.multiblock.status.builder_unavailable");
            return;
        }

        switch (MultiBlockPlacementService.tickBuild(serverLevel, buildSession, buildPlayer)) {
            case WAITING -> {
            }
            case ADVANCED -> syncPreview(
                buildSession.getRemainingBlockCount(),
                0,
                previewReusedBlocks,
                previewRequiredItems,
                "gui.neoecoae.multiblock.status.building",
                buildSession.getPlacedBlockCount(),
                buildSession.getTotalBlocks()
            );
            case COMPLETED -> {
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                rebuildMultiblock();
                syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            }
            case BLOCKED -> {
                int remainingBlocks = buildSession.getRemainingBlockCount();
                buildSession = null;
                buildPlayerId = null;
                buildInProgress = false;
                syncPreview(remainingBlocks, 1, previewReusedBlocks, previewRequiredItems, "gui.neoecoae.multiblock.status.build_interrupted");
            }
        }
    }

    public int getUsedThread() {
        return usedThread;
    }

    public boolean isFormed() {
        return formed;
    }

    public int getTotalThread() {
        return totalThread;
    }

    public int getParallelCount() {
        return parallelCount;
    }

    public long getAvailableBytes() {
        return availableBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public Component getPreviewStatusComponent() {
        return buildPreviewStatusComponent();
    }

    public int getSelectedBuildLength() {
        return selectedBuildLength;
    }

    public int getPreviewMissingBlocks() {
        return previewMissingBlocks;
    }

    public int getPreviewConflictBlocks() {
        return previewConflictBlocks;
    }

    public int getPreviewReusedBlocks() {
        return previewReusedBlocks;
    }

    public int getPreviewRequiredItems() {
        return previewRequiredItems;
    }

    public boolean isBuildInProgress() {
        return buildInProgress;
    }

    // ═══════════════════════════════════════════════════════════════
    // Multi-block builder methods (called from native Screen buttons)
    // ═══════════════════════════════════════════════════════════════

    public void increaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength = net.minecraft.util.Mth.clamp(selectedBuildLength + 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    public void decreaseBuildLength() {
        if (buildInProgress) {
            resetPreview("gui.neoecoae.multiblock.status.build_in_progress");
            return;
        }
        selectedBuildLength = net.minecraft.util.Mth.clamp(selectedBuildLength - 1, getMinBuildLength(), getMaxBuildLength());
        resetPreview("gui.neoecoae.multiblock.status.length_updated");
    }

    public void previewStructure(Player player) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress && buildSession != null) {
            syncPreview(buildSession.getRemainingBlockCount(), 0, previewReusedBlocks, previewRequiredItems, "gui.neoecoae.multiblock.status.building", buildSession.getPlacedBlockCount(), buildSession.getTotalBlocks());
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength = net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
            serverLevel,
            worldPosition,
            getBlockState(),
            definition,
            selectedBuildLength
        );
        boolean hasMaterials = player instanceof ServerPlayer serverPlayer
            && MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems());
        String statusKey = plan.getConflictPositions().isEmpty()
            ? (plan.getMissingBlocks().isEmpty()
                ? "gui.neoecoae.multiblock.status.structure_ready"
                : (hasMaterials ? "gui.neoecoae.multiblock.status.ready_to_build" : "gui.neoecoae.multiblock.status.not_enough_items"))
            : "gui.neoecoae.multiblock.status.conflicts_detected";
        syncPreview(
            plan.getMissingBlocks().size(),
            plan.getConflictPositions().size(),
            plan.getReusedBlockCount(),
            plan.getRequiredItemCount(),
            statusKey
        );
    }

    public void autoBuild(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        serverPlayer.closeContainer();
        if (formed) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.controller_formed");
            return;
        }
        if (buildInProgress) {
            syncPreview(previewMissingBlocks, previewConflictBlocks, previewReusedBlocks, previewRequiredItems, "gui.neoecoae.multiblock.status.build_already_in_progress");
            return;
        }
        MultiBlockDefinition definition = getBuildDefinition();
        if (definition == null) {
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.no_definition");
            return;
        }
        selectedBuildLength = net.minecraft.util.Mth.clamp(selectedBuildLength, definition.getExpandMin(), definition.getExpandMax());
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
            serverLevel,
            worldPosition,
            getBlockState(),
            definition,
            selectedBuildLength
        );
        if (!plan.getConflictPositions().isEmpty()) {
            syncPreview(
                plan.getMissingBlocks().size(),
                plan.getConflictPositions().size(),
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                "gui.neoecoae.multiblock.status.conflicts_detected"
            );
            return;
        }
        if (!serverPlayer.isCreative() && !MultiBlockPlacementService.hasRequiredItems(serverPlayer, plan.getRequiredItems())) {
            syncPreview(
                plan.getMissingBlocks().size(),
                0,
                plan.getReusedBlockCount(),
                plan.getRequiredItemCount(),
                "gui.neoecoae.multiblock.status.not_enough_items"
            );
            return;
        }
        if (plan.getMissingBlocks().isEmpty()) {
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        if (serverPlayer.isCreative()) {
            if (!MultiBlockPlacementService.buildInstant(serverLevel, plan)) {
                syncPreview(
                    plan.getMissingBlocks().size(),
                    plan.getConflictPositions().size(),
                    plan.getReusedBlockCount(),
                    plan.getRequiredItemCount(),
                    "gui.neoecoae.multiblock.status.build_failed"
                );
                return;
            }
            rebuildMultiblock();
            syncPreview(0, 0, 0, 0, "gui.neoecoae.multiblock.status.build_complete");
            return;
        }
        buildSession = MultiBlockPlacementService.createBuildSession(serverLevel, plan);
        buildPlayerId = serverPlayer.getUUID();
        buildInProgress = true;
        syncPreview(plan.getMissingBlocks().size(), 0, plan.getReusedBlockCount(), plan.getRequiredItemCount(), "gui.neoecoae.multiblock.status.building", buildSession.getPlacedBlockCount(), buildSession.getTotalBlocks());
    }

    private MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getComputationSystemDefinition(tier);
    }

    private int getMinBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMin();
    }

    private int getMaxBuildLength() {
        MultiBlockDefinition definition = getBuildDefinition();
        return definition == null ? 1 : definition.getExpandMax();
    }

    private void resetPreview(String statusKey) {
        syncPreview(0, 0, 0, 0, statusKey);
    }

    private void syncPreview(int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey) {
        syncPreview(missingBlocks, conflictBlocks, reusedBlocks, requiredItems, statusKey, 0, 0);
    }

    private void syncPreview(int missingBlocks, int conflictBlocks, int reusedBlocks, int requiredItems, String statusKey, int statusArg1, int statusArg2) {
        previewMissingBlocks = missingBlocks;
        previewConflictBlocks = conflictBlocks;
        previewReusedBlocks = reusedBlocks;
        previewRequiredItems = requiredItems;
        previewStatusKey = statusKey;
        previewStatusArg1 = statusArg1;
        previewStatusArg2 = statusArg2;
        setChanged();
        markForUpdate();
    }

    private Component buildPreviewStatusComponent() {
        if ("gui.neoecoae.multiblock.status.building".equals(previewStatusKey)) {
            return Component.translatable(previewStatusKey, previewStatusArg1, previewStatusArg2);
        }
        return Component.translatable(previewStatusKey);
    }

    // ── Client sync for Controller UI (LDLib2 sync bridge) ──

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        writeUiSyncTag(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        readUiSyncTag(tag);
    }

    private void syncUiToClient() {
        if (level != null && !level.isClientSide && getBlockPos() != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
        }
    }

    private void writeUiSyncTag(CompoundTag tag) {
        tag.putInt("neo_usedThread", usedThread);
        tag.putInt("neo_totalThread", totalThread);
        tag.putInt("neo_parallelCount", parallelCount);
        tag.putLong("neo_availableBytes", availableBytes);
        tag.putLong("neo_totalBytes", totalBytes);
    }

    private void readUiSyncTag(CompoundTag tag) {
        if (tag.contains("neo_usedThread")) usedThread = tag.getInt("neo_usedThread");
        if (tag.contains("neo_totalThread")) totalThread = tag.getInt("neo_totalThread");
        if (tag.contains("neo_parallelCount")) parallelCount = tag.getInt("neo_parallelCount");
        if (tag.contains("neo_availableBytes")) availableBytes = tag.getLong("neo_availableBytes");
        if (tag.contains("neo_totalBytes")) totalBytes = tag.getLong("neo_totalBytes");
    }
}
