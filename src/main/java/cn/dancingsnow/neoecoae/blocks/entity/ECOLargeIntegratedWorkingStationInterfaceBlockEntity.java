package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;

import java.util.EnumSet;
import java.util.List;

/** The only machine-interface variant that exposes AE2 pattern-provider behavior. */
public final class ECOLargeIntegratedWorkingStationInterfaceBlockEntity
    extends ECOMachineInterfaceBlockEntity<NEIntegratedWorkingStationCluster>
    implements PatternProviderLogicHost {

    private final LargeWorkstationPatternProvider workstationProvider;
    private transient boolean workstationProviderRefreshQueued;

    public ECOLargeIntegratedWorkingStationInterfaceBlockEntity(
        BlockEntityType<?> type,
        BlockPos pos,
        BlockState blockState,
        NEClusterCalculator.Factory<NEIntegratedWorkingStationCluster> calculator
    ) {
        super(type, pos, blockState, calculator);
        workstationProvider = new LargeWorkstationPatternProvider(this);
        // PatternProviderLogic configures the shared node for a normal provider.
        // Restore the multiblock flag required by this interface's cluster.
        getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL);
    }

    public LargeWorkstationPatternProvider getWorkstationProvider() {
        return workstationProvider;
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        workstationProvider.writeToNBT(data, registries);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        workstationProvider.readFromNBT(data, registries);
        readLegacyWorkstationPatterns(data, registries);
    }

    private void readLegacyWorkstationPatterns(CompoundTag data, HolderLookup.Provider registries) {
        if (data.contains(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS, Tag.TAG_LIST)
            || !data.contains("workstationPatterns", Tag.TAG_LIST)
            || !workstationProvider.getPatternInv().isEmpty()) {
            return;
        }

        var legacy = new AppEngInternalInventory(this, workstationProvider.getPatternInv().size(), 1);
        legacy.readFromNBT(data, "workstationPatterns", registries);
        for (int slot = 0; slot < legacy.size(); slot++) {
            workstationProvider.getPatternInv().setItemDirect(slot, legacy.getStackInSlot(slot));
        }
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        workstationProvider.addDrops(drops);
    }

    @Override
    public void onReady() {
        super.onReady();
        refreshWorkstationProviderLifecycle();
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel) {
            workstationProvider.updateRedstoneState();
        }
    }

    @Override
    protected void onInterfaceTopologyChanged() {
        refreshWorkstationProviderLifecycle();
    }

    @Override
    protected boolean handleSpecializedMainNodeStateChanged(IGridNodeListener.State reason) {
        if (workstationProvider == null) {
            return true;
        }
        workstationProvider.onMainNodeStateChanged();
        if (reason == IGridNodeListener.State.POWER || reason == IGridNodeListener.State.GRID_BOOT) {
            refreshWorkstationProviderLifecycle();
        }
        return true;
    }

    /**
     * Rebuilds the dynamic workstation provider view after the node and the multiblock have had a chance to settle.
     * The immediate refresh covers normal placement, while the deferred refresh covers world-load ordering where the
     * provider can initially be mounted before the controller or its final AE2 grid is available.
     */
    private void refreshWorkstationProviderLifecycle() {
        LargeWorkstationPatternProvider provider = workstationProvider;
        if (provider == null || !(level instanceof ServerLevel serverLevel)
            || isServerStopping() || !getMainNode().isReady()) {
            return;
        }

        provider.updatePatterns();
        if (workstationProviderRefreshQueued) {
            return;
        }

        workstationProviderRefreshQueued = true;
        serverLevel.getServer().executeIfPossible(() -> {
            workstationProviderRefreshQueued = false;
            if (!isServerStopping() && !isRemoved() && level == serverLevel
                && getMainNode().isReady() && workstationProvider == provider) {
                provider.updatePatterns();
            }
        });
    }

    @Override
    public PatternProviderLogic getLogic() {
        return workstationProvider;
    }

    @Override
    public ECOLargeIntegratedWorkingStationInterfaceBlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public void saveChanges() {
        setChanged();
        markForUpdate();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.asItem());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return NEBlocks.LARGE_INTEGRATED_WORKING_STATION_INTERFACE.asStack();
    }

    @Override
    public void openMenu(Player player, MenuHostLocator locator) {
        MenuOpener.open(LargeIntegratedWorkingStationPatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(LargeIntegratedWorkingStationPatternProviderMenu.TYPE, player, subMenu.getLocator());
    }
}
