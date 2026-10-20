package cn.dancingsnow.neoecoae.blocks.entity.storage;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.storage.IECOBulkMarkableCellItem;
import cn.dancingsnow.neoecoae.integration.StorageBulkMarkingIntegration;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static cn.dancingsnow.neoecoae.blocks.entity.storage.ECOStorageMegaController.EcoMegaFilterResult.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOStorageMegaControllerTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @Test
    void addonInterfaceEnablesPanelAndRemovingCellHidesIt() {
        Fixture fixture = new Fixture(9);
        assertTrue(fixture.controller.hasEcoMegaBulkCell());
        assertEquals(1, fixture.controller.getEcoMegaBulkCellCount());
        assertFalse(fixture.controller.hasEcoMegaUpgradeCard());
        assertTrue(fixture.controller.upgradeItemHandler().insertItem(0, new ItemStack(Items.STONE), false)
            .is(Items.STONE));

        when(fixture.drive.getCellStack()).thenReturn(new ItemStack(Items.STONE));
        assertFalse(fixture.controller.hasEcoMegaBulkCell());
        when(fixture.host.getStorageDrivesForIntegration()).thenReturn(List.of());
        assertFalse(fixture.controller.hasEcoMegaBulkCell());
    }

    @Test
    void smallAddonCellSupportsManualWriteReadClearAndRejectsUnusedSlots() {
        Fixture fixture = new Fixture(9);
        ItemStack marker = new ItemStack(Items.IRON_BLOCK, 8);
        try (var integration = mockStatic(StorageBulkMarkingIntegration.class)) {
            integration.when(() -> StorageBulkMarkingIntegration.normalizeMarker(marker))
                .thenReturn(marker.copyWithCount(1));
            assertEquals(SUCCESS, fixture.controller.setEcoMegaFilterDirect(0, 0, 8, marker));
            assertEquals(AEItemKey.of(Items.IRON_BLOCK), fixture.config.getKey(8));
            assertTrue(fixture.controller.filterItemHandler().getStackInSlot(8).is(Items.IRON_BLOCK));
            assertEquals(1, fixture.controller.filterItemHandler().getStackInSlot(8).getCount());
            verify(fixture.drive).onCellConfigurationChanged();
            verify(fixture.host).notifyStorageConfigurationChanged();

            assertFalse(fixture.controller.isEcoMegaFilterSlotAvailable(9));
            assertTrue(fixture.controller.filterItemHandler().getStackInSlot(9).isEmpty());
            assertEquals(INVALID_TARGET, fixture.controller.setEcoMegaFilterDirect(0, 0, 9, marker));
            assertEquals(INVALID_TARGET, fixture.controller.setEcoMegaFilterDirect(0, 1, 0, marker));
            assertEquals(SUCCESS, fixture.controller.setEcoMegaFilterDirect(0, 0, 8, ItemStack.EMPTY));
            assertNull(fixture.config.getKey(8));
        }
    }

    @Test
    void pagesFollowActiveInventorySizeWithoutRequiringAnUpgradeCard() {
        Fixture fixture = new Fixture(26);
        assertEquals(2, fixture.controller.getEcoMegaPageCount());
        fixture.controller.changeSelectedEcoMegaPage(1);
        assertEquals(1, fixture.controller.getSelectedEcoMegaPage());
        assertTrue(fixture.controller.isEcoMegaFilterSlotAvailable(0));
        assertFalse(fixture.controller.isEcoMegaFilterSlotAvailable(1));
        assertEquals(SUCCESS, fixture.controller.setEcoMegaFilterDirect(0, 1, 0, ItemStack.EMPTY));
        assertEquals(INVALID_TARGET, fixture.controller.setEcoMegaFilterDirect(0, 1, 1, ItemStack.EMPTY));

        ConfigInventory smaller = config(9);
        when(fixture.item.getConfigInventory(fixture.stack)).thenReturn(smaller);
        assertEquals(0, fixture.controller.getSelectedEcoMegaPage());
        assertEquals(1, fixture.controller.getEcoMegaPageCount());
    }

    @Test
    void validationStillRejectsNonCompressibleAndDuplicateMarkers() {
        Fixture fixture = new Fixture(9);
        ItemStack marker = new ItemStack(Items.IRON_BLOCK);
        try (var integration = mockStatic(StorageBulkMarkingIntegration.class)) {
            integration.when(() -> StorageBulkMarkingIntegration.normalizeMarker(marker))
                .thenReturn(ItemStack.EMPTY);
            assertEquals(NOT_COMPRESSIBLE, fixture.controller.setEcoMegaFilterDirect(0, 0, 0, marker));
            integration.when(() -> StorageBulkMarkingIntegration.normalizeMarker(marker)).thenReturn(marker);
            assertEquals(SUCCESS, fixture.controller.setEcoMegaFilterDirect(0, 0, 0, marker));
            integration.when(() -> StorageBulkMarkingIntegration.isSameMarkerChain(any(), any()))
                .thenReturn(true);
            assertEquals(DUPLICATE_CHAIN, fixture.controller.setEcoMegaFilterDirect(0, 0, 1, marker));
            assertNull(fixture.config.getKey(1));
        }
    }

    private static ConfigInventory config(int slots) {
        try (var keyTypes = mockStatic(AEKeyTypes.class)) {
            keyTypes.when(AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.items()));
            return ConfigInventory.configTypes(slots).supportedTypes(Set.of(AEKeyType.items())).build();
        }
    }

    private static final class Fixture {
        final ECOStorageSystemBlockEntity host = mock(ECOStorageSystemBlockEntity.class);
        final ECODriveBlockEntity drive = mock(ECODriveBlockEntity.class);
        final ItemStack stack = mock(ItemStack.class);
        final IECOBulkMarkableCellItem item;
        final ConfigInventory config;
        final ECOStorageMegaController controller;

        Fixture(int slots) {
            Item addonItem = mock(Item.class, withSettings().extraInterfaces(IECOBulkMarkableCellItem.class));
            item = (IECOBulkMarkableCellItem) addonItem;
            config = config(slots);
            when(stack.getItem()).thenReturn(addonItem);
            when(item.getConfigInventory(stack)).thenReturn(config);
            when(drive.getCellStack()).thenReturn(stack);
            when(drive.getBlockPos()).thenReturn(BlockPos.ZERO);
            when(host.getStorageDrivesForIntegration()).thenReturn(List.of(drive));
            AtomicInteger page = new AtomicInteger();
            when(host.selectedEcoMegaPage()).thenAnswer(ignored -> page.get());
            doAnswer(call -> { page.set(call.getArgument(0)); return null; })
                .when(host).selectEcoMegaPage(anyInt());
            controller = new ECOStorageMegaController(host);
        }
    }
}
