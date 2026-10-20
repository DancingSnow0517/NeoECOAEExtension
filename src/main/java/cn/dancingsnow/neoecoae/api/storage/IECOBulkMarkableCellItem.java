package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Opts an ECO drive-compatible item into the storage host's manual MEGA compression marker panel.
 * This does not register a storage backend or opt the cell into automatic marking/item transfers.
 * MEGA Cells integration must be available for compression-chain validation.
 */
public interface IECOBulkMarkableCellItem {
    /**
     * Returns the currently editable item-marker inventory. Its size determines the available slots
     * and pages (25 slots per page, at most 50 slots). Return only active slots, including any
     * upgrade-dependent limit. Changes must be persisted to the supplied stack by the inventory's
     * change listener; the host refreshes the drive's storage backend after a successful edit.
     * Inactive markers should be preserved by the implementation when upgrades change the size.
     */
    ConfigInventory getConfigInventory(ItemStack stack);

    /**
     * Optional upgrade inventory for the panel's ECO MEGA Upgrade Card slot.
     * Return null if the cell does not support this slot. The inventory must enforce its own
     * supported upgrades and removal restrictions. Pagination depends on configuration size.
     */
    default @Nullable IUpgradeInventory getUpgrades(ItemStack stack) {
        return null;
    }
}
