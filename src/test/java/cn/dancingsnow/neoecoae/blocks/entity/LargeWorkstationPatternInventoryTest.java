package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.networking.IManagedGridNode;
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
        itemField.set(AEItems.CAPACITY_CARD, Items.PAPER);
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
        assertTrue(AEItems.CAPACITY_CARD.isSameAs(provider.getCapacityUpgrades().extractItem(0, 1, false)));
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
        provider.writeToNBT(saved);
        var restored = provider();
        restored.readFromNBT(saved);
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
        original.writeToNBT(saved);
        var restored = provider();
        restored.readFromNBT(saved);
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
        oldInventory.writeToNBT(saved, PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS);
        var restored = provider();
        restored.readFromNBT(saved);
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
        original.writeToNBT(saved);
        var restored = provider();
        restored.readFromNBT(saved);
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
        assertEquals(36, first.getPatternSlotCount());
        withoutCards.getCapacityUpgrades().insertItem(0, AEItems.CAPACITY_CARD.stack(), false);
        assertEquals(45, first.getPatternSlotCount());
        assertEquals(72, first.getSlots(SlotSemantics.ENCODED_PATTERN).size());
    }

    private static LargeIntegratedWorkingStationPatternProviderMenu menu(LargeWorkstationPatternProvider provider)
        throws Exception {
        var constructor = LargeIntegratedWorkingStationPatternProviderMenu.class.getDeclaredConstructor(
            int.class, Inventory.class, PatternProviderLogicHost.class);
        constructor.setAccessible(true);
        return constructor.newInstance(1, new Inventory(mock(Player.class)), provider.getWorkstationInterface());
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
