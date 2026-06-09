package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import static cn.dancingsnow.neoecoae.gui.nativeui.layout.NEIntegratedWorkingStationLayout.*;

import cn.dancingsnow.neoecoae.blocks.entity.ECOIntegratedWorkingStationBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.gui.nativeui.slot.NEInternalInventoryItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

/**
 * Menu for the ECO Integrated Working Station with real machine slots.
 * <p>
 * Coordinates are shared with the client screen:
 * the Screen draws 18×18 slot backgrounds and this Menu places the 16×16
 * Slot item/click areas at background +1/+1.
 * </p>
 */
public class NEIntegratedWorkingStationMenu extends NEBaseMachineMenu {

    public static final int INPUT_SLOTS = 9;
    public static final int OUTPUT_SLOTS = 1;
    public static final int UPGRADE_SLOTS = 4;
    public static final int PLAYER_INV_SLOTS = 36;

    // Slot indexes: 0-8 input, 9 output, 10-13 upgrades, 14-49 player

    public static final int DATA_COUNT = 9;
    public static final int DATA_ENERGY = 0;
    public static final int DATA_MAX_ENERGY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_MAX_PROGRESS = 3;
    public static final int DATA_REQUIRED_ENERGY = 4;
    public static final int DATA_WORKING = 5;
    public static final int DATA_FLUID_IN_AMOUNT = 6;
    public static final int DATA_AUTO_EXPORT = 7;
    public static final int DATA_FLUID_OUT_AMOUNT = 8;

    // Layout constants imported from NEIntegratedWorkingStationLayout via static import above.

    private final ContainerData data;
    // Client-side fluid cache updated via IWSStatePacket from server
    private FluidStack clientInputFluid = FluidStack.EMPTY;
    private FluidStack clientOutputFluid = FluidStack.EMPTY;
    private boolean clientAutoExport;
    private boolean hasClientState;

    public NEIntegratedWorkingStationMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.INTEGRATED_WORKING_STATION.get(), containerId, playerInv, machinePos);

        boolean serverSide = !playerInv.player.level().isClientSide();
        ECOIntegratedWorkingStationBlockEntity be = serverSide ? getBlockEntity(playerInv.player) : null;

        IItemHandler inputHandler;
        IItemHandler outputHandler;
        IItemHandler upgradeHandler;
        if (be != null) {
            // Server-side slots mutate the real BE inventories.
            inputHandler = new NEInternalInventoryItemHandler(be.getInput(), be, true, true);
            outputHandler = new NEInternalInventoryItemHandler(be.getOutput(), be, false, true);
            upgradeHandler = be.getUpgradeItemHandler();
            this.data = be.getContainerData();
        } else {
            // Client-side slots must use menu-local storage. Container slot packets
            // will keep these dummy handlers in sync with the server. Binding the
            // client menu directly to the client BlockEntity inventory is unstable
            // because BE item inventories are not the source of truth for menu sync.
            inputHandler = new ItemStackHandler(INPUT_SLOTS);
            outputHandler = new ItemStackHandler(OUTPUT_SLOTS);
            upgradeHandler = new ItemStackHandler(UPGRADE_SLOTS);
            this.data = new SimpleContainerData(DATA_COUNT);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new SlotItemHandler(
                        inputHandler, col + row * 3, INPUT_SLOT_X + col * SLOT_SIZE, INPUT_SLOT_Y + row * SLOT_SIZE));
            }
        }

        addSlot(new SlotItemHandler(outputHandler, 0, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
            @Override
            public boolean mayPlace(@NotNull ItemStack stack) {
                return false;
            }
        });

        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            final int slotIdx = i;
            addSlot(new SlotItemHandler(upgradeHandler, i, UPGRADE_SLOT_X, UPGRADE_FIRST_SLOT_Y + i * SLOT_SIZE) {
                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return be != null && be.getUpgradeItemHandler().isItemValid(slotIdx, stack);
                }
            });
        }

        addDataSlots(this.data);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(
                        playerInv,
                        col + row * 9 + 9,
                        PLAYER_INV_SLOT_X + col * SLOT_SIZE,
                        PLAYER_INV_SLOT_Y + row * SLOT_SIZE));
            }
        }

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, HOTBAR_SLOT_X + col * SLOT_SIZE, HOTBAR_SLOT_Y));
        }
    }

    private ECOIntegratedWorkingStationBlockEntity getBlockEntity(Player player) {
        BlockEntity be = player.level().getBlockEntity(machinePos);
        if (be instanceof ECOIntegratedWorkingStationBlockEntity iws) {
            return iws;
        }
        return null;
    }

    // ── Shift-click transfer ──

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int machineEnd = INPUT_SLOTS + OUTPUT_SLOTS + UPGRADE_SLOTS; // 14
        int playerStart = machineEnd;
        int playerEnd = playerStart + PLAYER_INV_SLOTS; // 50

        if (index < machineEnd) {
            // From machine slot → player inventory
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player inventory → machine input slots only (not output, not upgrades)
            if (!moveItemStackTo(stack, 0, INPUT_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return original;
    }

    // ── Public accessors for Screen ──

    public ContainerData getData() {
        return data;
    }

    public int getEnergy() {
        return data.get(DATA_ENERGY);
    }

    public int getMaxEnergy() {
        return data.get(DATA_MAX_ENERGY);
    }

    public int getProgress() {
        return data.get(DATA_PROGRESS);
    }

    public int getMaxProgress() {
        return data.get(DATA_MAX_PROGRESS);
    }

    public int getRequiredEnergy() {
        return data.get(DATA_REQUIRED_ENERGY);
    }

    public boolean isWorking() {
        return data.get(DATA_WORKING) != 0;
    }

    public int getFluidInAmount() {
        return data.get(DATA_FLUID_IN_AMOUNT);
    }

    public int getFluidOutAmount() {
        return data.get(DATA_FLUID_OUT_AMOUNT);
    }

    public boolean isAutoExportEnabled() {
        if (hasClientState) return clientAutoExport;
        return data.get(DATA_AUTO_EXPORT) != 0;
    }

    // ── Client-side fluid/state cache (updated via IWSStatePacket from server) ──

    public FluidStack getClientInputFluid() {
        return clientInputFluid;
    }

    public FluidStack getClientOutputFluid() {
        return clientOutputFluid;
    }

    public void updateClientState(FluidStack input, FluidStack output, boolean autoExport) {
        this.clientInputFluid = input;
        this.clientOutputFluid = output;
        this.clientAutoExport = autoExport;
        this.hasClientState = true;
    }
}
