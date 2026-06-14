package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.config.Actionable;
import appeng.api.config.IncludeExclude;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import appeng.util.prioritylist.IPartitionList;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IBatchedECOCellSaveProvider;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ECOStorageCell implements IECOStorageCell {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable private final ISaveProvider container;

    private final IBasicECOCellItem cellType;

    @Getter
    private final AEKeyType keyType;
    // filter
    @Getter
    private final IPartitionList partitionList;

    @Getter
    private final IncludeExclude partitionListMode;

    private final boolean hasVoidUpgrade;

    private final ItemStack cellStack;

    private final int maxItemTypes;
    private int storedItems;

    @Getter
    private long storedItemCount;

    private Object2LongMap<AEKey> storedAmounts;
    private boolean isPersisted = true;

    @Getter
    private final IECOTier tier;

    public ECOStorageCell(ItemStack cellStack, @Nullable ISaveProvider container) {
        this.container = container;
        this.cellStack = cellStack;

        if (cellStack.getItem() instanceof IBasicECOCellItem c) {
            keyType = c.getKeyType();
            maxItemTypes = c.getTotalTypes();
            var storedStacks = getStoredStacks();
            this.storedItems = storedStacks.size();
            this.storedItemCount =
                    storedStacks.stream().mapToLong(GenericStack::amount).sum();
            this.storedAmounts = null;
            this.cellType = c;
            this.tier = c.getTier();

            // Updates the partition list and mode based on installed upgrades and the
            // configured filter.
            var builder = IPartitionList.builder();

            var upgrades = getUpgradesInventory();
            var config = getConfigInventory();

            boolean hasInverter = upgrades.isInstalled(AEItems.INVERTER_CARD);
            boolean isFuzzy = upgrades.isInstalled(AEItems.FUZZY_CARD);
            if (isFuzzy) {
                builder.fuzzyMode(c.getFuzzyMode(cellStack));
            }

            builder.addAll(config.keySet());

            partitionListMode = (hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
            partitionList = builder.build();

            this.hasVoidUpgrade = upgrades.isInstalled(AEItems.VOID_CARD);
        } else {
            throw new IllegalArgumentException("itemStack must be an ECOStorageCellItem");
        }
    }

    @Override
    public CellState getStatus() {
        if (this.getStoredItemTypes() == 0) {
            return CellState.EMPTY;
        }
        if (this.canHoldNewItem()) {
            return CellState.NOT_EMPTY;
        }
        if (this.getRemainingItemCount() > 0) {
            return CellState.TYPES_FULL;
        }
        return CellState.FULL;
    }

    public long getRemainingItemCount() {
        final long remaining = this.getFreeBytes() * keyType.getAmountPerByte() + this.getUnusedItemCount();
        return remaining > 0 ? remaining : 0;
    }

    public long getFreeBytes() {
        return this.getTotalBytes() - this.getUsedBytes();
    }

    public int getUnusedItemCount() {
        final int div = (int) (this.getStoredItemCount() % keyType.getAmountPerByte());

        if (div == 0) {
            return 0;
        }

        return keyType.getAmountPerByte() - div;
    }

    public int getBytesPerType() {
        return this.cellType.getBytesPerType();
    }

    public long getUsedBytes() {
        var bytesForItemCount = (this.getStoredItemCount() + this.getUnusedItemCount()) / keyType.getAmountPerByte();
        return this.getStoredItemTypes() * this.getBytesPerType() + bytesForItemCount;
    }

    public long getTotalBytes() {
        return cellType.getBytes();
    }

    public long getTotalItemTypes() {
        return this.maxItemTypes;
    }

    public long getRemainingItemTypes() {
        var basedOnStorage = this.getFreeBytes() / this.getBytesPerType();
        var baseOnTotal = this.getTotalItemTypes() - this.getStoredItemTypes();
        return Math.min(basedOnStorage, baseOnTotal);
    }

    private boolean canHoldNewItem() {
        final long bytesFree = this.getFreeBytes();
        return (bytesFree > this.getBytesPerType()
                        || bytesFree == this.getBytesPerType() && this.getUnusedItemCount() > 0)
                && this.getRemainingItemTypes() > 0;
    }

    public long getStoredItemTypes() {
        return storedItems;
    }

    private List<GenericStack> getStoredStacks() {
        if (!cellStack.hasTag() || !cellStack.getTag().contains("eco_cell_contents", Tag.TAG_LIST)) {
            return List.of();
        }
        ListTag list = cellStack.getTag().getList("eco_cell_contents", Tag.TAG_COMPOUND);
        List<GenericStack> stacks = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            GenericStack stack = GenericStack.readTag(list.getCompound(i));
            if (stack != null && stack.amount() > 0) {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    protected Object2LongMap<AEKey> getCellItems() {
        if (this.storedAmounts == null) {
            this.storedAmounts = new Object2LongOpenHashMap<>(maxItemTypes);
            this.loadCellItems();
        }

        return this.storedAmounts;
    }

    private void loadCellItems() {
        var stacks = getStoredStacks();
        for (var stack : stacks) {
            storedAmounts.put(stack.what(), stack.amount());
        }
    }

    @Override
    public double getIdleDrain() {
        return (double) cellType.getIdleDrainBytes() / (1 << 20);
    }

    @Override
    public void persist() {
        if (this.isPersisted) {
            return;
        }

        var itemCount = 0L;
        var stacks = new ArrayList<GenericStack>(storedAmounts.size());

        for (var entry : this.storedAmounts.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            itemCount += amount;

            if (amount > 0) {
                stacks.add(new GenericStack(entry.getKey(), amount));
            }
        }

        ListTag list = new ListTag();
        for (GenericStack stack : stacks) {
            list.add(GenericStack.writeTag(stack));
        }
        cellStack.getOrCreateTag().put("eco_cell_contents", list);

        int actualTypes = this.storedAmounts.size();
        if (actualTypes > this.maxItemTypes) {
            LOGGER.warn(
                    "ECO storage cell contains more types than allowed: actual={} max={} stack={}",
                    actualTypes,
                    this.maxItemTypes,
                    cellStack);
        }
        this.storedItems = actualTypes;

        this.storedItemCount = itemCount;
        this.isPersisted = true;
    }

    protected void saveChanges() {
        this.isPersisted = false;
        if (this.container == null) {
            this.persist();
        } else if (this.container instanceof IBatchedECOCellSaveProvider) {
            this.container.saveChanges();
        } else {
            this.persist();
            this.container.saveChanges();
        }
    }

    @Override
    public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
        if (amount <= 0 || !keyType.contains(what)) {
            return 0;
        }

        if (!this.partitionList.matchesFilter(what, this.partitionListMode)) {
            return 0;
        }

        if (this.cellType.isBlackListed(cellStack, what)) {
            return 0;
        }

        // Run regular insert logic and then apply void upgrade to the returned value.
        long inserted = innerInsert(what, amount, mode);

        // In the event that a void card is being used on a (full) unformatted cell,
        // ensure it doesn't void any items
        // that the cell isn't even storing and cannot store to begin with
        if (partitionList.isEmpty() && hasVoidUpgrade && !canHoldNewItem()) {
            return getCellItems().containsKey(what) ? amount : inserted;
        }

        return hasVoidUpgrade ? amount : inserted;
    }

    private long innerInsert(AEKey what, long amount, Actionable mode) {
        if (what instanceof AEItemKey itemKey) {
            var stack = itemKey.toStack();

            var cellInv = StorageCells.getCellInventory(stack, null);
            if (cellInv != null && !cellInv.canFitInsideCell()) {
                return 0;
            }
        }

        var currentAmount = this.getCellItems().getLong(what);
        long remainingItemCount = this.getRemainingItemCount();

        if (currentAmount <= 0) {
            if (!canHoldNewItem()) {
                // No space for more types
                return 0;
            }

            remainingItemCount -= (long) this.getBytesPerType() * keyType.getAmountPerByte();
            if (remainingItemCount <= 0) {
                return 0;
            }
        }

        remainingItemCount = Math.max(0, Math.min(Long.MAX_VALUE - currentAmount, remainingItemCount));

        if (amount > remainingItemCount) {
            amount = remainingItemCount;
        }

        if (mode == Actionable.MODULATE) {
            long newAmount = currentAmount + amount;
            getCellItems().put(what, newAmount);
            if (currentAmount <= 0) {
                storedItems++;
            }
            storedItemCount = saturatedAdd(storedItemCount, amount);
            this.saveChanges();
        }

        return amount;
    }

    @Override
    public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
        var currentAmount = getCellItems().getLong(what);
        if (currentAmount > 0) {
            if (amount >= currentAmount) {
                if (mode == Actionable.MODULATE) {
                    getCellItems().remove(what, currentAmount);
                    storedItems = Math.max(0, storedItems - 1);
                    storedItemCount = Math.max(0, storedItemCount - currentAmount);
                    this.saveChanges();
                }

                return currentAmount;
            } else {
                if (mode == Actionable.MODULATE) {
                    getCellItems().put(what, currentAmount - amount);
                    storedItemCount = Math.max(0, storedItemCount - amount);
                    this.saveChanges();
                }

                return amount;
            }
        }

        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return getAvailableStacks().isEmpty();
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        for (var entry : Object2LongMaps.fastIterable(this.getCellItems())) {
            out.add(entry.getKey(), entry.getLongValue());
        }
    }

    @Override
    public Component getDescription() {
        return cellStack.getHoverName();
    }

    @Override
    public boolean isPreferredStorageFor(AEKey what, IActionSource source) {
        boolean used = !this.getCellItems().isEmpty() && this.insert(what, 1, Actionable.SIMULATE, source) == 1;
        boolean sameItem = this.extract(what, 1, Actionable.SIMULATE, source) > 0;
        return used || sameItem;
    }

    @Override
    public ECOCellType getCellType() {
        return ((ECOStorageCellItem) cellStack.getItem()).getCellType();
    }

    public IUpgradeInventory getUpgradesInventory() {
        return ((ECOStorageCellItem) cellStack.getItem()).getUpgrades(cellStack);
    }

    public ConfigInventory getConfigInventory() {
        return ((ECOStorageCellItem) cellStack.getItem()).getConfigInventory(cellStack);
    }

    private static long saturatedAdd(long a, long b) {
        long result = a + b;
        return result < 0 ? Long.MAX_VALUE : result;
    }
}
