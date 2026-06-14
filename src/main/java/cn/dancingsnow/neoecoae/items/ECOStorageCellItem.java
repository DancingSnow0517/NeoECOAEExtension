package cn.dancingsnow.neoecoae.items;

import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.AEConfig;
import appeng.core.localization.PlayerMessages;
import appeng.core.localization.Tooltips;
import appeng.items.contents.CellConfig;
import appeng.items.storage.StorageCellTooltipComponent;
import appeng.util.ConfigInventory;
import appeng.util.InteractionUtil;
import cn.dancingsnow.neoecoae.all.NECellTypes;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.IBasicECOCellItem;
import cn.dancingsnow.neoecoae.api.storage.IECOCellHandler;
import cn.dancingsnow.neoecoae.api.storage.IECOStorageCell;
import cn.dancingsnow.neoecoae.compat.ae2.StorageCellDisassemblyRecipe;
import cn.dancingsnow.neoecoae.config.NEConfig;
import cn.dancingsnow.neoecoae.impl.storage.ECOStorageCell;
import com.tterrag.registrate.util.entry.RegistryEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public class ECOStorageCellItem extends Item implements IBasicECOCellItem {

    @Getter
    private final IECOTier tier;

    private final long fallbackTotalBytes;
    private final int bytesPerType;
    private final int totalTypes;
    private final AEKeyType keyType;
    /**
     * Registered cell-type entry — direct access avoids Component-desc matching
     * bugs.
     */
    private final RegistryEntry<ECOCellType> cellType;

    public ECOStorageCellItem(
            Properties properties, IECOTier tier, AEKeyType keyType, RegistryEntry<ECOCellType> cellType) {
        super(properties);
        this.tier = tier;
        this.fallbackTotalBytes = tier.getStorageTotalBytes();
        this.bytesPerType = 1 << (12 + tier.getTier());
        this.totalTypes = tier.getStorageTotalTypes(keyType);
        this.keyType = keyType;
        this.cellType = cellType;
    }

    @Override
    public AEKeyType getKeyType() {
        return keyType;
    }

    @Override
    public long getBytes() {
        return NEConfig.getEcoStorageCellCapacity(tier, fallbackTotalBytes);
    }

    @Override
    public long getIdleDrainBytes() {
        return fallbackTotalBytes;
    }

    @Override
    public int getBytesPerType() {
        return bytesPerType;
    }

    @Override
    public int getTotalTypes() {
        return totalTypes;
    }

    @Override
    public ECOCellType getCellType() {
        return cellType.get();
    }

    @Override
    public void appendHoverText(
            ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag tooltipFlag) {
        var handler = getCellInventory(stack);
        if (handler == null) {
            return;
        }
        lines.add(Tooltips.bytesUsed(handler.getUsedBytes(), handler.getTotalBytes()));
        lines.add(Tooltips.typesUsed(handler.getStoredItemTypes(), handler.getTotalItemTypes()));
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        var handler = getCellInventory(stack);
        if (handler == null) {
            return Optional.empty();
        }

        var upgradeStacks = new ArrayList<ItemStack>();
        if (AEConfig.instance().isTooltipShowCellUpgrades()) {
            for (var upgrade : handler.getUpgradesInventory()) {
                upgradeStacks.add(upgrade);
            }
        }

        // Find items with the highest stored amount
        boolean hasMoreContent;
        List<GenericStack> content;
        if (AEConfig.instance().isTooltipShowCellContent()) {
            content = new ArrayList<>();

            var maxCountShown = AEConfig.instance().getTooltipMaxCellContentShown();

            var availableStacks = handler.getAvailableStacks();
            for (var entry : availableStacks) {
                content.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }

            // Fill up with stacks from the filter if it's not inverted
            if (content.size() < maxCountShown && handler.getPartitionListMode() == IncludeExclude.WHITELIST) {
                var config = handler.getConfigInventory();
                for (int i = 0; i < config.size(); i++) {
                    var what = config.getKey(i);
                    if (what != null) {
                        // Don't add it twice
                        if (availableStacks.get(what) <= 0) {
                            content.add(new GenericStack(what, 0));
                        }
                    }
                    if (content.size() > maxCountShown) {
                        break; // Don't need to add filters beyond 6 (to determine if it has more than 5 below)
                    }
                }
            }

            // Sort by amount descending
            content.sort(Comparator.comparingLong(GenericStack::amount).reversed());

            hasMoreContent = content.size() > maxCountShown;
            if (content.size() > maxCountShown) {
                content.subList(maxCountShown, content.size()).clear();
            }
        } else {
            hasMoreContent = false;
            content = Collections.emptyList();
        }

        return Optional.of(new StorageCellTooltipComponent(upgradeStacks, content, hasMoreContent, true));
    }

    @Nullable public static ECOStorageCell getCellInventory(ItemStack stack) {
        return getCellInventory(stack, null);
    }

    @Nullable public static ECOStorageCell getCellInventory(ItemStack stack, @Nullable ISaveProvider host) {
        if (stack.getItem() instanceof ECOStorageCellItem) {
            return new ECOStorageCell(stack, host);
        }
        return null;
    }

    @Override
    public FuzzyMode getFuzzyMode(ItemStack is) {
        if (is.hasTag() && is.getTag().contains("fuzzyMode")) {
            try {
                return FuzzyMode.valueOf(is.getTag().getString("fuzzyMode"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FuzzyMode.IGNORE_ALL;
    }

    @Override
    public void setFuzzyMode(ItemStack is, FuzzyMode fzMode) {
        is.getOrCreateTag().putString("fuzzyMode", fzMode.name());
    }

    @Override
    public ConfigInventory getConfigInventory(ItemStack is) {
        return CellConfig.create(key -> key.getType() == keyType, is);
    }

    @Override
    public IUpgradeInventory getUpgrades(ItemStack stack) {
        return UpgradeInventories.forItem(stack, 4);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        this.disassembleDrive(player.getItemInHand(hand), level, player);
        return new InteractionResultHolder<>(
                InteractionResult.sidedSuccess(level.isClientSide()), player.getItemInHand(hand));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        return this.disassembleDrive(stack, context.getLevel(), context.getPlayer())
                ? InteractionResult.sidedSuccess(context.getLevel().isClientSide())
                : InteractionResult.PASS;
    }

    private boolean disassembleDrive(ItemStack stack, Level level, Player player) {
        if (!InteractionUtil.isInAlternateUseMode(player)) {
            return false;
        }

        List<ItemStack> disassembledStacks = StorageCellDisassemblyRecipe.getDisassemblyResult(level, stack.getItem());
        if (disassembledStacks.isEmpty()) {
            return false;
        }

        Inventory playerInventory = player.getInventory();
        if (playerInventory.getSelected() != stack) {
            return false;
        }

        ECOStorageCell cellInventory = getCellInventory(stack);
        if (cellInventory != null && !cellInventory.getAvailableStacks().isEmpty()) {
            player.displayClientMessage(PlayerMessages.OnlyEmptyCellsCanBeDisassembled.text(), true);
            return false;
        }

        playerInventory.setItem(playerInventory.selected, ItemStack.EMPTY);

        // Drop items from the recipe.
        for (var disassembledStack : disassembledStacks) {
            playerInventory.placeItemBackInInventory(disassembledStack.copy());
        }

        // Drop upgrades
        getUpgrades(stack).forEach(playerInventory::placeItemBackInInventory);

        return true;
    }

    // ── Cell handlers (registered in ECOStorageCells) ──

    /** Matches only item (non-fluid, non-chemical) storage cells. */
    public static class ItemCellHandler implements IECOCellHandler {

        public static final ItemCellHandler INSTANCE = new ItemCellHandler();

        @Override
        public boolean isCell(ItemStack stack) {
            if (stack.getItem() instanceof ECOStorageCellItem item) {
                return item.getCellType() == NECellTypes.ITEM.get() && item.getKeyType() == AEKeyType.items();
            }
            return false;
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
            return ECOStorageCellItem.getCellInventory(is, host);
        }
    }

    /** Matches only fluid storage cells. */
    public static class FluidCellHandler implements IECOCellHandler {

        public static final FluidCellHandler INSTANCE = new FluidCellHandler();

        @Override
        public boolean isCell(ItemStack stack) {
            if (stack.getItem() instanceof ECOStorageCellItem item) {
                return item.getCellType() == NECellTypes.FLUID.get() && item.getKeyType() == AEKeyType.fluids();
            }
            return false;
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
            return ECOStorageCellItem.getCellInventory(is, host);
        }
    }

    /**
     * @deprecated Replaced by {@link ItemCellHandler} and {@link FluidCellHandler}.
     */
    @Deprecated
    public static class Handler implements IECOCellHandler {

        public static final Handler INSTANCE = new Handler();

        @Override
        public boolean isCell(ItemStack stack) {
            return stack.getItem() instanceof ECOStorageCellItem;
        }

        @Override
        public @Nullable IECOStorageCell getCellInventory(ItemStack is, @Nullable ISaveProvider host) {
            return ECOStorageCellItem.getCellInventory(is, host);
        }
    }
}
