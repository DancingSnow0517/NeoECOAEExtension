package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidInputHatchBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOFluidOutputHatchBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.network.NENetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Menu for ECO Fluid Input/Output Hatches.
 */
public class NEFluidHatchMenu extends NEBaseMachineMenu {
    private static final int DATA_TANK_AMOUNT = 0;
    private static final int DATA_TANK_CAPACITY = 1;
    private static final int DATA_COUNT = 2;

    private final ContainerData data;
    private FluidStack clientFluid = FluidStack.EMPTY;
    @Nullable private CompoundTag lastSentTankTag;

    public NEFluidHatchMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.FLUID_HATCH.get(), containerId, playerInv, machinePos);
        boolean serverSide = !playerInv.player.level().isClientSide();
        FluidTank tank = serverSide ? getServerTank() : null;
        this.data = tank == null
                ? new SimpleContainerData(DATA_COUNT)
                : new ContainerData() {
                    @Override
                    public int get(int index) {
                        return switch (index) {
                            case DATA_TANK_AMOUNT -> tank.getFluidAmount();
                            case DATA_TANK_CAPACITY -> tank.getCapacity();
                            default -> 0;
                        };
                    }

                    @Override
                    public void set(int index, int value) {}

                    @Override
                    public int getCount() {
                        return DATA_COUNT;
                    }
                };
        addDataSlots(this.data);
    }

    public FluidStack getClientFluid() {
        return clientFluid;
    }

    public void updateClientFluid(FluidStack fluid) {
        clientFluid = fluid.copy();
    }

    public int getTankAmount() {
        return data.get(DATA_TANK_AMOUNT);
    }

    public int getTankCapacity() {
        int capacity = data.get(DATA_TANK_CAPACITY);
        return capacity <= 0 ? 16000 : capacity;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != 0 || player.level().isClientSide()) {
            return false;
        }
        FluidTank tank = getServerTank();
        return tank != null && FluidUtil.interactWithFluidHandler(player, InteractionHand.MAIN_HAND, tank);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        FluidTank tank = getServerTank();
        if (tank == null) {
            return;
        }
        CompoundTag tankTag = tank.writeToNBT(new CompoundTag());
        if (tankTag.equals(lastSentTankTag)) {
            return;
        }
        lastSentTankTag = tankTag.copy();
        NENetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> serverPlayer),
                new NENetwork.NEFluidHatchStatePacket(machinePos, tankTag));
    }

    private FluidTank getServerTank() {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof ECOFluidInputHatchBlockEntity input) {
            return input.tank;
        }
        if (be instanceof ECOFluidOutputHatchBlockEntity output) {
            return output.tank;
        }
        return null;
    }
}
