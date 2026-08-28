package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPULogic;
import cn.dancingsnow.neoecoae.api.me.ElapsedTimeTracker;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.gui.task.ComputationTaskEntry;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.gui.computation.ComputationHostPanelUI;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.common.HostSideButtonBar;
import cn.dancingsnow.neoecoae.gui.multiblock.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.gui.theme.NEStyleSheets;
import cn.dancingsnow.neoecoae.items.ECOComputationCellItem;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ECOComputationSystemBlockEntity extends NEBlockEntity<NEComputationCluster, ECOComputationSystemBlockEntity>
    implements ISyncPersistRPCBlockEntity, MultiBlockBuildController.Host {
    @Getter
    private final FieldManagedStorage syncStorage = new FieldManagedStorage(this);

    @Getter
    private final IECOTier tier;

    @Persisted
    @DescSynced
    private int selectedBuildLength = NEConfig.computationSystemMaxLength - 4;
    @Persisted
    @DescSynced
    private boolean mirrorBuild;
    @Persisted
    @DescSynced
    private int cpuSelectionMode = CpuSelectionMode.ANY.ordinal();
    @DescSynced
    private boolean buildInProgress;
    private final MultiBlockBuildController buildController = new MultiBlockBuildController(this);
    // Transient derived state rebuilt by the calculator; the BlockState property is render-only persistence.
    @Setter
    private boolean mirrored;

    public ECOComputationSystemBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        IECOTier tier
    ) {
        super(type, pos, blockState, NEComputationClusterCalculator::new);
        this.tier = tier;
    }

    @Override
    public void updateState(boolean updateExposed) {
        if (isServerStopping()) {
            return;
        }
        super.updateState(updateExposed);
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(ECOComputationSystem.MIRRORED)) {
                BlockState newState = state.setValue(ECOComputationSystem.MIRRORED, formed && mirrored);
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
        buildController.tick(level);
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        UIElement buildWindow = buildPanel(holder);
        ComputationHostPanelUI.Config panelConfig = createComputationPanelConfig(holder.player);

        UIElement root = new UIElement().layout(layout -> layout
            .width(340)
            .height(232)
            .flexDirection(FlexDirection.COLUMN))
            .addClasses("panel_bg", "eco-computation-host");

        UIElement header = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .height(18)
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.CENTER));
        header.addChild(new TextElement()
            .setText(getItemFromBlockEntity().getDescription())
            .textStyle(ECOComputationSystemBlockEntity::titleTextStyle)
            .layout(layout -> layout.flex(1)));
        root.addChild(header);

        UIElement panels = new UIElement().layout(layout -> layout
            .widthPercent(100)
            .height(ComputationHostPanelUI.PANEL_HEIGHT)
            .flexDirection(FlexDirection.ROW)
            .alignItems(AlignItems.STRETCH)
            .gapAll(10));
        UIElement leftColumn = new UIElement().layout(layout -> {
            layout.width(ComputationHostPanelUI.LEFT_PANEL_WIDTH);
            layout.height(ComputationHostPanelUI.PANEL_HEIGHT);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(4);
        });
        leftColumn.addChild(ComputationHostPanelUI.createLeftCapacityPanel(panelConfig));
        leftColumn.addChild(ComputationHostPanelUI.createInventoryPanel());
        panels.addChild(leftColumn);
        panels.addChild(ComputationHostPanelUI.createRightPanel(panelConfig));

        root.addChild(panels);
        root.addChild(HostSideButtonBar.left(
            GuideButton.create(holder.player, "neoecoae:neoecoae_intro/computation_system.md"),
            MultiblockBuilderUI.createInlineOpenButton(buildWindow),
            ComputationHostPanelUI.createCpuSelectionButton(panelConfig)
        ));
        root.addChild(buildWindow);
        return new ModularUI(UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(NEStyleSheets.ECO))), holder.player);
    }

    private static void titleTextStyle(TextElement.TextStyle style) {
        style.adaptiveHeight(true).adaptiveWidth(true).textWrap(TextWrap.HOVER_ROLL).textColor(0x3f3d52).textShadow(false);
    }

    private ComputationHostPanelUI.Config createComputationPanelConfig(Player player) {
        return new ComputationHostPanelUI.Config(
            this::getUsedComputationBytes,
            this::getTotalBytes,
            this::getAvailableBytes,
            this::getUsedThread,
            this::getTotalThread,
            this::getParallelCount,
            this::getCpuSelectionMode,
            () -> cycleCpuSelectionMode(player),
            this::getRegistryAccessForUi,
            this::getActiveTaskEntries
        );
    }

    public CpuSelectionMode getCpuSelectionMode() {
        CpuSelectionMode[] values = CpuSelectionMode.values();
        if (cpuSelectionMode < 0 || cpuSelectionMode >= values.length) {
            return CpuSelectionMode.ANY;
        }
        return values[cpuSelectionMode];
    }

    public void setCpuSelectionMode(CpuSelectionMode mode) {
        this.cpuSelectionMode = mode.ordinal();
        setChanged();
        markForUpdate();
    }

    private void cycleCpuSelectionMode(Player player) {
        if (!canPlayerInteract(player)) return;
        if (cluster != null) {
            cluster.cycleSelectionMode();
        } else {
            setCpuSelectionMode(nextCpuSelectionMode(getCpuSelectionMode()));
        }
    }

    private static CpuSelectionMode nextCpuSelectionMode(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> CpuSelectionMode.PLAYER_ONLY;
            case PLAYER_ONLY -> CpuSelectionMode.MACHINE_ONLY;
            case MACHINE_ONLY -> CpuSelectionMode.ANY;
        };
    }

    private HolderLookup.Provider getRegistryAccessForUi() {
        if (level != null) {
            return level.registryAccess();
        }
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
            .getServerResources()
            .managers()
            .fullRegistries()
            .get();
    }

    private List<ComputationTaskEntry> getActiveTaskEntries() {
        if (cluster == null) {
            return List.of();
        }
        List<ComputationTaskEntry> tasks = new ArrayList<>();
        int index = 0;
        for (ECOCraftingCPU cpu : cluster.getActiveCPUs()) {
            ComputationTaskEntry entry = createTaskEntry(cpu, index);
            if (entry != null) {
                tasks.add(entry);
            }
            index++;
        }
        return List.copyOf(tasks);
    }

    private @Nullable ComputationTaskEntry createTaskEntry(ECOCraftingCPU cpu, int index) {
        if (cpu == null) {
            return null;
        }
        ECOCraftingCPULogic logic = cpu.getLogic();
        GenericStack finalOutput = logic.getFinalJobOutput();
        if (finalOutput == null && cpu.getPlan() != null) {
            finalOutput = cpu.getPlan().finalOutput();
        }
        long requestedAmount = finalOutput != null ? finalOutput.amount() : 0L;
        long remainingAmount = logic.getRemainingJobOutputAmount();
        if (remainingAmount <= 0L && finalOutput != null) {
            remainingAmount = requestedAmount;
        }
        if (finalOutput == null || remainingAmount <= 0L || !(finalOutput.what() instanceof AEItemKey itemKey)) {
            return null;
        }
        ItemStack output = itemKey.toStack(1);
        if (output.isEmpty()) {
            return null;
        }
        ElapsedTimeTracker tracker = logic.getElapsedTimeTracker();
        long total = Math.max(1L, tracker.getSyntheticStartItemCount());
        long remaining = Math.max(0L, Math.min(total, tracker.getSyntheticRemainingItemCount()));
        ComputationTaskEntry.Status status = !logic.hasJob() || logic.isCantStoreItems() || logic.isJobSuspended()
            ? ComputationTaskEntry.Status.WAITING_OUTPUT
            : ComputationTaskEntry.Status.RUNNING;
        return new ComputationTaskEntry(
            computationTaskId(cpu, finalOutput, index),
            output,
            requestedAmount,
            1L,
            total,
            remaining,
            status,
            index + 1,
            cpu.getName(),
            cpu.getAvailableStorage(),
            cpu.getCoProcessors(),
            cpu.getSelectionMode(),
            Math.clamp(tracker.getProgress(), 0.0F, 1.0F),
            tracker.getElapsedTime()
        );
    }

    private static String computationTaskId(ECOCraftingCPU cpu, GenericStack output, int index) {
        BlockPos ownerPos = cpu.getOwner() != null ? cpu.getOwner().getBlockPos() : null;
        String owner = ownerPos != null ? Long.toString(ownerPos.asLong()) : "proxy";
        return "cpu:" + owner + ":" + index + ":" + output.what().hashCode();
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
        return cluster == null ? 0 : cluster.getCPUAccelerators();
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
            mirror -> buildController.setMirrorBuild(holder.player, mirror),
            () -> buildController.decreaseBuildLength(holder.player),
            () -> buildController.increaseBuildLength(holder.player),
            () -> buildController.autoBuild(holder.player),
            () -> formed,
            () -> buildInProgress,
            buildController::createLocalPreviewPlan
        ));
    }

    @Override
    public @Nullable MultiBlockDefinition getBuildDefinition() {
        return NEMultiBlocks.getComputationSystemDefinition(tier);
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
        return level != null && ECOComputationSystem.isPlayerCloseEnough(level, worldPosition, player);
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
