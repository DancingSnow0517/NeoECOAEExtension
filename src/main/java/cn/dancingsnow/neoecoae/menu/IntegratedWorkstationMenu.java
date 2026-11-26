package cn.dancingsnow.neoecoae.menu;

import appeng.api.upgrades.IUpgradeInventory;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType.BlockUI;
import com.lowdragmc.lowdraglib2.gui.factory.BlockUIMenuType.BlockUIHolder;
import com.lowdragmc.lowdraglib2.gui.factory.LDMenuTypes;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side quick moves; the client uses the same LDLib block UI and slot layout. */
public final class IntegratedWorkstationMenu extends ModularUIContainerMenu {
    private final IUpgradeInventory upgrades;

    private IntegratedWorkstationMenu(int id, Inventory inventory, BlockUIHolder holder, IUpgradeInventory upgrades) {
        super(LDMenuTypes.BLOCK_UI.get(), id, inventory, holder);
        this.upgrades = upgrades;
    }

    public static BlockUIHolder holder(BlockUI block, Player player, BlockPos pos, BlockState state,
                                       IUpgradeInventory upgrades) {
        return new BlockUIHolder(block, player, pos, state) {
            @Override
            public ModularUIContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new IntegratedWorkstationMenu(id, inventory, this, upgrades);
            }
        };
    }

    @Override
    protected ItemStack performQuickMoveStack(ItemStack stack, boolean fromPlayerSide) {
        if (fromPlayerSide && upgrades.getMaxInstalled(stack.getItem()) > 0) {
            // Install before LDLib tries merging with an existing stack in a recipe input.
            stack = upgrades.addItems(stack);
            if (stack.isEmpty()) return ItemStack.EMPTY;
        }
        return super.performQuickMoveStack(stack, fromPlayerSide);
    }
}
