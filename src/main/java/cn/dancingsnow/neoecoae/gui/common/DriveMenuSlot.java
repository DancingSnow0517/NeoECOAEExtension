package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import cn.dancingsnow.neoecoae.blocks.entity.DriveCellUiTransfer;
import java.util.function.BooleanSupplier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public final class DriveMenuSlot extends Slot {
    private final boolean client;
    private final ICellHost drive;
    private final BooleanSupplier valid;

    public DriveMenuSlot(Player player, @Nullable ICellHost drive, BooleanSupplier valid) {
        super(new SimpleContainer(1), 0, 0, 0);
        this.client = player.level().isClientSide;
        this.drive = drive;
        this.valid = valid;
    }

    private boolean available() {
        return drive != null && valid.getAsBoolean();
    }

    @Override
    public ItemStack getItem() {
        if (client) return super.getItem();
        if (!available()) return ItemStack.EMPTY;
        var stack = drive.getCellStack();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public boolean mayPlace(ItemStack stack) { return false; }

    @Override
    public boolean mayPickup(Player player) { return false; }

    @Override
    public void set(ItemStack stack) {
        if (client) super.set(stack);
    }

    @Override
    public ItemStack remove(int amount) { return ItemStack.EMPTY; }

    public ItemStack click(int button, ClickType type, Player player) {
        if (!available() || player.level().isClientSide || !player.containerMenu.stillValid(player)
            || button < 0 || button > 1) return ItemStack.EMPTY;
        var carried = player.containerMenu.getCarried();
        var installed = getItem();
        if (type == ClickType.QUICK_MOVE && carried.isEmpty() && !installed.isEmpty()) {
            DriveCellUiTransfer.extract(drive, player, installed, true);
        } else if (type == ClickType.PICKUP) {
            if (carried.isEmpty() && !installed.isEmpty()) {
                DriveCellUiTransfer.extract(drive, player, installed, false);
            } else if (!carried.isEmpty() && installed.isEmpty()) {
                DriveCellUiTransfer.insert(drive, player, carried);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public int getMaxStackSize() { return 1; }

    @Override
    public int getMaxStackSize(ItemStack stack) { return 1; }
}
