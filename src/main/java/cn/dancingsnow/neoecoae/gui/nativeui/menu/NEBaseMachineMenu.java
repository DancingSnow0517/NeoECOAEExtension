package cn.dancingsnow.neoecoae.gui.nativeui.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Base menu for all ECO machine native UIs.
 * <p>
 * Provides common {@link #stillValid} based on block position proximity,
 * and a safe empty {@link #quickMoveStack} default.
 * </p>
 */
public abstract class NEBaseMachineMenu extends AbstractContainerMenu {

    protected final BlockPos machinePos;
    protected final Player player;

    protected NEBaseMachineMenu(@Nullable MenuType<?> type, int containerId, Inventory playerInv, BlockPos machinePos) {
        super(type, containerId);
        this.machinePos = machinePos;
        this.player = playerInv.player;
    }

    public BlockPos getMachinePos() {
        return machinePos;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().getBlockEntity(machinePos) == null) {
            return false;
        }
        return player.distanceToSqr(machinePos.getX() + 0.5, machinePos.getY() + 0.5, machinePos.getZ() + 0.5) <= 64.0;
    }

    /**
     * Safe fallback — subclasses override when slots are added.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
