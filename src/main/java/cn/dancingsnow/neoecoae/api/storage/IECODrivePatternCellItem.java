package cn.dancingsnow.neoecoae.api.storage;

import appeng.api.storage.cells.CellState;
import cn.dancingsnow.neoecoae.api.IECOTier;
import net.minecraft.world.item.ItemStack;

/**
 * An Item that an ECO storage drive accepts although it holds no storage: a matrix that carries patterns.
 *
 * <p>Implement this on the Item class. The drive holds it the way it holds any disk, but mounts no storage for it -
 * {@link ECOStorageCells} does not know it, so every storage path sees a drive with nothing to mount. The patterns
 * it carries are not offered to autocrafting yet; the drive only keeps the matrix, and the storage host reports how
 * full it is.</p>
 */
public interface IECODrivePatternCellItem {

    /** @return the status the drive shows for {@code stack}; drives the status light */
    CellState getDriveCellState(ItemStack stack);

    /**
     * @return the cell type the storage host groups {@code stack} under. Should be
     *         {@link ECOCellType#countOnly() count-only}: the host then shows the pattern count alone
     */
    ECOCellType getDriveCellType(ItemStack stack);

    /** @return the tier the storage host labels {@code stack} with */
    IECOTier getDriveTier(ItemStack stack);

    /** @return how many patterns {@code stack} holds */
    long getDriveUsedPatterns(ItemStack stack);

    /** @return how many patterns {@code stack} can hold */
    long getDrivePatternCapacity(ItemStack stack);
}
