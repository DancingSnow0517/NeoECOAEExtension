package cn.dancingsnow.neoecoae.integration.dataenergistics;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.*;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.util.ConfigInventory;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECODataStorageCellTest {
    @BeforeAll
    static void bootstrap() {
        InventoryTestBootstrap.initialize();
    }

    @ParameterizedTest
    @CsvSource({"1,16777216", "2,67108864", "3,268435456"})
    void storesBothDataChannelsAndRejectsExternalKeysOnEveryInsertPath(int tierNumber, long capacity) {
        IECOTier tier = mock(IECOTier.class);
        when(tier.getTier()).thenReturn(tierNumber);
        when(tier.getStorageTotalBytes()).thenReturn(capacity);
        AEKeyType digital = mock(AEKeyType.class);
        AEKeyType binary = mock(AEKeyType.class);
        when(digital.getAmountPerByte()).thenReturn(8);
        when(binary.getAmountPerByte()).thenReturn(8);
        AEKey[] keys = {key(digital), key(digital), key(digital), key(binary)};
        try (var registry = mockStatic(AEKeyTypes.class)) {
            registry.when(() -> AEKeyTypes.get(ResourceLocation.parse("data_energistics:digitalization")))
                .thenReturn(digital);
            registry.when(() -> AEKeyTypes.get(ResourceLocation.parse("data_energistics:manifest_binary")))
                .thenReturn(binary);
            var item = mock(ECODataStorageCellItem.class, CALLS_REAL_METHODS);
            doReturn(digital).when(item).getKeyType();
            doReturn(4).when(item).getTotalTypes();
            doReturn(tier).when(item).getTier();
            doReturn(tier.getStorageTotalBytes()).when(item).getBytes();
            doReturn(1 << (12 + tier.getTier())).when(item).getBytesPerType();
            ItemStack stack = mock(ItemStack.class);
            when(stack.getItem()).thenReturn(item);
            when(stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY))
                .thenReturn(net.minecraft.world.item.component.CustomData.EMPTY);
            AtomicReference<List<GenericStack>> stored = new AtomicReference<>(List.of());
            when(stack.getOrDefault(eq(AEComponents.STORAGE_CELL_INV), anyList()))
                .thenAnswer(inv -> stored.get());
            when(stack.set(eq(AEComponents.STORAGE_CELL_INV), anyList())).thenAnswer(inv -> {
                stored.set(inv.getArgument(1));
                return null;
            });
            var upgrades = mock(IUpgradeInventory.class);
            when(upgrades.isInstalled(AEItems.VOID_CARD)).thenReturn(true);
            doReturn(upgrades).when(item).getUpgrades(stack);
            var config = mock(ConfigInventory.class);
            when(config.keySet()).thenReturn(Set.of());
            doReturn(config).when(item).getConfigInventory(stack);
            doReturn(FuzzyMode.IGNORE_ALL).when(item).getFuzzyMode(stack);

            assertEquals(Set.of(digital, binary), item.getKeyTypes());
            var cell = item.createCellInventory(stack, null);
            for (AEKey key : keys) {
                assertFalse(item.isBlackListed(stack, key));
                assertEquals(8, cell.insert(key, 8, Actionable.SIMULATE, null));
                assertEquals(8, cell.insert(key, 8, Actionable.MODULATE, null));
            }
            assertEquals(4, cell.getStoredItemTypes());
            assertEquals(4L * item.getBytesPerType() + 4, cell.getUsedBytes());
            var restored = item.createCellInventory(stack, null);
            for (AEKey key : keys) {
                assertEquals(8, restored.extract(key, 8, Actionable.SIMULATE, null));
                assertEquals(8, restored.insertForMigration(key, 8, Actionable.SIMULATE));
                assertEquals(8, restored.simulateInsertForMigration(key, 8, restored.getAvailableStacks()));
            }
            for (AEKey rejected : List.of(AEItemKey.of(Items.STONE), AEFluidKey.of(Fluids.WATER),
                key(mock(AEKeyType.class)))) {
                assertTrue(item.isBlackListed(stack, rejected));
                assertEquals(0, restored.insert(rejected, 100, Actionable.MODULATE, null));
                assertEquals(0, restored.insertForMigration(rejected, 100, Actionable.MODULATE));
                assertEquals(0, restored.simulateInsertForMigration(rejected, 100, restored.getAvailableStacks()));
            }
            assertEquals(4, restored.getStoredItemTypes());
            // Capacity simulation must never accept a fifth distinct resource, even from an allowed channel.
            assertEquals(0, restored.insertForMigration(key(digital), 8, Actionable.SIMULATE));
        }
    }

    private static AEKey key(AEKeyType type) {
        AEKey key = mock(AEKey.class);
        when(key.getType()).thenReturn(type);
        when(type.contains(key)).thenReturn(true);
        return key;
    }
}
