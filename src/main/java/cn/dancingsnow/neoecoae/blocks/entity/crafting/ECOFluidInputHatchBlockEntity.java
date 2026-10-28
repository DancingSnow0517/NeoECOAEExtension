package cn.dancingsnow.neoecoae.blocks.entity.crafting;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.util.IConfigManager;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.ISubMenu;
import appeng.menu.locator.MenuHostLocator;
import appeng.util.ConfigManager;
import cn.dancingsnow.neoecoae.all.NEItems;
import cn.dancingsnow.neoecoae.gui.common.FluidHatchUI;
import cn.dancingsnow.neoecoae.menu.FluidHatchStockAmount;
import cn.dancingsnow.neoecoae.util.LongFluidTank;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import java.util.List;

public class ECOFluidInputHatchBlockEntity extends ECOFluidInputHatchLegacyFields implements IUpgradeableObject, InterfaceLogicHost {

    /**
     * Retained for addons compiled against 21.2.11; the superclass retains the older descriptor.
     * @deprecated Use {@link #getFluidTankHandler()} and {@link #getStoredFluidAmount()}.
     * Read and operate on the buffer; replacing either public field reference is unsupported.
     */
    @Deprecated
    public LongFluidTank tank = new LongFluidTank() {
        @Override
        public int getCapacity() {
            return upgrades != null && hasNetworkPullCard() ? Integer.MAX_VALUE : 16_000;
        }

        @Override
        public int getTankCapacity(int tank) { return getCapacity(); }

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
        super(type, pos, blockState);
        super.tank = tank;
        upgrades = UpgradeInventories.forMachine(blockState.getBlock(), 1, this::saveChanges);
    }

    /** Stable addon handler view. Its int-sized quantities saturate at {@link Integer#MAX_VALUE}. */
    public FluidTank getFluidTankHandler() { return tank; }

    /** Exact amount in mB, including stock above the handler's int-sized view. */
    public long getStoredFluidAmount() { return tank.getStoredAmount(); }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        if (hasNetworkPullCard()) {
            if (level.getGameTime() % 20 == 0 && getMainNode().isActive()) {
                var grid = getMainNode().getGrid();
                if (grid != null) FluidHatchNetworkPull.sync(tank,
                    markedFluid.isEmpty() ? null : AEFluidKey.of(markedFluid), targetAmount,
                    grid.getStorageService().getInventory(), grid.getEnergyService(), IActionSource.ofMachine(this));
            }
            return;
        }
        for (Direction face : Direction.values()) {
            IFluidHandler sourceHandler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos.relative(face), face.getOpposite());
            if (sourceHandler != null) {
                if (!FluidUtil.tryFluidTransfer(tank, sourceHandler, tank.getCapacity(), true).isEmpty()) {
                    return;
                }
            }
        }
    }

    public ModularUI createUI(BlockUIMenuType.BlockUIHolder holder) {
        return FluidHatchUI.createInputHatchUI(holder, this);
    }

    @Override
    public IUpgradeInventory getUpgrades() { return upgrades; }

    public boolean hasNetworkPullCard() { return upgrades.getInstalledUpgrades(NEItems.NETWORK_PULL_CARD) > 0; }
    public FluidStack getMarkedFluid() { return markedFluid.copy(); }
    public long getTargetAmount() { return targetAmount; }

    public void setMarkedFluid(FluidStack fluid) {
        markedFluid = fluid.isEmpty() ? FluidStack.EMPTY : fluid.copyWithAmount(1);
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
    @Override public void openMenu(Player player, MenuHostLocator locator) {
        if (player instanceof ServerPlayer serverPlayer && canPlayerUse(player)) BlockUIMenuType.openUI(serverPlayer, worldPosition);
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

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        tank.writeToNBT(registries, data);
        upgrades.writeToNBT(data, "upgrades", registries);
        data.put("markedFluid", markedFluid.saveOptional(registries));
        data.putLong("targetAmount", targetAmount);
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        tank.readFromNBT(registries, data);
        upgrades.readFromNBT(data, "upgrades", registries);
        markedFluid = FluidStack.parseOptional(registries, data.getCompound("markedFluid"));
        targetAmount = data.contains("targetAmount") ? Math.max(0, data.getLong("targetAmount")) : 16_000;
    }
}
