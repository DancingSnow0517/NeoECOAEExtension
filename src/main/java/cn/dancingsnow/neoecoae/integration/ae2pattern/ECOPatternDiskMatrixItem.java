package cn.dancingsnow.neoecoae.integration.ae2pattern;

import appeng.api.inventories.InternalInventory;
import appeng.api.storage.cells.CellState;
import appeng.util.inv.filter.IAEItemFilter;
import cn.dancingsnow.neoecoae.api.ECOTier;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECODrivePatternCellItem;
import io.github.lounode.ae2pattern.api.PatternDiskApi;
import io.github.lounode.ae2pattern.api.PatternDiskContents;
import io.github.lounode.ae2pattern.common.item.PatternDiskItem;
import io.github.lounode.ae2pattern.common.pattern.PatternDiskTier;
import net.minecraft.world.item.ItemStack;

/**
 * NEO ECO's pattern disk matrix: an AE2 Pattern Disk {@link PatternDiskItem}, kept to NEO ECO machines.
 *
 * <p>It stays a {@code PatternDiskItem} so the pattern bus, the large workstation interface and AE2 Pattern Disk's
 * terminals read it exactly like any other disk. It is also an {@link IECODrivePatternCellItem}, which is what lets
 * an ECO storage drive hold it. What it gives up is AE2 Pattern Disk's own hosts - its
 * providers and its batch assembler - the same way an ECO storage matrix is kept out of AE2's drive and chest.
 * Those hosts accept anything that is a {@code PatternDiskItem}, so the refusal lives in the
 * {@code mixins.ae2_pattern_disk} package, which asks {@link #isRestrictedToEco} at every slot and inventory that
 * admits disks there.</p>
 */
public final class ECOPatternDiskMatrixItem extends PatternDiskItem implements IECODrivePatternCellItem {

    /**
     * The disk-inventory filter installed on AE2 Pattern Disk's hosts. Kept here rather than in the mixins: a class
     * inside a mixin package cannot be loaded on its own, and this one is handed to the inventory as an object.
     */
    public static final IAEItemFilter AEPD_HOST_FILTER = new IAEItemFilter() {
        @Override
        public boolean allowInsert(InternalInventory inventory, int slot, ItemStack stack) {
            return !isRestrictedToEco(stack);
        }
    };

    private final ECOTier ecoTier;

    public ECOPatternDiskMatrixItem(Properties properties, PatternDiskTier tier, ECOTier ecoTier) {
        super(properties, tier);
        this.ecoTier = ecoTier;
    }

    /** @return whether {@code stack} is an ECO pattern disk matrix, which AE2 Pattern Disk's own hosts must refuse */
    public static boolean isRestrictedToEco(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ECOPatternDiskMatrixItem;
    }

    @Override
    public CellState getDriveCellState(ItemStack stack) {
        PatternDiskContents contents = PatternDiskApi.contents(stack);
        if (contents == null || contents.isEmpty()) {
            return CellState.EMPTY;
        }
        return contents.isFull() ? CellState.FULL : CellState.NOT_EMPTY;
    }

    @Override
    public ECOCellType getDriveCellType(ItemStack stack) {
        return NEPatternDiskItems.CELL_TYPE.get();
    }

    @Override
    public IECOTier getDriveTier(ItemStack stack) {
        return ecoTier;
    }

    @Override
    public long getDriveUsedPatterns(ItemStack stack) {
        PatternDiskContents contents = PatternDiskApi.contents(stack);
        return contents == null ? 0L : contents.used();
    }

    /** The disk's own capacity: a matrix written before a capacity change keeps the capacity it was written with. */
    @Override
    public long getDrivePatternCapacity(ItemStack stack) {
        PatternDiskContents contents = PatternDiskApi.contents(stack);
        return contents == null ? capacity() : contents.capacity();
    }
}
