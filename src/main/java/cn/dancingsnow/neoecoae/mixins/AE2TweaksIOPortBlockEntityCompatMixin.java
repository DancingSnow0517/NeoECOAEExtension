package cn.dancingsnow.neoecoae.mixins;

import appeng.api.config.Actionable;
import appeng.api.config.OperationMode;
import appeng.api.config.Settings;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.api.storage.StorageHelper;
import appeng.api.storage.cells.StorageCell;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.util.IConfigManager;
import appeng.blockentity.grid.AENetworkedInvBlockEntity;
import appeng.blockentity.storage.IOPortBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.util.inv.AppEngInternalInventory;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = IOPortBlockEntity.class, priority = 1100)
public abstract class AE2TweaksIOPortBlockEntityCompatMixin extends AENetworkedInvBlockEntity {

    @Shadow
    private static int NUMBER_OF_CELL_SLOTS;
    @Shadow
    private AppEngInternalInventory inputCells;
    @Shadow
    private IUpgradeInventory upgrades;
    @Shadow
    private IConfigManager manager;
    @Shadow
    private IActionSource mySrc;

    protected AE2TweaksIOPortBlockEntityCompatMixin(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    /**
     * Preserves AE2 Tweaks' I/O-port behavior while also accepting NeoECOAE storage cells.
     */
    @Overwrite
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        if (!this.getMainNode().isActive()) {
            return TickRateModulation.IDLE;
        }

        TickRateModulation ret = TickRateModulation.SLEEP;
        long itemsToMove = 256;

        switch (upgrades.getInstalledUpgrades(AEItems.SPEED_CARD)) {
            case 1 -> itemsToMove *= 2;
            case 2 -> itemsToMove *= 4;
            case 4 -> itemsToMove *= 8;
            case 5 -> itemsToMove *= 16;
            case 6 -> itemsToMove *= 32;
            case 7 -> itemsToMove *= 64;
            case 8 -> itemsToMove *= 128;
        }

        var grid = getMainNode().getGrid();
        if (grid == null) {
            return TickRateModulation.IDLE;
        }

        for (int x = 0; x < NUMBER_OF_CELL_SLOTS; x++) {
            var cell = this.inputCells.getStackInSlot(x);
            var cellInv = getCellInventory(cell);

            if (cellInv == null) {
                moveSlot(x);
                continue;
            }

            if (itemsToMove > 0) {
                itemsToMove = transferContents(grid, cellInv, itemsToMove);

                if (itemsToMove > 0) {
                    ret = TickRateModulation.IDLE;
                } else {
                    ret = TickRateModulation.URGENT;
                }
            }

            if (itemsToMove > 0 && matchesFullnessMode(cellInv) && this.moveSlot(x)) {
                ret = TickRateModulation.URGENT;
            }
        }

        return ret;
    }

    @Shadow
    public abstract boolean matchesFullnessMode(StorageCell inv);

    @Shadow
    private boolean moveSlot(int slot) {
        throw new AssertionError();
    }

    private StorageCell getCellInventory(ItemStack cell) {
        StorageCell cellInv = StorageCells.getCellInventory(cell, null);
        return cellInv != null ? cellInv : ECOStorageCells.getCellInventory(cell, null);
    }

    private long transferContents(IGrid grid, StorageCell cellInv, long itemsToMove) {
        var networkInv = grid.getStorageService().getInventory();

        KeyCounter srcList;
        MEStorage src;
        MEStorage destination;
        if (this.manager.getSetting(Settings.OPERATION_MODE) == OperationMode.EMPTY) {
            src = cellInv;
            srcList = cellInv.getAvailableStacks();
            destination = networkInv;
        } else {
            src = networkInv;
            srcList = grid.getStorageService().getCachedInventory();
            destination = cellInv;
        }

        var energy = grid.getEnergyService();
        boolean didStuff;

        do {
            didStuff = false;

            for (var srcEntry : srcList) {
                var totalStackSize = srcEntry.getLongValue();
                if (totalStackSize <= 0) {
                    continue;
                }

                var what = srcEntry.getKey();
                var possible = destination.insert(what, totalStackSize, Actionable.SIMULATE, this.mySrc);
                if (possible <= 0) {
                    continue;
                }

                possible = Math.min(possible, itemsToMove * what.getAmountPerOperation());
                possible = src.extract(what, possible, Actionable.MODULATE, this.mySrc);
                if (possible <= 0) {
                    continue;
                }

                var inserted = StorageHelper.poweredInsert(energy, destination, what, possible, this.mySrc);
                if (inserted < possible) {
                    src.insert(what, possible - inserted, Actionable.MODULATE, this.mySrc);
                }

                if (inserted > 0) {
                    itemsToMove -= Math.max(1, inserted / what.getAmountPerOperation());
                    didStuff = true;
                }
                break;
            }
        } while (itemsToMove > 0 && didStuff);

        return itemsToMove;
    }
}
