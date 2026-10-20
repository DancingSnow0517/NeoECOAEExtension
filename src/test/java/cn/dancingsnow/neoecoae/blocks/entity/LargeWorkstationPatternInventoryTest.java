package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.IManagedGridNode;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.ItemDefinition;
import appeng.helpers.patternprovider.PatternProviderLogic;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.registration.NERegistrate;
import cn.dancingsnow.neoecoae.menu.LargeIntegratedWorkingStationPatternProviderMenu;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.SlotSemantics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationPatternInventoryTest {
    private static Object originalCapacityItem;

    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        try (var registrate = mockStatic(NERegistrate.class)) {
            registrate.when(() -> NERegistrate.create("neoecoae")).thenReturn(mock(NERegistrate.class));
            Class.forName("cn.dancingsnow.neoecoae.NeoECOAE");
        }
        // The isolated JVM has no deferred mod-item registration. Bind the definition to a registered stand-in.
        var itemField = ItemDefinition.class.getDeclaredField("item");
        itemField.setAccessible(true);
        originalCapacityItem = itemField.get(AEItems.CAPACITY_CARD);
        var deferred = mock(net.neoforged.neoforge.registries.DeferredItem.class);
        when(deferred.get()).thenReturn(Items.PAPER);
        when(deferred.asItem()).thenReturn(Items.PAPER);
        itemField.set(AEItems.CAPACITY_CARD, deferred);
        Upgrades.add(AEItems.CAPACITY_CARD, Blocks.BARREL, 4);
        Upgrades.add(Items.REDSTONE, Blocks.BARREL, 1);
    }

    @AfterAll
    static void restoreDeferredItem() throws Exception {
        if (originalCapacityItem == null) return;
        var field = ItemDefinition.class.getDeclaredField("item");
        field.setAccessible(true);
        field.set(AEItems.CAPACITY_CARD, originalCapacityItem);
    }

    @Test
    void eachOfFourNativeCapacityCardsImmediatelyAddsOneRowToTheSameInventoryView() {
        var provider = provider();
        var patterns = provider.getPatternInv();
        assertEquals(5, provider.getCapacityUpgrades().size());
        assertEquals(36, patterns.size());
        assertEquals(72, provider.getMenuPatternInventory().size());
        for (int cards = 1; cards <= 4; cards++) {
            assertTrue(provider.getCapacityUpgrades().insertItem(cards - 1, AEItems.CAPACITY_CARD.stack(), false).isEmpty());
            assertEquals(4 + cards, provider.getUnlockedPatternRows());
            assertEquals((4 + cards) * 9, patterns.size());
            assertSame(patterns, provider.getPatternInv(), "Disk and terminal views must keep seeing capacity changes");
            assertTrue(provider.canInsertPattern((4 + cards) * 9 - 1));
            assertFalse(provider.canInsertPattern((4 + cards) * 9));
        }
        assertFalse(provider.getCapacityUpgrades().insertItem(4, AEItems.CAPACITY_CARD.stack(), false).isEmpty());
        assertEquals(8, provider.getUnlockedPatternRows());
    }

    @Test
    void upgradeSlotsRejectOtherItemsAndSimulationDoesNotExpandTheInventory() {
        var provider = provider();
        var upgrades = provider.getCapacityUpgrades();
        assertFalse(upgrades.insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty());
        assertFalse(upgrades.insertItem(0, new ItemStack(Items.EMERALD), false).isEmpty());
        assertTrue(upgrades.insertItem(0, AEItems.CAPACITY_CARD.stack(), true).isEmpty());
        assertEquals(36, provider.getPatternInv().size());
        assertTrue(upgrades.getStackInSlot(0).isEmpty());
        assertEquals(1, upgrades.insertItem(0, AEItems.CAPACITY_CARD.stack(2), false).getCount());
        assertEquals(1, upgrades.getStackInSlot(0).getCount());
        assertEquals(45, provider.getPatternInv().size());
    }

    @Test
    void aCapacityCardCannotBeExtractedUntilTheLastUnlockedRowIsEmpty() {
        var provider = provider();
        provider.getCapacityUpgrades().insertItem(0, AEItems.CAPACITY_CARD.stack(), false);
        provider.getPatternInv().setItemDirect(44, new ItemStack(Items.PAPER));
        assertFalse(provider.canRemoveCapacityCard());
        assertTrue(provider.getCapacityUpgrades().extractItem(0, 1, true).isEmpty());
        assertTrue(provider.getCapacityUpgrades().extractItem(0, 1, false).isEmpty());
        assertEquals(45, provider.getPatternInv().size());
        provider.getPatternInv().setItemDirect(44, ItemStack.EMPTY);
        assertTrue(provider.canRemoveCapacityCard());
        assertTrue(AEItems.CAPACITY_CARD.is(provider.getCapacityUpgrades().extractItem(0, 1, false)));
        assertEquals(36, provider.getPatternInv().size());
    }

    @Test
    void fourCapacityCardsAndOneRegisteredAddonCardShareThePersistedInventory() throws Exception {
        var provider = provider();
        var upgrades = provider.getUpgrades();
        assertSame(provider.getCapacityUpgrades(), upgrades);
        assertTrue(upgrades.insertItem(4, new ItemStack(Items.REDSTONE), false).isEmpty());
        assertFalse(upgrades.insertItem(0, new ItemStack(Items.REDSTONE), false).isEmpty());
        for (int slot = 0; slot < 4; slot++) {
            assertTrue(upgrades.insertItem(slot, AEItems.CAPACITY_CARD.stack(), false).isEmpty());
        }
        provider.getMenuPatternInventory().setItemDirect(71, new ItemStack(Items.PAPER));
        assertFalse(provider.canRemoveCapacityCard());

        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved = new CompoundTag();
        provider.writeToNBT(saved, registries);
        var restored = provider();
        restored.readFromNBT(saved, registries);
        assertEquals(4, restored.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD));
        assertEquals(1, restored.getUpgrades().getInstalledUpgrades(Items.REDSTONE));
        var menu = menu(restored);
        assertFalse(menu.getSlots(SlotSemantics.UPGRADE).get(0).mayPickup(mock(Player.class)));
        assertTrue(menu.getSlots(SlotSemantics.UPGRADE).get(4).mayPickup(mock(Player.class)));
        assertTrue(restored.getUpgrades().extractItem(0, 1, false).isEmpty());
        assertTrue(restored.getUpgrades().extractItem(4, 1, false).is(Items.REDSTONE));
        assertEquals(72, restored.getPatternInv().size());
    }

    @Test
    void previouslySavedExtraPatternsRemainRetrievableWithoutAllowingNewInsertionsIntoLockedRows() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var original = provider();
        original.getMenuPatternInventory().setItemDirect(35, new ItemStack(Items.PAPER));
        original.getMenuPatternInventory().setItemDirect(71, new ItemStack(Items.EMERALD));
        CompoundTag saved = new CompoundTag();
        original.writeToNBT(saved, registries);
        var restored = provider();
        restored.readFromNBT(saved, registries);
        assertEquals(4, restored.getUnlockedPatternRows());
        assertEquals(72, restored.getPatternInv().size());
        assertFalse(restored.canInsertPattern(71));
        assertFalse(restored.getPatternInv().insertItem(70, new ItemStack(Items.PAPER), false).isEmpty());
        assertTrue(restored.getPatternInv().getStackInSlot(35).is(Items.PAPER));
        assertTrue(restored.getPatternInv().extractItem(71, 1, false).is(Items.EMERALD));
        assertEquals(36, restored.getPatternInv().size());
    }

    @Test
    void originalFourRowSavesLoadWithoutMovingTheirPatterns() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var oldInventory = new appeng.util.inv.AppEngInternalInventory(36);
        oldInventory.setItemDirect(35, new ItemStack(Items.PAPER));
        CompoundTag saved = new CompoundTag();
        oldInventory.writeToNBT(saved, PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS, registries);
        var restored = provider();
        restored.readFromNBT(saved, registries);
        assertEquals(36, restored.getPatternInv().size());
        assertTrue(restored.getPatternInv().getStackInSlot(35).is(Items.PAPER));
        assertTrue(restored.getMenuPatternInventory().getStackInSlot(36).isEmpty());
    }

    @Test
    void installedCapacityCardsRoundTripAndRestoreTheirUnlockedRows() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var original = provider();
        original.getCapacityUpgrades().insertItem(0, AEItems.CAPACITY_CARD.stack(), false);
        original.getCapacityUpgrades().insertItem(1, AEItems.CAPACITY_CARD.stack(), false);
        CompoundTag saved = new CompoundTag();
        original.writeToNBT(saved, registries);
        var restored = provider();
        restored.readFromNBT(saved, registries);
        assertEquals(2, restored.getCapacityUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD));
        assertEquals(54, restored.getPatternInv().size());
    }

    @Test
    void menusUseIdenticalPhysicalIndicesBeforeCardsAndLegacyRowsAreSynchronized() throws Exception {
        var withoutCards = provider();
        var withCards = provider();
        for (int slot = 0; slot < 4; slot++) {
            withCards.getCapacityUpgrades().insertItem(slot, AEItems.CAPACITY_CARD.stack(), false);
        }
        var legacy = provider();
        legacy.getMenuPatternInventory().setItemDirect(71, new ItemStack(Items.EMERALD));
        var first = menu(withoutCards);
        for (var other : java.util.List.of(menu(withCards), menu(legacy))) {
            assertEquals(first.slots.size(), other.slots.size());
            for (var semantic : java.util.List.of(SlotSemantics.ENCODED_PATTERN, SlotSemantics.UPGRADE,
                SlotSemantics.STORAGE, SlotSemantics.PLAYER_INVENTORY, SlotSemantics.PLAYER_HOTBAR)) {
                assertEquals(first.getSlots(semantic).stream().map(slot -> slot.index).toList(),
                    other.getSlots(semantic).stream().map(slot -> slot.index).toList());
            }
        }
        assertEquals(72, first.getSlots(SlotSemantics.ENCODED_PATTERN).size());
        assertEquals(5, first.getSlots(SlotSemantics.UPGRADE).size());
        assertTrue(first.getSlots(SlotSemantics.STORAGE).isEmpty());
        assertEquals(36, first.getPatternSlotCount());
        withoutCards.getCapacityUpgrades().insertItem(0, AEItems.CAPACITY_CARD.stack(), false);
        assertEquals(45, first.getPatternSlotCount());
        assertEquals(72, first.getSlots(SlotSemantics.ENCODED_PATTERN).size());
    }

    @Test
    void shiftClickMovesPatternsIntoPatternSlotsAndLeavesTheReturnBufferUntouched() throws Exception {
        var provider = provider();
        var menu = serverMenu(provider);
        menu.getPlayerInventory().setItem(9, new ItemStack(Items.WRITABLE_BOOK, 3));
        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(any(ItemStack.class)))
                .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.WRITABLE_BOOK));
            menu.quickMoveStack(menu.getPlayer(), 9);
        }
        assertTrue(menu.getPlayerInventory().getItem(9).isEmpty());
        for (int slot = 0; slot < 3; slot++) {
            assertTrue(provider.getPatternInv().getStackInSlot(slot).is(Items.WRITABLE_BOOK));
            assertEquals(1, provider.getPatternInv().getStackInSlot(slot).getCount());
        }
        assertTrue(provider.getReturnInv().isEmpty());
        assertTrue(provider.getUpgrades().isEmpty());
    }

    @Test
    void shiftClickInstallsCapacityAndAddonCardsInTheRightUpgradeSlots() throws Exception {
        var provider = provider();
        var menu = serverMenu(provider);
        menu.getPlayerInventory().setItem(9, new ItemStack(Items.PAPER, 6));
        menu.getPlayerInventory().setItem(10, new ItemStack(Items.REDSTONE, 2));
        try (var upgrades = mockStatic(Upgrades.class, CALLS_REAL_METHODS)) {
            upgrades.when(() -> Upgrades.isUpgradeCardItem(any(ItemStack.class)))
                .thenAnswer(call -> {
                    ItemStack stack = call.getArgument(0);
                    return stack.is(Items.PAPER) || stack.is(Items.REDSTONE);
                });
            menu.quickMoveStack(menu.getPlayer(), 9);
            menu.quickMoveStack(menu.getPlayer(), 10);
            assertEquals(2, menu.getPlayerInventory().getItem(9).getCount());
            assertEquals(1, menu.getPlayerInventory().getItem(10).getCount());
            assertEquals(4, provider.getUpgrades().getInstalledUpgrades(AEItems.CAPACITY_CARD));
            assertEquals(1, provider.getUpgrades().getInstalledUpgrades(Items.REDSTONE));
            assertEquals(72, provider.getPatternInv().size());
            for (int slot = 0; slot < 4; slot++) assertTrue(provider.getUpgrades().getStackInSlot(slot).is(Items.PAPER));
            assertTrue(provider.getUpgrades().getStackInSlot(4).is(Items.REDSTONE));
            // Repeating a Shift-drag over full upgrade slots must not spill the remaining cards elsewhere.
            menu.quickMoveStack(menu.getPlayer(), 9);
            assertEquals(2, menu.getPlayerInventory().getItem(9).getCount());
        }
        assertTrue(provider.getPatternInv().isEmpty());
        assertTrue(provider.getReturnInv().isEmpty());
    }

    @Test
    void fullPatternSlotsKeepTheRemainderInTheBackpackAndNeverUseLockedRows() throws Exception {
        var provider = provider();
        var menu = serverMenu(provider);
        for (int slot = 0; slot < 35; slot++) {
            provider.getMenuPatternInventory().setItemDirect(slot, new ItemStack(Items.WRITABLE_BOOK));
        }
        menu.getPlayerInventory().setItem(9, new ItemStack(Items.WRITABLE_BOOK, 3));
        try (var patterns = mockStatic(PatternDetailsHelper.class)) {
            patterns.when(() -> PatternDetailsHelper.isEncodedPattern(any(ItemStack.class)))
                .thenAnswer(call -> ((ItemStack) call.getArgument(0)).is(Items.WRITABLE_BOOK));
            menu.quickMoveStack(menu.getPlayer(), 9);
            assertEquals(2, menu.getPlayerInventory().getItem(9).getCount());
            menu.quickMoveStack(menu.getPlayer(), 9);
            assertEquals(2, menu.getPlayerInventory().getItem(9).getCount());
        }
        assertTrue(provider.getPatternInv().getStackInSlot(35).is(Items.WRITABLE_BOOK));
        assertTrue(provider.getMenuPatternInventory().getStackInSlot(36).isEmpty());
        assertTrue(provider.getReturnInv().isEmpty());
    }

    @Test
    void shiftClickCanReturnPatternsToTheBackpackAndRejectsUnrelatedItems() throws Exception {
        var provider = provider();
        var menu = serverMenu(provider);
        var patternSlot = menu.getSlots(SlotSemantics.ENCODED_PATTERN).getFirst();
        provider.getMenuPatternInventory().setItemDirect(0, new ItemStack(Items.WRITABLE_BOOK));
        menu.quickMoveStack(menu.getPlayer(), patternSlot.index);
        assertTrue(provider.getPatternInv().getStackInSlot(0).isEmpty());
        assertTrue(menu.getPlayerInventory().getItem(9).is(Items.WRITABLE_BOOK));
        menu.getPlayerInventory().setItem(10, new ItemStack(Items.DIAMOND, 16));
        menu.quickMoveStack(menu.getPlayer(), 10);
        assertEquals(16, menu.getPlayerInventory().getItem(10).getCount());
        assertTrue(provider.getPatternInv().isEmpty());
        assertTrue(provider.getReturnInv().isEmpty());
    }

    @Test
    void replacingTheReturnRowKeepsPreviouslyBufferedResourcesAndTheirPersistence() throws Exception {
        var provider = provider();
        var savedStack = new GenericStack(AEFluidKey.of(Fluids.WATER), 17);
        provider.getReturnInv().setStack(0, savedStack);
        var menu = menu(provider);
        assertTrue(menu.getSlots(SlotSemantics.STORAGE).isEmpty());
        assertEquals(savedStack, menu.getReturnInv().getStack(0));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag saved = new CompoundTag();
        try (var keys = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            keys.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            provider.writeToNBT(saved, registries);
            var restored = provider();
            restored.readFromNBT(saved, registries);
            assertEquals(savedStack, restored.getReturnInv().getStack(0));
        }
    }

    private static LargeIntegratedWorkingStationPatternProviderMenu serverMenu(LargeWorkstationPatternProvider provider)
        throws Exception {
        var player = mock(Player.class);
        when(player.getCommandSenderWorld()).thenReturn(mock(Level.class));
        return menu(provider, new Inventory(player));
    }

    private static LargeIntegratedWorkingStationPatternProviderMenu menu(LargeWorkstationPatternProvider provider)
        throws Exception {
        return menu(provider, new Inventory(mock(Player.class)));
    }

    private static LargeIntegratedWorkingStationPatternProviderMenu menu(
        LargeWorkstationPatternProvider provider, Inventory inventory
    ) throws Exception {
        var constructor = LargeIntegratedWorkingStationPatternProviderMenu.class.getDeclaredConstructor(
            int.class, Inventory.class, PatternProviderLogicHost.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, inventory, provider.getWorkstationInterface());
    }

    private static LargeWorkstationPatternProvider provider() {
        var host = mock(ECOLargeIntegratedWorkingStationInterfaceBlockEntity.class);
        when(host.getMainNode()).thenReturn(mock(IManagedGridNode.class, RETURNS_SELF));
        when(host.getBlockEntity()).thenReturn(host);
        when(host.getBlockState()).thenReturn(Blocks.BARREL.defaultBlockState());
        try (var types = mockStatic(AEKeyTypes.class)) {
            types.when(AEKeyTypes::getAll).thenReturn(java.util.Set.of(AEKeyType.items(), AEKeyType.fluids()));
            var provider = new LargeWorkstationPatternProvider(host);
            when(host.getLogic()).thenReturn(provider);
            return provider;
        }
    }
}
