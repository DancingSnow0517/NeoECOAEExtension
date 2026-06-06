package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import static cn.dancingsnow.neoecoae.gui.nativeui.layout.NECraftingPatternBusLayout.*;

import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import cn.dancingsnow.neoecoae.gui.nativeui.NENativeMenus;
import cn.dancingsnow.neoecoae.gui.nativeui.slot.NEPagedItemHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class NECraftingPatternBusMenu extends NEBaseMachineMenu {
    public static final int PATTERN_SLOTS = PATTERN_COLS * PATTERN_ROWS;
    public static final int PLAYER_INV_SLOTS = 36;
    public static final int BUTTON_PREVIOUS_PAGE = 0;
    public static final int BUTTON_NEXT_PAGE = 1;
    private static final int DATA_CURRENT_PAGE = 0;
    private static final int DATA_PAGE_COUNT = 1;
    private static final int DATA_COUNT = 2;

    private int currentPage;
    private int pageCount = 1;
    private final ContainerData pageData;

    public NECraftingPatternBusMenu(int containerId, Inventory playerInv, BlockPos machinePos) {
        super(NENativeMenus.CRAFTING_PATTERN_BUS.get(), containerId, playerInv, machinePos);

        BlockEntity be = playerInv.player.level().getBlockEntity(machinePos);
        boolean serverSide = !playerInv.player.level().isClientSide();
        IItemHandlerModifiable visibleHandler;
        if (serverSide && be instanceof ECOCraftingPatternBusBlockEntity bus) {
            this.pageCount = Math.max(1, bus.getPageCount());
            visibleHandler = new NEPagedItemHandler(
                    bus.itemHandler, () -> this.currentPage, () -> this.pageCount, PATTERN_SLOTS);
        } else {
            visibleHandler = new ItemStackHandler(PATTERN_SLOTS);
        }

        this.pageData = serverSide
                ? new ContainerData() {
                    @Override
                    public int get(int index) {
                        return switch (index) {
                            case DATA_CURRENT_PAGE -> currentPage;
                            case DATA_PAGE_COUNT -> pageCount;
                            default -> 0;
                        };
                    }

                    @Override
                    public void set(int index, int value) {}

                    @Override
                    public int getCount() {
                        return DATA_COUNT;
                    }
                }
                : new SimpleContainerData(DATA_COUNT);
        addDataSlots(this.pageData);

        for (int row = 0; row < PATTERN_ROWS; row++) {
            for (int col = 0; col < PATTERN_COLS; col++) {
                addSlot(new SlotItemHandler(
                        visibleHandler,
                        col + row * PATTERN_COLS,
                        PATTERN_SLOT_X + col * SLOT_SIZE,
                        PATTERN_SLOT_Y + row * SLOT_SIZE));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(
                        playerInv, col + row * 9 + 9, INV_SLOT_X + col * SLOT_SIZE, INV_SLOT_Y + row * SLOT_SIZE));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, HOTBAR_SLOT_X + col * SLOT_SIZE, HOTBAR_SLOT_Y));
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerStart = PATTERN_SLOTS;
        int playerEnd = playerStart + PLAYER_INV_SLOTS;
        if (index < PATTERN_SLOTS) {
            if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, PATTERN_SLOTS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().isClientSide()) {
            return false;
        }
        int targetPage =
                switch (id) {
                    case BUTTON_PREVIOUS_PAGE -> currentPage - 1;
                    case BUTTON_NEXT_PAGE -> currentPage + 1;
                    default -> currentPage;
                };
        if (targetPage < 0 || targetPage >= pageCount || targetPage == currentPage) {
            return false;
        }
        currentPage = targetPage;
        broadcastFullState();
        return true;
    }

    public int getCurrentPage() {
        return player.level().isClientSide() ? pageData.get(DATA_CURRENT_PAGE) : currentPage;
    }

    public int getPageCount() {
        return Math.max(1, player.level().isClientSide() ? pageData.get(DATA_PAGE_COUNT) : pageCount);
    }
}
