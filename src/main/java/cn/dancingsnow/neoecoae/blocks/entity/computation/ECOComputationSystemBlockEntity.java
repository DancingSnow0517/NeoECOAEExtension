package cn.dancingsnow.neoecoae.blocks.entity.computation;

import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.orientation.OrientationStrategies;
import appeng.api.orientation.RelativeSide;
import cn.dancingsnow.neoecoae.all.NEMultiBlocks;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.me.network.ECOCraftingNetworkSettings;
import cn.dancingsnow.neoecoae.blocks.computation.ECOComputationSystem;
import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.gui.common.ECOModularUI;
import cn.dancingsnow.neoecoae.gui.common.GuideButton;
import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerPanelState;
import cn.dancingsnow.neoecoae.gui.computation.ComputationControllerUI;
import cn.dancingsnow.neoecoae.gui.computation.ComputationDriveEntry;
import cn.dancingsnow.neoecoae.gui.multiblock.MultiblockBuilderUI;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEComputationClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import cn.dancingsnow.neoecoae.multiblock.definition.MultiBlockDefinition;
import cn.dancingsnow.neoecoae.multiblock.network.NEFrequencyAllocator;
import cn.dancingsnow.neoecoae.multiblock.network.NELogicalNetworkManager;
import cn.dancingsnow.neoecoae.multiblock.placement.MultiBlockBuildController;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.syncdata.holder.blockentity.ISyncPersistRPCBlockEntity;
import com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

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
    @Persisted
    @DescSynced
    private int networkFrequency = NEFrequencyAllocator.DEFAULT_FREQUENCY;
    @Persisted
    private boolean ignorePatternSubstitutions;
    @Persisted
    @DescSynced
    private boolean fastCraftingPlannerEnabled = true;
    @Persisted
    @DescSynced
    private boolean cyclePlanningEnabled = true;
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
        ComputationControllerUI.Config panelConfig = createComputationPanelConfig(holder.player);
        UIElement root = ComputationControllerUI.create(panelConfig,
            this::getComputationControllerPanelState,
            GuideButton.create(holder.player, "neoecoae:neoecoae_intro/computation_system.md"),
            MultiblockBuilderUI.createInlineOpenButton(buildWindow),
            buildWindow, this::getTier);
        return ECOModularUI.create(root, holder.player);
    }

    private ComputationControllerUI.Config createComputationPanelConfig(Player player) {
        return new ComputationControllerUI.Config(
                () -> getMainNode().isOnline() && getMainNode().getGrid() != null,
                () -> cluster == null ? 1 : cluster.getNetworkMultiplier(),
                () -> cluster == null ? (hasNormalNetworkSwitch() ? 1 : 0) : cluster.getNormalSwitchHostCount(),
                () -> cluster == null ? (hasHighEnergyNetworkSwitch() ? 1 : 0) : cluster.getHighEnergySwitchHostCount(),
                this::getUsedComputationBytes,
                this::getTotalBytes,
                this::getAvailableBytes,
                this::getUsedThread,
                this::getTotalThread,
                this::getParallelCount,
                this::getFullParallelCount,
                this::getCpuSelectionMode,
                delta -> adjustCpuSelectionMode(player, delta),
                this::isIgnoringPatternSubstitutions,
                this::getSubstitutionPatternCount,
                () -> toggleIgnoringPatternSubstitutions(player),
                this::isCyclePlanningEnabled,
                () -> toggleCyclePlanning(player),
                this::isFastCraftingPlannerEnabled,
                () -> toggleFastCraftingPlanner(player),
                this::getNetworkFrequency,
                delta -> {
                    if (canPlayerInteract(player)) adjustNetworkFrequency(delta);
                },
                () -> new ItemStack(getBlockState().getBlock()),
                () -> getComputationUiDrives(true),
                () -> getComputationUiDrives(false),
                player,
                (position, expected, toInventory) -> ComputationCellUi.extract(this, player, position, expected, toInventory),
                (position, expected) -> ComputationCellUi.insert(this, player, position, expected),
                () -> level == null ? 0L : level.getGameTime(),
                () -> canPlayerInteract(player),
                this::collectDisplayedCpus,
                cpu -> {
                    if (canPlayerInteract(player)) {
                        cpu.getLogic().setJobSuspended(!cpu.getLogic().isJobSuspended());
                    }
                },
                cpu -> {
                    if (canPlayerInteract(player)) cpu.cancelJob();
                },
                this::getEndgameStatusTooltip
        );
    }

    Component getEndgameStatusTooltip() {
        List<String> blockers = cluster == null ? List.of("host_count") : cluster.getEndgameBlockers();
        if (blockers.isEmpty()) {
            return Component.translatable("gui.neoecoae.computation.endgame.active").withColor(0xFF55FF8A);
        }
        MutableComponent result = Component.translatable("gui.neoecoae.computation.endgame.title")
            .withColor(0xFFD8B4FE);
        int memberCount = cluster == null ? 1 : cluster.getNetworkMemberCount();
        for (String key : blockers) {
            MutableComponent reason = "host_count".equals(key)
                ? Component.translatable("gui.neoecoae.computation.endgame.reason.host_count", memberCount, 8)
                : Component.translatable("gui.neoecoae.computation.endgame.reason." + key);
            result.append("\n").append(reason.withColor(0xFFFF5555));
        }
        return result;
    }

    public ComputationControllerPanelState getComputationControllerPanelState() {
        if (!formed || cluster == null) {
            return ComputationControllerPanelState.UNFORMED;
        }
        Direction top = OrientationStrategies.horizontalFacing().getSide(getBlockState(), RelativeSide.TOP);
        Map<BlockPos, Integer> parallel = new HashMap<>();
        for (ECOComputationParallelCoreBlockEntity core : cluster.getParallelCores()) {
            parallel.put(core.getBlockPos(), core.getTier().getTier());
        }
        List<ECOComputationThreadingCoreBlockEntity> cores = cluster.getThreadingCores().stream()
            .sorted(Comparator.comparingDouble(core -> core.getBlockPos().distSqr(getBlockPos())))
            .limit(ComputationControllerPanelState.SLOT_COUNT)
            .toList();
        List<Integer> upper = new ArrayList<>(cores.size());
        List<Integer> mid = new ArrayList<>(cores.size());
        List<Integer> lower = new ArrayList<>(cores.size());
        for (ECOComputationThreadingCoreBlockEntity core : cores) {
            upper.add(parallel.getOrDefault(core.getBlockPos().relative(top), 0));
            mid.add(core.getTier().getTier());
            lower.add(parallel.getOrDefault(core.getBlockPos().relative(top.getOpposite()), 0));
        }
        return ComputationControllerPanelState.of(getTier().getTier(), upper, mid, lower, mirrored);
    }

    public List<ComputationDriveEntry> getComputationUiDrives(boolean upper) {
        if (!formed || cluster == null) return List.of();
        return ComputationDriveLayout.entries(upper ? cluster.getUpperDrives() : cluster.getLowerDrives(), getBlockPos(), mirrored);
    }

    /** The list and capacity readouts cover the same logical computation network. */
    private List<ECOCraftingCPU> collectDisplayedCpus() {
        if (cluster == null || !getMainNode().isOnline()) return List.of();
        var network = cluster.getNetworkCluster();
        var members = network == null ? List.of(cluster) : network.getMembers();
        List<ECOCraftingCPU> cpus = new ArrayList<>();
        for (NEComputationCluster member : members) {
            if (!member.isActive()) continue;
            cpus.addAll(member.getActiveCPUs());
            if (member.isNetworkRepresentative() && member.getActiveCPUCount() < member.getMaxThreads()) {
                cpus.add(member.getFakeCPU());
            }
        }
        return cpus;
    }

    public boolean isLocallyIgnoringPatternSubstitutions() {
        return ignorePatternSubstitutions;
    }

    public void applyNetworkIgnoringPatternSubstitutions(boolean value) {
        if (ignorePatternSubstitutions == value) return;
        ignorePatternSubstitutions = value;
        setChanged();
        markForUpdate();
    }

    private boolean isIgnoringPatternSubstitutions() {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        return settings != null
                ? settings.neoecoae$isIgnoringPatternSubstitutions()
                : ignorePatternSubstitutions;
    }

    private int getSubstitutionPatternCount() {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        return settings == null ? 0 : settings.neoecoae$getSubstitutionPatternCount();
    }

    private void toggleIgnoringPatternSubstitutions(Player player) {
        if (!canPlayerInteract(player)) return;
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        if (settings != null) {
            settings.neoecoae$setIgnoringPatternSubstitutions(
                    !settings.neoecoae$isIgnoringPatternSubstitutions());
        } else {
            applyNetworkIgnoringPatternSubstitutions(!ignorePatternSubstitutions);
        }
    }

    public boolean isLocallyFastCraftingPlannerEnabled() {
        return fastCraftingPlannerEnabled;
    }

    public void applyNetworkFastCraftingPlannerEnabled(boolean enabled) {
        if (fastCraftingPlannerEnabled == enabled) return;
        fastCraftingPlannerEnabled = enabled;
        setChanged();
        markForUpdate();
    }

    public boolean isLocallyCyclePlanningEnabled() {
        return cyclePlanningEnabled;
    }

    public void applyNetworkCyclePlanningEnabled(boolean enabled) {
        if (cyclePlanningEnabled == enabled) return;
        cyclePlanningEnabled = enabled;
        setChanged();
        markForUpdate();
    }

    public boolean isFastCraftingPlannerEnabled() {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        return settings != null
                ? settings.neoecoae$isFastPlannerEnabled()
                : fastCraftingPlannerEnabled;
    }

    private void toggleFastCraftingPlanner(Player player) {
        if (!canPlayerInteract(player)) return;
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        if (settings != null) {
            settings.neoecoae$setFastPlannerEnabled(!settings.neoecoae$isFastPlannerEnabled());
        } else {
            applyNetworkFastCraftingPlannerEnabled(!fastCraftingPlannerEnabled);
        }
    }

    public boolean isCyclePlanningEnabled() {
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        return settings != null
                ? settings.neoecoae$isCyclePlanningEnabled()
                : cyclePlanningEnabled;
    }

    private void toggleCyclePlanning(Player player) {
        if (!canPlayerInteract(player)) return;
        ECOCraftingNetworkSettings settings = ECOCraftingNetworkSettings.of(getMainNode().getGrid());
        if (settings != null) {
            settings.neoecoae$setCyclePlanningEnabled(!settings.neoecoae$isCyclePlanningEnabled());
        } else {
            applyNetworkCyclePlanningEnabled(!cyclePlanningEnabled);
        }
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

    private void adjustCpuSelectionMode(Player player, int delta) {
        if (!canPlayerInteract(player)) return;
        CpuSelectionMode[] modes = CpuSelectionMode.values();
        CpuSelectionMode mode = modes[Math.floorMod(getCpuSelectionMode().ordinal() + delta, modes.length)];
        if (cluster != null) {
            cluster.setSelectionMode(mode);
        } else {
            setCpuSelectionMode(mode);
        }
    }

    public boolean hasNormalNetworkSwitch() {
        BlockState state = getBlockState();
        return state.hasProperty(ECOComputationSystem.NETWORK_SWITCH) && state.getValue(ECOComputationSystem.NETWORK_SWITCH);
    }

    public boolean hasHighEnergyNetworkSwitch() {
        BlockState state = getBlockState();
        return state.hasProperty(ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH) && state.getValue(ECOComputationSystem.HIGH_ENERGY_NETWORK_SWITCH);
    }

    public boolean hasNetworkSwitch() {
        return hasNormalNetworkSwitch() || hasHighEnergyNetworkSwitch();
    }

    public boolean hasNetworkFrequency() {
        return networkFrequency >= 1 && networkFrequency <= NEFrequencyAllocator.FREQUENCY_COUNT;
    }

    public int getNetworkFrequency() {
        return hasNetworkFrequency() ? networkFrequency : NEFrequencyAllocator.DEFAULT_FREQUENCY;
    }

    /**
     * Manager-only: assigns a frequency to a newly-eligible host that has none yet.
     */
    public void assignNetworkFrequency(int frequency) {
        if (hasNetworkFrequency()) {
            return;
        }
        this.networkFrequency = NEFrequencyAllocator.normalize(frequency);
        setChanged();
        markForUpdate();
    }

    public void cycleNetworkFrequency(Player player) {
        if (!canPlayerInteract(player)) return;
        adjustNetworkFrequency(1);
    }

    public void adjustNetworkFrequency(int delta) {
        int current = hasNetworkFrequency() ? networkFrequency : NEFrequencyAllocator.DEFAULT_FREQUENCY;
        setNetworkFrequency(NEFrequencyAllocator.normalize(current + delta));
    }

    /**
     * Player-facing: manually reassigns this host's frequency, splitting/rejoining groups.
     */
    public void setNetworkFrequency(int frequency) {
        this.networkFrequency = NEFrequencyAllocator.normalize(frequency);
        setChanged();
        markForUpdate();
        if (cluster != null) {
            NELogicalNetworkManager.refresh(cluster);
        }
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (!isServerStopping()) {
            // Becoming online does not necessarily change the grid identity.
            onMainNodeGridChanged();
            if (cluster != null) {
                // Custom ECO CPUs are not discovered from the node state change by
                // AE2's native CPU cache; publish the transition explicitly.
                cluster.notifyGridCpuChange();
            }
        }
    }

    @Override
    protected void onMainNodeGridChanged() {
        if (cluster != null) {
            NELogicalNetworkManager.refreshAfterGridChange(cluster);
        }
    }

    private long getUsedComputationBytes() {
        return Math.max(getTotalBytes() - getAvailableBytes(), 0);
    }

    private int getUsedThread() {
        return cluster == null ? 0 : cluster.getActiveCPUCount();
    }

    private int getTotalThread() {
        return cluster == null ? 0 : cluster.getMaxThreads();
    }

    private int getParallelCount() {
        return cluster == null ? 0 : cluster.getPooledParallelism();
    }

    private int getFullParallelCount() {
        return cluster == null ? 0 : cluster.getPooledFullParallelism();
    }

    private long getAvailableBytes() {
        return cluster == null ? 0 : cluster.getAvailableStorage();
    }

    private long getTotalBytes() {
        return cluster == null ? 0 : cluster.getPooledTotalStorage();
    }

    /**
     * Fills empty computation drives from a held flash crystal matrix stack, up to one stack per use.
     */
    public int insertComputationCells(ItemStack stack) {
        if (cluster == null || stack.isEmpty()) {
            return 0;
        }

        int maxInsertions = Math.min(8, stack.getCount());
        int inserted = 0;
        for (var drive : cluster.getUpperDrives()) {
            if (inserted >= maxInsertions) {
                break;
            }
            if (drive.getCellStack() == null && drive.isItemValid(stack)) {
                drive.setCellStack(stack.copyWithCount(1));
                inserted++;
            }
        }
        for (var drive : cluster.getLowerDrives()) {
            if (inserted >= maxInsertions) {
                break;
            }
            if (drive.getCellStack() == null && drive.isItemValid(stack)) {
                drive.setCellStack(stack.copyWithCount(1));
                inserted++;
            }
        }
        return inserted;
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
