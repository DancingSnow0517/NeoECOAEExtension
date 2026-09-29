package cn.dancingsnow.neoecoae.menu;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantic;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.slot.RestrictedInputSlot;
import cn.dancingsnow.neoecoae.NeoECOAE;
import cn.dancingsnow.neoecoae.api.AuxiliaryPatternHolder;
import cn.dancingsnow.neoecoae.blocks.entity.LargeWorkstationPatternProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The copied ExtendedAE-style menu for the large workstation interface.
 *
 * <p>The only deviation from the copied menu is the pattern slot: see {@link #addSlot}.</p>
 */
public class LargeIntegratedWorkingStationPatternProviderMenu extends PatternProviderMenu {
    public static final MenuType<LargeIntegratedWorkingStationPatternProviderMenu> TYPE = MenuTypeBuilder
        .create(LargeIntegratedWorkingStationPatternProviderMenu::new, PatternProviderLogicHost.class)
        .buildUnregistered(NeoECOAE.id("large_integrated_working_station_interface"));

    protected LargeIntegratedWorkingStationPatternProviderMenu(
        int id,
        Inventory playerInventory,
        PatternProviderLogicHost host
    ) {
        super(TYPE, id, playerInventory, host);
    }

    /**
     * Widens the pattern slot to accept this machine's pattern disks.
     *
     * <p>{@code PatternProviderMenu} builds every pattern slot as
     * {@code RestrictedInputSlot(PROVIDER_PATTERN, ...)}, whose rule is "an encoded pattern", so a pattern disk
     * - a container of patterns, not one - is refused by this machine's own screen. Everything behind that
     * screen takes disks: the slots' inventory, the disk-aware terminal view, and the disk management
     * terminal, which is why a disk could be put in from there but not from here. The bus met the same wall
     * and answered it in its inventory filter; this is the same rule, applied where the restriction actually
     * lives.</p>
     *
     * <p>Called from the superclass constructor, before this class's fields are initialised - the superclass
     * assigns {@code logic} before it builds the pattern slots and nothing else is read here, which is what
     * makes that safe. Slots that are not pattern slots, and calls made before {@code logic} is set, fall
     * through untouched.</p>
     */
    @Override
    protected Slot addSlot(Slot slot, SlotSemantic semantic) {
        if (semantic == SlotSemantics.ENCODED_PATTERN
            && slot instanceof RestrictedInputSlot patternSlot
            && logic instanceof LargeWorkstationPatternProvider provider) {
            AuxiliaryPatternHolder holder = provider.getWorkstationInterface();
            return super.addSlot(
                new DiskAwarePatternSlot(patternSlot.getInventory(), patternSlot.getSlotIndex(), holder), semantic);
        }
        return super.addSlot(slot, semantic);
    }

    /** AE2's pattern-slot rule, plus the one stack type that rule cannot know about: this machine's disks. */
    private static final class DiskAwarePatternSlot extends RestrictedInputSlot {
        private final AuxiliaryPatternHolder holder;

        private DiskAwarePatternSlot(InternalInventory inventory, int slotIndex, AuxiliaryPatternHolder holder) {
            super(PlacableItemType.PROVIDER_PATTERN, inventory, slotIndex);
            this.holder = holder;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Asked on both sides: the client draws the placement, the server authorises it. ownsAuxiliary()
            // reads the stack alone, so it answers the same either way.
            return super.mayPlace(stack) || holder.ownsAuxiliary(stack);
        }
    }
}
