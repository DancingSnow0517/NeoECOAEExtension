package cn.dancingsnow.neoecoae.integration.megacells.backend;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import gripe._90.megacells.definition.MEGAComponents;
import gripe._90.megacells.definition.MEGAItems;
import gripe._90.megacells.misc.CompressionChain;
import gripe._90.megacells.misc.CompressionService;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

/**
 * Finite-capacity ECO adapter for MEGA Cells' bulk compression chain.
 * External MEGA references intentionally remain isolated in this backend package.
 */
public final class ECOMegaBulkStorageCell extends ECOStorageCell {
    private final ItemStack stack;
    @Nullable
    private final ISaveProvider container;
    @Nullable
    private final AEItemKey filterItem;
    private final boolean compressionEnabled;

    @Nullable
    private AEItemKey storedItem;
    private CompressionChain compressionChain;
    private BigInteger unitCount;
    private BigInteger storedItemFactor;
    private boolean persisted = true;

    public ECOMegaBulkStorageCell(ItemStack stack, @Nullable ISaveProvider container) {
        super(stack, container);
        this.stack = stack;
        this.container = container;
        this.filterItem = getConfigInventory().getKey(0) instanceof AEItemKey item ? item : null;
        this.compressionEnabled = getUpgradesInventory().isInstalled(MEGAItems.COMPRESSION_CARD);
        this.storedItem = stack.get(MEGAComponents.BULK_CELL_ITEM) instanceof AEItemKey item ? item : null;
        this.unitCount = stack.getOrDefault(MEGAComponents.BULK_CELL_UNIT_COUNT, BigInteger.ZERO).max(BigInteger.ZERO);

        AEItemKey determiningItem = storedItem != null ? storedItem : filterItem;
        this.compressionChain = CompressionService.getChain(determiningItem);
        this.storedItemFactor = compressionChain.unitFactor(determiningItem);

        BigInteger recordedFactor = stack.getOrDefault(MEGAComponents.BULK_CELL_UNIT_FACTOR, storedItemFactor);
        if (recordedFactor.signum() > 0 && !storedItemFactor.equals(recordedFactor)) {
            unitCount = unitCount.multiply(storedItemFactor).divide(recordedFactor);
            saveChanges();
        }
    }

    public boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    public boolean hasCompressionChain() {
        return !compressionChain.isEmpty();
    }

    @Nullable
    public AEItemKey getFilterItem() {
        return filterItem;
    }

    @Override
    public CellState getStatus() {
        if (storedItem == null || unitCount.signum() == 0) {
            return CellState.EMPTY;
        }
        return getRemainingItemCount() > 0 && !isFilterMismatched() ? CellState.NOT_EMPTY : CellState.FULL;
    }

    @Override
    public long getStoredItemCount() {
        return CompressionChain.clamp(unitCount, Long.MAX_VALUE);
    }

    @Override
    public long getRemainingItemCount() {
        return CompressionChain.clamp(maxUnits().subtract(unitCount).max(BigInteger.ZERO), Long.MAX_VALUE);
    }

    @Override
    public long getFreeBytes() {
        return Math.max(0L, getTotalBytes() - getUsedBytes());
    }

    @Override
    public int getUnusedItemCount() {
        int amountPerByte = getKeyType().getAmountPerByte();
        int remainder = unitCount.remainder(BigInteger.valueOf(amountPerByte)).intValue();
        return remainder == 0 ? 0 : amountPerByte - remainder;
    }

    @Override
    public long getUsedBytes() {
        if (unitCount.signum() == 0) {
            return 0;
        }
        BigInteger amountPerByte = BigInteger.valueOf(getKeyType().getAmountPerByte());
        long contentBytes = CompressionChain.clamp(
            unitCount.add(amountPerByte).subtract(BigInteger.ONE).divide(amountPerByte), Long.MAX_VALUE);
        return Math.min(getTotalBytes(), Math.addExact(getBytesPerType(), contentBytes));
    }

    @Override
    public long getStoredItemTypes() {
        return unitCount.signum() > 0 ? 1 : 0;
    }

    @Override
    public long getRemainingItemTypes() {
        return unitCount.signum() > 0 ? 0 : 1;
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        return insertInternal(what, amount, mode);
    }

    @Override
    public long insertForMigration(AEKey what, long amount, Actionable mode) {
        return insertInternal(what, amount, mode);
    }

    private long insertInternal(AEKey what, long amount, Actionable mode) {
        if (amount <= 0 || !(what instanceof AEItemKey item) || filterItem == null || isFilterMismatched()) {
            return 0;
        }
        if (!item.equals(filterItem) && (!compressionEnabled || !compressionChain.containsVariant(item))) {
            return 0;
        }

        BigInteger factor = compressionChain.unitFactor(item);
        BigInteger accepted = BigInteger.valueOf(amount)
            .min(maxUnits().subtract(unitCount).max(BigInteger.ZERO).divide(factor));
        long acceptedAmount = CompressionChain.clamp(accepted, amount);
        if (acceptedAmount <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            if (storedItem == null) {
                storedItem = filterItem;
                storedItemFactor = compressionChain.unitFactor(storedItem);
            }
            unitCount = unitCount.add(BigInteger.valueOf(acceptedAmount).multiply(factor));
            saveChanges();
        }
        return acceptedAmount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || storedItem == null || !(what instanceof AEItemKey item) || isFilterMismatched()) {
            return 0;
        }
        if (!item.equals(storedItem) && (!compressionEnabled || !compressionChain.containsVariant(item))) {
            return 0;
        }

        BigInteger factor = compressionChain.unitFactor(item);
        BigInteger units = BigInteger.valueOf(amount).multiply(factor).min(unitCount);
        long extracted = CompressionChain.clamp(units.divide(factor), amount);
        if (extracted <= 0) {
            return 0;
        }

        if (mode == Actionable.MODULATE) {
            unitCount = unitCount.subtract(BigInteger.valueOf(extracted).multiply(factor));
            if (unitCount.signum() == 0) {
                storedItem = null;
                compressionChain = CompressionService.getChain(filterItem);
                storedItemFactor = compressionChain.unitFactor(filterItem);
            }
            saveChanges();
        }
        return extracted;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        if (storedItem == null || unitCount.signum() == 0) {
            return;
        }
        if (compressionEnabled && hasCompressionChain()) {
            compressionChain.initStacks(unitCount, compressionChain.size() - 1, storedItem).forEach(out::add);
        } else {
            out.add(storedItem, CompressionChain.clamp(unitCount.divide(storedItemFactor), CompressionChain.STACK_LIMIT));
        }
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        return what instanceof AEItemKey item
            && (item.equals(storedItem) || item.equals(filterItem)
            || compressionEnabled && compressionChain.containsVariant(item));
    }

    @Override
    public boolean canFitInsideCell() {
        return unitCount.signum() == 0;
    }

    @Override
    public void clearAllStoredStacks() {
        storedItem = null;
        unitCount = BigInteger.ZERO;
        compressionChain = CompressionService.getChain(filterItem);
        storedItemFactor = compressionChain.unitFactor(filterItem);
        saveChanges();
    }

    @Override
    public void persist() {
        if (persisted) {
            return;
        }
        if (storedItem == null || unitCount.signum() == 0) {
            stack.remove(MEGAComponents.BULK_CELL_ITEM);
            stack.remove(MEGAComponents.BULK_CELL_UNIT_COUNT);
            stack.remove(MEGAComponents.BULK_CELL_UNIT_FACTOR);
        } else {
            stack.set(MEGAComponents.BULK_CELL_ITEM, storedItem);
            stack.set(MEGAComponents.BULK_CELL_UNIT_COUNT, unitCount);
            stack.set(MEGAComponents.BULK_CELL_UNIT_FACTOR, storedItemFactor);
        }
        persisted = true;
    }

    private boolean isFilterMismatched() {
        if (storedItem == null) {
            return false;
        }
        return filterItem == null
            || !storedItem.equals(filterItem) && !compressionChain.containsVariant(filterItem);
    }

    private BigInteger maxUnits() {
        long usableBytes = Math.max(0L, getTotalBytes() - getBytesPerType());
        return BigInteger.valueOf(usableBytes).multiply(BigInteger.valueOf(getKeyType().getAmountPerByte()));
    }

    @Override
    protected void saveChanges() {
        persisted = false;
        persist();
        if (container != null) {
            container.saveChanges();
        }
    }
}
