package cn.dancingsnow.neoecoae.gui.common;

import appeng.api.behaviors.ContainerItemStrategies;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import appeng.api.stacks.GenericStack;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Uses the same CONFIG_STACKS/FakeSlot path and fluid units as AE2's InterfaceMenu. */
final class FluidHatchInterfaceConfig {
    private FluidHatchInterfaceConfig() {}

    static @Nullable GenericStack click(@Nullable GenericStack current, ItemStack carried, int button) {
        var inventory = ConfigInventory.configStacks(1).supportedType(AEKeyType.fluids())
            .allowOverstacking(true).build();
        inventory.setStack(0, current);
        var slot = new FakeSlot(inventory.createMenuWrapper(), 0);
        if (button == 0) {
            // GenericStackInv adds longs before clamping. Bound this increment before the native action.
            var increment = GenericStack.unwrapItemStack(carried);
            if (current != null && increment != null && current.what().equals(increment.what())
                && increment.amount() > Long.MAX_VALUE - current.amount()) {
                long remaining = Long.MAX_VALUE - current.amount();
                if (remaining == 0) return current;
                carried = GenericStack.wrapInItemStack(new GenericStack(increment.what(), remaining));
            }
            slot.increase(carried);
        } else if (button == 1) {
            var emptying = ContainerItemStrategies.getEmptyingAction(carried);
            if (emptying != null && emptying.what() instanceof AEFluidKey) {
                // AEBaseScreen routes a container right-click to EMPTY_ITEM before FakeSlot actions.
                slot.set(GenericStack.wrapInItemStack(new GenericStack(emptying.what(), emptying.maxAmount())));
            } else if (!slot.getItem().isEmpty()) {
                slot.decrease(carried);
            } else if (!carried.isEmpty()) {
                slot.set(carried.copyWithCount(1));
            }
        }
        return inventory.getStack(0);
    }

    static String slotAmount(AEFluidKey key, long amount) { return key.formatAmount(amount, AmountFormat.SLOT); }

}
