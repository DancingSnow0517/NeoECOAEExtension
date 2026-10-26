package cn.dancingsnow.neoecoae.mixins.ae2.menu;

import appeng.api.stacks.AEKey;
import appeng.api.inventories.ISegmentedInventory;
import appeng.blockentity.misc.CellWorkbenchBlockEntity;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.CellWorkbenchMenu;
import appeng.menu.implementations.UpgradeableMenu;
import cn.dancingsnow.neoecoae.blocks.entity.storage.StorageCellWorkbench;
import cn.dancingsnow.neoecoae.mixins.ae2.accessor.AEBaseMenuAccessor;
import cn.dancingsnow.neoecoae.impl.storage.bulk.ECOBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;

@Mixin(CellWorkbenchMenu.class)
public abstract class CellWorkbenchMenuMixin extends UpgradeableMenu<CellWorkbenchBlockEntity> {
    protected CellWorkbenchMenuMixin(MenuType<?> type, int id, Inventory inventory, CellWorkbenchBlockEntity host) {
        super(type, id, inventory, host);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void neoecoae$bindRealHost(int id, Inventory inventory, CellWorkbenchBlockEntity host, CallbackInfo ci) {
        if (host instanceof StorageCellWorkbench workbench) {
            // AE2 validates the placed block entity while UpgradeableMenu retains the workbench adapter.
            ((AEBaseMenuAccessor) (Object) this).neoecoae$setBlockEntity(workbench.controller());
        }
    }

    @Inject(method = "setupInventorySlots", at = @At("HEAD"), cancellable = true)
    private void neoecoae$installedCellSlot(CallbackInfo ci) {
        if (!(getHost() instanceof StorageCellWorkbench)) return;
        addSlot(new StorageCellWorkbench.CellSlot(getHost().getSubInventory(ISegmentedInventory.CELLS)),
            SlotSemantics.STORAGE_CELL);
        ci.cancel();
    }

    @Unique
    private boolean neoecoae$isInstalledCellSlot(int slot) {
        return slot >= 0 && slot < slots.size() && getSlotSemantic(slots.get(slot)) == SlotSemantics.STORAGE_CELL;
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (getHost() instanceof StorageCellWorkbench workbench) {
            if (!workbench.isValidFor(player)) return;
            if (neoecoae$isInstalledCellSlot(slot)) {
                if (type == ClickType.PICKUP || type == ClickType.QUICK_MOVE || type == ClickType.SWAP
                    || type == ClickType.THROW) workbench.extractToCursor(player);
                return;
            }
        }
        super.clicked(slot, button, type, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slot) {
        if (getHost() instanceof StorageCellWorkbench workbench) {
            if (!workbench.isValidFor(player)) return ItemStack.EMPTY;
            if (neoecoae$isInstalledCellSlot(slot)) {
                workbench.extractToCursor(player);
                return ItemStack.EMPTY;
            }
        }
        return super.quickMoveStack(player, slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return (!(getHost() instanceof StorageCellWorkbench workbench) || workbench.isValidFor(player))
            && super.stillValid(player);
    }

    @Override
    public boolean isValidForSlot(Slot slot, ItemStack stack) {
        return (!(getHost() instanceof StorageCellWorkbench) || getSlotSemantic(slot) != SlotSemantics.STORAGE_CELL)
            && super.isValidForSlot(slot, stack);
    }

    @Override
    public void broadcastChanges() {
        if (getHost() instanceof StorageCellWorkbench workbench) {
            if (!workbench.isValidFor(getPlayer())) {
                setValidMenu(false);
                return;
            }
            workbench.flushConfiguration(getPlayer());
        }
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        if (getHost() instanceof StorageCellWorkbench workbench) workbench.flushConfiguration(player);
        super.removed(player);
    }

    @Inject(method = "iterateCellStacks", at = @At("HEAD"), cancellable = true)
    private void neoecoae$partitionEcoCell(ItemStack stack,
            CallbackInfoReturnable<Iterator<? extends AEKey>> cir) {
        var inventory = ECOStorageCellItem.getCellInventory(stack);
        if (inventory instanceof ECOBulkStorageCell bulk) {
            // The expanded available stacks contain several variants per chain.
            // Partition only once per stored chain, preserving its configured form.
            cir.setReturnValue(bulk.getStoredChainFilters().iterator());
        } else if (inventory != null) {
            var keys = new java.util.ArrayList<AEKey>();
            for (var entry : inventory.getAvailableStacks()) keys.add(entry.getKey());
            cir.setReturnValue(keys.iterator());
        }
    }
}
