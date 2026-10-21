package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypesInternal;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.core.RegistryAccess;
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
            named.setHoverName(Component.literal("Exact sample"));
            var item = AEItemKey.of(named);
            var water = AEFluidKey.of(Fluids.WATER);
            var lightning = LightningKey.EXTREME_HIGH_VOLTAGE;
            CompoundTag empty = new CompoundTag();
            CompoundTag saved = StorageInterfaceFilter.with(empty, 0, item);
            saved = StorageInterfaceFilter.with(saved, 13, water);
            saved = StorageInterfaceFilter.with(saved, 26, lightning);
            assertTrue(empty.isEmpty());
            var restored = new StorageInterfaceFilter(saved.copy());
            assertTrue(restored.test(item));
            assertTrue(restored.test(water));
            assertTrue(restored.test(lightning));
            assertFalse(restored.test(AEItemKey.of(Items.STONE)));
            assertFalse(restored.test(AEFluidKey.of(Fluids.LAVA)));
            assertEquals(water, StorageInterfaceFilter.get(saved, 13));
            assertEquals(lightning, StorageInterfaceFilter.get(saved, 26));
        }
    }

    @Test
    void clearingTheLastSampleRestoresUnrestrictedTransferAndBoundsAreChecked() {
        try (var keys = mockStatic(AEKeyTypesInternal.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var stone = AEItemKey.of(Items.STONE);
            CompoundTag saved = StorageInterfaceFilter.with(new CompoundTag(), 26, stone);
            assertFalse(new StorageInterfaceFilter(saved).test(AEItemKey.of(Items.DIRT)));
            saved = StorageInterfaceFilter.with(saved, 26, null);
            assertTrue(new StorageInterfaceFilter(saved).test(AEItemKey.of(Items.DIRT)));
            assertThrows(IllegalArgumentException.class, () -> StorageInterfaceFilter.with(new CompoundTag(), -1, stone));
            assertThrows(IllegalArgumentException.class, () -> StorageInterfaceFilter.with(new CompoundTag(), 27, stone));
        }
    }

    @Test
    void unavailableSavedResourceRemainsRestrictive() {
        try (var keys = mockStatic(AEKeyTypesInternal.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            CompoundTag saved = new CompoundTag();
            saved.put("0", new CompoundTag());
            assertFalse(new StorageInterfaceFilter(saved).test(AEItemKey.of(Items.STONE)));
        }
    }
}
