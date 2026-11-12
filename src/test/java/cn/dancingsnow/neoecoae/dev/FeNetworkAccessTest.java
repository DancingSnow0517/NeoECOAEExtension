package cn.dancingsnow.neoecoae.dev;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import com.glodblock.github.appflux.common.me.key.FluxKey;
import com.glodblock.github.appflux.common.me.key.type.EnergyType;
import cn.dancingsnow.neoecoae.util.InventoryTestBootstrap;
import net.neoforged.fml.ModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FeNetworkAccessTest {
    @BeforeAll static void bootstrap() { InventoryTestBootstrap.initialize(); }
    @AfterEach void stopReuse() { cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse.configure(false); }

    @Test void everyNetworkCallInvokesTheRealFluxFactoryPath() {
        var inventory = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var mods = mock(ModList.class);
        when(mods.isLoaded("appflux")).thenReturn(true);
        var key = FluxKey.of(EnergyType.FE);
        try (var loading = mockStatic(ModList.class); var factory = mockStatic(FluxKey.class)) {
            loading.when(ModList::get).thenReturn(mods);
            factory.when(() -> FluxKey.of(EnergyType.FE)).thenReturn(key);
            var access = FeNetworkAccess.create(inventory, source);
            when(inventory.insert(key, 100, Actionable.SIMULATE, source)).thenReturn(70L);
            when(inventory.insert(key, 70, Actionable.MODULATE, source)).thenReturn(60L);
            when(inventory.extract(key, 50, Actionable.SIMULATE, source)).thenReturn(40L);
            when(inventory.extract(key, 40, Actionable.MODULATE, source)).thenReturn(30L);
            assertEquals(70, access.insert(100, true));
            assertEquals(60, access.insert(70, false));
            assertEquals(40, access.extract(50, true));
            assertEquals(30, access.extract(40, false));
            factory.verify(() -> FluxKey.of(EnergyType.FE), times(4));
        }
    }

    @Test void missingAppliedFluxRejectsModelWithoutChangingGeneratorMode() {
        try (var loading = mockStatic(ModList.class)) {
            loading.when(ModList::get).thenReturn(null);
            assertFalse(FeNetworkAccess.available());
            assertThrows(IllegalStateException.class, () -> FeNetworkAccess.create(mock(MEStorage.class), IActionSource.empty()));
            var state = new GeneratorState();
            assertThrows(IllegalArgumentException.class, () -> state.configureFe(new FeWorkloadSettings(1, 1, 1, 1, 1, 1, 0, true)));
            assertNull(state.fe);
        }
    }

    @Test void enabledReuseIsStillMeasuredThroughTheAddonsFactory() {
        var inventory = mock(MEStorage.class);
        var source = mock(IActionSource.class);
        var mods = mock(ModList.class);
        when(mods.isLoaded("appflux")).thenReturn(true);
        var key = FluxKey.of(EnergyType.FE);
        cn.dancingsnow.neoecoae.compat.ae2.keyreuse.KeyReuse.configure(true);
        try (var loading = mockStatic(ModList.class); var factory = mockStatic(FluxKey.class)) {
            loading.when(ModList::get).thenReturn(mods);
            factory.when(() -> FluxKey.of(EnergyType.FE)).thenReturn(key);
            var access = FeNetworkAccess.create(inventory, source);
            access.insert(100, true);
            access.insert(100, false);
            access.extract(100, true);
            access.extract(100, false);
            factory.verify(() -> FluxKey.of(EnergyType.FE), times(4));
            verify(inventory).insert(key, 100, Actionable.SIMULATE, source);
            verify(inventory).extract(key, 100, Actionable.MODULATE, source);
        }
    }
}
