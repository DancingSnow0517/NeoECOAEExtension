package cn.dancingsnow.neoecoae.blocks.entity;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import com.moakiee.ae2lt.me.key.LightningKey;
import com.moakiee.ae2lt.me.key.LightningKeyType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LargeWorkstationBatchPersistenceTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }

    @Test void rebatchedFractionalProgressSurvivesReloadAndRejectsInvalidRemainders() throws Exception {
        try (var keys = mockStatic(AEKeyTypes.class);
             var internal = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            internal.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            keys.when(() -> AEKeyTypes.get(AEKeyType.fluids().getId())).thenReturn(AEKeyType.fluids());
            var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
            var constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var owned = new KeyCounter();
            owned.add(AEFluidKey.of(Fluids.WATER), 1_048_576);
            var output = new KeyCounter();
            output.add(AEFluidKey.of(Fluids.LAVA), 262_144);
            Object batch = constructor.newInstance(262_144L, 1_000L, 9, 1, owned, output,
                null, new ResourceLocation("test:rebatched"), null, null, 123L);
            var progress = type.getDeclaredField("progress");
            progress.setAccessible(true);
            progress.set(batch, 1);
            var remainder = type.getDeclaredField("progressRemainder");
            remainder.setAccessible(true);
            remainder.set(batch, 196_601L);
            var save = type.getDeclaredMethod("save");
            save.setAccessible(true);
            var load = type.getDeclaredMethod("load", CompoundTag.class, long.class);
            load.setAccessible(true);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            CompoundTag tag = (CompoundTag) save.invoke(batch);
            var restored = load.invoke(null, tag, 234L);
            assertNotNull(restored);
            assertEquals(tag, save.invoke(restored));
            assertEquals(65_535L * 7, (long) progress.getInt(restored) * 262_144 + remainder.getLong(restored));

            tag.putLong("progressRemainder", -1);
            assertNull(load.invoke(null, tag, 234L));
            tag.putLong("progressRemainder", 262_144);
            assertNull(load.invoke(null, tag, 234L));
            tag.putInt("progress", 200);
            tag.putLong("progressRemainder", 1);
            assertNull(load.invoke(null, tag, 234L));
        }
    }

    @Test void partialLightningAndLongEnergySurviveSaveReload() throws Exception {
        try (var keys = mockStatic(AEKeyTypes.class);
             var internal = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            internal.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            keys.when(() -> AEKeyTypes.get(AEKeyType.fluids().getId())).thenReturn(AEKeyType.fluids());
            keys.when(() -> AEKeyTypes.get(LightningKey.TYPE_ID)).thenReturn(LightningKeyType.INSTANCE);
            var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
            var constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var owned = new KeyCounter();
            owned.add(AEFluidKey.of(Fluids.WATER), 16000);
            owned.add(LightningKey.EXTREME_HIGH_VOLTAGE, 10);
            var output = new KeyCounter();
            output.add(AEFluidKey.of(Fluids.LAVA), 4000);
            Object batch = constructor.newInstance(4L, 5_000_000_000L, 0, 1, owned, output,
                null, new ResourceLocation("ae2lt:test"), null, null, 123L);
            var missingField = type.getDeclaredField("missingExtras");
            missingField.setAccessible(true);
            ((KeyCounter) missingField.get(batch)).add(LightningKey.EXTREME_HIGH_VOLTAGE, 18);
            var save = type.getDeclaredMethod("save");
            save.setAccessible(true);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            CompoundTag tag = (CompoundTag) save.invoke(batch);
            var load = type.getDeclaredMethod("load", CompoundTag.class, long.class);
            load.setAccessible(true);
            var restored = load.invoke(null, tag, 234L);
            assertNotNull(restored);
            var resaved = (CompoundTag) save.invoke(restored);
            assertEquals(tag, resaved);
            assertEquals(5_000_000_000L, resaved.getLong("energyPerCraft"));
            assertEquals(18, ((KeyCounter) missingField.get(restored)).get(LightningKey.EXTREME_HIGH_VOLTAGE));

            // Legacy batches stored an int; NBT numeric widening must preserve that energy cost.
            tag.putInt("energyPerCraft", 12345);
            assertNotNull(load.invoke(null, tag, 234L));
            tag.putInt("coolingTier", 6);
            tag.putInt("energyMultiplier", 32);
            var migrated = load.invoke(null, tag, 234L);
            assertNotNull(migrated, "Legacy overclocked batches retain their owned materials");
            var migratedTag = (CompoundTag) save.invoke(migrated);
            assertEquals(1, migratedTag.getInt("energyMultiplier"));
            assertEquals(tag.get("inputTotal"), migratedTag.get("inputTotal"));
            tag.putInt("coolingTier", 0);
            tag.putInt("energyMultiplier", 1);
            tag.putLong("craftCount", 1024);
            assertNotNull(load.invoke(null, tag, 234L), "Accepted legacy normal batches must not be discarded");
            tag.putInt("progress", 1);
            assertNull(load.invoke(null, tag, 234L), "Cannot restore processing with unpaid lightning");
        }
    }

    @Test void downgradedTaskKeepsItsFallbackPaidWorkAndRemainingCompatibilityInputsAfterReload() throws Exception {
        try (var keys = mockStatic(AEKeyTypes.class);
             var internal = mockStatic(appeng.api.stacks.AEKeyTypesInternal.class)) {
            internal.when(appeng.api.stacks.AEKeyTypesInternal::getRegistry)
                .thenReturn(cn.dancingsnow.neoecoae.util.LargeWorkstationTestKeys.REGISTRY);
            keys.when(() -> AEKeyTypes.get(AEKeyType.fluids().getId())).thenReturn(AEKeyType.fluids());
            keys.when(() -> AEKeyTypes.get(LightningKey.TYPE_ID)).thenReturn(LightningKeyType.INSTANCE);
            var type = Class.forName(ECOLargeIntegratedWorkingStationBlockEntity.class.getName() + "$PendingBatch");
            var constructor = type.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            var owned = new KeyCounter();
            owned.add(AEFluidKey.of(Fluids.WATER), 3_072);
            owned.add(LightningKey.EXTREME_HIGH_VOLTAGE, 256);
            var outputs = new KeyCounter();
            outputs.add(AEFluidKey.of(Fluids.LAVA), 768);
            Object batch = constructor.newInstance(768L, 1_000L, 9, 1, owned, outputs,
                null, new ResourceLocation("test:downgraded"), null, null, 123L);
            var segmented = type.getDeclaredField("segmented");
            segmented.setAccessible(true);
            segmented.setBoolean(batch, true);
            var progress = type.getDeclaredField("progress");
            progress.setAccessible(true);
            progress.setInt(batch, 33);
            var remainder = type.getDeclaredField("progressRemainder");
            remainder.setAccessible(true);
            remainder.setLong(batch, 256);
            var missing = type.getDeclaredField("missingExtras");
            missing.setAccessible(true);
            ((KeyCounter) missing.get(batch)).add(LightningKey.EXTREME_HIGH_VOLTAGE, 512);
            var save = type.getDeclaredMethod("save");
            save.setAccessible(true);
            var load = type.getDeclaredMethod("load", CompoundTag.class, long.class);
            load.setAccessible(true);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

            CompoundTag tag = (CompoundTag) save.invoke(batch);
            Object restored = load.invoke(null, tag, 234L);
            assertNotNull(restored);
            assertEquals(tag, save.invoke(restored));
            assertTrue(segmented.getBoolean(restored));
            assertEquals(25_600L, (long) progress.getInt(restored) * 768 + remainder.getLong(restored));
            assertEquals(512, ((KeyCounter) missing.get(restored)).get(LightningKey.EXTREME_HIGH_VOLTAGE));

            // A blocked portion can coexist with the remaining task in the same saved queue entry.
            progress.setInt(batch, 0);
            remainder.setLong(batch, 0);
            var pending = type.getDeclaredField("pendingOutput");
            pending.setAccessible(true);
            ((KeyCounter) pending.get(batch)).add(AEFluidKey.of(Fluids.LAVA), 256);
            tag = (CompoundTag) save.invoke(batch);
            restored = load.invoke(null, tag, 234L);
            assertNotNull(restored);
            assertEquals(tag, save.invoke(restored));

            tag.putBoolean("segmented", false);
            tag.putInt("progress", 1);
            assertNull(load.invoke(null, tag, 234L), "Unsegmented batches still require all extras before processing");
        }
    }
}
