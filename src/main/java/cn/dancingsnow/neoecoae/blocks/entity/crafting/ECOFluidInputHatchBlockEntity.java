package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.util.IConfigManager;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.locator.MenuLocator;
import appeng.util.ConfigManager;
import com.lowdragmc.lowdraglib.gui.factory.BlockEntityUIFactory;
import appeng.menu.ISubMenu;
import cn.dancingsnow.neoecoae.menu.FluidHatchStockAmount;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerPlayer;
import cn.dancingsnow.neoecoae.gui.common.FluidInputHatchUI;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.util.LongFluidTank;
import cn.dancingsnow.neoecoae.compat.ae2.keyreuse.ECOKeyFactory;
import appeng.api.networking.security.IActionSource;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import net.minecraftforge.fluids.FluidStack;
import net.minecraft.world.item.ItemStack;
import java.util.List;
import cn.dancingsnow.neoecoae.platform.ui.NEBlockEntityUI;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public class ECOFluidInputHatchBlockEntity extends cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity<cn.dancingsnow.neoecoae.multiblock.cluster.NECraftingCluster, ECOFluidInputHatchBlockEntity> implements NEBlockEntityUI, IUpgradeableObject, InterfaceLogicHost {

    // Retain the public FluidTank descriptor for existing 1.20.1 addons.
    public FluidTank tank = new LongFluidTank() {
        @Override
        public int getCapacity() { return upgrades != null && hasNetworkPullCard() ? Integer.MAX_VALUE : 16_000; }

        @Override
        public int getTankCapacity(int index) { return getCapacity(); }

        @Override
        public int getSpace() {
            return upgrades != null && hasNetworkPullCard() ? super.getSpace()
                : (int) Math.max(0, 16_000L - getStoredAmount());
        }

        @Override
        protected void onContentsChanged() {
            setChanged();
            markForUpdate();
        }
    };

    private final IUpgradeInventory upgrades;
    private FluidStack markedFluid = FluidStack.EMPTY;
    private long targetAmount = 16_000;
    private GenericStackInv stockAmountConfig;
    private final IConfigManager stockMenuConfigManager = new ConfigManager(this::saveChanges);

    public ECOFluidInputHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState) {
        super(type, pos, blockState, cn.dancingsnow.neoecoae.multiblock.calculator.NECraftingClusterCalculator::new);
        upgrades = UpgradeInventories.forMachine(blockState.getBlock(), 1, this::saveChanges);
    }

    /** Stable addon handler view, matching the 1.21.1 accessor. */
    public FluidTank getFluidTankHandler() { return tank; }

    /** Exact amount in mB; the Forge handler view saturates at Integer.MAX_VALUE. */
    public long getStoredFluidAmount() { return ((LongFluidTank) tank).getStoredAmount(); }

    @Override
    public IUpgradeInventory getUpgrades() { return upgrades; }

    public boolean hasNetworkPullCard() { return upgrades.getInstalledUpgrades(NEItems.NETWORK_PULL_CARD) > 0; }
    public FluidStack getMarkedFluid() { return markedFluid.copy(); }
    public long getTargetAmount() { return targetAmount; }

    public void setMarkedFluid(FluidStack fluid) {
        markedFluid = fluid.isEmpty() ? FluidStack.EMPTY : new FluidStack(fluid, 1);
        saveChanges();
    }

    public void setTargetAmount(long amount) {
        if (amount < 0) return;
        targetAmount = amount;
        saveChanges();
    }

    public boolean canPlayerUse(Player player) {
        return level != null && !isRemoved() && player.level() == level
            && level.getBlockEntity(worldPosition) == this && player.blockPosition().distSqr(worldPosition) <= 64;
    }

    @Override public BlockEntity getBlockEntity() { return this; }
    @Override public IConfigManager getConfigManager() { return stockMenuConfigManager; }
    @Override public GenericStackInv getConfig() {
        if (stockAmountConfig == null) stockAmountConfig = FluidHatchStockAmount.configuration(this);
        return stockAmountConfig;
    }
    @Override public ItemStack getMainMenuIcon() { return new ItemStack(getBlockState().getBlock()); }
    @Override public int getPriority() { return 0; }
    @Override public void setPriority(int value) {}

    // InterfaceLogicHost is required by AE2's stock submenu, but this machine has no ME interface logic.
    @Override public InterfaceLogic getInterfaceLogic() {
        throw new UnsupportedOperationException("The fluid hatch only hosts the stock amount submenu");
    }
    @Override public GenericStackInv getStorage() {
        throw new UnsupportedOperationException("Use the hatch's long fluid tank");
    }
    @Override public void openMenu(Player player, MenuLocator locator) {
        if (player instanceof ServerPlayer serverPlayer && canPlayerUse(player)) {
            BlockEntityUIFactory.INSTANCE.openUI(this, serverPlayer);
        }
    }
    @Override public void returnToMainMenu(Player player, ISubMenu subMenu) {
        if (player.containerMenu == subMenu) openMenu(player, subMenu.getLocator());
    }

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        for (var upgrade : upgrades) drops.add(upgrade);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        upgrades.clear();
        tank.setFluid(FluidStack.EMPTY);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        if (hasNetworkPullCard()) {
            if (level.getGameTime() % 20 == 0 && getMainNode().isActive()) {
                var grid = getMainNode().getGrid();
                if (grid != null) FluidHatchNetworkPull.sync((LongFluidTank) tank,
                    markedFluid.isEmpty() ? null : ECOKeyFactory.fluid(markedFluid), targetAmount,
                    grid.getStorageService().getInventory(), grid.getEnergyService(), IActionSource.ofMachine(this));
            }
            return;
        }
        for (Direction face : Direction.values()) {
            IFluidHandler sourceHandler = cn.dancingsnow.neoecoae.platform.ForgeBlockCapabilities.find(level, pos.relative(face), ForgeCapabilities.FLUID_HANDLER, face.getOpposite());
            if (sourceHandler != null) {
                if (!FluidUtil.tryFluidTransfer(tank, sourceHandler, tank.getCapacity(), true).isEmpty()) {
                    return;
                }
            }
        }
    }

    public ModularUI createUI(Player player) {
        return FluidInputHatchUI.create(this, player).nativeUI(this);
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        tank.writeToNBT(data);
        upgrades.writeToNBT(data, "upgrades");
        data.put("markedFluid", markedFluid.writeToNBT(new CompoundTag()));
        data.putLong("targetAmount", targetAmount);
    }

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        tank.readFromNBT(data);
        upgrades.readFromNBT(data, "upgrades");
        markedFluid = FluidStack.loadFluidStackFromNBT(data.getCompound("markedFluid"));
        if (!markedFluid.isEmpty()) markedFluid.setAmount(1);
        targetAmount = data.contains("targetAmount") ? Math.max(0, data.getLong("targetAmount")) : 16_000;
    }
}
