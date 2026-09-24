package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.ISubMenu;
import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuLocator;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.all.NEBlocks;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import cn.dancingsnow.neoecoae.multiblock.calculator.NEClusterCalculator;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEIntegratedWorkingStationCluster;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Network-facing pattern storage for the large workstation. */
public final class ECOLargeIntegratedWorkingStationInterfaceBlockEntity
        extends ECOMachineInterfaceBlockEntity<NEIntegratedWorkingStationCluster>
        implements PatternProviderLogicHost {
    private final LargeWorkstationPatternProvider workstationProvider;
    private boolean providerRefreshQueued;
    private Object lastRecipeManager;

    public ECOLargeIntegratedWorkingStationInterfaceBlockEntity(BlockEntityType<?> type, BlockPos pos,
            BlockState blockState, NEClusterCalculator.Factory<NEIntegratedWorkingStationCluster> calculator) {
        super(type, pos, blockState, calculator);
        workstationProvider = new LargeWorkstationPatternProvider(this);
        getMainNode().setFlags(GridFlags.MULTIBLOCK, GridFlags.REQUIRE_CHANNEL);
    }

    public LargeWorkstationPatternProvider getWorkstationProvider() {
        return workstationProvider;
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        workstationProvider.writeToNBT(tag);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        workstationProvider.readFromNBT(tag);
        if (!tag.contains(PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS, Tag.TAG_LIST)
                && tag.contains("workstationPatterns", Tag.TAG_LIST)
                && workstationProvider.getPatternInv().isEmpty()) {
            var oldPatterns = new AppEngInternalInventory(36);
            oldPatterns.readFromNBT(tag, "workstationPatterns");
            for (int slot = 0; slot < oldPatterns.size(); slot++) {
                workstationProvider.getPatternInv().setItemDirect(slot, oldPatterns.getStackInSlot(slot));
            }
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
        refreshProvider();
    }

    @Override
    public void tick() {
        super.tick();
        if (level instanceof ServerLevel serverLevel) {
            workstationProvider.updateRedstoneState();
            Object recipes = serverLevel.getRecipeManager();
            if (lastRecipeManager != recipes) {
                lastRecipeManager = recipes;
                refreshProvider();
            }
        }
    }

    @Override
    public void updateCluster(NEIntegratedWorkingStationCluster cluster) {
        super.updateCluster(cluster);
        refreshProvider();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (workstationProvider != null) {
            workstationProvider.onMainNodeStateChanged();
            refreshProvider();
        }
    }

    private void refreshProvider() {
        if (workstationProvider == null || !(level instanceof ServerLevel serverLevel)
                || !getMainNode().isReady()) return;
        workstationProvider.updatePatterns();
        if (providerRefreshQueued) return;
        providerRefreshQueued = true;
        serverLevel.getServer().executeIfPossible(() -> {
            providerRefreshQueued = false;
            if (!isRemoved() && level == serverLevel && getMainNode().isReady()) {
                workstationProvider.updatePatterns();
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
    public void openMenu(Player player, MenuLocator locator) {
        MenuOpener.open(LargeIntegratedWorkingStationPatternProviderMenu.TYPE, player, locator);
    }

    @Override
    public void returnToMainMenu(Player player, ISubMenu subMenu) {
        MenuOpener.returnTo(LargeIntegratedWorkingStationPatternProviderMenu.TYPE, player, subMenu.getLocator());
    }
}
