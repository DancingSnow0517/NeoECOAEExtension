package cn.dancingsnow.neoecoae.storage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypesInternal;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageInterfaceFilterTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test
    void savedSamplesKeepExactItemComponentsFluidsAndAddonKeys() {
        try (var keys = mockStatic(AEKeyTypesInternal.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            ItemStack named = new ItemStack(Items.STONE, 32);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Exact sample"));
            var item = AEItemKey.of(named);
            var water = AEFluidKey.of(Fluids.WATER);
            // 26.1 port: the ae2lt LightningKey (addon key) part was removed; its 1.21.1 classes cannot run on 26.1.
            CompoundTag empty = new CompoundTag();
            CompoundTag saved = StorageInterfaceFilter.with(empty, 0, item, registries);
            saved = StorageInterfaceFilter.with(saved, 13, water, registries);
            assertTrue(empty.isEmpty());
            var restored = new StorageInterfaceFilter(saved.copy(), registries);
            assertTrue(restored.test(item));
            assertTrue(restored.test(water));
            assertFalse(restored.test(AEItemKey.of(Items.STONE)));
            assertFalse(restored.test(AEFluidKey.of(Fluids.LAVA)));
            assertEquals(water, StorageInterfaceFilter.get(saved, 13, registries));
        }
    }

    @Test
    void clearingTheLastSampleRestoresUnrestrictedTransferAndBoundsAreChecked() {
        try (var keys = mockStatic(AEKeyTypesInternal.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var stone = AEItemKey.of(Items.STONE);
            CompoundTag saved = StorageInterfaceFilter.with(new CompoundTag(), 26, stone, registries);
            assertFalse(new StorageInterfaceFilter(saved, registries).test(AEItemKey.of(Items.DIRT)));
            saved = StorageInterfaceFilter.with(saved, 26, null, registries);
            assertTrue(new StorageInterfaceFilter(saved, registries).test(AEItemKey.of(Items.DIRT)));
            assertThrows(IllegalArgumentException.class, () -> StorageInterfaceFilter.with(new CompoundTag(), -1, stone, registries));
            assertThrows(IllegalArgumentException.class, () -> StorageInterfaceFilter.with(new CompoundTag(), 27, stone, registries));
        }
    }

    @Test
    void unavailableSavedResourceRemainsRestrictive() {
        try (var keys = mockStatic(AEKeyTypesInternal.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            CompoundTag saved = new CompoundTag();
            saved.put("0", new CompoundTag());
            assertFalse(new StorageInterfaceFilter(saved, registries).test(AEItemKey.of(Items.STONE)));
        }
    }
}
