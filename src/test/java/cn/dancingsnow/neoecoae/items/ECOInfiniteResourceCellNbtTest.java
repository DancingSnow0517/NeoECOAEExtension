package cn.dancingsnow.neoecoae.items;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.Upgrades;
import cn.dancingsnow.neoecoae.api.IECOTier;
import cn.dancingsnow.neoecoae.api.storage.ECOCellType;
import cn.dancingsnow.neoecoae.api.storage.ECOStorageCells;
import cn.dancingsnow.neoecoae.client.NEItemColors;
import cn.dancingsnow.neoecoae.storage.persistence.ECOCellReference;
import cn.dancingsnow.neoecoae.storage.persistence.ECOStorageRepository;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.NativePatternTestSupport;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ECOInfiniteResourceCellNbtTest {
    private static ECOInfiniteResourceCellItem baseItem;
    private static ECOInfiniteResourceCellItem sandItem;
    private static ECOStorageCellItem finiteItem;
    private static Item upgradeCard;

    @BeforeAll
    static void bootstrap() throws Exception {
        InventoryTestBootstrap.initialize();
        if (appeng.core.AEConfig.instance() == null) {
            appeng.core.AEConfig.load(Files.createTempDirectory("infinite-cell-nbt-config"));
        }
        IECOTier tier = mock(IECOTier.class);
        when(tier.getTier()).thenReturn(9);
        when(tier.getStorageTotalBytes()).thenReturn(268435456L);
        var type = new ECOCellType(Component.literal("Item"), 63);
        baseItem = Registry.register(BuiltInRegistries.ITEM, "infinite_cell_nbt_test:base",
            new ECOInfiniteResourceCellItem(new Item.Properties(), tier, () -> type));
        sandItem = Registry.register(BuiltInRegistries.ITEM, "infinite_cell_nbt_test:sand",
            new ECOInfiniteResourceCellItem(new Item.Properties(), tier, () -> type,
                List.of(AEItemKey.of(Items.SAND)), "tooltip.neoecoae.infinite_sand"));
        finiteItem = Registry.register(BuiltInRegistries.ITEM, "infinite_cell_nbt_test:finite",
            new ECOStorageCellItem(new Item.Properties(), tier, AEKeyType.items(), () -> type));
        upgradeCard = Registry.register(BuiltInRegistries.ITEM, "infinite_cell_nbt_test:upgrade",
            new Item(new Item.Properties()));
        for (var item : List.of(baseItem, sandItem, finiteItem)) {
            Upgrades.add(upgradeCard, item, 4);
        }
        ECOStorageCells.register(ECOStorageCellItem.Handler.INSTANCE);
    }

    private static ECOInfiniteResourceCellItem item(boolean sand) {
        return sand ? sandItem : baseItem;
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void displayReadsKeepProcessingPatternOutputIdenticalToCraftedOutput(boolean sand) {
        var item = item(sand);
        ItemStack display = new ItemStack(item);
        NEItemColors.getCellColor(display, 1);
        item.getTooltipImage(display);
        item.appendHoverText(display, null, new java.util.ArrayList<>(), net.minecraft.world.item.TooltipFlag.NORMAL);
        var pattern = NativePatternTestSupport.processing(
            List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)),
            List.of(new GenericStack(AEItemKey.of(display), 1)));
        var craftedKey = AEItemKey.of(new ItemStack(item));
        assertNull(display.getTag());
        assertEquals(craftedKey, pattern.getOutputs()[0].what());
        KeyCounter awaited = new KeyCounter();
        awaited.add(pattern.getOutputs()[0].what(), 1);
        assertEquals(1, awaited.get(craftedKey));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void upgradeReadsAndSimulationsDoNotWriteButRealEditsStillPersist(boolean sand) {
        var item = item(sand);
        ItemStack stack = new ItemStack(item);
        var upgrades = item.getUpgrades(stack);
        assertEquals(4, upgrades.size());
        assertNull(stack.getTag());
        assertTrue(upgrades.insertItem(0, new ItemStack(upgradeCard), true).isEmpty());
        assertNull(stack.getTag());
        assertTrue(upgrades.insertItem(0, new ItemStack(upgradeCard), false).isEmpty());
        assertEquals(1, item.getUpgrades(stack).getInstalledUpgrades(upgradeCard));
        CompoundTag saved = stack.getTag().copy();
        item.getTooltipImage(stack);
        assertEquals(saved, stack.getTag());
        assertEquals(upgradeCard, item.getUpgrades(stack).extractItem(0, 1, false).getItem());
        assertEquals(0, item.getUpgrades(stack).getInstalledUpgrades(upgradeCard));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void readOnlyQueriesPreserveExistingTagsAndLegacyReference(boolean sand) {
        ItemStack stack = new ItemStack(item(sand));
        CompoundTag original = new CompoundTag();
        CompoundTag reference = new CompoundTag();
        reference.putInt("format", 1);
        reference.putUUID("id", UUID.randomUUID());
        original.put(ECOCellReference.TAG, reference);
        original.putString("custom_data", "retain");
        stack.setTag(original.copy());
        var key = AEItemKey.of(stack);
        NEItemColors.getCellColor(stack, 1);
        item(sand).getTooltipImage(stack);
        ECOStorageCellItem.getCellInventory(stack).persist();
        assertEquals(original, stack.getTag());
        assertEquals(key, AEItemKey.of(stack));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serverMountAndResourceOperationsNeverAllocateStorageIdentity(boolean sand, @TempDir Path directory) throws java.io.IOException {
        MinecraftServer server = mock(MinecraftServer.class);
        when(server.isSameThread()).thenReturn(true);
        ServerLevel level = mock(ServerLevel.class);
        when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(mock(DimensionDataStorage.class));
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(directory);
        try (var hooks = mockStatic(ServerLifecycleHooks.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            var item = item(sand);
            for (int index = 0; index < 2; index++) {
                ItemStack stack = new ItemStack(item);
                var cell = ECOStorageCellItem.getCellInventory(stack, mock(appeng.api.storage.cells.ISaveProvider.class));
                assertNotNull(cell);
                var resource = item.suppliedKeys().get(0);
                for (var action : Actionable.values()) {
                    assertEquals(64, cell.insert(resource, 64, action, null));
                    assertEquals(64, cell.extract(resource, 64, action, null));
                }
                cell.persist();
                assertFalse(cell.isInfiniteStorageEligible());
                assertNull(cell.storageSession());
                assertNull(ECOCellReference.id(stack));
                assertNull(stack.getTag());
                assertEquals(AEItemKey.of(item), AEItemKey.of(stack));
            }
            verify(server, never()).overworld();
            try (var files = Files.walk(directory)) {
                assertEquals(1, files.count(), "Infinite resource cells must not create SavedData files");
            }
        } finally {
            ECOStorageRepository.stopped(server);
        }
    }

    @Test
    void finiteCellsStillUsePersistentStorageAndDistinctIdentities(@TempDir Path directory) {
        MinecraftServer server = mock(MinecraftServer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(server.isSameThread()).thenReturn(true);
        when(server.overworld()).thenReturn(level);
        when(level.getDataStorage()).thenReturn(mock(DimensionDataStorage.class));
        when(server.getWorldPath(LevelResource.ROOT)).thenReturn(directory);
        try (var hooks = mockStatic(ServerLifecycleHooks.class)) {
            hooks.when(ServerLifecycleHooks::getCurrentServer).thenReturn(server);
            ItemStack first = new ItemStack(finiteItem), second = new ItemStack(finiteItem);
            var firstCell = ECOStorageCellItem.getCellInventory(first);
            var secondCell = ECOStorageCellItem.getCellInventory(second);
            assertTrue(firstCell.storageWritable());
            assertTrue(secondCell.storageWritable());
            assertNotNull(ECOCellReference.id(first));
            assertNotEquals(ECOCellReference.id(first), ECOCellReference.id(second));
            assertEquals(7, firstCell.insert(AEItemKey.of(Items.DIAMOND), 7, Actionable.MODULATE, null));
            assertEquals(7, firstCell.extract(AEItemKey.of(Items.DIAMOND), 7, Actionable.MODULATE, null));
        } finally {
            ECOStorageRepository.stopped(server);
        }
    }
}
