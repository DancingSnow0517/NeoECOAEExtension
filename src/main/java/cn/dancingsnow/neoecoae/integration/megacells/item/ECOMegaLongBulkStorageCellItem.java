package cn.dancingsnow.neoecoae.integration.megacells.item;

import appeng.items.contents.CellConfig;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOBulkMarkableCellItem;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.integration.megacells.MegaCellCapacities;
import cn.dancingsnow.neoecoae.integration.megacells.NEMegaItems;
import cn.dancingsnow.neoecoae.integration.megacells.backend.ECOMegaLongBulkStorageCell;
import cn.dancingsnow.neoecoae.items.ECOStorageCellItem;
import gripe._90.megacells.definition.MEGAItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ECOMegaLongBulkStorageCellItem extends ECOStorageCellItem implements IECOBulkMarkableCellItem {
    public ECOMegaLongBulkStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type) {
        super(properties, tier, AEKeyType.items(), type, Long.MAX_VALUE,
            MegaCellCapacities.normalBytesPerType(tier), 1024.0);
    }

    @Override
    public int getTotalTypes() {
        return MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        int slots = getUpgrades(stack).isInstalled(NEMegaItems.ECO_MEGA_UPGRADE_CARD)
            ? MegaCellCapacities.LONG_BULK_UPGRADED_TYPE_LIMIT
            : MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
        return createPreservingConfigInventory(stack, slots);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return new LockedUpgradeInventory(stack, super.getUpgrades(stack));
    }

    private static ConfigInventory createPreservingConfigInventory(ItemStack stack, int activeSlots) {
        ConfigInventory[] holder = new ConfigInventory[1];
        holder[0] = ConfigInventory.configTypes(
            key -> AEKeyType.items().contains(key), activeSlots,
            () -> saveConfigInventory(stack, holder[0]));
        // Loading is read-only: a workbench may be copying this stack at the same time.
        // Do not publish partially loaded configurations through the change listener.
        holder[0].beginBatch();
        try {
            List<GenericStack> stored = configuredStacks(stack);
            for (int slot = 0; slot < activeSlots; slot++) holder[0].setStack(slot, stored.get(slot));
        } finally {
            holder[0].endBatchSuppressed();
        }
        return holder[0];
    }

    private static void saveConfigInventory(ItemStack stack, ConfigInventory activeInventory) {
        var stored = fullConfig(stack);
        stored.beginBatch();
        try {
            for (int slot = 0; slot < activeInventory.size(); slot++) {
                stored.setStack(slot, activeInventory.getStack(slot));
            }
        } finally {
            stored.endBatch();
        }
    }

    private static ConfigInventory fullConfig(ItemStack stack) {
        return CellConfig.create(key -> AEKeyType.items().contains(key), stack,
            MegaCellCapacities.LONG_BULK_UPGRADED_TYPE_LIMIT);
    }

    public static List<GenericStack> configuredStacks(ItemStack stack) {
        var inventory = fullConfig(stack);
        List<GenericStack> entries = new ArrayList<>(inventory.size());
        for (int slot = 0; slot < inventory.size(); slot++) entries.add(inventory.getStack(slot));
        return entries;
    }

    private static boolean hasTooManyMarkers(ItemStack stack) {
        List<GenericStack> configured = configuredStacks(stack);
        return configured.stream().filter(java.util.Objects::nonNull).count()
            > MegaCellCapacities.LONG_BULK_TYPE_LIMIT;
    }

    private static void compactMarkers(ItemStack stack) {
        List<GenericStack> configured = configuredStacks(stack).stream()
            .filter(java.util.Objects::nonNull).toList();
        var inventory = fullConfig(stack);
        inventory.beginBatch();
        try {
            for (int slot = 0; slot < inventory.size(); slot++) {
                inventory.setStack(slot, slot < configured.size() ? configured.get(slot) : null);
            }
        } finally {
            inventory.endBatch();
        }
    }

    private static final class LockedUpgradeInventory implements IUpgradeInventory {
        private final ItemStack cellStack;
        private final IUpgradeInventory delegate;

        private LockedUpgradeInventory(ItemStack cellStack, IUpgradeInventory delegate) {
            this.cellStack = cellStack;
            this.delegate = delegate;
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            if (isLockedCard(slot) && (stack.isEmpty()
                || !stack.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get()))) {
                return;
            }
            if (stack.is(MEGAItems.COMPRESSION_CARD.asItem())) {
                return;
            }
            boolean removingCard = delegate.getStackInSlot(slot).is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get())
                && !stack.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get());
            delegate.setItemDirect(slot, stack);
            if (removingCard) {
                compactMarkers(cellStack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.is(MEGAItems.COMPRESSION_CARD.asItem())
                && delegate.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.is(MEGAItems.COMPRESSION_CARD.asItem())) {
                return stack;
            }
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (isLockedCard(slot)) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = delegate.extractItem(slot, amount, simulate);
            if (!simulate && extracted.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get())) {
                compactMarkers(cellStack);
            }
            return extracted;
        }

        private boolean isLockedCard(int slot) {
            ItemStack installed = delegate.getStackInSlot(slot);
            return !installed.isEmpty()
                && installed.is(NEMegaItems.ECO_MEGA_UPGRADE_CARD.get())
                && hasTooManyMarkers(cellStack);
        }

        @Override
        public ItemLike getUpgradableItem() {
            return delegate.getUpgradableItem();
        }

        @Override
        public int getInstalledUpgrades(ItemLike upgrade) {
            return delegate.getInstalledUpgrades(upgrade);
        }

        @Override
        public int getMaxInstalled(ItemLike upgrade) {
            return delegate.getMaxInstalled(upgrade);
        }

        @Override
        public void readFromNBT(CompoundTag data, String subtag) {
            delegate.readFromNBT(data, subtag);
        }

        @Override
        public void writeToNBT(CompoundTag data, String subtag) {
            delegate.writeToNBT(data, subtag);
        }
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, ISaveProvider host) {
        return new ECOMegaLongBulkStorageCell(stack, host);
    }

}
