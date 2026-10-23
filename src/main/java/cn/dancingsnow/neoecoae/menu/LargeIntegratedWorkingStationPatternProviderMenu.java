package cn.dancingsnow.neoecoae.menu;

import appeng.api.inventories.InternalInventory;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import cn.dancingsnow.neoecoae.platform.menu.NEMenuTypes;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.core.definitions.AEItems;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.AuxiliaryPatternHolder;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The copied ExtendedAE-style menu for the large workstation interface.
 *
 * <p>All physical pattern slots remain in the menu while capacity cards unlock their visible rows.</p>
 */
public class LargeIntegratedWorkingStationPatternProviderMenu extends PatternProviderMenu {
    public static final MenuType<LargeIntegratedWorkingStationPatternProviderMenu> TYPE = NEMenuTypes.create(
        NeoECOAE.id("large_integrated_working_station_interface"),
        LargeIntegratedWorkingStationPatternProviderMenu::new, PatternProviderLogicHost.class);

    protected LargeIntegratedWorkingStationPatternProviderMenu(
        int id,
        Inventory playerInventory,
        PatternProviderLogicHost host
    ) {
        super(TYPE, id, playerInventory, host);
        if (logic instanceof LargeWorkstationPatternProvider provider) {
            for (int slot = 0; slot < LargeWorkstationPatternProvider.MAX_PATTERN_SLOTS; slot++) {
                super.addSlot(new DiskAwarePatternSlot(provider.getMenuPatternInventory(), slot, provider),
                    SlotSemantics.ENCODED_PATTERN);
            }
            for (int slot = 0; slot < provider.getUpgrades().size(); slot++) {
                super.addSlot(new WorkstationUpgradeSlot(provider, slot), SlotSemantics.UPGRADE);
            }
        }
    }

    public int getPatternSlotCount() {
        return logic.getPatternInv().size();
    }

    public IUpgradeInventory getCapacityUpgrades() {
        return ((LargeWorkstationPatternProvider) logic).getCapacityUpgrades();
    }

    protected boolean isValidQuickMoveDestination(Slot slot, ItemStack stack, boolean fromPlayerSide) {
        if (fromPlayerSide) {
            SlotSemantic target = Upgrades.isUpgradeCardItem(stack)
                ? SlotSemantics.UPGRADE : SlotSemantics.ENCODED_PATTERN;
            if (getSlotSemantic(slot) != target) return false;
        }
        return slot.mayPlace(stack);
    }

    /** AE2 15 has no quick-move destination hook, so route player inputs through the semantic slots here. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot source = slots.get(index);
        if (source.container != getPlayerInventory()) return super.quickMoveStack(player, index);
        if (!source.hasItem() || !source.mayPickup(player)
            || isPlayerInventorySlotLocked(source.getContainerSlot())) return ItemStack.EMPTY;
        ItemStack original = source.getItem().copy();
        ItemStack remaining = original.copy();
        SlotSemantic target = Upgrades.isUpgradeCardItem(remaining)
            ? SlotSemantics.UPGRADE : SlotSemantics.ENCODED_PATTERN;
        // Merge existing stacks before filling empty slots, preserving all unaccepted player items.
        for (int pass = 0; pass < 2 && !remaining.isEmpty(); pass++) {
            for (Slot slot : getSlots(target)) {
                if ((pass == 0) != slot.hasItem() || !isValidQuickMoveDestination(slot, remaining, true)) continue;
                remaining = slot.safeInsert(remaining);
                if (remaining.isEmpty()) break;
            }
        }
        if (remaining.getCount() == original.getCount()) return ItemStack.EMPTY;
        source.set(remaining);
        source.setChanged();
        source.onTake(player, original.copyWithCount(original.getCount() - remaining.getCount()));
        return original;
    }

    /** Build our fixed physical slots after the superclass, so both sides use identical slot indices. */
    @Override
    protected Slot addSlot(Slot slot, SlotSemantic semantic) {
        // The seven-row artwork replaces the old return-inventory area. Keep the provider's persisted
        // return buffer intact, but do not expose it as an input destination or overlap it with patterns.
        if (semantic == SlotSemantics.STORAGE) return slot;
        // Addons can inject upgrades before the superclass assigns logic. This menu always owns
        // its physical pattern and upgrade slots, which are added explicitly after super returns.
        if (semantic == SlotSemantics.UPGRADE || semantic == SlotSemantics.ENCODED_PATTERN) {
            // Installed cards and recovered legacy rows are not synchronized while the menu is being constructed.
            // Letting the superclass vary its slot count would shift return-inventory indices between the two sides.
            return slot;
        }
        return super.addSlot(slot, semantic);
    }

    /** AE2's pattern-slot rule, plus the one stack type that rule cannot know about: this machine's disks. */
    private static final class DiskAwarePatternSlot extends RestrictedInputSlot {
        private final AuxiliaryPatternHolder holder;
        private final LargeWorkstationPatternProvider provider;

        private DiskAwarePatternSlot(InternalInventory inventory, int slotIndex, LargeWorkstationPatternProvider provider) {
            super(PlacableItemType.ENCODED_PATTERN, inventory, slotIndex);
            this.provider = provider;
            this.holder = provider.getWorkstationInterface();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Asked on both sides: the client draws the placement, the server authorises it. ownsAuxiliary()
            // reads the stack alone, so it answers the same either way.
            return provider.canInsertPattern(getSlotIndex()) && (super.mayPlace(stack) || holder.ownsAuxiliary(stack));
        }
    }

    private static final class WorkstationUpgradeSlot extends RestrictedInputSlot {
        private final LargeWorkstationPatternProvider provider;

        private WorkstationUpgradeSlot(LargeWorkstationPatternProvider provider, int slot) {
            super(PlacableItemType.UPGRADES, provider.getUpgrades(), slot);
            this.provider = provider;
            setNotDraggable();
        }

        @Override
        public boolean mayPickup(Player player) {
            return (!AEItems.CAPACITY_CARD.isSameAs(getItem()) || provider.canRemoveCapacityCard())
                && super.mayPickup(player);
        }
    }
}
