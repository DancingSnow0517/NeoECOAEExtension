package cn.dancingsnow.neoecoae.items;

import appeng.api.ids.AEComponents;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKeyType;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IECOBulkMarkableCellItem;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import cn.dancingsnow.neoecoae.impl.storage.bulk.ECOBulkStorageCell;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class ECOBulkStorageCellItem extends ECOStorageCellItem implements IECOBulkMarkableCellItem {
    public static final int TYPE_LIMIT = 25;
    public static final int UPGRADED_TYPE_LIMIT = 50;
    // Resolve by registry id so legacy disks never initialize the optional MEGA item registrations.
    private static final ResourceLocation EXPANSION_CARD =
        ResourceLocation.fromNamespaceAndPath("neoecoae", "eco_mega_upgrade_card");
    private final int baseTypeLimit;
    public ECOBulkStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type) {
        this(properties, tier, type, TYPE_LIMIT);
    }
    public ECOBulkStorageCellItem(Properties properties, IECOTier tier, Supplier<ECOCellType> type, int baseTypeLimit) {
        super(properties, tier, AEKeyType.items(), type, Long.MAX_VALUE,
            1 << (12 + tier.getTier()), baseTypeLimit == 1 ? (double) tier.getStorageTotalBytes() / (1L << 20) : 1024.0);
        if (baseTypeLimit != 1 && baseTypeLimit != TYPE_LIMIT) throw new IllegalArgumentException("Unsupported bulk type limit");
        this.baseTypeLimit = baseTypeLimit;
    }

    @Override
    public int getTotalTypes() {
        return baseTypeLimit;
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack stack) {
        return createPreservingConfigInventory(stack, activeSlots(stack));
    }

    public int activeSlots(ItemStack stack) {
        if (baseTypeLimit == 1) return 1;
        return BuiltInRegistries.ITEM.getOptional(EXPANSION_CARD)
            .filter(card -> getUpgrades(stack).isInstalled(card)).isPresent() ? UPGRADED_TYPE_LIMIT : TYPE_LIMIT;
    }

    private static boolean isExpansionCard(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getOptional(EXPANSION_CARD).map(stack::is).orElse(false);
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        var cell = (ECOBulkStorageCell) getCellInventory(stack);
        if (cell == null) return;
        lines.add(Component.translatable("tooltip.neoecoae.bulk.capacity", cell.getStoredItemTypes(), cell.getTotalItemTypes()));
        lines.add(Component.translatable(baseTypeLimit == 1 ? "tooltip.neoecoae.bulk.legacy_configure" : "tooltip.neoecoae.bulk.configure").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.neoecoae.bulk.crafting").withStyle(ChatFormatting.GRAY));
        if (!cell.getPendingOutputs().isEmpty()) lines.add(Component.translatable("tooltip.neoecoae.bulk.pending").withStyle(ChatFormatting.YELLOW));
        if (cell.getUnresolvedEntryCount() > 0) lines.add(Component.translatable("tooltip.neoecoae.storage.bulk_unresolved", cell.getUnresolvedEntryCount()).withStyle(ChatFormatting.RED));
        if (cell.getRecoveringEntryCount() > 0) lines.add(Component.translatable("tooltip.neoecoae.bulk.recipe_changed", cell.getRecoveringEntryCount()).withStyle(ChatFormatting.YELLOW));
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return new LockedUpgradeInventory(stack, super.getUpgrades(stack));
    }

    private static ConfigInventory createPreservingConfigInventory(ItemStack stack, int activeSlots) {
        ConfigInventory[] holder = new ConfigInventory[1];
        holder[0] = ConfigInventory.configTypes(activeSlots)
            .supportedTypes(Set.of(AEKeyType.items()))
            .changeListener(() -> saveConfigInventory(stack, holder[0]))
            .build();
        // Loading is read-only: a workbench may be copying this stack at the same time.
        // Do not publish partially loaded configurations through the change listener.
        holder[0].beginBatch();
        try {
            holder[0].readFromList(stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of()));
        } finally {
            holder[0].endBatchSuppressed();
        }
        return holder[0];
    }

    private static void saveConfigInventory(ItemStack stack, ConfigInventory activeInventory) {
        List<GenericStack> stored = new ArrayList<>(
            stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of()));
        while (stored.size() < UPGRADED_TYPE_LIMIT) {
            stored.add(null);
        }
        List<GenericStack> active = activeInventory.toList();
        for (int slot = 0; slot < active.size(); slot++) {
            stored.set(slot, active.get(slot));
        }
        stack.set(AEComponents.STORAGE_CELL_CONFIG_INV, stored);
    }

    private static boolean hasTooManyMarkers(ItemStack stack) {
        List<GenericStack> configured = stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of());
        return configured.stream().filter(java.util.Objects::nonNull).count()
            > TYPE_LIMIT;
    }

    private static void compactMarkers(ItemStack stack) {
        List<GenericStack> configured = stack.getOrDefault(AEComponents.STORAGE_CELL_CONFIG_INV, List.of());
        stack.set(AEComponents.STORAGE_CELL_CONFIG_INV,
            configured.stream().filter(java.util.Objects::nonNull).toList());
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
                || !isExpansionCard(stack))) {
                return;
            }
            if (!stack.isEmpty() && !isItemValid(slot, stack)) return;
            boolean removingCard = isExpansionCard(delegate.getStackInSlot(slot))
                && !isExpansionCard(stack);
            delegate.setItemDirect(slot, stack);
            if (removingCard) {
                compactMarkers(cellStack);
            }
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isExpansionCard(stack) && delegate.isItemValid(slot, stack);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) {
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
            if (!simulate && isExpansionCard(extracted)) {
                compactMarkers(cellStack);
            }
            return extracted;
        }

        private boolean isLockedCard(int slot) {
            ItemStack installed = delegate.getStackInSlot(slot);
            return !installed.isEmpty()
                && isExpansionCard(installed)
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
        public void readFromNBT(CompoundTag data, String subtag, HolderLookup.Provider registries) {
            delegate.readFromNBT(data, subtag, registries);
        }

        @Override
        public void writeToNBT(CompoundTag data, String subtag, HolderLookup.Provider registries) {
            delegate.writeToNBT(data, subtag, registries);
        }
    }

    @Override
    protected ECOStorageCell createCellInventory(ItemStack stack, ISaveProvider host) {
        return new ECOBulkStorageCell(stack, host);
    }

}
