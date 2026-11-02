package cn.dancingsnow.neoecoae.items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.crafting.amount.ExactAmount;
import cn.dancingsnow.neoecoae.storage.ECOInfiniteResourceCell;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOInfiniteSandCellTest {
    private static ECOInfiniteResourceCellItem sandItem;
    private static ECOInfiniteResourceCellItem baseItem;
    private org.mockito.MockedStatic<appeng.api.stacks.AEKeyTypes> keyTypes;

    @org.junit.jupiter.api.BeforeEach void keyTypes() {
        keyTypes = mockStatic(appeng.api.stacks.AEKeyTypes.class);
        keyTypes.when(appeng.api.stacks.AEKeyTypes::getAll).thenReturn(Set.of(AEKeyType.items(), AEKeyType.fluids()));
    }

    @org.junit.jupiter.api.AfterEach void closeKeyTypes() { keyTypes.close(); }

    @BeforeAll static void bootstrap() throws ReflectiveOperationException {
        InventoryTestBootstrap.initialize();
        ((net.minecraft.core.MappedRegistry<?>) BuiltInRegistries.ITEM).unfreeze();
        try (var configuration = mockStatic(appeng.core.AEConfig.class)) {
            configuration.when(appeng.core.AEConfig::instance).thenReturn(mock(appeng.core.AEConfig.class));
            Class.forName("appeng.core.definitions.AEItems");
        }
        IECOTier tier = mock(IECOTier.class);
        when(tier.getTier()).thenReturn(9);
        when(tier.getStorageTotalBytes()).thenReturn(268435456L);
        var type = new ECOCellType(Component.literal("Item"), 63);
        sandItem = Registry.register(BuiltInRegistries.ITEM, "infinite_sand_test:sand", new ECOInfiniteResourceCellItem(
            new Item.Properties(), tier, () -> type, List.of(AEItemKey.of(Items.SAND)), "tooltip.neoecoae.infinite_sand") {
                @Override public appeng.api.upgrades.IUpgradeInventory getUpgrades(ItemStack stack) {
                    return mock(appeng.api.upgrades.IUpgradeInventory.class);
                }
            });
        baseItem = Registry.register(BuiltInRegistries.ITEM, "infinite_sand_test:base",
            new ECOInfiniteResourceCellItem(new Item.Properties(), tier, () -> type));
    }

    @Test void sandSupplyCannotBeWidenedWithPartitionConfiguration() {
        ItemStack stack = new ItemStack(sandItem);
        assertEquals(Set.of(AEKeyType.items()), sandItem.getKeyTypes());
        assertFalse(sandItem.isBlackListed(stack, AEItemKey.of(Items.SAND)));
        for (var key : List.of(AEItemKey.of(Items.RED_SAND), AEItemKey.of(Items.COBBLESTONE), AEFluidKey.of(Fluids.WATER))) {
            assertTrue(sandItem.isBlackListed(stack, key));
        }
    }

    @Test void extractionIsUnboundedAndOnlySandCanBeInserted() {
        var cell = new ECOInfiniteResourceCell(new ItemStack(sandItem), null);
        var sand = AEItemKey.of(Items.SAND);
        for (var action : Actionable.values()) {
            assertEquals(Long.MAX_VALUE, cell.extract(sand, Long.MAX_VALUE, action, null));
            assertEquals(Long.MAX_VALUE, cell.insert(sand, Long.MAX_VALUE, action, null));
            assertEquals(0, cell.extract(AEItemKey.of(Items.RED_SAND), 64, action, null));
        }
        assertFalse(cell.isInfiniteStorageEligible());
        KeyCounter listing = new KeyCounter();
        cell.getAvailableStacks(listing);
        cell.getAvailableStacks(listing);
        assertEquals(1, listing.size());
        assertEquals(Long.MAX_VALUE, listing.get(sand));
        cell.neoecoae$visitExactAmounts((key, amount) -> {
            assertEquals(sand, key);
            assertEquals(ExactAmount.unbounded(), amount);
        });
    }

    @Test void originalBaseResourceCellStillOffersOnlyItsThreeResources() {
        assertEquals(ECOInfiniteResourceCellItem.lockedKeys(), baseItem.suppliedKeys());
        assertFalse(baseItem.suppliesKey(AEItemKey.of(Items.SAND)));
        assertTrue(baseItem.suppliesKey(AEItemKey.of(Items.COBBLESTONE)));
        assertTrue(baseItem.suppliesKey(AEFluidKey.of(Fluids.WATER)));
        assertTrue(baseItem.suppliesKey(AEFluidKey.of(Fluids.LAVA)));
    }
}
