package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.core.localization.Tooltips;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.gui.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.NEStyleSheets;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostMetric;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostStyles;
import cn.dancingsnow.neoecoae.gui.widget.ECOHostWidgets;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildSession;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementPlan;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockPlacementService;
import cn.dancingsnow.neoecoae.util.ComponentUtil;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ECOComputationSystemBlockEntity extends AbstractComputationBlockEntity<ECOComputationSystemBlockEntity> implements ISyncPersistRPCBlockEntity {
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
    @DescSynced
    private boolean buildInProgress;
    private transient MultiBlockBuildSession buildSession;
    private transient UUID buildPlayerId;
    @Setter
    private boolean mirrored;

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
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOComputationSystem.MIRRORED)) {
                level.setBlock(
                    worldPosition,
                    state.setValue(ECOComputationSystem.MIRRORED, formed && mirrored),
                    Block.UPDATE_CLIENTS
                );
            }
        }
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
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
            case WAITING -> {
            }
            case ADVANCED -> {
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
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement buildWindow = buildPanel(holder);

        UIElement details = ECOHostWidgets.detailArea(false);
        ECOHostWidgets.addDetailChild(details, ECOHostWidgets.sectionTitle("gui.neoecoae.host.computation.capacity"));
        ECOHostWidgets.addDetailChild(details, ECOHostWidgets.tileRow(List.of(
            ECOHostWidgets.tile("gui.neoecoae.host.computation.active_vcpu", () -> Component.literal(String.valueOf(getUsedThread()))),
            ECOHostWidgets.tile("gui.neoecoae.host.computation.max_vcpu", () -> Component.literal(String.valueOf(getTotalThread()))),
            ECOHostWidgets.tile("gui.neoecoae.host.computation.parallel_count", () -> Component.literal(String.valueOf(getParallelCount()))),
            ECOHostWidgets.tile("gui.neoecoae.host.computation.free_memory", () -> {
                Tooltips.Amount byteAmount = Tooltips.getByteAmount(Math.max(getAvailableBytes(), 0));
                return Component.literal(byteAmount.digit()+byteAmount.unit());
            }
        ))));

        UIElement root = ECOHostWidgets.hostPanel(
            () -> getItemFromBlockEntity().getName(getItemFromBlockEntity().getDefaultInstance()),
            () -> Component.translatable("gui.neoecoae.host.computation.subtitle"),
            () -> Component.translatable(buildInProgress ? "gui.neoecoae.host.status.running" : "gui.neoecoae.host.status.online"),
            List.of(
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.computation.cpu_storage"),
                    () -> ComponentUtil.coloredBytesPair(getUsedComputationBytes(), getTotalBytes(), false),
                    () -> ECOHostStyles.ratio(getUsedComputationBytes(), getTotalBytes())
                ),
                ECOHostMetric.ratio(
                    () -> Component.translatable("gui.neoecoae.host.computation.thread_usage"),
                    () -> ComponentUtil.coloredNumberPair(getUsedThread(), getTotalThread(), false),
                    () -> ECOHostStyles.ratio(getUsedThread(), getTotalThread())
                ),
                ECOHostMetric.scalar(
                    () -> Component.translatable("gui.neoecoae.host.computation.parallel_count"),
                    () -> Component.literal(String.valueOf(getParallelCount()))
                )
            ),
            details,
            () -> Component.translatable("gui.neoecoae.host.computation.footer"),
            buildWindow
        );
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private long getUsedComputationBytes() {
        return Math.max(getTotalBytes() - getAvailableBytes(), 0);
    }

    private int getUsedThread() {
        return cluster == null ? 0 : cluster.getActiveCPUs().size();
    }

    private int getTotalThread() {
        return cluster == null ? 0 : cluster.getMaxThreads();
    }

    private int getParallelCount() {
        return cluster == null ? 0 : cluster.getParallelCores().stream().mapToInt(e -> e.getTier().getCPUAccelerators()).sum();
    }

    private long getAvailableBytes() {
        return cluster == null ? 0 : cluster.getAvailableStorage();
    }

    private long getTotalBytes() {
        if (cluster == null) {
            return 0;
        }
        long total = 0;
        for (ECOComputationDriveBlockEntity drive : cluster.getUpperDrives()) {
            total += getDriveBytes(drive);
        }
        for (ECOComputationDriveBlockEntity drive : cluster.getLowerDrives()) {
            total += getDriveBytes(drive);
        }
        return total;
    }

    private static long getDriveBytes(ECOComputationDriveBlockEntity drive) {
        ItemStack cellStack = drive.getCellStack();
        if (cellStack != null && cellStack.getItem() instanceof ECOComputationCellItem cellItem) {
            return cellItem.getTier().getCPUTotalBytes();
        }
        return 0;
    }

    private UIElement buildPanel(BlockUIMenuType.BlockUIHolder holder) {
        return MultiblockBuilderUI.createFloatingPanel(new MultiblockBuilderUI.Config(
            holder.player,
            () -> selectedBuildLength,
            () -> mirrorBuild,
            mirror -> setMirrorBuild(holder.player, mirror),
            () -> decreaseBuildLength(holder.player),
            () -> increaseBuildLength(holder.player),
            () -> autoBuild(holder.player),
            () -> formed,
            () -> buildInProgress,
            this::createLocalPreviewPlan
        ));
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
        MultiBlockPlacementPlan plan = MultiBlockPlacementService.preview(
            serverLevel,
            worldPosition,
            getBlockState(),
            definition,
            selectedBuildLength,
            mirrorBuild
        );
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
