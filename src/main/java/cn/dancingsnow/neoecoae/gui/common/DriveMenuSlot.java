package cn.dancingsnow.neoecoae.gui.common;

import cn.dancingsnow.neoecoae.api.storage.ICellHost;
import cn.dancingsnow.neoecoae.blocks.entity.DriveCellUiTransfer;
import java.util.function.BooleanSupplier;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class DriveMenuSlot extends Slot {
    private final Level level;
    private final ICellHost drive;
    private final BooleanSupplier valid;

    public DriveMenuSlot(Player player, @Nullable ICellHost drive, BooleanSupplier valid) {
        super(new SimpleContainer(1), 0, 0, 0);
        this.level = player.level();
        this.drive = drive;
        this.valid = valid;
    }

    private boolean installed() {
        return !(drive instanceof BlockEntity be)
            || be.getLevel() == level && level.hasChunkAt(be.getBlockPos()) && level.getBlockEntity(be.getBlockPos()) == be;
    }

    @Override
    public ItemStack getItem() {
        if (level.isClientSide) return super.getItem();
        if (drive == null || !valid.getAsBoolean()) return ItemStack.EMPTY;
        var stack = installed() ? drive.getCellStack() : drive.getBuiltInCellForDisplay();
        return stack == null ? ItemStack.EMPTY : stack;
    }

    @Override
    public boolean mayPlace(ItemStack stack) { return false; }

    @Override
    public boolean mayPickup(Player player) { return false; }

    @Override
    public void set(ItemStack stack) {
        if (level.isClientSide) super.set(stack);
    }

    @Override
    public ItemStack remove(int amount) { return ItemStack.EMPTY; }

    public ItemStack click(int button, ClickType type, Player player) {
        if (drive == null || !valid.getAsBoolean() || !installed() || player.level().isClientSide || !player.containerMenu.stillValid(player)
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
