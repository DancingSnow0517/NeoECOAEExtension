package cn.dancingsnow.neoecoae.impl.storage;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.api.stacks.AEKeyTypes;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys;
import com.moakiee.ae2lt.me.key.LightningKey;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageInterfaceFilterTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    private MockedStatic<AEKeyTypes> supportedTypes;

    @BeforeEach void installSupportedTypes() {
        supportedTypes = mockStatic(AEKeyTypes.class);
        supportedTypes.when(AEKeyTypes::getAll).thenReturn(
            java.util.stream.StreamSupport.stream(LargeWorkstationTestKeys.REGISTRY.spliterator(), false)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @AfterEach void closeSupportedTypes() { supportedTypes.close(); }

    @Test
    void savedSamplesKeepExactItemComponentsFluidsAndAddonKeys() {
        try (var keys = mockStatic(AEKeyTypesInternal.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            ItemStack named = new ItemStack(Items.STONE, 32);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Exact sample"));
            var item = AEItemKey.of(named);
            var water = AEFluidKey.of(Fluids.WATER);
            var lightning = LightningKey.EXTREME_HIGH_VOLTAGE;
            CompoundTag empty = new CompoundTag();
            CompoundTag saved = StorageInterfaceFilter.with(empty, 0, item, registries);
            saved = StorageInterfaceFilter.with(saved, 13, water, registries);
            saved = StorageInterfaceFilter.with(saved, 26, lightning, registries);
            assertTrue(empty.isEmpty());
            var restored = new StorageInterfaceFilter(saved.copy(), registries);
            assertTrue(restored.test(item));
            assertTrue(restored.test(water));
            assertTrue(restored.test(lightning));
            assertFalse(restored.test(AEItemKey.of(Items.STONE)));
            assertFalse(restored.test(AEFluidKey.of(Fluids.LAVA)));
            assertEquals(water, StorageInterfaceFilter.get(saved, 13, registries));
            assertEquals(lightning, StorageInterfaceFilter.get(saved, 26, registries));
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
    @Test
    void mixedLegacySlotsRetainResolvableKeysWithoutOpeningMissingAddonSlots() {
        try (var keys = mockStatic(AEKeyTypesInternal.class)) {
            keys.when(AEKeyTypesInternal::getRegistry).thenReturn(LargeWorkstationTestKeys.REGISTRY);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            CompoundTag saved = new CompoundTag();
            CompoundTag item = new CompoundTag();
            item.putString("#t", "ae2:i"); item.putString("id", "minecraft:stone");
            CompoundTag fluid = new CompoundTag();
            fluid.putString("#t", "ae2:f"); fluid.putString("id", "minecraft:water");
            CompoundTag missing = new CompoundTag();
            missing.putString("#t", "absent:channel"); missing.putString("id", "absent:key");
            saved.put("0", item); saved.put("13", fluid); saved.put("26", missing);
            saved.put("5", new CompoundTag());
            saved.put("99", item.copy());
            CompoundTag before = saved.copy();
            var filter = new StorageInterfaceFilter(saved, registries);
            assertTrue(filter.test(AEItemKey.of(Items.STONE)));
            assertTrue(filter.test(AEFluidKey.of(Fluids.WATER)));
            assertFalse(filter.test(AEItemKey.of(Items.DIRT)));
            assertEquals(before, saved, "loading cannot rewrite unresolved legacy payloads");
            CompoundTag cleared = StorageInterfaceFilter.with(saved, 0, null, registries);
            cleared = StorageInterfaceFilter.with(cleared, 13, null, registries);
            assertFalse(new StorageInterfaceFilter(cleared, registries).test(AEItemKey.of(Items.DIRT)));
            assertNull(StorageInterfaceFilter.get(saved, 99, registries));
        }
    }

}
